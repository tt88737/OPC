package com.opc.quartz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import com.opc.common.annotation.Log;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.quartz.domain.SysJobLog;
import com.opc.quartz.service.ISysJobLogService;

/**
 * 调度日志操作处理
 *
 * @author opc
 */
@RestController
@RequestMapping("/monitor/jobLog")
@Tag(name = "调度日志", description = "定时任务执行日志查询、导出与清理")
public class SysJobLogController extends BaseController
{
    @Autowired
    private ISysJobLogService jobLogService;

    /**
     * 查询定时任务调度日志列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:list')")
    @Operation(summary = "查询调度日志列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "调度日志查询条件", required = false) SysJobLog sysJobLog)
    {
        startPage();
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        return getDataTable(list);
    }

    /**
     * 导出定时任务调度日志列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:export')")
    @Log(title = "任务调度日志", businessType = BusinessType.EXPORT)
    @Operation(summary = "导出调度日志")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "调度日志查询条件", required = false) SysJobLog sysJobLog)
    {
        List<SysJobLog> list = jobLogService.selectJobLogList(sysJobLog);
        ExcelUtil<SysJobLog> util = new ExcelUtil<SysJobLog>(SysJobLog.class);
        util.exportExcel(response, list, "调度日志");
    }

    /**
     * 根据调度编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    @Operation(summary = "获取调度日志详细信息")
    @GetMapping(value = "/{jobLogId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "调度日志编号", required = true, example = "1") @PathVariable Long jobLogId)
    {
        return success(jobLogService.selectJobLogById(jobLogId));
    }


    /**
     * 删除定时任务调度日志
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    @Log(title = "定时任务调度日志", businessType = BusinessType.DELETE)
    @Operation(summary = "删除调度日志")
    @DeleteMapping("/{jobLogIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "调度日志编号列表", required = true, example = "1,2") @PathVariable Long[] jobLogIds)
    {
        return toAjax(jobLogService.deleteJobLogByIds(jobLogIds));
    }

    /**
     * 清空定时任务调度日志
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    @Log(title = "调度日志", businessType = BusinessType.CLEAN)
    @Operation(summary = "清空调度日志")
    @DeleteMapping("/clean")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult clean()
    {
        jobLogService.cleanJobLog();
        return success();
    }
}
