package com.opc.web.controller.system;

import java.util.List;
import java.util.Map;
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
import com.opc.common.constant.UserConstants;
import com.opc.common.core.controller.BaseController;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.domain.entity.SysMenu;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.StringUtils;
import com.opc.system.service.ISysMenuService;

/**
 * 菜单信息
 * 
 * @author opc
 */
@RestController
@RequestMapping("/system/menu")
@Tag(name = "菜单管理", description = "菜单配置、树形查询及排序管理")
public class SysMenuController extends BaseController
{
    @Autowired
    private ISysMenuService menuService;

    /**
     * 获取菜单列表
     */
    @PreAuthorize("@ss.hasPermi('system:menu:list')")
    @Operation(summary = "查询菜单列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult list(@Parameter(description = "菜单查询条件", required = false) SysMenu menu)
    {
        List<SysMenu> menus = menuService.selectMenuList(menu, getUserId());
        return success(menus);
    }

    /**
     * 根据菜单编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:menu:query')")
    @Operation(summary = "获取菜单详细信息")
    @GetMapping(value = "/{menuId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "菜单编号", required = true, example = "1") @PathVariable Long menuId)
    {
        return success(menuService.selectMenuById(menuId));
    }

    /**
     * 获取菜单下拉树列表
     */
    @Operation(summary = "获取菜单下拉树")
    @GetMapping("/treeselect")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult treeselect(@Parameter(description = "菜单查询条件", required = false) SysMenu menu)
    {
        List<SysMenu> menus = menuService.selectMenuList(menu, getUserId());
        return success(menuService.buildMenuTreeSelect(menus));
    }

    /**
     * 加载对应角色菜单列表树
     */
    @Operation(summary = "获取角色菜单树")
    @GetMapping(value = "/roleMenuTreeselect/{roleId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult roleMenuTreeselect(@Parameter(description = "角色编号", required = true, example = "1") @PathVariable("roleId") Long roleId)
    {
        List<SysMenu> menus = menuService.selectMenuList(getUserId());
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", menuService.selectMenuListByRoleId(roleId));
        ajax.put("menus", menuService.buildMenuTreeSelect(menus));
        return ajax;
    }

    /**
     * 新增菜单
     */
    @PreAuthorize("@ss.hasPermi('system:menu:add')")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @Operation(summary = "新增菜单")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增菜单", required = true) @RequestBody SysMenu menu)
    {
        if (!menuService.checkMenuNameUnique(menu))
        {
            return error("新增菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        else if (UserConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath()))
        {
            return error("新增菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        else if (!menuService.checkRouteConfigUnique(menu))
        {
            return error("新增菜单'" + menu.getMenuName() + "'失败，路由名称或地址已存在");
        }
        menu.setCreateBy(getUsername());
        return toAjax(menuService.insertMenu(menu));
    }

    /**
     * 修改菜单
     */
    @PreAuthorize("@ss.hasPermi('system:menu:edit')")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改菜单")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改菜单", required = true) @RequestBody SysMenu menu)
    {
        if (!menuService.checkMenuNameUnique(menu))
        {
            return error("修改菜单'" + menu.getMenuName() + "'失败，菜单名称已存在");
        }
        else if (UserConstants.YES_FRAME.equals(menu.getIsFrame()) && !StringUtils.ishttp(menu.getPath()))
        {
            return error("修改菜单'" + menu.getMenuName() + "'失败，地址必须以http(s)://开头");
        }
        else if (menu.getMenuId().equals(menu.getParentId()))
        {
            return error("修改菜单'" + menu.getMenuName() + "'失败，上级菜单不能选择自己");
        }
        else if (!menuService.checkRouteConfigUnique(menu))
        {
            return error("修改菜单'" + menu.getMenuName() + "'失败，路由名称或地址已存在");
        }
        menu.setUpdateBy(getUsername());
        return toAjax(menuService.updateMenu(menu));
    }

    /**
     * 保存菜单排序
     */
    @PreAuthorize("@ss.hasPermi('system:menu:edit')")
    @Log(title = "保存菜单排序", businessType = BusinessType.UPDATE)
    @Operation(summary = "更新菜单排序")
    @PutMapping("/updateSort")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult updateSort(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "菜单排序参数", required = true) @RequestBody Map<String, String> params)
    {
        String[] menuIds = params.get("menuIds").split(",");
        String[] orderNums = params.get("orderNums").split(",");
        menuService.updateMenuSort(menuIds, orderNums);
        return success();
    }

    /**
     * 删除菜单
     */
    @PreAuthorize("@ss.hasPermi('system:menu:remove')")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @Operation(summary = "删除菜单")
    @DeleteMapping("/{menuId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "菜单编号", required = true, example = "1") @PathVariable("menuId") Long menuId)
    {
        if (menuService.hasChildByMenuId(menuId))
        {
            return warn("存在子菜单,不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId))
        {
            return warn("菜单已分配,不允许删除");
        }
        return toAjax(menuService.deleteMenuById(menuId));
    }
}
