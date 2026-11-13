package com.opc.quartz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.opc.common.annotation.Log;
import com.opc.common.constant.Constants;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.exception.job.TaskException;
import com.opc.common.utils.StringUtils;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.quartz.domain.SysJob;
import com.opc.quartz.service.ISysJobService;
import com.opc.quartz.util.CronUtils;
import com.opc.quartz.util.ScheduleUtils;

/**
 * 调度任务信息操作处理
 *
 * @author opc
 */
@RestController
@RequestMapping("/monitor/job")
@Tag(name = "定时任务", description = "定时任务配置、状态控制及立即执行")
public class SysJobController extends BaseController
{
    @Autowired
    private ISysJobService jobService;

    /**
     * 查询定时任务列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:list')")
    @Operation(summary = "查询定时任务列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "定时任务查询条件", required = false) SysJob sysJob)
    {
        startPage();
        List<SysJob> list = jobService.selectJobList(sysJob);
        return getDataTable(list);
    }

    /**
     * 导出定时任务列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:export')")
    @Log(title = "定时任务", businessType = BusinessType.EXPORT)
    @Operation(summary = "导出定时任务")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "定时任务查询条件", required = false) SysJob sysJob)
    {
        List<SysJob> list = jobService.selectJobList(sysJob);
        ExcelUtil<SysJob> util = new ExcelUtil<SysJob>(SysJob.class);
        util.exportExcel(response, list, "定时任务");
    }

    /**
     * 获取定时任务详细信息
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:query')")
    @Operation(summary = "获取定时任务详细信息")
    @GetMapping(value = "/{jobId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "定时任务编号", required = true, example = "1") @PathVariable("jobId") Long jobId)
    {
        return success(jobService.selectJobById(jobId));
    }

    /**
     * 新增定时任务
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:add')")
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    @Operation(summary = "新增定时任务")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增定时任务", required = true) @RequestBody SysJob job) throws SchedulerException, TaskException
    {
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return error("新增任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return error("新增任务'" + job.getJobName() + "'失败，目标字符串不在白名单内");
        }
        job.setCreateBy(getUsername());
        return toAjax(jobService.insertJob(job));
    }

    /**
     * 修改定时任务
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:edit')")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改定时任务")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改定时任务", required = true) @RequestBody SysJob job) throws SchedulerException, TaskException
    {
        if (!CronUtils.isValid(job.getCronExpression()))
        {
            return error("修改任务'" + job.getJobName() + "'失败，Cron表达式不正确");
        }
        else if (StringUtils.containsIgnoreCase(job.getInvokeTarget(), Constants.LOOKUP_RMI))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'rmi'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.LOOKUP_LDAP, Constants.LOOKUP_LDAPS }))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'ldap(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), new String[] { Constants.HTTP, Constants.HTTPS }))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不允许'http(s)'调用");
        }
        else if (StringUtils.containsAnyIgnoreCase(job.getInvokeTarget(), Constants.JOB_ERROR_STR))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串存在违规");
        }
        else if (!ScheduleUtils.whiteList(job.getInvokeTarget()))
        {
            return error("修改任务'" + job.getJobName() + "'失败，目标字符串不在白名单内");
        }
        job.setUpdateBy(getUsername());
        return toAjax(jobService.updateJob(job));
    }

    /**
     * 定时任务状态修改
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:changeStatus')")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改定时任务状态")
    @PutMapping("/changeStatus")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult changeStatus(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "定时任务状态信息", required = true) @RequestBody SysJob job) throws SchedulerException
    {
        SysJob newJob = jobService.selectJobById(job.getJobId());
        newJob.setStatus(job.getStatus());
        return toAjax(jobService.changeStatus(newJob));
    }

    /**
     * 定时任务立即执行一次
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:changeStatus')")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @Operation(summary = "执行一次定时任务")
    @PutMapping("/run")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult run(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待执行的定时任务", required = true) @RequestBody SysJob job) throws SchedulerException
    {
        boolean result = jobService.run(job);
        return result ? success() : error("任务不存在或已过期！");
    }

    /**
     * 删除定时任务
     */
    @PreAuthorize("@ss.hasPermi('monitor:job:remove')")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @Operation(summary = "删除定时任务")
    @DeleteMapping("/{jobIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "定时任务编号列表", required = true, example = "1,2") @PathVariable Long[] jobIds) throws SchedulerException
    {
        jobService.deleteJobByIds(jobIds);
        return success();
    }
}
