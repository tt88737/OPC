package com.opc.web.controller.monitor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.opc.common.core.domain.AjaxResult;
import com.opc.framework.web.domain.Server;

/**
 * 服务器监控
 * 
 * @author opc
 */
@RestController
@RequestMapping("/monitor/server")
@Tag(name = "服务监控", description = "获取服务器运行状态的监控接口")
public class ServerController
{
    @PreAuthorize("@ss.hasPermi('monitor:server:list')")
    @Operation(summary = "获取服务器监控信息")
    @GetMapping()
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo() throws Exception
    {
        Server server = new Server();
        server.copyTo();
        return AjaxResult.success(server);
    }
}
