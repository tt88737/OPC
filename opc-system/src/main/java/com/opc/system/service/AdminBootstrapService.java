package com.opc.system.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opc.common.core.domain.entity.SysUser;
import com.opc.system.mapper.SysUserMapper;

@Service
public class AdminBootstrapService
{
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

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
        String newPassword;
        try
        {
            newPassword = passwordEncoder.encode(configuredPassword);
        }
        catch (IllegalArgumentException exception)
        {
            log.warn("Administrator bootstrap rejected because the configured password cannot be encoded");
            return AdminBootstrapResult.REJECTED;
        }
        int updated = userMapper.initializeAdminPassword(PLACEHOLDER_PASSWORD_HASH, newPassword);
        if (updated == 1)
        {
            return AdminBootstrapResult.INITIALIZED;
        }
        SysUser administrator = userMapper.selectUserById(1L);
        if (isInitializedAdministrator(administrator))
        {
            return AdminBootstrapResult.ALREADY_INITIALIZED;
        }
        log.warn("Administrator bootstrap rejected because the administrator account is not eligible");
        return AdminBootstrapResult.REJECTED;
    }

    private boolean isStrongPassword(String password)
    {
        int characterCount = password.codePointCount(0, password.length());
        if (characterCount < 12 || characterCount > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
        {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int offset = 0; offset < password.length(); )
        {
            int codePoint = password.codePointAt(offset);
            upper |= Character.isUpperCase(codePoint);
            lower |= Character.isLowerCase(codePoint);
            digit |= Character.isDigit(codePoint);
            special |= !Character.isLetterOrDigit(codePoint);
            offset += Character.charCount(codePoint);
        }
        return upper && lower && digit && special;
    }

    private boolean isInitializedAdministrator(SysUser user)
    {
        return user != null
                && Long.valueOf(1L).equals(user.getUserId())
                && "admin".equals(user.getUserName())
                && "0".equals(user.getStatus())
                && "0".equals(user.getDelFlag())
                && user.getPassword() != null
                && !user.getPassword().isEmpty()
                && !PLACEHOLDER_PASSWORD_HASH.equals(user.getPassword());
    }
}
