package com.opc.system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.validation.Validator;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.opc.common.core.domain.entity.SysUser;
import com.opc.common.exception.ServiceException;
import com.opc.system.mapper.SysUserMapper;
import com.opc.system.mapper.SysPostMapper;
import com.opc.system.mapper.SysRoleMapper;
import com.opc.system.mapper.SysUserPostMapper;
import com.opc.system.mapper.SysUserRoleMapper;
import com.opc.system.service.ISysDeptService;
import com.opc.system.service.SecureTemporaryPasswordGenerator;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SysUserImportTransactionTest.TestConfiguration.class)
class SysUserImportTransactionTest
{
    @Autowired
    private SysUserServiceImpl service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpTable()
    {
        jdbcTemplate.execute("drop table if exists imported_user");
        jdbcTemplate.execute("create table imported_user (user_name varchar(30) primary key)");
    }

    @Test
    void rollsBackEarlierImportsWhenALaterRecordFails()
    {
        assertTrue(AopUtils.isAopProxy(service));
        assertThrows(ServiceException.class,
                () -> service.importUser(List.of(user("alice"), user("bob")), false, "operator"));

        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from imported_user", Integer.class));
    }

    private SysUser user(String userName)
    {
        SysUser user = new SysUser();
        user.setUserName(userName);
        user.setNickName(userName);
        user.setDeptId(7L);
        return user;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration
    {
        @Bean
        DataSource dataSource()
        {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:import_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource)
        {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SysUserMapper userMapper(JdbcTemplate jdbcTemplate)
        {
            SysUserMapper mapper = mock(SysUserMapper.class);
            when(mapper.selectUserByUserName(anyString())).thenReturn(null);
            when(mapper.insertUser(any(SysUser.class))).thenAnswer(invocation -> {
                SysUser user = invocation.getArgument(0);
                if ("bob".equals(user.getUserName()))
                {
                    throw new IllegalStateException("simulated second-row failure");
                }
                return jdbcTemplate.update("insert into imported_user (user_name) values (?)", user.getUserName());
            });
            return mapper;
        }

        @Bean
        SysUserServiceImpl service(SysUserMapper userMapper)
        {
            SecureTemporaryPasswordGenerator generator = mock(SecureTemporaryPasswordGenerator.class);
            when(generator.generate()).thenReturn("temporary-random-credential");
            return new SysUserServiceImpl(generator);
        }

        @Bean
        SysRoleMapper roleMapper()
        {
            return mock(SysRoleMapper.class);
        }

        @Bean
        SysPostMapper postMapper()
        {
            return mock(SysPostMapper.class);
        }

        @Bean
        SysUserRoleMapper userRoleMapper()
        {
            return mock(SysUserRoleMapper.class);
        }

        @Bean
        SysUserPostMapper userPostMapper()
        {
            return mock(SysUserPostMapper.class);
        }

        @Bean
        ISysDeptService deptService()
        {
            return mock(ISysDeptService.class);
        }

        @Bean
        Validator validator()
        {
            Validator validator = mock(Validator.class);
            when(validator.validate(any(), any(Class[].class))).thenReturn(Set.of());
            return validator;
        }
    }
}
