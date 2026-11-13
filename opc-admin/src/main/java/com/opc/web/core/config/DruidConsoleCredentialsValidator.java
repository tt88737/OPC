package com.opc.web.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "spring.datasource.druid.statViewServlet.enabled", havingValue = "true")
public class DruidConsoleCredentialsValidator
{
    public DruidConsoleCredentialsValidator(@Value("${OPC_DRUID_USERNAME:}") String username,
            @Value("${OPC_DRUID_PASSWORD:}") String password)
    {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password))
        {
            throw new IllegalStateException(
                    "OPC_DRUID_USERNAME and OPC_DRUID_PASSWORD are required when the Druid console is enabled");
        }
    }
}
