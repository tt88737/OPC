package com.opc.web.controller.system;

import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.opc.common.config.OPCConfig;
import com.opc.common.core.domain.AjaxResult;
import com.opc.common.core.domain.entity.SysUser;
import com.opc.common.utils.SecurityUtils;
import com.opc.common.utils.StringUtils;
import com.opc.system.service.ISysUserService;

/**
 * 首页
 *
 * @author opc
 */
@RestController
@Tag(name = "系统入口", description = "系统首页信息与屏幕解锁")
public class SysIndexController
{
    /** 系统基础配置 */
    @Autowired
    private OPCConfig opcConfig;

    @Autowired
    private ISysUserService userService;

    /**
     * 访问首页，提示语
     */
    @RequestMapping("/")
    @Operation(summary = "获取系统首页信息")
    @ApiResponse(responseCode = "200", description = "页面加载成功", content = @Content(mediaType = "text/html"))
    public String index()
    {
        return StringUtils.format("欢迎使用{}后台管理框架，当前版本：v{}，请通过前端地址访问。", opcConfig.getName(), opcConfig.getVersion());
    }

    /**
     * 解锁屏幕
     */
    @PostMapping("/unlockscreen")
    @Operation(summary = "解锁屏幕")
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AjaxResult.class)))
    public AjaxResult unlockScreen(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "解锁屏幕凭据", required = true) @RequestBody Map<String, String> body)
    {
        String password = body.get("password");
        if (StringUtils.isEmpty(password))
        {
            return AjaxResult.error("密码不能为空");
        }
        String username = SecurityUtils.getUsername();
        SysUser user = userService.selectUserByUserName(username);
        if (user == null)
        {
            return AjaxResult.error("服务器超时，请重新登录");
        }
        if (!SecurityUtils.matchesPassword(password, user.getPassword()))
        {
            return AjaxResult.error("密码错误，请重新输入");
        }

        return AjaxResult.success("解锁成功");
    }
}
