# 工程治理约束与发现

- 标准工具链为 Java 21、Node.js 24.15.0（或后续兼容的 Node 24 版本）、仓库 Maven Wrapper 和 `npm ci`。本次统一验证实际观测到本机 Node.js v22.21.0，低于标准 24.15.0，并产生 `EBADENGINE` 警告；本地命令虽成功，但不能替代标准工具链下的统一入口和 CI 结果。
- 当前 JaCoCo 聚合行覆盖率门槛为 5%。这是防止回退的低基线，不代表充分覆盖；后续变更应增加测试并以实测数据逐步提高门槛。
- 生产环境必须使用 `prod` profile、最小权限账户和受控秘密注入，并保持 Swagger/OpenAPI、Druid Web 控制台和 DevTools 关闭。治理文档不包含任何真实秘密。
- 数据库变更目前不能假定由 Flyway 或 Liquibase 管理。每次生产变更仍需显式审查、可恢复备份、健康检查和经过批准的回滚或前向修复方案。
- `main` 的 GitHub 保护已于 2026-11-14 配置并通过 REST API 核验。经典分支保护负责不可绕过的 PR、`verify`、管理员强制执行以及强制推送和删除禁令；`main-review-approval` ruleset 负责日常 1 人审批，并只允许 RepositoryRole Admin 以 `pull_request` 模式应急绕过审批。该组合是远端状态，不由 Git 提交本身保证；管理员应定期通过 API 复核 classic protection 与 ruleset，避免两者职责漂移。
