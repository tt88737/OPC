package com.opc.web.controller.monitor;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.opc.common.annotation.Log;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.system.domain.SysOperLog;
import com.opc.system.service.ISysOperLogService;

/**
 * 操作日志记录
 * 
 * @author opc
 */
@RestController
@RequestMapping("/monitor/operlog")
@Tag(name = "操作日志", description = "操作日志查询、导出与清理接口")
public class SysOperlogController extends BaseController
{
    @Autowired
    private ISysOperLogService operLogService;

    @PreAuthorize("@ss.hasPermi('monitor:operlog:list')")
    @Operation(summary = "查询操作日志列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "操作日志查询条件", required = false) SysOperLog operLog)
    {
        startPage();
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        return getDataTable(list);
    }

    @Log(title = "操作日志", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    @Operation(summary = "导出操作日志")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "操作日志查询条件", required = false) SysOperLog operLog)
    {
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        ExcelUtil<SysOperLog> util = new ExcelUtil<SysOperLog>(SysOperLog.class);
        util.exportExcel(response, list, "操作日志");
    }

    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    @Operation(summary = "删除操作日志")
    @DeleteMapping("/{operIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "操作日志编号列表", required = true, example = "1,2") @PathVariable Long[] operIds)
    {
        return toAjax(operLogService.deleteOperLogByIds(operIds));
    }

    @Log(title = "操作日志", businessType = BusinessType.CLEAN)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    @Operation(summary = "清空操作日志")
    @DeleteMapping("/clean")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult clean()
    {
        operLogService.cleanOperLog();
        return success();
    }
}
