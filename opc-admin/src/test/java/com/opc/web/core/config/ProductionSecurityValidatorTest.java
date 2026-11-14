package com.opc.web.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

class ProductionSecurityValidatorTest
{
    private static final Map<String, Object> SAFE_PRODUCTION_PROPERTIES = Map.ofEntries(
            Map.entry("springdoc.api-docs.enabled", "false"),
            Map.entry("springdoc.swagger-ui.enabled", "false"),
            Map.entry("spring.devtools.restart.enabled", "false"),
            Map.entry("spring.datasource.druid.web-stat-filter.enabled", "false"),
            Map.entry("spring.datasource.druid.stat-view-servlet.enabled", "false"),
            Map.entry("management.endpoints.web.exposure.include", "health"),
            Map.entry("management.endpoint.health.show-details", "never"),
            Map.entry("management.endpoint.health.show-components", "never"),
            Map.entry("spring.datasource.druid.master.url", "jdbc:mysql://db.example/opc"),
            Map.entry("spring.datasource.druid.master.username", "opc"),
            Map.entry("spring.datasource.druid.master.password", "database-secret"),
            Map.entry("spring.data.redis.password", "redis-secret"),
            Map.entry("token.secret", "token-secret"),
            Map.entry("cors.allowed-origins", "https://app.example"));

    @Test
    void acceptsSafeFinalProductionEnvironment()
    {
        contextRunner(SAFE_PRODUCTION_PROPERTIES).run(context ->
        {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(ProductionSecurityValidator.class));
        });
    }

    @ParameterizedTest
    @MethodSource("disabledProductionFeatures")
    void rejectsHighPriorityOverridesThatEnableProductionFeatures(String propertyName)
    {
        contextRunner(SAFE_PRODUCTION_PROPERTIES, propertyName + "=true").run(context ->
                assertRejected(context.getStartupFailure(), propertyName));
    }

    @ParameterizedTest
    @MethodSource("unsafeManagementOverrides")
    void rejectsHighPriorityOverridesThatExposeManagementData(String propertyName, String value)
    {
        contextRunner(SAFE_PRODUCTION_PROPERTIES, propertyName + "=" + value).run(context ->
                assertRejected(context.getStartupFailure(), propertyName));
    }

    @ParameterizedTest
    @MethodSource("requiredProductionProperties")
    void rejectsMissingRequiredProductionProperties(String propertyName)
    {
        Map<String, Object> properties = new LinkedHashMap<>(SAFE_PRODUCTION_PROPERTIES);
        properties.remove(propertyName);

        contextRunner(properties).run(context -> assertRejected(context.getStartupFailure(), propertyName));
    }

    @ParameterizedTest
    @MethodSource("requiredProductionProperties")
    void rejectsBlankHighPriorityRequiredProductionProperties(String propertyName)
    {
        contextRunner(SAFE_PRODUCTION_PROPERTIES, propertyName + "=" + '\u2003').run(context ->
                assertRejected(context.getStartupFailure(), propertyName));
    }

    @ParameterizedTest
    @MethodSource("requiredProductionPropertiesAndUnicodeSpaces")
    void rejectsUnicodeSpaceOnlyRequiredProductionProperties(String propertyName, String unicodeSpace)
    {
        contextRunner(SAFE_PRODUCTION_PROPERTIES, propertyName + "=" + unicodeSpace).run(context ->
                assertRejected(context.getStartupFailure(), propertyName));
    }

    @Test
    void acceptsNonAsciiRequiredProductionValues()
    {
        String[] properties = requiredProductionProperties()
                .map(propertyName -> propertyName + "=valid-\u5bc6\u94a5")
                .toArray(String[]::new);

        contextRunner(SAFE_PRODUCTION_PROPERTIES, properties).run(context ->
                assertNull(context.getStartupFailure()));
    }

    private ApplicationContextRunner contextRunner(Map<String, Object> fileProperties, String... systemProperties)
    {
        String[] properties = Stream.concat(Stream.of("spring.profiles.active=prod"), Stream.of(systemProperties))
                .toArray(String[]::new);
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addLast(new MapPropertySource("application-prod.yml", fileProperties)))
                .withSystemProperties(properties)
                .withUserConfiguration(TestConfiguration.class);
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

    static Stream<String> disabledProductionFeatures()
    {
        return Stream.of(
                "springdoc.api-docs.enabled",
                "springdoc.swagger-ui.enabled",
                "spring.devtools.restart.enabled",
                "spring.datasource.druid.web-stat-filter.enabled",
                "spring.datasource.druid.stat-view-servlet.enabled");
    }

    static Stream<String> requiredProductionProperties()
    {
        return Stream.of(
                "spring.datasource.druid.master.url",
                "spring.datasource.druid.master.username",
                "spring.datasource.druid.master.password",
                "spring.data.redis.password",
                "token.secret",
                "cors.allowed-origins");
    }

    static Stream<Arguments> unsafeManagementOverrides()
    {
        return Stream.of(
                Arguments.of("management.endpoints.web.exposure.include", "health,env"),
                Arguments.of("management.endpoints.web.exposure.include", "*"),
                Arguments.of("management.endpoint.health.show-details", "always"),
                Arguments.of("management.endpoint.health.show-components", "always"));
    }

    static Stream<Arguments> requiredProductionPropertiesAndUnicodeSpaces()
    {
        return requiredProductionProperties().flatMap(propertyName -> Stream.of(
                Arguments.of(propertyName, "\u00a0"),
                Arguments.of(propertyName, "\u202f")));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ProductionSecurityValidator.class)
    static class TestConfiguration
    {
    }
}
