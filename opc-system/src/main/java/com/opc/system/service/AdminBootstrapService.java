package com.opc.system.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opc.system.mapper.SysUserMapper;

@Service
public class AdminBootstrapService
{
    public static final String PLACEHOLDER_PASSWORD_HASH =
            "$2a$10$G/SUvAekkFytyaaBYNX0a.1S1aQmkvX8W5A6LvRG9j1lFjPqvpghG";

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminBootstrapService(SysUserMapper userMapper)
    {
        this.userMapper = userMapper;
    }

    @Transactional
    public AdminBootstrapResult bootstrap(String configuredPassword)
    {
        if (configuredPassword == null || configuredPassword.trim().isEmpty())
        {
            return AdminBootstrapResult.NOT_CONFIGURED;
        }
        if (!isStrongPassword(configuredPassword))
        {
            return AdminBootstrapResult.REJECTED;
        }
        String newPassword = passwordEncoder.encode(configuredPassword);
        int updated = userMapper.initializeAdminPassword(PLACEHOLDER_PASSWORD_HASH, newPassword);
        return updated == 1 ? AdminBootstrapResult.INITIALIZED : AdminBootstrapResult.ALREADY_INITIALIZED;
    }

    private boolean isStrongPassword(String password)
    {
        if (password.length() < 12 || password.length() > 64)
        {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char character : password.toCharArray())
        {
            upper |= Character.isUpperCase(character);
            lower |= Character.isLowerCase(character);
            digit |= Character.isDigit(character);
            special |= !Character.isLetterOrDigit(character);
        }
        return upper && lower && digit && special;
    }
}
