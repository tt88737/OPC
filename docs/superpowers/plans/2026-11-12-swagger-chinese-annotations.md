# Swagger 中文注解全局优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为全仓库 24 个实际 Web Controller 和所有 HTTP 映射方法补齐准确的 OpenAPI 3 中文分组及中文摘要，并建立自动化遗漏检查。

**Architecture:** 在 Web Controller 层增加 `@Tag` 和 `@Operation`；生成器和调度模块各声明最小 Swagger 注解编译依赖，不修改请求处理逻辑、权限或数据模型。先用静态覆盖脚本约束源码完整性，再通过 Maven 编译和运行中的 `/v3/api-docs` 验证 Springdoc 实际输出。

**Tech Stack:** Java 17、Spring Boot 4.1.2、Springdoc OpenAPI 3.2.0、Swagger annotations Jakarta 2.2.55、Swagger UI 5.34.1、Maven、PowerShell 7/Windows PowerShell

---

## 文件结构

- 创建 `bin/verify-swagger-annotations.ps1`：检查 Controller 类级 `@Tag`、映射方法级 `@Operation` 以及中文文案。
- 修改 `opc-admin/src/main/java/com/opc/web/controller/common/*.java`：验证码和通用文件文档。
- 修改 `opc-admin/src/main/java/com/opc/web/controller/system/*.java`：认证、入口和系统管理文档。
- 修改 `opc-admin/src/main/java/com/opc/web/controller/monitor/*.java`：系统监控文档。
- 修改 `opc-admin/src/main/java/com/opc/web/controller/tool/TestController.java`：统一示例接口分组名称。
- 修改 `opc-generator/src/main/java/com/opc/generator/controller/GenController.java`：代码生成接口文档。
- 修改 `opc-quartz/src/main/java/com/opc/quartz/controller/SysJobController.java` 和 `SysJobLogController.java`：定时任务与调度日志接口文档。
- 修改根 `pom.xml`、`opc-generator/pom.xml`、`opc-quartz/pom.xml`：统一管理并声明最小 Swagger 注解编译依赖。

### Task 1: 建立 Swagger 注解覆盖检查

**Files:**
- Create: `bin/verify-swagger-annotations.ps1`

- [ ] **Step 1: 创建会失败的覆盖检查脚本**

脚本递归读取仓库各模块 `src/main/java` 下的 `*Controller.java`，只检查类上有 Spring `@RestController` 或 `@Controller` 的实际 Web Controller，执行以下断言：

以 `bin/verify-swagger-annotations.ps1` 为最终实现：遍历源码根目录并跳过 `target`，屏蔽注释后识别实际类注解，将映射注解与方法声明关联，分别验证类级中文 `@Tag(name)` 和方法级中文 `@Operation(summary)`。检查失败时输出文件、行号与缺失总数。

- [ ] **Step 2: 运行检查并确认当前失败**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: exit code `1`，输出 `SysUserController.java: 缺少中文 @Tag` 等遗漏；`TestController` 的方法摘要应已通过。

- [ ] **Step 3: 提交覆盖检查**

```powershell
git add -- bin/verify-swagger-annotations.ps1
git commit -m "test: enforce Chinese Swagger annotations"
```

### Task 2: 补齐基础、认证与入口接口

**Files:**
- Modify: `opc-admin/src/main/java/com/opc/web/controller/common/CaptchaController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/common/CommonController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysLoginController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysRegisterController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysIndexController.java`

- [ ] **Step 1: 为每个文件导入 OpenAPI 注解**

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
```

- [ ] **Step 2: 添加类级分组和方法摘要**

使用以下精确映射：

| Controller / `@Tag` | HTTP 映射 | `@Operation(summary)` |
| --- | --- | --- |
| `CaptchaController` / 验证码 | `GET /captchaImage` | 获取图片验证码 |
| `CommonController` / 通用文件 | `GET /common/download` | 下载临时文件 |
|  | `POST /common/upload` | 上传单个文件 |
|  | `POST /common/uploads` | 批量上传文件 |
|  | `GET /common/download/resource` | 下载资源文件 |
| `SysLoginController` / 登录认证 | `POST /login` | 用户登录 |
|  | `GET /getInfo` | 获取当前用户信息 |
|  | `GET /getRouters` | 获取当前用户路由 |
| `SysRegisterController` / 用户注册 | `POST /register` | 注册用户 |
| `SysIndexController` / 系统入口 | `GET /` | 获取系统首页信息 |
|  | `POST /unlockscreen` | 解锁屏幕 |

类注解示例：

```java
@Tag(name = "登录认证", description = "用户登录、当前用户信息和路由权限")
public class SysLoginController
```

方法注解示例：

```java
@Operation(summary = "用户登录")
@PostMapping("/login")
public AjaxResult login(@RequestBody LoginBody loginBody)
```

- [ ] **Step 3: 运行覆盖检查并确认只剩其他 Controller 报错**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: exit code `1`；本任务的 5 个 Controller 不再出现在错误列表中。

- [ ] **Step 4: 编译 `opc-admin`**

Run: `mvn -pl opc-admin -am -DskipTests package`

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交基础接口注解**

```powershell
git add -- opc-admin/src/main/java/com/opc/web/controller/common opc-admin/src/main/java/com/opc/web/controller/system/SysLoginController.java opc-admin/src/main/java/com/opc/web/controller/system/SysRegisterController.java opc-admin/src/main/java/com/opc/web/controller/system/SysIndexController.java
git commit -m "docs: annotate authentication and common APIs"
```

### Task 3: 补齐核心系统管理接口

**Files:**
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysUserController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysRoleController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysMenuController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysDeptController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysPostController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysProfileController.java`

- [ ] **Step 1: 添加 `Operation` 和 `Tag` 导入**

在 6 个文件中添加：

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
```

- [ ] **Step 2: 补齐用户、角色、菜单、部门、岗位和个人中心注解**

类分组分别使用：`用户管理`、`角色管理`、`菜单管理`、`部门管理`、`岗位管理`、`个人中心`。

方法摘要按 Controller 顺序精确使用：

```text
SysUserController:
查询用户列表；导出用户数据；导入用户数据；下载用户导入模板；获取用户详细信息；
新增用户；修改用户；删除用户；重置用户密码；修改用户状态；获取用户授权角色；
保存用户角色授权；获取部门树

SysRoleController:
查询角色列表；导出角色数据；获取角色详细信息；新增角色；修改角色；修改角色数据权限；
修改角色状态；删除角色；获取角色选择列表；查询已分配用户；查询未分配用户；
取消用户角色授权；批量取消用户角色授权；批量选择用户授权；获取角色部门树

SysMenuController:
查询菜单列表；获取菜单详细信息；获取菜单下拉树；获取角色菜单树；新增菜单；修改菜单；
更新菜单排序；删除菜单

SysDeptController:
查询部门列表；查询排除指定节点的部门列表；获取部门详细信息；新增部门；修改部门；
更新部门排序；删除部门

SysPostController:
查询岗位列表；导出岗位数据；获取岗位详细信息；新增岗位；修改岗位；删除岗位；获取岗位选择列表

SysProfileController:
获取个人信息；修改个人信息；修改个人密码；上传用户头像
```

每条摘要放在对应映射注解正上方，例如：

```java
@Operation(summary = "修改角色数据权限")
@PutMapping("/dataScope")
public AjaxResult dataScope(@RequestBody SysRole role)
```

- [ ] **Step 3: 运行覆盖检查**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: exit code `1`；本任务的 6 个 Controller 不再出现在错误列表中。

- [ ] **Step 4: 编译并提交核心系统接口注解**

Run: `mvn -pl opc-admin -am -DskipTests package`

Expected: `BUILD SUCCESS`。

```powershell
git add -- opc-admin/src/main/java/com/opc/web/controller/system/SysUserController.java opc-admin/src/main/java/com/opc/web/controller/system/SysRoleController.java opc-admin/src/main/java/com/opc/web/controller/system/SysMenuController.java opc-admin/src/main/java/com/opc/web/controller/system/SysDeptController.java opc-admin/src/main/java/com/opc/web/controller/system/SysPostController.java opc-admin/src/main/java/com/opc/web/controller/system/SysProfileController.java
git commit -m "docs: annotate core system management APIs"
```

### Task 4: 补齐字典、参数和公告接口

**Files:**
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysDictTypeController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysDictDataController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysConfigController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/system/SysNoticeController.java`

- [ ] **Step 1: 添加 OpenAPI 导入和类级 `@Tag`**

导入与前述任务相同，分组分别为：`字典类型管理`、`字典数据管理`、`参数配置`、`通知公告`。

- [ ] **Step 2: 按接口映射添加中文摘要**

```text
SysDictTypeController:
查询字典类型列表；导出字典类型数据；获取字典类型详细信息；新增字典类型；修改字典类型；
删除字典类型；刷新字典缓存；获取字典类型选择列表

SysDictDataController:
查询字典数据列表；导出字典数据；获取字典数据详细信息；根据字典类型查询数据；
新增字典数据；修改字典数据；删除字典数据

SysConfigController:
查询参数列表；导出参数数据；获取参数详细信息；根据参数键名查询参数值；新增参数；
修改参数；删除参数；刷新参数缓存

SysNoticeController:
查询通知公告列表；获取通知公告详细信息；新增通知公告；修改通知公告；查询顶部通知公告；
标记通知公告已读；批量标记通知公告已读；查询通知公告已读用户；删除通知公告
```

- [ ] **Step 3: 运行覆盖检查**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: exit code `1`；只剩监控模块和示例接口分组相关错误。

- [ ] **Step 4: 编译并提交配置类接口注解**

Run: `mvn -pl opc-admin -am -DskipTests package`

Expected: `BUILD SUCCESS`。

```powershell
git add -- opc-admin/src/main/java/com/opc/web/controller/system/SysDictTypeController.java opc-admin/src/main/java/com/opc/web/controller/system/SysDictDataController.java opc-admin/src/main/java/com/opc/web/controller/system/SysConfigController.java opc-admin/src/main/java/com/opc/web/controller/system/SysNoticeController.java
git commit -m "docs: annotate configuration and notice APIs"
```

### Task 5: 补齐监控模块并统一示例接口

**Files:**
- Modify: `opc-admin/src/main/java/com/opc/web/controller/monitor/SysUserOnlineController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/monitor/SysOperlogController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/monitor/SysLogininforController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/monitor/ServerController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/monitor/CacheController.java`
- Modify: `opc-admin/src/main/java/com/opc/web/controller/tool/TestController.java`

- [ ] **Step 1: 为监控 Controller 添加 OpenAPI 导入和类级分组**

分组分别为：`在线用户`、`操作日志`、`登录日志`、`服务监控`、`缓存监控`。`TestController` 的现有分组由 `用户信息管理` 改为 `示例用户接口`。

- [ ] **Step 2: 添加监控接口中文摘要**

```text
SysUserOnlineController:
查询在线用户列表；强制用户退出

SysOperlogController:
查询操作日志列表；导出操作日志；删除操作日志；清空操作日志

SysLogininforController:
查询登录日志列表；导出登录日志；删除登录日志；清空登录日志；解锁登录用户

ServerController:
获取服务器监控信息

CacheController:
获取缓存监控信息；获取缓存名称列表；获取缓存键名列表；获取缓存内容；
清理指定缓存名称；清理指定缓存键名；清空全部缓存
```

- [ ] **Step 3: 运行完整静态检查**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`

Expected: exit code `0`，输出 `Swagger 中文注解检查通过，共检查 24 个 Controller。`

- [ ] **Step 4: 编译并提交监控接口注解**

Run: `mvn -pl opc-admin -am -DskipTests package`

Expected: `BUILD SUCCESS`。

```powershell
git add -- opc-admin/src/main/java/com/opc/web/controller/monitor opc-admin/src/main/java/com/opc/web/controller/tool/TestController.java
git commit -m "docs: annotate monitoring APIs"
```

### Task 6: 运行时 OpenAPI 与 Swagger UI 回归

**Files:**
- No source changes expected

- [ ] **Step 1: 确认端口并重启后端**

先通过 `Get-NetTCPConnection -LocalPort 8080 -State Listen` 定位当前 Java 进程；只终止该监听进程，再启动最新构建产物：

```powershell
java -jar .\opc-admin\target\opc-admin.jar
```

Expected: 应用启动完成，日志包含 Spring Boot started 信息，`8080` 进入监听状态。

- [ ] **Step 2: 验证 OpenAPI JSON 可访问且所有操作有中文摘要**

```powershell
$client = New-Object System.Net.WebClient
$client.Encoding = [System.Text.Encoding]::UTF8
try {
    $document = ($client.DownloadString('http://127.0.0.1:8080/v3/api-docs') | ConvertFrom-Json)
} finally {
    $client.Dispose()
}
$missing = foreach ($path in $document.paths.PSObject.Properties) {
    foreach ($method in $path.Value.PSObject.Properties) {
        if ($method.Name -notin @('get','post','put','delete','patch')) { continue }
        $operation = $method.Value
        if ([string]::IsNullOrWhiteSpace($operation.summary) -or $operation.summary -notmatch '[\u4e00-\u9fff]') {
            "$($method.Name.ToUpper()) $($path.Name)"
        }
    }
}
if ($missing) { throw "缺少中文摘要：$($missing -join ', ')" }
```

Expected: 命令无异常，`$missing` 为空。

- [ ] **Step 3: 验证中文分组完整且没有自动英文 Controller 分组**

```powershell
$expectedTags = @('用户管理','角色管理','用户注册','个人中心','岗位管理','通知公告','菜单管理','登录认证','系统入口','字典类型管理','字典数据管理','部门管理','参数配置','在线用户','操作日志','登录日志','服务监控','缓存监控','通用文件','验证码','示例用户接口','代码生成','定时任务','调度日志')
$actualTags = @($document.tags.name)
$missingTags = $expectedTags | Where-Object { $_ -notin $actualTags }
$automaticTags = $actualTags | Where-Object { $_ -match '-controller$' }
if ($missingTags) { throw "缺少分组：$($missingTags -join ', ')" }
if ($automaticTags) { throw "存在自动英文分组：$($automaticTags -join ', ')" }
```

Expected: `$missingTags` 和 `$automaticTags` 均为空。

- [ ] **Step 4: 验证 Swagger UI 静态页面**

Run: `(Invoke-WebRequest 'http://127.0.0.1:8080/swagger-ui/index.html').StatusCode`

Expected: `200`。若通过前端代理访问，再验证 `http://localhost/dev-api/swagger-ui/index.html` 同样返回 `200`。

- [ ] **Step 5: 最终状态检查**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1
mvn -pl opc-admin -am -DskipTests package
git status --short
```

Expected: 注解检查通过、Maven `BUILD SUCCESS`；`git status` 只显示任务开始前已有的无关未跟踪文件或修改。

### Task 7: 运行时发现的跨模块补充

**Files:**
- Modify: `bin/verify-swagger-annotations.ps1`
- Modify: `opc-generator/src/main/java/com/opc/generator/controller/GenController.java`
- Modify: `opc-quartz/src/main/java/com/opc/quartz/controller/SysJobController.java`
- Modify: `opc-quartz/src/main/java/com/opc/quartz/controller/SysJobLogController.java`
- Modify: `docs/superpowers/specs/2026-11-12-swagger-chinese-annotations-design.md`
- Modify: `docs/superpowers/plans/2026-11-12-swagger-chinese-annotations.md`
- Modify: `pom.xml`
- Modify: `opc-generator/pom.xml`
- Modify: `opc-quartz/pom.xml`

- [ ] **Step 1: 将静态检查扩展到全仓库实际 Web Controller**

扫描各模块 `src/main/java` 下的 `*Controller.java`，仅检查类上有 Spring `@RestController` 或 `@Controller` 的文件；排除 `target` 与无 Web 注解的 `BaseController`。在补注解前，检查应覆盖 24 个 Controller，并报出 29 处缺失（3 个 `@Tag`、26 个 `@Operation`）。

- [ ] **Step 2: 为代码生成和定时调度接口补齐中文注解**

`GenController` 的 `@Tag` 为“代码生成”，13 条映射摘要按源码顺序为：查询代码生成表列表；获取代码生成表详细信息；查询数据库表列表；查询表字段列表；导入数据库表；创建数据库表；修改代码生成配置；删除代码生成表；预览生成代码；下载生成代码；生成代码；同步数据库表结构；批量生成代码。

`SysJobController` 的 `@Tag` 为“定时任务”，8 条映射摘要按源码顺序为：查询定时任务列表；导出定时任务；获取定时任务详细信息；新增定时任务；修改定时任务；修改定时任务状态；执行一次定时任务；删除定时任务。

`SysJobLogController` 的 `@Tag` 为“调度日志”，5 条映射摘要按源码顺序为：查询调度日志列表；导出调度日志；获取调度日志详细信息；删除调度日志；清空调度日志。

根 POM 管理与 Springdoc 3.2.0 当前解析结果一致的 `swagger-annotations-jakarta:2.2.55`，生成器和调度模块各声明无版本号的直接编译依赖；Springdoc UI starter 仍只由 `opc-admin` 使用。

- [ ] **Step 3: 重新执行静态、编译和运行时回归**

运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-swagger-annotations.ps1`，确认 24 个 Controller、0 处缺失；运行 Maven `-pl opc-admin -am -DskipTests compile`，确认 `BUILD SUCCESS`。重启后端后重新检查 `/v3/api-docs` 的 26 条摘要、三个中文分组和 Swagger UI 可访问性，确认接口路径、权限及业务逻辑未变化。

