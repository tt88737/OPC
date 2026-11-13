# OPC Foundation Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 OPC 整理为完整纳管、默认安全、无 RuoYi 工程残留、具备健康检查、测试和 CI 的可交付底座。

**Architecture:** `opc-system` 提供事务性管理员首次引导服务，`opc-admin` 通过启动 Runner 读取环境变量并调用；环境差异由 Spring Profile 隔离。仓库级 PowerShell 验证脚本负责锁文件、SQL/源码残留和安全配置检查，JUnit 负责 Java 行为，GitHub Actions 组合执行全部验证。

**Tech Stack:** Java 17、Spring Boot 4.1、Spring Security、MyBatis、Actuator、JUnit 5、Mockito、Vue 3、Vite、npm、PowerShell、GitHub Actions。

---

### Task 1: 建立 Git 纳管边界与锁文件规则

**Files:**
- Modify: `.gitignore`
- Modify: `frontend/.gitignore`
- Create: `bin/verify-repository.ps1`
- Track: `frontend/package-lock.json`

- [ ] **Step 1: 编写失败的仓库验证脚本**

脚本必须检查锁文件存在且未被忽略，并拒绝未跟踪的 Java、Vue、SQL、POM 和运行配置：

```powershell
$lockFile = 'frontend/package-lock.json'
if (-not (Test-Path $lockFile)) { throw "$lockFile 不存在" }
git check-ignore $lockFile 2>$null
if ($LASTEXITCODE -eq 0) { throw "$lockFile 被 .gitignore 忽略" }

$required = git ls-files --others --exclude-standard -- '*.java' '*.vue' '*.sql' 'pom.xml' '*.yml'
if ($required) { throw "存在未纳管工程文件：`n$($required -join "`n")" }
```

- [ ] **Step 2: 验证 RED**

Run: `powershell -ExecutionPolicy Bypass -File bin/verify-repository.ps1`

Expected: FAIL，至少报告 `frontend/package-lock.json` 被忽略和未纳管工程文件。

- [ ] **Step 3: 收紧忽略规则**

从 `frontend/.gitignore` 删除 `package-lock.json`；在根 `.gitignore` 增加：

```gitignore
.worktrees/
worktrees/
.local-backups/
dependency-tree*.txt
**/dependency-tree*.txt
task_plan.md
findings.md
progress.md
powershell-console-backup.reg
powershell-diagnostics.md
```

保持 `opc-local.env.ps1`、日志、`target`、`node_modules` 和 `dist` 被忽略。

- [ ] **Step 4: 纳管交付文件并验证 GREEN**

Run:

```powershell
git add .gitignore frontend/.gitignore frontend/package-lock.json bin/verify-repository.ps1
git add .github bin doc docs frontend opc-admin opc-common opc-framework opc-generator opc-quartz opc-system sql LICENSE README.md SECURITY.md dependency-check-suppressions.xml opc-local.env.example.ps1 opc.bat opc.sh pom.xml
powershell -ExecutionPolicy Bypass -File bin/verify-repository.ps1
git diff --cached --check
```

Expected: PASS；暂存区不包含本地密钥、日志、构建产物、依赖树和规划文件。

- [ ] **Step 5: 提交纳管基线**

```bash
git commit -m "build: track complete reproducible foundation"
```

### Task 2: 建立残留检查并删除 RuoYi 示例

**Files:**
- Create: `bin/verify-foundation-clean.ps1`
- Delete: `opc-admin/src/main/java/com/opc/web/controller/tool/TestController.java`
- Delete: `opc-quartz/src/main/java/com/opc/quartz/task/RyTask.java`
- Modify: `sql/opc_20260417.sql`
- Modify: `frontend/src/views/monitor/job/index.vue`
- Modify: `frontend/src/views/system/menu/index.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `opc-system/src/main/java/com/opc/system/domain/vo/RouterVo.java`
- Modify: `frontend/src/settings.js`
- Modify: `opc.sh`
- Create: `THIRD_PARTY_NOTICES.md`

- [ ] **Step 1: 编写失败的残留检查**

```powershell
$patterns = 'ruoyi|若依|ryTask|RyTask|admin123|Copyright © 2018-2026|\bry\b'
$matches = rg -n -i --glob '!target/**' --glob '!frontend/node_modules/**' --glob '!frontend/dist/**' --glob '!docs/superpowers/**' --glob '!THIRD_PARTY_NOTICES.md' $patterns .
if ($LASTEXITCODE -eq 0) { throw "发现底座残留：`n$matches" }
if ($LASTEXITCODE -gt 1) { throw 'rg 扫描失败' }
```

- [ ] **Step 2: 验证 RED**

Run: `powershell -ExecutionPolicy Bypass -File bin/verify-foundation-clean.ps1`

Expected: FAIL，报告 `RyTask`、`ry`、`admin123`、旧页脚和脚本注释。

- [ ] **Step 3: 删除示例代码和初始化数据**

删除两个示例 Java 文件；从 SQL 删除用户 `2`、对应 `sys_user_role`/`sys_user_post` 关联和三条 `sys_job` 插入；将路由示例值统一改为 `opc`；任务页只保留通用 Bean/Class 调用格式，不引用已删除类；页脚改为：

```js
footerContent: 'Copyright © 2026 OPC. All Rights Reserved.'
```

将 `opc.sh` 的示例命令改为 `./opc.sh`。

- [ ] **Step 4: 增加第三方 MIT 通知**

`THIRD_PARTY_NOTICES.md` 明确说明 OPC 包含基于 RuoYi MIT 许可修改的部分，并保留 `Copyright (c) 2018 RuoYi` 及 MIT 许可全文；该文件被残留扫描显式排除。

- [ ] **Step 5: 验证 GREEN 并提交**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File bin/verify-foundation-clean.ps1
& .\bin\verify-swagger-annotations.ps1
& 'D:\tools\apache-maven-3.9.16\bin\mvn.cmd' test
git add -A
git commit -m "refactor: remove upstream demo residue"
```

Expected: 残留检查、Swagger 检查和 Java 编译均通过。

### Task 3: 用 TDD 实现一次性管理员引导

**Files:**
- Modify: `pom.xml`
- Modify: `opc-system/pom.xml`
- Modify: `opc-admin/pom.xml`
- Create: `opc-system/src/main/java/com/opc/system/service/AdminBootstrapService.java`
- Create: `opc-system/src/main/java/com/opc/system/service/AdminBootstrapResult.java`
- Create: `opc-system/src/test/java/com/opc/system/service/AdminBootstrapServiceTest.java`
- Modify: `opc-system/src/main/java/com/opc/system/service/impl/SysUserServiceImpl.java`
- Create: `opc-system/src/test/java/com/opc/system/service/impl/SysUserImportSecurityTest.java`
- Modify: `opc-system/src/main/java/com/opc/system/mapper/SysUserMapper.java`
- Modify: `opc-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Create: `opc-admin/src/main/java/com/opc/web/core/bootstrap/AdminBootstrapRunner.java`
- Create: `opc-admin/src/test/java/com/opc/web/core/bootstrap/AdminBootstrapRunnerTest.java`
- Modify: `opc-common/src/main/java/com/opc/common/core/domain/entity/SysUser.java`
- Modify: `sql/opc_20260417.sql`

- [ ] **Step 1: 加入测试依赖并编写 Service 失败测试**

通过根 `dependencyManagement` 管理 `spring-boot-starter-test`，在 `opc-system` 和 `opc-admin` 以 test scope 引入。测试覆盖：变量为空不更新、弱密码拒绝、占位账号成功初始化、已初始化账号不覆盖、并发条件更新失败不误报成功。

核心期望：

```java
@Test
void initializesOnlyBootstrapPendingAdmin() {
    SysUser admin = admin(AdminBootstrapService.PENDING_PASSWORD_HASH, "1");
    when(userMapper.selectUserByUserName("admin")).thenReturn(admin);
    when(userMapper.initializeAdminPassword(eq(1L), eq(admin.getPassword()), anyString())).thenReturn(1);

    AdminBootstrapResult result = service.initialize("Strong!Pass2026");

    assertEquals(AdminBootstrapResult.INITIALIZED, result);
    verify(userMapper).initializeAdminPassword(eq(1L), eq(admin.getPassword()), argThat(hash ->
            SecurityUtils.matchesPassword("Strong!Pass2026", hash)));
}
```

- [ ] **Step 2: 验证 RED**

Run: `mvn -pl opc-system,opc-admin -am test -Dtest=AdminBootstrapServiceTest,AdminBootstrapRunnerTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL，类尚不存在。

- [ ] **Step 3: 实现最小事务服务和条件更新**

服务规则：12 至 64 位，至少包含大写、小写、数字和特殊字符。Mapper 使用单条条件更新保证原子性：

```xml
<update id="initializeAdminPassword">
  update sys_user
     set password = #{newPassword}, status = '0', pwd_update_date = sysdate(), update_time = sysdate()
   where user_id = #{userId} and status = '1' and password = #{expectedPassword}
</update>
```

Runner 读取 `${OPC_BOOTSTRAP_ADMIN_PASSWORD:}`，只记录 `INITIALIZED`、`NOT_CONFIGURED`、`ALREADY_INITIALIZED` 或 `REJECTED`，不得记录原始密码。

- [ ] **Step 4: 移除其他默认密码入口**

先在 `SysUserImportSecurityTest` 证明新导入用户不会得到共享密码，然后修改 `SysUserServiceImpl#importUser`：为每个新用户生成不可回推的随机 BCrypt，状态固定为停用；管理员必须单独重置密码并启用账号。现有用户更新导入不得覆盖密码或状态。删除对 `sys.user.initPassword` 的读取。

Run: `mvn -pl opc-system -am test -Dtest=SysUserImportSecurityTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected RED: 新测试观察到当前代码仍读取共享初始密码；实现后 GREEN。

- [ ] **Step 5: 移除密码日志风险并更新 SQL**

`SysUser.toString()` 删除 password 字段。SQL 中管理员状态改为 `1`，密码和服务常量都使用以下仅保留哈希、原文已丢弃的高熵占位 BCrypt：

```text
$2a$10$G/SUvAekkFytyaaBYNX0a.1S1aQmkvX8W5A6LvRG9j1lFjPqvpghG
```

删除 `sys.user.initPassword` 配置记录，不新增其他共享初始密码。

- [ ] **Step 6: 验证 GREEN 并提交**

Run:

```powershell
& 'D:\tools\apache-maven-3.9.16\bin\mvn.cmd' -pl opc-system,opc-admin -am test
powershell -ExecutionPolicy Bypass -File bin/verify-foundation-clean.ps1
git add pom.xml opc-system opc-admin opc-common sql
git commit -m "feat: add one-time administrator bootstrap"
```

### Task 4: 拆分开发与生产安全配置

**Files:**
- Modify: `opc-admin/src/main/resources/application.yml`
- Rename: `opc-admin/src/main/resources/application-druid.yml` to `application-datasource.yml`
- Create: `opc-admin/src/main/resources/application-dev.yml`
- Create: `opc-admin/src/main/resources/application-prod.yml`
- Modify: `opc-local.env.example.ps1`
- Modify: `README.md`
- Modify: `SECURITY.md`
- Create: `bin/verify-security-profiles.ps1`

- [ ] **Step 1: 编写失败的配置验证**

脚本解析配置文本并断言：默认 profile 为 `${OPC_PROFILE:dev}`；prod 中 Swagger、API docs、Druid、DevTools 均为 false；Druid 用户名密码无固定默认值；prod CORS、DB、JWT 必须来自无默认值的环境变量。

- [ ] **Step 2: 验证 RED**

Run: `powershell -ExecutionPolicy Bypass -File bin/verify-security-profiles.ps1`

Expected: FAIL，当前没有 dev/prod 配置且 Druid 存在固定默认凭据。

- [ ] **Step 3: 拆分 Profile**

通用配置：

```yaml
spring:
  profiles:
    active: ${OPC_PROFILE:dev}
    group:
      dev: datasource
      prod: datasource
```

`application-dev.yml` 允许通过环境变量开启文档和 Druid，但不提供 Druid 凭据；`application-prod.yml` 固定关闭文档、Druid 和 DevTools，并要求显式 CORS 与 Redis 密码。

- [ ] **Step 4: 更新环境示例和文档**

示例增加 `OPC_PROFILE=dev` 与 `OPC_BOOTSTRAP_ADMIN_PASSWORD` 占位符；README 分开描述首次启动和生产部署；SECURITY 明确禁止生产启用文档/Druid。

- [ ] **Step 5: 验证 GREEN 并提交**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File bin/verify-security-profiles.ps1
& 'D:\tools\apache-maven-3.9.16\bin\mvn.cmd' test
git add opc-admin/src/main/resources opc-local.env.example.ps1 README.md SECURITY.md bin/verify-security-profiles.ps1
git commit -m "config: separate development and production security"
```

### Task 5: 用 TDD 增加标准健康检查

**Files:**
- Modify: `opc-admin/pom.xml`
- Modify: `opc-framework/src/main/java/com/opc/framework/config/SecurityConfig.java`
- Modify: `opc-admin/src/main/resources/application.yml`
- Modify: `opc-admin/src/main/resources/application-prod.yml`
- Create: `opc-admin/src/test/java/com/opc/web/core/health/HealthEndpointSecurityTest.java`

- [ ] **Step 1: 编写失败的安全边界测试**

测试使用 MockMvc/Spring Security 断言 `/actuator/health` 匿名返回 200，而 `/actuator/env` 不公开，普通业务接口无 Token 返回 401。

- [ ] **Step 2: 验证 RED**

Run: `mvn -pl opc-admin -am test -Dtest=HealthEndpointSecurityTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: FAIL，Actuator 依赖和匿名规则尚不存在。

- [ ] **Step 3: 实现最小健康检查**

加入 `spring-boot-starter-actuator`，Security 只增加：

```java
.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
```

配置只暴露 health：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 4: 验证 GREEN 并提交**

Run: `mvn -pl opc-admin -am test -Dtest=HealthEndpointSecurityTest -Dsurefire.failIfNoSpecifiedTests=false`

Expected: PASS。

```bash
git add opc-admin opc-framework
git commit -m "feat: expose minimal actuator health endpoint"
```

### Task 6: 安全迁移当前本地数据库并验证首次引导

**Files:**
- Create ignored backup: `.local-backups/foundation-cleanup-20261113.sql`
- Modify ignored local config: `opc-local.env.ps1`
- Runtime data: local MySQL `opc`

- [ ] **Step 1: 精确备份待修改记录**

使用 `mysqldump --where` 分别备份 `sys_user`、`sys_user_role`、`sys_user_post`、`sys_job` 和 `sys_config` 涉及记录到 `.local-backups`；确认备份文件非空且不被 Git 纳管。

- [ ] **Step 2: 生成本地强密码并准备引导变量**

用以下逻辑生成本地随机密码，不输出变量值；随后通过 `apply_patch` 将 `$bootstrapPassword` 的实际值写入被忽略的 `opc-local.env.ps1` 的 `OPC_BOOTSTRAP_ADMIN_PASSWORD`，并将 `OPC_PROFILE` 设为 `dev`：

```powershell
$bytes = [byte[]]::new(24)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$bootstrapPassword = [Convert]::ToBase64String($bytes) + 'aA1!'
```

不得把密码输出到终端日志或提交历史。

- [ ] **Step 3: 事务清理数据库**

事务中删除用户 `ry` 的岗位/角色关系和账号、删除 `ryTask` 任务，将 `admin` 更新为禁用和占位哈希，并移除 `123456` 初始密码配置。提交后只输出行数统计。

- [ ] **Step 4: 重启并验证一次性引导**

停止精确的 OPC Java PID，执行 `mvn -DskipTests package`，加载本地环境并启动。验证数据库中 `admin.status='0'`、密码已变化且不是占位哈希；第二次重启后密码哈希保持不变。

- [ ] **Step 5: 验证登录、健康和文档**

验证 `/actuator/health` 返回标准 `UP`；开发环境 Swagger 可访问；使用本地引导密码完成登录；数据库中 `ry` 用户和 `ryTask` 任务数量均为 0。

### Task 7: 增加 CI 和统一验证入口

**Files:**
- Create: `bin/verify-foundation.ps1`
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/dependency-check.yml`
- Modify: `README.md`

- [ ] **Step 1: 编写统一验证脚本并先观察失败项**

脚本按顺序运行仓库纳管、残留、安全 Profile、Swagger、Maven 测试、`npm ci`、前端生产构建和 `npm audit --audit-level=high`，任一步失败即返回非零。

- [ ] **Step 2: 配置主 CI**

`ci.yml` 在 push/PR 上使用 Java 17、Maven 缓存和 Node 20，执行：

```yaml
- run: mvn --batch-mode test
- shell: pwsh
  run: ./bin/verify-swagger-annotations.ps1
- shell: pwsh
  run: ./bin/verify-foundation-clean.ps1
- working-directory: frontend
  run: npm ci
- working-directory: frontend
  run: npm run build:prod
```

- [ ] **Step 3: 配置依赖扫描工作流**

手动及每周运行 OWASP aggregate 扫描，阈值 CVSS 7；使用 `NVD_API_KEY` secret（存在时）并始终上传 HTML/JSON 报告。`npm audit --audit-level=high` 同步执行；扫描源错误必须使 job 失败并在日志中明确，不输出“零漏洞”。

- [ ] **Step 4: 本地验证并提交**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File bin/verify-foundation.ps1
git add bin .github/workflows README.md
git commit -m "ci: verify foundation build security and dependencies"
```

Expected: 所有本地可执行检查通过。

### Task 8: 最终回归与交付审计

**Files:**
- Modify: `progress.md` and `findings.md` only as ignored working records
- Report: `target/dependency-check-report.html`

- [ ] **Step 1: 运行完整后端和前端验证**

Run:

```powershell
& 'D:\tools\apache-maven-3.9.16\bin\mvn.cmd' clean test package
Set-Location frontend
npm ci
npm run build:prod
npm audit --json
```

Expected: 后端测试/打包、前端安装/构建和 audit 全部通过。

- [ ] **Step 2: 运行实际依赖扫描**

Run OWASP aggregate，确保报告扫描依赖数大于 0；记录哪些数据源成功、哪些因认证或网络不可用而缺失，不把部分扫描描述成绝对无漏洞。

- [ ] **Step 3: 从 Git 索引验证交付完整性**

Run:

```powershell
git status --short
git ls-files frontend/package-lock.json sql/opc_20260417.sql opc-admin/src/main/resources/application-prod.yml
git grep -n -i -E 'admin123|ryTask|Copyright © 2018-2026'
git diff --check HEAD
```

Expected: 必要文件均被跟踪，残留搜索无结果，本地秘密和构建产物未被跟踪。

- [ ] **Step 4: 运行时回归**

验证后端进程、`/actuator/health`、`/v3/api-docs`（dev）、验证码、登录和前端首页；确认生产 Profile 下 Swagger 与 Druid 不可访问。

- [ ] **Step 5: 汇总提交和剩余风险**

输出各提交、测试数量、扫描依赖数量、运行地址，以及 CISA/Sonatype 等未覆盖数据源。只有所有验收项均有新鲜证据时才声明底座满足交付要求。
