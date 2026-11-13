package com.opc.web.controller.system;

import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.ArrayUtils;
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
import org.springframework.web.multipart.MultipartFile;
import com.opc.common.annotation.Log;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.domain.entity.SysDept;
import com.opc.common.core.domain.entity.SysRole;
import com.opc.common.core.domain.entity.SysUser;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.SecurityUtils;
import com.opc.common.utils.StringUtils;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.system.service.ISysDeptService;
import com.opc.system.service.ISysPostService;
import com.opc.system.service.ISysRoleService;
import com.opc.system.service.ISysUserService;

/**
 * 用户信息
 * 
 * @author opc
 */
@RestController
@RequestMapping("/system/user")
@Tag(name = "用户管理", description = "用户账号、资料及角色授权管理")
public class SysUserController extends BaseController
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysPostService postService;

    /**
     * 获取用户列表
     */
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @Operation(summary = "查询用户列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "用户查询条件", required = false) SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectUserList(user);
        return getDataTable(list);
    }

    @Log(title = "用户管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:user:export')")
    @Operation(summary = "导出用户数据")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "用户查询条件", required = false) SysUser user)
    {
        List<SysUser> list = userService.selectUserList(user);
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.exportExcel(response, list, "用户数据");
    }

    @Log(title = "用户管理", businessType = BusinessType.IMPORT)
    @PreAuthorize("@ss.hasPermi('system:user:import')")
    @Operation(summary = "导入用户数据")
    @PostMapping("/importData")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult importData(@Parameter(description = "用户导入文件", required = true, content = @Content(mediaType = "multipart/form-data")) MultipartFile file,
            @Parameter(description = "是否更新已存在用户", required = false, example = "false") boolean updateSupport) throws Exception
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        List<SysUser> userList = util.importExcel(file.getInputStream());
        String operName = getUsername();
        String message = userService.importUser(userList, updateSupport, operName);
        return success(message);
    }

    @Operation(summary = "下载用户导入模板")
    @PostMapping("/importTemplate")
    @ApiResponse(responseCode = "200", description = "文件下载成功", content = @Content(mediaType = "application/octet-stream"))
    public void importTemplate(HttpServletResponse response)
    {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        util.importTemplateExcel(response, "用户数据");
    }

    /**
     * 根据用户编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @Operation(summary = "获取用户详细信息")
    @GetMapping(value = { "/", "/{userId}" })
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "用户编号", required = false, example = "1") @PathVariable(value = "userId", required = false) Long userId)
    {
        AjaxResult ajax = AjaxResult.success();
        if (StringUtils.isNotNull(userId))
        {
            userService.checkUserDataScope(userId);
            SysUser sysUser = userService.selectUserById(userId);
            ajax.put(AjaxResult.DATA_TAG, sysUser);
            ajax.put("postIds", postService.selectPostListByUserId(userId));
            ajax.put("roleIds", sysUser.getRoles().stream().map(SysRole::getRoleId).collect(Collectors.toList()));
        }
        List<SysRole> roles = roleService.selectRoleAll();
        ajax.put("roles", SecurityUtils.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList()));
        ajax.put("posts", postService.selectPostAll());
        return ajax;
    }

    /**
     * 新增用户
     */
    @PreAuthorize("@ss.hasPermi('system:user:add')")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增用户")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增用户", required = true) @RequestBody SysUser user)
    {
        deptService.checkDeptDataScope(user.getDeptId());
        roleService.checkRoleDataScope(user.getRoleIds());
        if (!userService.checkUserNameUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return error("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setCreateBy(getUsername());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        return toAjax(userService.insertUser(user));
    }

    /**
     * 修改用户
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改用户")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改用户", required = true) @RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        deptService.checkDeptDataScope(user.getDeptId());
        roleService.checkRoleDataScope(user.getRoleIds());
        if (!userService.checkUserNameUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        }
        else if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        }
        else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return error("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setUpdateBy(getUsername());
        return toAjax(userService.updateUser(user));
    }

    /**
     * 删除用户
     */
    @PreAuthorize("@ss.hasPermi('system:user:remove')")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @Operation(summary = "删除用户")
    @DeleteMapping("/{userIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "用户编号列表", required = true, example = "1,2") @PathVariable Long[] userIds)
    {
        if (ArrayUtils.contains(userIds, getUserId()))
        {
            return error("当前用户不能删除");
        }
        return toAjax(userService.deleteUserByIds(userIds));
    }

    /**
     * 重置密码
     */
    @PreAuthorize("@ss.hasPermi('system:user:resetPwd')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "重置用户密码")
    @PutMapping("/resetPwd")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult resetPwd(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户密码重置参数", required = true) @RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        user.setUpdateBy(getUsername());
        return toAjax(userService.resetPwd(user));
    }

    /**
     * 状态修改
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改用户状态")
    @PutMapping("/changeStatus")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult changeStatus(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户状态参数", required = true) @RequestBody SysUser user)
    {
        userService.checkUserAllowed(user);
        userService.checkUserDataScope(user.getUserId());
        user.setUpdateBy(getUsername());
        return toAjax(userService.updateUserStatus(user));
    }

    /**
     * 根据用户编号获取授权角色
     */
    @PreAuthorize("@ss.hasPermi('system:user:query')")
    @Operation(summary = "获取用户授权角色")
    @GetMapping("/authRole/{userId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult authRole(@Parameter(description = "用户编号", required = true, example = "1") @PathVariable("userId") Long userId)
    {
        AjaxResult ajax = AjaxResult.success();
        SysUser user = userService.selectUserById(userId);
        List<SysRole> roles = roleService.selectRolesByUserId(userId);
        ajax.put("user", user);
        ajax.put("roles", SecurityUtils.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin()).collect(Collectors.toList()));
        return ajax;
    }

    /**
     * 用户授权角色
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @Operation(summary = "保存用户角色授权")
    @PutMapping("/authRole")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult insertAuthRole(@Parameter(description = "用户编号", required = true, example = "1") Long userId,
            @Parameter(description = "角色编号列表", required = true, example = "1,2") Long[] roleIds)
    {
        userService.checkUserDataScope(userId);
        roleService.checkRoleDataScope(roleIds);
        userService.insertUserAuth(userId, roleIds);
        return success();
    }

    /**
     * 获取部门树列表
     */
    @PreAuthorize("@ss.hasPermi('system:user:list')")
    @Operation(summary = "获取部门树")
    @GetMapping("/deptTree")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult deptTree(@Parameter(description = "部门查询条件", required = false) SysDept dept)
    {
        return success(deptService.selectDeptTreeList(dept));
    }
}
