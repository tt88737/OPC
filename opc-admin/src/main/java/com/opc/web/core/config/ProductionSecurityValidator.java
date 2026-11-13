package com.opc.web.core.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecurityValidator implements InitializingBean
{
    private static final List<String> DISABLED_PROPERTIES = List.of(
            "springdoc.api-docs.enabled",
            "springdoc.swagger-ui.enabled",
            "spring.devtools.restart.enabled",
            "spring.datasource.druid.web-stat-filter.enabled",
            "spring.datasource.druid.stat-view-servlet.enabled");

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "spring.datasource.druid.master.url",
            "spring.datasource.druid.master.username",
            "spring.datasource.druid.master.password",
            "spring.data.redis.password",
            "token.secret",
            "cors.allowed-origins");

    private final Environment environment;

    public ProductionSecurityValidator(Environment environment)
    {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet()
    {
        List<String> violations = new ArrayList<>();
        for (String propertyName : DISABLED_PROPERTIES)
        {
            if (environment.getProperty(propertyName, Boolean.class, false))
            {
                violations.add(propertyName);
            }
        }
        for (String propertyName : REQUIRED_PROPERTIES)
        {
            if (!UnicodeText.hasVisibleText(environment.getProperty(propertyName)))
            {
                violations.add(propertyName);
            }
        }
        if (!"health".equals(environment.getProperty("management.endpoints.web.exposure.include")))
        {
            violations.add("management.endpoints.web.exposure.include");
        }
        if (!"never".equalsIgnoreCase(environment.getProperty("management.endpoint.health.show-details")))
        {
            violations.add("management.endpoint.health.show-details");
        }
        if (!"never".equalsIgnoreCase(environment.getProperty("management.endpoint.health.show-components")))
        {
            violations.add("management.endpoint.health.show-components");
        }
        if (!violations.isEmpty())
        {
            throw new IllegalStateException("Production security invariant violation: "
                    + String.join(", ", violations));
        }
    }

}
