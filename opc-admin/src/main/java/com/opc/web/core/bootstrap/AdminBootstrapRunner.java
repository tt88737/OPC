package com.opc.web.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.opc.system.service.AdminBootstrapResult;
import com.opc.system.service.AdminBootstrapService;

@Component
public class AdminBootstrapRunner implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapService bootstrapService;
    private final String configuredPassword;

    public AdminBootstrapRunner(AdminBootstrapService bootstrapService,
            @Value("${OPC_BOOTSTRAP_ADMIN_PASSWORD:}") String configuredPassword)
    {
        this.bootstrapService = bootstrapService;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        AdminBootstrapResult result = bootstrapService.bootstrap(configuredPassword);
        log.info("Administrator bootstrap result: {}", result);
    }
}
