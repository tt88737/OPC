package com.opc.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureTemporaryPasswordGeneratorTest
{
    @Test
    void generatesUniqueCredentialsWithAtLeast256BitsOfRandomness()
    {
        SecureTemporaryPasswordGenerator generator = new SecureTemporaryPasswordGenerator();
        Set<String> credentials = new HashSet<>();

        for (int index = 0; index < 100; index++)
        {
            String credential = generator.generate();
            assertTrue(Base64.getUrlDecoder().decode(credential).length >= 32);
            credentials.add(credential);
        }

        assertEquals(100, credentials.size());
    }
}
