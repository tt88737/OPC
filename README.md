# OPC

OPC 是一个基于 Java 与 Vue 的前后端分离管理平台，适合作为后台管理系统、SaaS 和业务平台的基础项目。

## 技术栈

- 后端：Java 21、Spring Boot、Spring Security、MyBatis、Redis、JWT
- 前端：Vue 3、Vite、Element Plus、Pinia（构建与验证使用 Node.js 24.15.0 或更高的 Node 24 版本）
- 数据库：MySQL

## 项目结构

```text
OPC/
├── opc-admin/       # 后端启动模块
├── opc-common/      # 通用工具与公共组件
├── opc-framework/   # 核心框架能力
├── opc-system/      # 系统管理业务
├── opc-generator/   # 代码生成
├── opc-quartz/      # 定时任务
├── frontend/          # Vue 前端
└── sql/               # 数据库初始化脚本
```

## 快速开始

### 后端

1. 创建 MySQL 数据库并执行 `sql/opc_20260417.sql`。
2. 将 `opc-local.env.example.ps1` 复制为被 Git 忽略的 `opc-local.env.ps1`，替换其中的占位值后加载环境变量。首次启动必须设置一次性 `OPC_BOOTSTRAP_ADMIN_PASSWORD`；引导成功后立即删除该变量并修改管理员密码。
3. 默认使用 `dev` profile。开发环境可通过 `OPC_SPRINGDOC_ENABLED` 控制接口文档；Druid 默认关闭，仅在同时配置非空 `OPC_DRUID_USERNAME`、`OPC_DRUID_PASSWORD` 后才可设置 `OPC_DRUID_ENABLED=true`。
4. 使用 Java 21 和仓库固定的 Maven Wrapper 构建：

```bash
./mvnw clean package
```

其中 `clean` 会先删除上次构建输出，避免已删除或已改名的配置资源残留在 `target` 和最终 JAR 中。

5. 启动 `opc-admin/target/opc-admin.jar`，默认端口为 `8080`。

Windows 本地开发建议使用标准启动脚本。它会加载被 Git 忽略的 `opc-local.env.ps1`，并统一设置 UTF-8 控制台编码，避免中文日志乱码：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\start-backend-local.ps1
```

### 生产部署

生产环境必须设置 `OPC_PROFILE=prod`，并显式提供 `OPC_DB_URL`、`OPC_DB_USERNAME`、`OPC_DB_PASSWORD`、`OPC_REDIS_PASSWORD`、`OPC_TOKEN_SECRET` 和 `OPC_CORS_ALLOWED_ORIGINS`。`prod` profile 固定关闭 Swagger/OpenAPI、Druid Web 控制台和 DevTools，不能通过 `OPC_SPRINGDOC_ENABLED`、`OPC_DRUID_ENABLED` 或 `OPC_DEVTOOLS_ENABLED` 重新开启。

### 前端

```bash
cd frontend
npm ci
npm run dev
```

前端开发服务器默认地址为 `http://127.0.0.1:5173`。

## 构建验证与持续集成

本地需要 Java 21、Node.js 24.15.0 或更高的 Node 24 版本，以及 `git`、`rg`。Maven 由仓库中的 Wrapper 固定。在仓库根目录执行统一验证入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-foundation.ps1
```

脚本依次检查仓库纳管边界、基础清理、安全 profile、Swagger 注解、Maven 测试、前端依赖锁定安装、前端测试与生产构建，并执行高危级别的 npm 依赖审计；任一阶段失败都会立即返回对应的非零退出码。GitHub Actions 的 `CI` workflow 会在每次 push 和 pull request 上使用相同入口。

后端覆盖率由 JaCoCo 聚合报告统一度量，HTML 报告位于 `opc-coverage/target/site/jacoco-aggregate/`。当前聚合行覆盖率门槛为 5%，这是基于现状设置的最低基线，不是质量目标；新增和修改代码应补充测试并逐步提高门槛。

## 工程协作

- 所有研发、架构、数据、部署和运行规则统一从 [工程契约索引](docs/engineering/README.md) 进入。
- 贡献流程、分支、提交和 PR 要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 后端、前端、测试与完成定义见 [代码规范契约](docs/engineering/code-contract.md)。
- 团队分工、风险分级和审查要求见 [团队协作契约](docs/engineering/collaboration-contract.md)。
- 使用 Codex 进行需求、实施、验证和交付见 [Codex 协助开发工作流](docs/engineering/codex-workflow.md)。
- 一人公司角色、决策权、资源优先级和风险授权见 [一人公司运营与工程契约](docs/engineering/solo-company-operating-contract.md)。
- 从需求澄清到发布观察的状态和证据要求见 [从需求到落地工作流](docs/engineering/requirements-to-delivery-workflow.md)。
- 新功能开发前后的 API、权限、审计、数据和验证检查见 [新功能契约清单](docs/engineering/feature-contract-checklist.md)。
- C/B 端、服务端和模块拆分边界见 [平台架构契约](docs/engineering/platform-architecture-contract.md)。
- 接口、数据所有权和数据库演进见 [API 与数据契约](docs/engineering/api-data-contract.md)。
- Docker、环境晋级和发布要求见 [部署与容器契约](docs/engineering/deployment-container-contract.md)。
- SLO、监控、容量和灾备要求见 [运行可靠性契约](docs/engineering/operations-reliability-contract.md)。
- 契约自动化与平台能力的建设顺序见 [平台治理路线图](docs/engineering/governance-roadmap.md)。
- 版本、上线检查、发布证据和回滚步骤见 [发布与回滚手册](docs/release-and-rollback.md)。

## 依赖安全扫描

`Dependency Check` workflow 每周运行，也可在 GitHub Actions 中手动触发。它使用 OWASP Dependency-Check 对 Maven 聚合依赖执行 CVSS 7 阈值扫描，并执行 `npm ci` 和 `npm audit --audit-level=high`；HTML、JSON 报告会作为 workflow artifact 上传。扫描或数据源更新失败会使任务失败，报告仅代表扫描时可用的数据和规则，不应视为绝对无漏洞的保证。

建议在仓库的 Actions secrets 中配置 `NVD_API_KEY`，以降低 NVD 数据更新的限流风险。workflow 只通过进程环境向 Dependency-Check 传入该值，不会主动输出密钥；未配置时仍会尝试使用公开数据源运行，数据源错误不会被忽略。

## 许可证

OPC 自身代码遵循 [MIT License](LICENSE)。
