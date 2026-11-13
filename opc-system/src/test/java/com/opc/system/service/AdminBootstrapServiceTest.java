package com.opc.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.opc.common.core.domain.entity.SysUser;
import com.opc.system.mapper.SysUserMapper;

@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class AdminBootstrapServiceTest
{
    private static final String STRONG_PASSWORD = "Strong!Pass2026";

    @Mock
    private SysUserMapper userMapper;

    private AdminBootstrapService service;

    @BeforeEach
    void setUp()
    {
        service = new AdminBootstrapService(userMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    void returnsNotConfiguredWhenPasswordIsMissing(String password)
    {
        assertEquals(AdminBootstrapResult.NOT_CONFIGURED, service.bootstrap(password));
        verify(userMapper, never()).initializeAdminPassword(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "Short1!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecial2026" })
    void rejectsWeakPasswords(String password)
    {
        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(password));
        verify(userMapper, never()).initializeAdminPassword(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!xxxxxxxx",
            "Aa1!xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    })
    void acceptsStrongPasswordsAtCharacterBoundaries(String password)
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(1);

        assertEquals(AdminBootstrapResult.INITIALIZED, service.bootstrap(password));
    }

    @Test
    void acceptsStrongPasswordAtBcrypt72ByteBoundary()
    {
        String password = "Aa1!" + "é".repeat(34);
        assertEquals(72, password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(1);

        assertEquals(AdminBootstrapResult.INITIALIZED, service.bootstrap(password));
    }

    @Test
    void rejectsPasswordBeyondBcrypt72ByteBoundary()
    {
        String password = "Aa1!" + "é".repeat(33) + "€";
        assertEquals(73, password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);

        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(password));
        verify(userMapper, never()).initializeAdminPassword(anyString(), anyString());
    }

    @Test
    void doesNotTreatSurrogatePairsAsSpecialCharacters()
    {
        String password = "Aa1" + "\uD801\uDC28".repeat(9);
        assertEquals(12, password.codePointCount(0, password.length()));

        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(password));
        verify(userMapper, never()).initializeAdminPassword(anyString(), anyString());
    }

    @Test
    void atomicallyInitializesAdminWithBcryptPassword()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(1);

        assertEquals(AdminBootstrapResult.INITIALIZED, service.bootstrap(STRONG_PASSWORD));

        ArgumentCaptor<String> expectedPassword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> newPassword = ArgumentCaptor.forClass(String.class);
        verify(userMapper).initializeAdminPassword(expectedPassword.capture(), newPassword.capture());
        assertEquals(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH, expectedPassword.getValue());
        assertNotEquals(STRONG_PASSWORD, newPassword.getValue());
        assertTrue(new BCryptPasswordEncoder().matches(STRONG_PASSWORD, newPassword.getValue()));
    }

    @Test
    void doesNotOverwriteAnInitializedAdministrator()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);
        when(userMapper.selectUserById(1L)).thenReturn(admin("0", "initialized-hash"));

        assertEquals(AdminBootstrapResult.ALREADY_INITIALIZED, service.bootstrap(STRONG_PASSWORD));
        verify(userMapper).initializeAdminPassword(eq(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH),
                org.mockito.ArgumentMatchers.argThat(hash -> new BCryptPasswordEncoder().matches(STRONG_PASSWORD, hash)));
    }

    @Test
    void reportsConcurrentConditionalUpdateFailureAsAlreadyInitialized()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);
        when(userMapper.selectUserById(1L)).thenReturn(admin("0", "concurrent-hash"));

        assertEquals(AdminBootstrapResult.ALREADY_INITIALIZED, service.bootstrap(STRONG_PASSWORD));
    }

    @Test
    void rejectsMissingAdministratorAfterConditionalUpdateFailure(CapturedOutput output)
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);

        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(STRONG_PASSWORD));
        assertTrue(output.getAll().contains("Administrator bootstrap rejected"));
        assertFalse(output.getAll().contains(STRONG_PASSWORD));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1", "2" })
    void rejectsAdministratorInUnexpectedStatus(String status)
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);
        when(userMapper.selectUserById(1L)).thenReturn(admin(status, "unexpected-hash"));

        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(STRONG_PASSWORD));
    }

    @Test
    void rejectsWrongAccountAtAdministratorId()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);
        SysUser user = admin("0", "initialized-hash");
        user.setUserName("not-admin");

        when(userMapper.selectUserById(1L)).thenReturn(user);
        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(STRONG_PASSWORD));
    }

    @Test
    void rejectsAdministratorRecordWithWrongId()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);
        SysUser user = admin("0", "initialized-hash");
        user.setUserId(2L);

        when(userMapper.selectUserById(1L)).thenReturn(user);
        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(STRONG_PASSWORD));
    }

    @Test
    void rejectsDisabledAdministratorStillUsingPlaceholderHash()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);
        when(userMapper.selectUserById(1L)).thenReturn(admin("0", AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH));

        assertEquals(AdminBootstrapResult.REJECTED, service.bootstrap(STRONG_PASSWORD));
    }

    private SysUser admin(String status, String password)
    {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName("admin");
        user.setStatus(status);
        user.setPassword(password);
        return user;
    }
}
