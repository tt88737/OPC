package com.opc.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.opc.common.core.domain.entity.SysUser;
import com.opc.common.core.domain.model.RegisterBody;
import com.opc.system.service.ISysConfigService;
import com.opc.system.service.ISysUserService;

class SysRegisterServiceTest
{
    private ISysUserService userService;
    private SysRegisterService service;

    @BeforeEach
    void setUp()
    {
        userService = mock(ISysUserService.class);
        ISysConfigService configService = mock(ISysConfigService.class);
        service = new SysRegisterService();
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "configService", configService);
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(userService.checkUserNameUnique(any(SysUser.class))).thenReturn(true);
    }

    @Test
    void acceptsPasswordAtBcryptByteLimit()
    {
        RegisterBody body = registration("Aa1!" + "x".repeat(52) + "\u00E9".repeat(8));
        when(userService.registerUser(any(SysUser.class))).thenReturn(false);

        assertDoesNotThrow(() -> service.register(body));

        verify(userService).registerUser(any(SysUser.class));
    }

    @Test
    void rejectsPasswordOverBcryptByteLimitBeforeEncoding()
    {
        RegisterBody body = registration("Aa1!" + "x".repeat(51) + "\u00E9".repeat(9));

        String message = assertDoesNotThrow(() -> service.register(body));

        assertFalse(message.isEmpty());
        verify(userService, never()).registerUser(any(SysUser.class));
    }

    private RegisterBody registration(String password)
    {
        RegisterBody body = new RegisterBody();
        body.setUsername("validUser");
        body.setPassword(password);
        return body;
    }
}
