# Swagger 参数与返回数据注解 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 24 个 Web Controller 补齐中文请求参数、请求体字段、公共返回模型和文件接口媒体类型说明，并通过运行中的 OpenAPI 文档验证契约准确。

**Architecture:** 在 DTO/公共响应模型上使用字段级 `@Schema`，在 Controller 参数上使用 `@Parameter`、`@RequestBody` 和 `@Content`，在方法上使用 `@ApiResponse` 描述可确认的返回结构。保留现有 Java 类型、JSON 键名和业务逻辑；不创建替代运行时 DTO。

**Tech Stack:** Java 17、Springdoc OpenAPI 3.2.0、Swagger annotations 2.2.55、Spring MVC、Maven、PowerShell 5.1

---

## 文件分组

- `opc-common/src/main/java/com/opc/common/core/domain/{AjaxResult,R}.java`、`opc-common/src/main/java/com/opc/common/core/page/TableDataInfo.java`：公共响应字段和统一响应说明。
- `opc-common/src/main/java/com/opc/common/core/domain/entity/*.java`：用户、角色、部门、菜单、字典等请求/返回实体。
- `opc-common/src/main/java/com/opc/common/core/domain/model/{LoginBody,RegisterBody}.java`：认证请求体。
- `opc-system/src/main/java/com/opc/system/domain/*.java`：公告、配置、日志、缓存等模型；`opc-system/.../domain/vo/*.java`：路由/元数据模型。
- `opc-generator/src/main/java/com/opc/generator/domain/{GenTable,GenTableColumn}.java`：代码生成模型。
- `opc-quartz/src/main/java/com/opc/quartz/domain/{SysJob,SysJobLog}.java`：定时任务模型。
- `opc-admin/src/main/java/com/opc/web/controller/**/*.java`、`opc-generator/.../GenController.java`、`opc-quartz/.../{SysJobController,SysJobLogController}.java`：参数和方法响应元数据。
- `bin/verify-swagger-annotations.ps1`：扩展为检查参数/请求体/公共响应字段中文说明。

### Task 1: 公共响应模型与验证脚本基线

**Files:**
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/AjaxResult.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/R.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/page/TableDataInfo.java`
- Modify: `bin/verify-swagger-annotations.ps1`

- [ ] **Step 1: 运行当前契约检查建立失败基线**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: 24 个 Controller 的 Tag/Operation 检查通过，但新增的参数和返回字段检查尚未存在。

- [ ] **Step 2: 为公共字段添加 Schema**

使用以下字段注解，保留原字段类型、名称和 getter/setter：

```java
@Schema(description = "响应状态码", example = "200")
private int code;

@Schema(description = "响应消息", example = "操作成功")
private String msg;

@Schema(description = "业务数据")
private T data;
```

`AjaxResult` 继承 `HashMap`，不把静态常量误当成 JSON 属性；使用类级 `@Schema(description = "统一响应结果，包含 code、msg、data 字段")`，并在 Controller 的 `@ApiResponse` 中提供不含敏感值的 JSON `@ExampleObject`，说明真实键名。`TableDataInfo` 为 `total`、`rows`、`code`、`msg` 分别添加“总记录数、分页数据、状态码、消息”说明；`R<T>` 使用字段级 `@Schema` 说明 code/msg/data。

- [ ] **Step 3: 扩展静态检查**

检查公共模型四个字段均存在 `@Schema` 且 description 含中文；扫描所有映射方法的参数，要求路径/查询/请求体/文件参数存在中文说明。检查只针对实际 Web Controller 的 public mapping 方法，不将 `HttpServletRequest/Response` 作为业务参数要求说明。

- [ ] **Step 4: 编译并提交**

Run: `C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd -pl opc-admin -am -DskipTests compile`

```powershell
git add -- opc-common/src/main/java/com/opc/common/core/domain/AjaxResult.java opc-common/src/main/java/com/opc/common/core/domain/R.java opc-common/src/main/java/com/opc/common/core/page/TableDataInfo.java bin/verify-swagger-annotations.ps1
git commit -m "docs: describe common Swagger response models"
```

### Task 2: 核心系统请求模型字段

**Files:**
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/entity/SysUser.java`
- Modify: `.../SysRole.java`, `SysDept.java`, `SysMenu.java`, `SysDictData.java`, `SysDictType.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/model/LoginBody.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/model/RegisterBody.java`
- Modify: `opc-system/src/main/java/com/opc/system/domain/SysPost.java`
- Modify: `opc-system/src/main/java/com/opc/system/domain/SysNotice.java`
- Modify: `opc-system/src/main/java/com/opc/system/domain/SysConfig.java`

- [ ] **Step 1: 为模型类增加中文 Schema 和字段说明**

每个模型使用类级 `@Schema(description = "...")`；字段使用真实业务含义，例如：

```java
@Schema(description = "用户编号", example = "100")
private Long userId;

@Schema(description = "登录账号", example = "admin")
private String userName;
```

密码、Token、密钥字段只写用途，不填写真实值或可复用凭据。列表查询字段、状态字段、时间字段和关联 ID 说明与现有校验/枚举保持一致。

- [ ] **Step 2: 验证模型字段覆盖**

静态脚本读取这些类的非静态持久化字段，要求每个字段存在 `@Schema(description=...)`；继承自 `BaseEntity` 的字段由公共基类统一覆盖。

- [ ] **Step 3: 编译并提交**

Run: `...\mvn.cmd -pl opc-admin -am -DskipTests compile`

```powershell
git add -- opc-common/src/main/java/com/opc/common/core/domain/entity opc-common/src/main/java/com/opc/common/core/domain/model opc-system/src/main/java/com/opc/system/domain/SysPost.java opc-system/src/main/java/com/opc/system/domain/SysNotice.java opc-system/src/main/java/com/opc/system/domain/SysConfig.java
git commit -m "docs: describe core system request models"
```

### Task 3: 监控、代码生成和定时任务模型字段

**Files:**
- Modify: `opc-system/src/main/java/com/opc/system/domain/{SysCache,SysLogininfor,SysOperLog,SysUserOnline}.java`
- Modify: `opc-system/src/main/java/com/opc/system/domain/vo/{MetaVo,RouterVo}.java`
- Modify: `opc-generator/src/main/java/com/opc/generator/domain/{GenTable,GenTableColumn}.java`
- Modify: `opc-quartz/src/main/java/com/opc/quartz/domain/SysJob.java`
- Modify: `opc-quartz/src/main/java/com/opc/quartz/domain/SysJobLog.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/BaseEntity.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/TreeEntity.java`

`BaseEntity` and `TreeEntity` are included because their inherited audit/tree fields are serialized by the request models; annotate only their existing fields and do not add new properties.

- [ ] **Step 1: 添加类级和字段级 Schema**

说明缓存名称/键/值、登录 IP/浏览器/状态、代码生成表和字段配置、Cron 表达式、任务状态、调度异常等字段。`GenTable.options` 等 JSON 字符串说明其用途，不展开成虚假的静态对象。

- [ ] **Step 2: 检查敏感字段和枚举说明**

不为异常堆栈、密码、Token 添加真实 example；状态、并发策略、misfire 策略使用中文 description，必要时使用 `allowableValues` 列出源码中的常量值。

- [ ] **Step 3: 编译并提交**

Run: `...\mvn.cmd -pl opc-admin -am -DskipTests compile`

```powershell
git add -- opc-system/src/main/java/com/opc/system/domain opc-generator/src/main/java/com/opc/generator/domain opc-quartz/src/main/java/com/opc/quartz/domain
git commit -m "docs: describe monitoring and scheduler models"
```

### Task 4: Controller 参数与请求体注解

**Files:**
- Modify: all 24 Controller files identified by `bin/verify-swagger-annotations.ps1`.

- [ ] **Step 1: 为路径和查询参数添加 Parameter**

使用真实参数名和映射路径，例如：

```java
public AjaxResult getInfo(
    @Parameter(description = "用户编号", required = true, example = "100")
    @PathVariable Long userId)
```

对可选查询参数使用 `required = false`；对路径参数保持 `required = true`。不覆盖 Spring 从 `@RequestParam` / `@PathVariable` 推断出的名称和默认值。

- [ ] **Step 2: 为请求体和文件参数添加说明**

```java
public AjaxResult add(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户新增信息", required = true)
    @Validated @RequestBody SysUser user)
```

文件参数使用 `@Parameter(description = "待上传的文件", required = true, content = @Content(mediaType = "multipart/form-data"))`；不要把 `HttpServletRequest`、`HttpServletResponse` 标成业务入参。

- [ ] **Step 3: 为返回响应添加 ApiResponse**

对 JSON 接口使用 `@ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = AjaxResult.class)))` 或 `TableDataInfo.class`；对 `void` 文件下载使用 `@Content(mediaType = "application/octet-stream")`。响应模型只能引用实际返回类型，不伪造 data 的具体泛型。

- [ ] **Step 4: 分模块静态检查和编译**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: 24 个 Controller、所有 mapping 参数有中文说明、所有 JSON mapping 有响应说明、文件接口媒体类型有明确声明；无敏感 example。

Run: `...\mvn.cmd -pl opc-admin -am -DskipTests compile`

### Task 5: 特殊媒体类型与全量契约验证

**Files:**
- Modify only these special-interface files: `opc-admin/src/main/java/com/opc/web/controller/common/CommonController.java`, `opc-admin/src/main/java/com/opc/web/controller/common/CaptchaController.java`, `opc-admin/src/main/java/com/opc/web/controller/system/SysUserController.java`, `opc-generator/src/main/java/com/opc/generator/controller/GenController.java`.
- Modify: `bin/verify-swagger-annotations.ps1` only when a reproducible false positive is demonstrated by a fixture; otherwise leave the checker unchanged.

- [ ] **Step 1: 固定特殊接口契约**

验证并修正以下媒体类型：文件上传 `multipart/form-data`；图片验证码 `image/*`；文件下载和代码生成下载 `application/octet-stream`；Excel/CSV 导出按现有响应头声明。不得改动实际响应头代码。

- [ ] **Step 2: 启动并读取 OpenAPI**

停止并仅重启当前仓库的 `opc-admin.jar`，加载既有本地环境配置，不输出密钥。使用 PowerShell 5.1 的 UTF-8 `WebClient.DownloadString` + `ConvertFrom-Json` 读取 `/v3/api-docs`。

- [ ] **Step 3: 自动契约断言**

断言每个 operation 至少有一个准确的 `parameters`、必要时有 `requestBody`，`responses.200` 存在；关键模型属性含中文 description；文件接口媒体类型符合 Task 5 Step 1；24 个中文 tags 和所有中文 summaries 继续保持。

- [ ] **Step 4: 回归验证与提交**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1
& 'C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd' -pl opc-admin -am test
```

Expected: 静态检查通过、Maven `BUILD SUCCESS`、OpenAPI 参数/请求体/响应结构有效、Swagger UI 200 HTML。

