package com.opc.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.opc.system.mapper.SysUserMapper;

@ExtendWith(MockitoExtension.class)
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

        assertEquals(AdminBootstrapResult.ALREADY_INITIALIZED, service.bootstrap(STRONG_PASSWORD));
        verify(userMapper).initializeAdminPassword(eq(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH),
                org.mockito.ArgumentMatchers.argThat(hash -> new BCryptPasswordEncoder().matches(STRONG_PASSWORD, hash)));
    }

    @Test
    void reportsConcurrentConditionalUpdateFailureAsAlreadyInitialized()
    {
        when(userMapper.initializeAdminPassword(anyString(), anyString())).thenReturn(0);

        assertEquals(AdminBootstrapResult.ALREADY_INITIALIZED, service.bootstrap(STRONG_PASSWORD));
    }
}
