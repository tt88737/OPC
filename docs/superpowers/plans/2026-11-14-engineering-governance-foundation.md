# Engineering Governance Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `main` 底座上建立可执行的工程治理、工具链、质量门禁、测试覆盖率和维护规范。

**Architecture:** 保留现有 `bin/verify-foundation.ps1` 作为统一入口，在其前后增加工具链和质量检查阶段；Maven 负责 Java 编译、测试和 JaCoCo，前端 npm scripts 负责 ESLint/Prettier/Node 测试。GitHub 分支保护通过仓库设置启用，并由 API 命令验证，不把远端权限伪装成代码配置。

**Tech Stack:** Java 21、Maven Wrapper、Spring Boot、JUnit 5、JaCoCo、Node 24、npm、ESLint、Prettier、GitHub Actions、GitHub CLI。

---

### Task 1: 建立工具链和仓库治理基线

**Files:**
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Modify: `.gitignore`
- Modify: `README.md`
- Modify: `bin/verify-repository.ps1`
- Modify: `bin/verify-foundation.ps1`
- Test: `test/foundation-contract.test.mjs`

- [ ] **Step 1: 写工具链契约测试**

在 `test/foundation-contract.test.mjs` 增加检查：`mvnw`、`mvnw.cmd` 和 wrapper properties 必须被 Git 跟踪，properties 必须固定 Maven 3.9.x；README 必须使用 `./mvnw` 或 `mvnw.cmd`。

- [ ] **Step 2: 运行契约测试确认 RED**

Run: `node --test test/foundation-contract.test.mjs`

Expected: 因 wrapper 文件不存在而失败。

- [ ] **Step 3: 添加 Maven Wrapper 和版本说明**

使用 Maven Wrapper 官方脚本生成器生成 wrapper，固定 Maven 3.9.16；README 的后端构建和统一验证命令改为 wrapper 命令。仓库校验脚本必须把 wrapper 文件列为必需纳管文件。

- [ ] **Step 4: 更新统一验证入口**

将 `bin/verify-foundation.ps1` 的 Maven 阶段改为调用 `mvnw.cmd`，保留对 Node/npm 的显式检查。

- [ ] **Step 5: 运行 GREEN 验证**

Run: `node --test test/foundation-contract.test.mjs`

Expected: 所有 foundation contract tests 通过。

- [ ] **Step 6: 提交**

```powershell
git add .mvn mvnw mvnw.cmd .gitignore README.md bin test/foundation-contract.test.mjs
git commit -m "build: pin reproducible Maven toolchain"
```

### Task 2: 接入 Java 和前端质量门禁

**Files:**
- Modify: `pom.xml`
- Modify: `frontend/package.json`
- Create: `frontend/eslint.config.js`
- Create: `frontend/.prettierrc.json`
- Create: `frontend/.prettierignore`
- Modify: `bin/verify-foundation.ps1`
- Modify: `.github/workflows/ci.yml`
- Test: `test/foundation-contract.test.mjs`

- [ ] **Step 1: 写质量命令契约测试**

断言根 POM 包含 `maven-checkstyle-plugin`，前端 package scripts 包含 `lint` 和 `format:check`，CI 在 Maven test 前调用质量阶段。

- [ ] **Step 2: 运行 RED**

Run: `node --test test/foundation-contract.test.mjs`

Expected: 因质量插件和 npm scripts 不存在而失败。

- [ ] **Step 3: 添加可迁移的基线规则**

Java 使用 `maven-checkstyle-plugin` 3.6.0 和仓库内的 `config/checkstyle/checkstyle.xml`，先启用不改变业务代码的基础检查；前端使用 ESLint flat config 和 Prettier，明确忽略 `dist`、`node_modules` 和生成产物，必要的生成器长行通过局部配置处理。

- [ ] **Step 4: 接入统一验证和 CI**

统一入口按顺序执行 Java 质量检查、前端 `npm run lint`、前端 `npm run format:check`，质量阶段失败时停止后续测试和构建。

- [ ] **Step 5: 运行 GREEN 验证**

Run: `./mvnw.cmd verify -DskipTests`；`Set-Location frontend; npm ci; npm run lint; npm run format:check`

Expected: 所有质量检查通过。

- [ ] **Step 6: 提交**

```powershell
git add pom.xml frontend bin .github test/foundation-contract.test.mjs
git commit -m "build: enforce Java and frontend quality gates"
```

### Task 3: 增加 JaCoCo 覆盖率基线

**Files:**
- Modify: `pom.xml`
- Modify: `bin/verify-foundation.ps1`
- Modify: `.github/workflows/ci.yml`
- Test: `test/foundation-contract.test.mjs`

- [ ] **Step 1: 写覆盖率契约测试**

断言根 POM 配置 JaCoCo、生成 XML/HTML 报告并设置明确但可落地的整体行覆盖率阈值；CI 上传 `target/site/jacoco` artifact。

- [ ] **Step 2: 运行 RED**

Run: `node --test test/foundation-contract.test.mjs`

Expected: 覆盖率配置和 artifact 路径断言失败。

- [ ] **Step 3: 接入 JaCoCo**

通过 Maven plugin 在测试阶段准备 agent，在 verify 阶段生成报告并执行当前基线阈值；多模块报告使用聚合执行，阈值不得凭空设置，先以当前测量值为依据。

- [ ] **Step 4: 接入统一验证和 CI artifact**

测试失败或覆盖率低于阈值时返回非零；CI 无论测试结果如何都上传覆盖率报告。

- [ ] **Step 5: 运行 GREEN 验证**

Run: `./mvnw.cmd verify`

Expected: 测试和 JaCoCo verify 通过，`target/site/jacoco` 存在报告。

- [ ] **Step 6: 提交**

```powershell
git add pom.xml bin .github test/foundation-contract.test.mjs
git commit -m "test: enforce backend coverage baseline"
```

### Task 4: 固化贡献、发布和回滚规范

**Files:**
- Create: `CONTRIBUTING.md`
- Create: `docs/release-and-rollback.md`
- Modify: `README.md`
- Modify: `SECURITY.md`
- Modify: `task_plan.md`
- Modify: `progress.md`
- Modify: `findings.md`

- [ ] **Step 1: 编写贡献规范**

明确 `main` 只能通过 PR 合并、提交信息格式、必须运行的本地命令、测试失败处理和敏感信息规则。

- [ ] **Step 2: 编写发布与回滚规范**

明确版本号、构建命令、配置检查、数据库备份、健康检查、回滚顺序和失败证据。

- [ ] **Step 3: 同步 README/Security 和工作记录**

删除旧的 Java 17/Maven PATH 说明，补充 wrapper 命令；将工作记录标记为当前治理阶段进行中，避免显示已完成或过时状态。

- [ ] **Step 4: 文档验证**

Run: `git diff --check`; `powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-foundation.ps1`

Expected: 文档无空白错误，统一验证通过。

- [ ] **Step 5: 提交**

```powershell
git add CONTRIBUTING.md docs/release-and-rollback.md README.md SECURITY.md task_plan.md progress.md findings.md
git commit -m "docs: define contribution release and rollback rules"
```

### Task 5: 配置 GitHub main 分支保护并完成验收

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Test: remote GitHub branch protection API

- [ ] **Step 1: 确认远端状态**

Run: `gh repo view tt88737/OPC --json defaultBranchRef`; `gh api repos/tt88737/OPC/branches/main/protection`

Expected: 确认 `main` 是默认分支；若未保护，API 返回未保护状态并记录在 progress。

- [ ] **Step 2: 配置 main 保护**

使用 GitHub CLI/API 配置 PR 必须通过、CI 必须成功、至少一名审核者、禁止强推和删除分支；不得改变默认分支。

- [ ] **Step 3: 验证保护策略**

Run: `gh api repos/tt88737/OPC/branches/main/protection`

Expected: 返回 required status checks、required pull request reviews 和禁止强制推送/删除的配置。

- [ ] **Step 4: 更新文档和验收**

README/CONTRIBUTING 明确说明 `main` 是唯一底座分支；运行完整测试、前端构建和统一验证。

- [ ] **Step 5: 提交**

```powershell
git add README.md CONTRIBUTING.md .github/workflows/ci.yml progress.md
git commit -m "chore: protect main foundation branch"
git push origin main
```
