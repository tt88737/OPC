package com.opc.web.core.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DruidConsoleCredentialsValidatorTest
{
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t", "\u00a0", "\u202f" })
    void rejectsBlankUsername(String username)
    {
        assertThrows(IllegalStateException.class,
                () -> new DruidConsoleCredentialsValidator(username, "strong-console-password"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t", "\u00a0", "\u202f" })
    void rejectsBlankPassword(String password)
    {
        assertThrows(IllegalStateException.class,
                () -> new DruidConsoleCredentialsValidator("druid-operator", password));
    }

    @Test
    void acceptsInjectedCredentials()
    {
        assertDoesNotThrow(() -> new DruidConsoleCredentialsValidator("\u8fd0\u7ef4", "\u5f3a\u5bc6\u7801"));
    }
}
