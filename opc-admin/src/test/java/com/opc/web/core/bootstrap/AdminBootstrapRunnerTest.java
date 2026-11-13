package com.opc.web.core.bootstrap;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.opc.system.service.AdminBootstrapResult;
import com.opc.system.service.AdminBootstrapService;

class AdminBootstrapRunnerTest
{
    @Test
    void delegatesConfiguredSecretExactlyOnceWithoutReimplementingBootstrapRules() throws Exception
    {
        AdminBootstrapService service = org.mockito.Mockito.mock(AdminBootstrapService.class);
        String secret = "Strong!Pass2026";
        when(service.bootstrap(secret)).thenReturn(AdminBootstrapResult.INITIALIZED);

        new AdminBootstrapRunner(service, secret).run(null);

        verify(service).bootstrap(secret);
        verifyNoMoreInteractions(service);
    }
}
