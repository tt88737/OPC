package com.opc.web.controller.system;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RestController;
import com.opc.common.annotation.Log;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.system.domain.SysPost;
import com.opc.system.service.ISysPostService;

/**
 * 岗位信息操作处理
 * 
 * @author opc
 */
@RestController
@RequestMapping("/system/post")
@Tag(name = "岗位管理", description = "岗位资料及岗位选择列表管理")
public class SysPostController extends BaseController
{
    @Autowired
    private ISysPostService postService;

    /**
     * 获取岗位列表
     */
    @PreAuthorize("@ss.hasPermi('system:post:list')")
    @Operation(summary = "查询岗位列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "岗位查询条件", required = false) SysPost post)
    {
        startPage();
        List<SysPost> list = postService.selectPostList(post);
        return getDataTable(list);
    }
    
    @Log(title = "岗位管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:post:export')")
    @Operation(summary = "导出岗位数据")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "岗位查询条件", required = false) SysPost post)
    {
        List<SysPost> list = postService.selectPostList(post);
        ExcelUtil<SysPost> util = new ExcelUtil<SysPost>(SysPost.class);
        util.exportExcel(response, list, "岗位数据");
    }

    /**
     * 根据岗位编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:post:query')")
    @Operation(summary = "获取岗位详细信息")
    @GetMapping(value = "/{postId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "岗位编号", required = true, example = "1") @PathVariable Long postId)
    {
        return success(postService.selectPostById(postId));
    }

    /**
     * 新增岗位
     */
    @PreAuthorize("@ss.hasPermi('system:post:add')")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增岗位")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增岗位", required = true) @RequestBody SysPost post)
    {
        if (!postService.checkPostNameUnique(post))
        {
            return error("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }
        else if (!postService.checkPostCodeUnique(post))
        {
            return error("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        post.setCreateBy(getUsername());
        return toAjax(postService.insertPost(post));
    }

    /**
     * 修改岗位
     */
    @PreAuthorize("@ss.hasPermi('system:post:edit')")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改岗位")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改岗位", required = true) @RequestBody SysPost post)
    {
        if (!postService.checkPostNameUnique(post))
        {
            return error("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }
        else if (!postService.checkPostCodeUnique(post))
        {
            return error("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        post.setUpdateBy(getUsername());
        return toAjax(postService.updatePost(post));
    }

    /**
     * 删除岗位
     */
    @PreAuthorize("@ss.hasPermi('system:post:remove')")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @Operation(summary = "删除岗位")
    @DeleteMapping("/{postIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "岗位编号列表", required = true, example = "1,2") @PathVariable Long[] postIds)
    {
        return toAjax(postService.deletePostByIds(postIds));
    }

    /**
     * 获取岗位选择框列表
     */
    @Operation(summary = "获取岗位选择列表")
    @GetMapping("/optionselect")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult optionselect()
    {
        List<SysPost> posts = postService.selectPostAll();
        return success(posts);
    }
}
