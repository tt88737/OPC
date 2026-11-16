package com.opc.web.controller.system;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import com.opc.common.annotation.Log;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.core.text.Convert;
import com.opc.common.enums.BusinessType;
import com.opc.system.domain.SysNotice;
import com.opc.system.service.ISysNoticeReadService;
import com.opc.system.service.ISysNoticeService;

/**
 * 公告 信息操作处理
 *
 * @author opc
 */
@RestController
@RequestMapping("/system/notice")
@Tag(name = "通知公告", description = "通知公告发布、查询及已读状态管理")
public class SysNoticeController extends BaseController
{
    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private ISysNoticeReadService noticeReadService;

    /**
     * 获取通知公告列表
     */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @Operation(summary = "查询通知公告列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "通知公告查询条件", required = false) SysNotice notice)
    {
        startPage();
        List<SysNotice> list = noticeService.selectNoticeList(notice);
        return getDataTable(list);
    }

    /**
     * 根据通知公告编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:notice:query')")
    @Operation(summary = "获取通知公告详细信息")
    @GetMapping(value = "/{noticeId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "公告编号", required = true, example = "1") @PathVariable Long noticeId)
    {
        return success(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 新增通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:add')")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @Operation(summary = "新增通知公告")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增通知公告", required = true) @RequestBody SysNotice notice)
    {
        notice.setCreateBy(getUsername());
        return toAjax(noticeService.insertNotice(notice));
    }

    /**
     * 修改通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:edit')")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改通知公告")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改通知公告", required = true) @RequestBody SysNotice notice)
    {
        notice.setUpdateBy(getUsername());
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 首页顶部公告列表（返回全部正常公告，带当前用户已读标记，最多5条）
     */
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "查询顶部通知公告")
    @GetMapping("/listTop")
    @ResponseBody
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult listTop()
    {
        Long userId = getUserId();
        List<SysNotice> list = noticeReadService.selectNoticeListWithReadStatus(userId, 5);
        long unreadCount = list.stream().filter(n -> !n.getIsRead()).count();
        AjaxResult result = AjaxResult.success(list);
        result.put("unreadCount", unreadCount);
        return result;
    }

    /**
     * 标记公告已读
     */
    @PreAuthorize("isAuthenticated()")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @Operation(summary = "标记通知公告已读")
    @PostMapping("/markRead")
    @ResponseBody
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult markRead(@Parameter(description = "公告编号", required = true, example = "1") Long noticeId)
    {
        Long userId = getUserId();
        noticeReadService.markRead(noticeId, userId);
        return success();
    }

    /**
     * 批量标记已读
     */
    @PreAuthorize("isAuthenticated()")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @Operation(summary = "批量标记通知公告已读")
    @PostMapping("/markReadAll")
    @ResponseBody
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult markReadAll(@Parameter(description = "公告编号列表", required = true, example = "1,2") String ids)
    {
        Long userId = getUserId();
        Long[] noticeIds = Convert.toLongArray(ids);
        noticeReadService.markReadBatch(userId, noticeIds);
        return success();
    }

    /**
     * 已读用户列表数据
     */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @Operation(summary = "查询通知公告已读用户")
    @GetMapping("/readUsers/list")
    @ResponseBody
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo readUsersList(@Parameter(description = "公告编号", required = true, example = "1") Long noticeId,
            @Parameter(description = "用户查询关键字", required = false, example = "admin") String searchValue)
    {
        startPage();
        List<?> list = noticeReadService.selectReadUsersByNoticeId(noticeId, searchValue);
        return getDataTable(list);
    }

    /**
     * 删除通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:remove')")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @Operation(summary = "删除通知公告")
    @DeleteMapping("/{noticeIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "公告编号列表", required = true, example = "1,2") @PathVariable Long[] noticeIds)
    {
        noticeReadService.deleteByNoticeIds(noticeIds);
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }
}
