package com.opc.web.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class SecurityProfileConfigDataIntegrationTest
{
    private static final String[] SAFE_PRODUCTION_SYSTEM_PROPERTIES = {
            "OPC_PROFILE=prod",
            "OPC_DB_URL=jdbc:mysql://db.example/opc",
            "OPC_DB_USERNAME=opc",
            "OPC_DB_PASSWORD=database-secret",
            "OPC_REDIS_PASSWORD=redis-secret",
            "OPC_TOKEN_SECRET=token-secret",
            "OPC_CORS_ALLOWED_ORIGINS=https://app.example"
    };

    @Test
    void loadsDefaultDevAndDatasourceProfilesFromRealConfigData()
    {
        configDataRunner().run(context ->
        {
            assertNull(context.getStartupFailure());
            Environment environment = context.getEnvironment();
            assertTrue(environment.acceptsProfiles(Profiles.of("dev")));
            assertTrue(environment.acceptsProfiles(Profiles.of("datasource")));
            assertEquals("com.alibaba.druid.pool.DruidDataSource",
                    environment.getProperty("spring.datasource.type"));
            assertEquals("true", environment.getProperty("springdoc.api-docs.enabled"));
            assertEquals("true", environment.getProperty("springdoc.swagger-ui.enabled"));
            assertEquals("false", environment.getProperty("spring.datasource.druid.web-stat-filter.enabled"));
            assertEquals("false", environment.getProperty("spring.datasource.druid.stat-view-servlet.enabled"));
        });
    }

    @Test
    void loadsSafeProdAndDatasourceProfilesFromRealConfigData()
    {
        productionConfigDataRunner().run(context ->
        {
            assertNull(context.getStartupFailure());
            Environment environment = context.getEnvironment();
            assertTrue(environment.acceptsProfiles(Profiles.of("prod")));
            assertTrue(environment.acceptsProfiles(Profiles.of("datasource")));
            assertFalse(environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true));
            assertFalse(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true));
            assertFalse(environment.getProperty("spring.devtools.restart.enabled", Boolean.class, true));
            assertFalse(environment.getProperty("spring.datasource.druid.web-stat-filter.enabled",
                    Boolean.class, true));
            assertFalse(environment.getProperty("spring.datasource.druid.stat-view-servlet.enabled",
                    Boolean.class, true));
            assertEquals("jdbc:mysql://db.example/opc",
                    environment.getProperty("spring.datasource.druid.master.url"));
            assertEquals("opc", environment.getProperty("spring.datasource.druid.master.username"));
            assertEquals("database-secret",
                    environment.getProperty("spring.datasource.druid.master.password"));
            assertEquals("redis-secret", environment.getProperty("spring.data.redis.password"));
            assertEquals("token-secret", environment.getProperty("token.secret"));
            assertEquals("https://app.example", environment.getProperty("cors.allowed-origins"));
            assertNotNull(context.getBean(ProductionSecurityValidator.class));
        });
    }

    @Test
    void rejectsCanonicalSystemPropertyThatReEnablesProdDocumentation()
    {
        productionConfigDataRunner("springdoc.api-docs.enabled=true").run(context ->
                assertRejected(context.getStartupFailure(), "springdoc.api-docs.enabled"));
    }

    @Test
    void rejectsStandardEnvironmentPropertyThatReEnablesProdDruidConsole()
    {
        productionConfigDataRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("security-test-environment", Map.of(
                                "SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_ENABLED", "true"))))
                .run(context -> assertRejected(context.getStartupFailure(),
                        "spring.datasource.druid.stat-view-servlet.enabled"));
    }

    private ApplicationContextRunner configDataRunner()
    {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TestConfiguration.class);
    }

    private ApplicationContextRunner productionConfigDataRunner(String... overrides)
    {
        String[] systemProperties = Stream.concat(
                Stream.of(SAFE_PRODUCTION_SYSTEM_PROPERTIES), Stream.of(overrides)).toArray(String[]::new);
        return configDataRunner().withSystemProperties(systemProperties);
    }

    private void assertRejected(Throwable failure, String propertyName)
    {
        assertNotNull(failure);
        Throwable rootCause = failure;
        while (rootCause.getCause() != null)
        {
            rootCause = rootCause.getCause();
        }
        assertTrue(rootCause instanceof IllegalStateException);
        assertEquals("Production security invariant violation: " + propertyName, rootCause.getMessage());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ProductionSecurityValidator.class)
    static class TestConfiguration
    {
    }
}
