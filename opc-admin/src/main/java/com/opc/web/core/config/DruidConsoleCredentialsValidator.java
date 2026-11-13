package com.opc.web.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.datasource.druid.statViewServlet.enabled", havingValue = "true")
public class DruidConsoleCredentialsValidator
{
    public DruidConsoleCredentialsValidator(@Value("${OPC_DRUID_USERNAME:}") String username,
            @Value("${OPC_DRUID_PASSWORD:}") String password)
    {
        if (!UnicodeText.hasVisibleText(username) || !UnicodeText.hasVisibleText(password))
        {
            throw new IllegalStateException(
                    "OPC_DRUID_USERNAME and OPC_DRUID_PASSWORD are required when the Druid console is enabled");
        }
    }
}
