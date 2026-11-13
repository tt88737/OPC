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
import com.opc.common.core.domain.entity.SysDept;
import com.opc.common.core.domain.entity.SysRole;
import com.opc.common.core.domain.entity.SysUser;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.framework.web.service.SysPermissionService;
import com.opc.framework.web.service.TokenService;
import com.opc.system.domain.SysUserRole;
import com.opc.system.service.ISysDeptService;
import com.opc.system.service.ISysRoleService;
import com.opc.system.service.ISysUserService;

/**
 * 角色信息
 * 
 * @author opc
 */
@RestController
@RequestMapping("/system/role")
@Tag(name = "角色管理", description = "角色配置、数据权限及用户授权管理")
public class SysRoleController extends BaseController
{
    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysDeptService deptService;

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @Operation(summary = "查询角色列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "角色查询条件", required = false) SysRole role)
    {
        startPage();
        List<SysRole> list = roleService.selectRoleList(role);
        return getDataTable(list);
    }

    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:role:export')")
    @Operation(summary = "导出角色数据")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "角色查询条件", required = false) SysRole role)
    {
        List<SysRole> list = roleService.selectRoleList(role);
        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
        util.exportExcel(response, list, "角色数据");
    }

    /**
     * 根据角色编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @Operation(summary = "获取角色详细信息")
    @GetMapping(value = "/{roleId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "角色编号", required = true, example = "1") @PathVariable Long roleId)
    {
        roleService.checkRoleDataScope(roleId);
        return success(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增角色")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增角色", required = true) @RequestBody SysRole role)
    {
        if (!roleService.checkRoleNameUnique(role))
        {
            return error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (!roleService.checkRoleKeyUnique(role))
        {
            return error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setCreateBy(getUsername());
        return toAjax(roleService.insertRole(role));

    }

    /**
     * 修改保存角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改角色")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改角色", required = true) @RequestBody SysRole role)
    {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (!roleService.checkRoleNameUnique(role))
        {
            return error("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (!roleService.checkRoleKeyUnique(role))
        {
            return error("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setUpdateBy(getUsername());
        
        if (roleService.updateRole(role) > 0)
        {
            // 刷新所有持有该角色的在线用户权限
            tokenService.refreshPermissionByRoleId(role.getRoleId(), permissionService);
            return success();
        }
        return error("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改保存数据权限
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改角色数据权限")
    @PutMapping("/dataScope")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult dataScope(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "角色数据权限配置", required = true) @RequestBody SysRole role)
    {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        return toAjax(roleService.authDataScope(role));
    }

    /**
     * 状态修改
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改角色状态")
    @PutMapping("/changeStatus")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult changeStatus(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "角色状态信息", required = true) @RequestBody SysRole role)
    {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        role.setUpdateBy(getUsername());
        return toAjax(roleService.updateRoleStatus(role));
    }

    /**
     * 删除角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "角色编号列表", required = true, example = "1,2") @PathVariable Long[] roleIds)
    {
        return toAjax(roleService.deleteRoleByIds(roleIds));
    }

    /**
     * 获取角色选择框列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @Operation(summary = "获取角色选择列表")
    @GetMapping("/optionselect")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult optionselect()
    {
        return success(roleService.selectRoleAll());
    }

    /**
     * 查询已分配用户角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @Operation(summary = "查询已分配用户")
    @GetMapping("/authUser/allocatedList")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo allocatedList(@Parameter(description = "已分配用户查询条件", required = false) SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectAllocatedList(user);
        return getDataTable(list);
    }

    /**
     * 查询未分配用户角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @Operation(summary = "查询未分配用户")
    @GetMapping("/authUser/unallocatedList")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo unallocatedList(@Parameter(description = "未分配用户查询条件", required = false) SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectUnallocatedList(user);
        return getDataTable(list);
    }

    /**
     * 取消授权用户
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @Operation(summary = "取消用户角色授权")
    @PutMapping("/authUser/cancel")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult cancelAuthUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "取消用户角色授权信息", required = true) @RequestBody SysUserRole userRole)
    {
        return toAjax(roleService.deleteAuthUser(userRole));
    }

    /**
     * 批量取消授权用户
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @Operation(summary = "批量取消用户角色授权")
    @PutMapping("/authUser/cancelAll")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult cancelAuthUserAll(@Parameter(description = "角色编号", required = true, example = "1") Long roleId,
            @Parameter(description = "用户编号列表", required = true, example = "1,2") Long[] userIds)
    {
        return toAjax(roleService.deleteAuthUsers(roleId, userIds));
    }

    /**
     * 批量选择用户授权
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @Operation(summary = "批量选择用户授权")
    @PutMapping("/authUser/selectAll")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult selectAuthUserAll(@Parameter(description = "角色编号", required = true, example = "1") Long roleId,
            @Parameter(description = "用户编号列表", required = true, example = "1,2") Long[] userIds)
    {
        roleService.checkRoleDataScope(roleId);
        return toAjax(roleService.insertAuthUsers(roleId, userIds));
    }

    /**
     * 获取对应角色部门树列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @Operation(summary = "获取角色部门树")
    @GetMapping(value = "/deptTree/{roleId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult deptTree(@Parameter(description = "角色编号", required = true, example = "1") @PathVariable("roleId") Long roleId)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", deptService.selectDeptListByRoleId(roleId));
        ajax.put("depts", deptService.selectDeptTreeList(new SysDept()));
        return ajax;
    }
}
