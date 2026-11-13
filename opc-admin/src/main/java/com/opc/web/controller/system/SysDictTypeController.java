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
import com.opc.common.core.domain.entity.SysDictType;
import com.opc.common.core.page.TableDataInfo;
import com.opc.common.enums.BusinessType;
import com.opc.common.utils.poi.ExcelUtil;
import com.opc.system.service.ISysDictTypeService;

/**
 * 数据字典信息
 *
 * @author opc
 */
@RestController
@RequestMapping("/system/dict/type")
@Tag(name = "字典类型管理", description = "字典类型配置、查询及缓存管理")
public class SysDictTypeController extends BaseController
{
    @Autowired
    private ISysDictTypeService dictTypeService;

    @PreAuthorize("@ss.hasPermi('system:dict:list')")
    @Operation(summary = "查询字典类型列表")
    @GetMapping("/list")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = TableDataInfo.class)))
    public TableDataInfo list(@Parameter(description = "字典类型查询条件", required = false) SysDictType dictType)
    {
        startPage();
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        return getDataTable(list);
    }

    @Log(title = "字典类型", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:dict:export')")
    @Operation(summary = "导出字典类型数据")
    @PostMapping("/export")
    @ApiResponse(responseCode = "200", description = "文件导出成功", content = @Content(mediaType = "application/octet-stream"))
    public void export(HttpServletResponse response, @Parameter(description = "字典类型查询条件", required = false) SysDictType dictType)
    {
        List<SysDictType> list = dictTypeService.selectDictTypeList(dictType);
        ExcelUtil<SysDictType> util = new ExcelUtil<SysDictType>(SysDictType.class);
        util.exportExcel(response, list, "字典类型");
    }

    /**
     * 查询字典类型详细
     */
    @PreAuthorize("@ss.hasPermi('system:dict:query')")
    @Operation(summary = "获取字典类型详细信息")
    @GetMapping(value = "/{dictId}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult getInfo(@Parameter(description = "字典类型编号", required = true, example = "1") @PathVariable Long dictId)
    {
        return success(dictTypeService.selectDictTypeById(dictId));
    }

    /**
     * 新增字典类型
     */
    @PreAuthorize("@ss.hasPermi('system:dict:add')")
    @Log(title = "字典类型", businessType = BusinessType.INSERT)
    @Operation(summary = "新增字典类型")
    @PostMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult add(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新增字典类型", required = true) @RequestBody SysDictType dict)
    {
        if (!dictTypeService.checkDictTypeUnique(dict))
        {
            return error("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setCreateBy(getUsername());
        return toAjax(dictTypeService.insertDictType(dict));
    }

    /**
     * 修改字典类型
     */
    @PreAuthorize("@ss.hasPermi('system:dict:edit')")
    @Log(title = "字典类型", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改字典类型")
    @PutMapping
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult edit(@Validated @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改字典类型", required = true) @RequestBody SysDictType dict)
    {
        if (!dictTypeService.checkDictTypeUnique(dict))
        {
            return error("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dict.setUpdateBy(getUsername());
        return toAjax(dictTypeService.updateDictType(dict));
    }

    /**
     * 删除字典类型
     */
    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.DELETE)
    @Operation(summary = "删除字典类型")
    @DeleteMapping("/{dictIds}")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult remove(@Parameter(description = "字典类型编号列表", required = true, example = "1,2") @PathVariable Long[] dictIds)
    {
        dictTypeService.deleteDictTypeByIds(dictIds);
        return success();
    }

    /**
     * 刷新字典缓存
     */
    @PreAuthorize("@ss.hasPermi('system:dict:remove')")
    @Log(title = "字典类型", businessType = BusinessType.CLEAN)
    @Operation(summary = "刷新字典缓存")
    @DeleteMapping("/refreshCache")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult refreshCache()
    {
        dictTypeService.resetDictCache();
        return success();
    }

    /**
     * 获取字典选择框列表
     */
    @Operation(summary = "获取字典类型选择列表")
    @GetMapping("/optionselect")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult optionselect()
    {
        List<SysDictType> dictTypes = dictTypeService.selectDictTypeAll();
        return success(dictTypes);
    }
}
