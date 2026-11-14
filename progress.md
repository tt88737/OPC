# 工程治理进度

截至 2026-11-14，Task 1 至 Task 5 已全部完成：仓库具备统一验证入口、Java/前端质量门禁、聚合覆盖率基线、贡献和发布回滚规范，以及 GitHub `main` 分支保护。关键仓库要求由 `test/foundation-contract.test.mjs` 约束，并通过 `bin/verify-foundation.ps1` 纳入本地与 CI 的统一流程。

远端 API 回读确认 `main` 仍为默认分支，保护策略严格要求状态检查 `verify`，需要至少 1 名审查者批准，新的提交会撤销过期批准，且合并前所有讨论必须解决。管理员不可绕过保护；强制推送和分支删除均已禁用。未启用 last-push approval、线性历史或分支锁定。
