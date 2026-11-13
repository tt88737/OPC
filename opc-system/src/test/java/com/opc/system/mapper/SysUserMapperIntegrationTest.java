package com.opc.system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opc.common.core.domain.entity.SysDept;
import com.opc.common.core.domain.entity.SysRole;
import com.opc.common.core.domain.entity.SysUser;
import com.opc.system.service.AdminBootstrapService;

class SysUserMapperIntegrationTest
{
    private static final String NEW_PASSWORD_HASH = "new-bcrypt-hash";

    private DataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception
    {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource = h2;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("create table sys_user (user_id bigint primary key, user_name varchar(30), "
                    + "password varchar(100), status char(1), pwd_update_date timestamp, update_time timestamp)");
        }

        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(new H2SysdateAdapter());
        configuration.getTypeAliasRegistry().registerAlias("SysUser", SysUser.class);
        configuration.getTypeAliasRegistry().registerAlias("SysDept", SysDept.class);
        configuration.getTypeAliasRegistry().registerAlias("SysRole", SysRole.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/system/SysUserMapper.xml"))
        {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/system/SysUserMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    void initializesOnlyTheDisabledAdministratorWithPlaceholderHash() throws Exception
    {
        insertUser("admin", "1", AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH);

        assertEquals(1, initialize(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select password, status, pwd_update_date, update_time from sys_user where user_id = 1");
                ResultSet result = statement.executeQuery())
        {
            result.next();
            assertEquals(NEW_PASSWORD_HASH, result.getString("password"));
            assertEquals("0", result.getString("status"));
            assertNotNull(result.getTimestamp("pwd_update_date"));
            assertNotNull(result.getTimestamp("update_time"));
        }
    }

    @Test
    void rejectsAdministratorWithWrongStatus() throws Exception
    {
        insertUser("admin", "0", AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH);

        assertEquals(0, initialize(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH));
    }

    @Test
    void rejectsAdministratorWithWrongPasswordHash() throws Exception
    {
        insertUser("admin", "1", "different-hash");

        assertEquals(0, initialize(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH));
    }

    @Test
    void rejectsPlaceholderAccountWithNonAdminUsername() throws Exception
    {
        insertUser("not-admin", "1", AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH);

        assertEquals(0, initialize(AdminBootstrapService.PLACEHOLDER_PASSWORD_HASH));
    }

    private int initialize(String expectedPassword)
    {
        try (SqlSession session = sqlSessionFactory.openSession(true))
        {
            return session.getMapper(SysUserMapper.class).initializeAdminPassword(expectedPassword, NEW_PASSWORD_HASH);
        }
    }

    private void insertUser(String userName, String status, String password) throws Exception
    {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "insert into sys_user (user_id, user_name, password, status) values (1, ?, ?, ?)"))
        {
            statement.setString(1, userName);
            statement.setString(2, password);
            statement.setString(3, status);
            statement.executeUpdate();
        }
    }

    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
            args = { Connection.class, Integer.class }))
    private static class H2SysdateAdapter implements Interceptor
    {
        @Override
        public Object intercept(Invocation invocation) throws Throwable
        {
            // H2 2.x does not support MySQL's sysdate() function even in MySQL mode.
            MetaObject statement = SystemMetaObject.forObject(invocation.getTarget());
            String sql = (String) statement.getValue("delegate.boundSql.sql");
            statement.setValue("delegate.boundSql.sql", sql.replace("sysdate()", "current_timestamp"));
            return invocation.proceed();
        }
    }
}
