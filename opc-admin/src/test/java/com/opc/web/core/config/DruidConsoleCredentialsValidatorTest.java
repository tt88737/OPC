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
    @ValueSource(strings = { " ", "\t" })
    void rejectsBlankUsername(String username)
    {
        assertThrows(IllegalStateException.class,
                () -> new DruidConsoleCredentialsValidator(username, "strong-console-password"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    void rejectsBlankPassword(String password)
    {
        assertThrows(IllegalStateException.class,
                () -> new DruidConsoleCredentialsValidator("druid-operator", password));
    }

    @Test
    void acceptsInjectedCredentials()
    {
        assertDoesNotThrow(() -> new DruidConsoleCredentialsValidator("druid-operator", "strong-console-password"));
    }
}
