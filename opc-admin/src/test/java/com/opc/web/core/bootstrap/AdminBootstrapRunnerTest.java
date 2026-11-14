package com.opc.web.core.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.opc.system.service.AdminBootstrapResult;
import com.opc.system.service.AdminBootstrapService;

@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapRunnerTest
{
    @Test
    void delegatesConfiguredSecretWithoutWritingItToLogs(CapturedOutput output)
    {
        AdminBootstrapService service = org.mockito.Mockito.mock(AdminBootstrapService.class);
        String secret = "Strong!Pass2026";
        when(service.bootstrap(secret)).thenReturn(AdminBootstrapResult.INITIALIZED);

        new AdminBootstrapRunner(service, secret).run(null);

        verify(service).bootstrap(secret);
        verifyNoMoreInteractions(service);
        assertFalse(output.getAll().contains(secret));
    }
}
