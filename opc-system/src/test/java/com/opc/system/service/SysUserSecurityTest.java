package com.opc.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.opc.common.core.domain.entity.SysUser;

class SysUserSecurityTest
{
    @Test
    void toStringDoesNotExposePassword()
    {
        SysUser user = new SysUser();
        user.setPassword("Strong!Pass2026");

        assertFalse(user.toString().contains("Strong!Pass2026"));
    }
}
