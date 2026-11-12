# Swagger 中文注解全局优化设计

## 目标

为仓库中全部 24 个实际 Web Controller 补齐准确、一致的 OpenAPI 3 中文注解，使 Swagger UI 能按中文业务名称分组，并为每个接口展示清晰的中文摘要。

本次只调整接口文档元数据，不改变接口路径、HTTP 方法、权限、请求参数、响应结构或业务行为。

## 范围

覆盖仓库各模块 `src/main/java` 下带 Spring `@RestController` 或 `@Controller` 的全部 24 个 Web Controller，包括：

- 系统管理：用户、角色、菜单、部门、岗位、字典类型、字典数据、参数、通知公告和个人中心。
- 认证与入口：登录、注册、验证码和系统入口。
- 系统监控：在线用户、操作日志、登录日志、服务监控和缓存监控。
- 通用能力：文件上传、批量上传、下载和资源下载。
- 示例接口：保留现有测试 Controller，并统一其分组命名风格。
- 代码生成：数据库表导入、配置、预览与生成代码。
- 定时调度：定时任务管理和调度日志。

## 注解规范

每个 Controller 使用类级 `@Tag`：

```java
@Tag(name = "用户管理", description = "系统用户查询、维护、状态管理和角色授权")
```

每个对外映射方法使用方法级 `@Operation`：

```java
@Operation(summary = "查询用户列表")
```

中文摘要根据接口的实际业务语义、映射路径、权限标识和现有方法注释逐项确定，不根据 Java 方法名批量机械翻译。相似操作统一使用“查询、获取、新增、修改、删除、导出、导入、清空、刷新、授权、取消授权”等动词。

本次不扩展 DTO 字段级 `@Schema`、响应码级 `@ApiResponse` 或参数级 `@Parameter`。这些内容涉及跨模块模型与统一响应协议，后续可作为独立任务补充。

## 中文分组

| Controller | Swagger 分组 |
| --- | --- |
| `SysUserController` | 用户管理 |
| `SysRoleController` | 角色管理 |
| `SysRegisterController` | 用户注册 |
| `SysProfileController` | 个人中心 |
| `SysPostController` | 岗位管理 |
| `SysNoticeController` | 通知公告 |
| `SysMenuController` | 菜单管理 |
| `SysLoginController` | 登录认证 |
| `SysIndexController` | 系统入口 |
| `SysDictTypeController` | 字典类型管理 |
| `SysDictDataController` | 字典数据管理 |
| `SysDeptController` | 部门管理 |
| `SysConfigController` | 参数配置 |
| `SysUserOnlineController` | 在线用户 |
| `SysOperlogController` | 操作日志 |
| `SysLogininforController` | 登录日志 |
| `ServerController` | 服务监控 |
| `CacheController` | 缓存监控 |
| `CommonController` | 通用文件 |
| `CaptchaController` | 验证码 |
| `TestController` | 示例用户接口 |
| `GenController` | 代码生成 |
| `SysJobController` | 定时任务 |
| `SysJobLogController` | 调度日志 |

## 兼容性与错误处理

使用项目当前 Springdoc OpenAPI 3 的 `io.swagger.v3.oas.annotations` 注解。`opc-admin` 保留 Springdoc UI starter；`opc-generator` 和 `opc-quartz` 仅声明与其当前解析版本一致的 `swagger-annotations-jakarta` 编译依赖，版本在根 POM 中统一管理。注解仅参与文档生成，不介入请求处理流程。

若某接口在运行时未进入 OpenAPI 文档，优先检查 Springdoc 扫描范围、映射冲突和接口可见性；不通过修改安全权限来解决文档缺失问题。

## 验证

1. 执行 `bin/verify-swagger-annotations.ps1`，检查全仓库 `src/main/java` 下实际 Web Controller 的中文注解覆盖，确认共 24 个 Controller 且无遗漏；执行 Maven 构建，确保所有注解导入和编译通过。
2. 启动或重启后端，确认 `/v3/api-docs` 返回有效 OpenAPI JSON。
3. 自动检查所有映射接口均有非空 `summary`。
4. 自动检查全部 24 个业务分组均为预期中文名称，不再出现 `sys-user-controller`、`cache-controller`、`gen-controller` 等自动英文分组。
5. 打开 Swagger UI，检查系统、监控、认证和通用接口均可展开查看，原有接口路径和调用方式不变。

## 非目标

- 不隐藏任何现有基础或业务接口。
- 不修改认证、鉴权或 Swagger 访问策略。
- 不修改数据库、前端菜单或业务逻辑。
- 不在本次任务中补齐全部数据模型字段说明和响应码说明。
