package com.opc.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import com.opc.common.exception.user.UserPasswordNotMatchException;
import com.opc.common.utils.spring.SpringUtils;
import com.opc.framework.manager.factory.AsyncFactory;
import com.opc.system.service.ISysConfigService;

class SysLoginServiceTest
{
    @BeforeAll
    static void configureAsyncManager()
    {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("scheduledExecutorService", mock(ScheduledExecutorService.class));
        MessageSource messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class), org.mockito.ArgumentMatchers.any(Locale.class)))
                .thenReturn("message");
        beanFactory.registerSingleton("messageSource", messageSource);
        new SpringUtils().postProcessBeanFactory(beanFactory);
    }

    @Test
    void rejectsPasswordOverBcryptByteLimitBeforeAuthentication()
    {
        String password = "Aa1!" + "x".repeat(51) + "\u00E9".repeat(9);
        SysLoginService service = new SysLoginService();
        ISysConfigService configService = mock(ISysConfigService.class);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "authenticationManager", authenticationManager);
        when(configService.selectCaptchaEnabled()).thenReturn(false);

        try (MockedStatic<AsyncFactory> asyncFactory = mockStatic(AsyncFactory.class))
        {
            assertThrows(UserPasswordNotMatchException.class,
                    () -> service.login("admin", password, null, null));
            verifyNoInteractions(authenticationManager);
        }
    }
}
