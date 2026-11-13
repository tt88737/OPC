package com.opc.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.opc.common.constant.UserConstants;

class SysLoginServiceTest
{
    @Test
    void acceptsBootstrapCompatiblePasswordLength()
    {
        String bootstrapPassword = "Ab1!" + "x".repeat(32);

        assertTrue(UserConstants.PASSWORD_MAX_LENGTH >= bootstrapPassword.length());
    }
}
