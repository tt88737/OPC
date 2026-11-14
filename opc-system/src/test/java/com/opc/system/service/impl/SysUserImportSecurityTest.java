package com.opc.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.opc.common.core.domain.entity.SysUser;
import com.opc.system.mapper.SysUserMapper;
import com.opc.system.service.ISysDeptService;
import com.opc.system.service.SecureTemporaryPasswordGenerator;

@ExtendWith(MockitoExtension.class)
class SysUserImportSecurityTest
{
    @Mock
    private SysUserMapper userMapper;

    @Mock
    private ISysDeptService deptService;

    @Mock
    private Validator validator;

    @Mock
    private SecureTemporaryPasswordGenerator passwordGenerator;

    private SysUserServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = spy(new SysUserServiceImpl(passwordGenerator));
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "deptService", deptService);
        ReflectionTestUtils.setField(service, "validator", validator);
        when(validator.validate(any(), any(Class[].class))).thenReturn(Set.of());
    }

    @Test
    void newImportsReceiveUniqueUnrecoverablePasswordsAndAreDisabled()
    {
        String firstCredential = "first-random-import-credential";
        String secondCredential = "second-random-import-credential";
        when(passwordGenerator.generate()).thenReturn(firstCredential, secondCredential);
        SysUser first = importUser("alice");
        SysUser second = importUser("bob");

        service.importUser(List.of(first, second), false, "operator");

        ArgumentCaptor<SysUser> users = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper, org.mockito.Mockito.times(2)).insertUser(users.capture());
        String firstHash = users.getAllValues().get(0).getPassword();
        String secondHash = users.getAllValues().get(1).getPassword();
        verify(passwordGenerator, times(2)).generate();
        assertTrue(new BCryptPasswordEncoder().matches(firstCredential, firstHash));
        assertTrue(new BCryptPasswordEncoder().matches(secondCredential, secondHash));
        assertNotEquals(firstHash, secondHash);
        assertEquals("1", users.getAllValues().get(0).getStatus());
        assertEquals("1", users.getAllValues().get(1).getStatus());
    }

    @Test
    void updatingAnImportedUserDoesNotOverwritePasswordOrStatus()
    {
        SysUser existing = importUser("alice");
        existing.setUserId(42L);
        existing.setDeptId(7L);
        when(userMapper.selectUserByUserName("alice")).thenReturn(existing);
        doNothing().when(service).checkUserAllowed(any(SysUser.class));
        doNothing().when(service).checkUserDataScope(any(Long.class));
        SysUser update = importUser("alice");
        update.setPassword("attacker-controlled");
        update.setStatus("0");

        service.importUser(List.of(update), true, "operator");

        verify(passwordGenerator, never()).generate();
        ArgumentCaptor<SysUser> updatedUser = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateUser(updatedUser.capture());
        assertEquals(null, updatedUser.getValue().getPassword());
        assertEquals(null, updatedUser.getValue().getStatus());
    }

    private SysUser importUser(String userName)
    {
        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setNickName(userName);
        user.setDeptId(7L);
        return user;
    }
}
