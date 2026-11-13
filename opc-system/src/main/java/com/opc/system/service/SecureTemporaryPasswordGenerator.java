package com.opc.system.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class SecureTemporaryPasswordGenerator
{
    private static final int RANDOM_BYTE_COUNT = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate()
    {
        byte[] randomBytes = new byte[RANDOM_BYTE_COUNT];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
