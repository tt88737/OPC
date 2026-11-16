package com.opc.framework.web.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.exception.ServiceException;
import com.opc.common.exception.base.BaseException;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest
{
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void expectedUserExceptionDoesNotWriteErrorStackTrace(CapturedOutput output)
    {
        HttpServletRequest request = request("/login");

        AjaxResult result = handler.handleBaseException(new BaseException("验证码错误"), request);

        assertEquals("验证码错误", result.get(AjaxResult.MSG_TAG));
        assertTrue(output.getOut().contains("业务请求被拒绝"));
        assertFalse(output.getAll().contains("ERROR"));
        assertFalse(output.getAll().contains("BaseException:"));
    }

    @Test
    void serviceExceptionDoesNotWriteErrorStackTrace(CapturedOutput output)
    {
        HttpServletRequest request = request("/system/user");

        AjaxResult result = handler.handleServiceException(new ServiceException("用户状态不允许操作"), request);

        assertEquals("用户状态不允许操作", result.get(AjaxResult.MSG_TAG));
        assertTrue(output.getOut().contains("业务请求处理失败"));
        assertFalse(output.getAll().contains("ERROR"));
        assertFalse(output.getAll().contains("ServiceException:"));
    }

    @Test
    void unexpectedRuntimeExceptionKeepsDetailsInternal()
    {
        HttpServletRequest request = request("/system/user");

        AjaxResult result = handler.handleRuntimeException(new RuntimeException("database-secret-detail"), request);

        assertEquals("系统繁忙，请稍后重试", result.get(AjaxResult.MSG_TAG));
    }

    private HttpServletRequest request(String uri)
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
