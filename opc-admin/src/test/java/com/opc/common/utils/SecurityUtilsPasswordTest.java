package com.opc.common.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SecurityUtilsPasswordTest
{
    @Test
    void rejectsDifferentPasswordThatExtendsBeyondBcryptByteLimit()
    {
        String passwordAtLimit = "Aa1!" + "x".repeat(52) + "\u00E9".repeat(8);
        String encodedPassword = new BCryptPasswordEncoder().encode(passwordAtLimit);

        assertFalse(SecurityUtils.matchesPassword(passwordAtLimit + "12345678", encodedPassword));
    }
}
