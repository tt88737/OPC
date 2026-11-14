# 贡献指南

## 开发环境

提交变更前请使用以下仓库工具链：

- Java 21；
- Node.js 24.15.0（或后续兼容的 Node 24 版本）；
- 仓库内置的 Maven Wrapper（Windows 使用 `mvnw.cmd`，其他平台使用 `./mvnw`）；
- 前端依赖必须通过 `npm ci` 按锁文件安装。

密码、令牌、私钥、连接串中的凭据或其他敏感信息不得提交到仓库。只可提交带占位值的示例配置，真实值应由本地环境或受控的秘密管理服务注入。

## 分支与提交

`main` 只能通过 Pull Request（PR）合并，禁止直接推送。GitHub 经典分支保护严格要求 `verify` 状态检查成功，并禁止强制推送和删除分支；独立的 `main-review-approval` ruleset 规定日常合并至少需要 1 名审查者批准。建议采用用途明确的分支命名，例如 `feat/short-topic`、`fix/short-topic`、`docs/short-topic` 或 `chore/short-topic`。

提交信息应遵循 Conventional Commits，例如 `feat: add account lockout`、`fix: reject invalid token`、`docs: clarify deployment checks`。若工具或历史原因无法严格遵循，也必须使用简短、祈使语气且能清楚说明变更目的的格式。

## Pull Request

每个 PR 应保持最小变更范围，只解决一个清晰问题；无关重构、格式化或依赖升级应拆分。PR 描述至少说明目的、风险、验证结果以及配置或数据库影响。

合并前必须由至少一名未参与主要实现的维护者完成审查，所有讨论和阻塞意见必须解决；新的提交会撤销过期批准。涉及认证、授权、秘密处理、生产配置或数据库变更时，应邀请相应领域维护者审查。

管理员仅在事故处置、安全修复或无可用独立审查者时，才能在 PR 中应急绕过审批；管理员绕过审批时 `verify` 仍须通过，且禁止直接推送、禁止强制推送和禁止删除分支。应急 PR 必须记录绕过理由、风险和验证结果，事后补审并复盘。

## 必须验证

在仓库根目录运行唯一的本地统一验证命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-foundation.ps1
```

该统一验证入口当前仅支持 Windows PowerShell。非 Windows 开发者必须在 Windows VM 或 Windows CI runner 中执行完整门禁；`./mvnw`、单项前端命令或其他局部检查不得替代完整门禁，也不得据此声称统一验证已经通过。

命令失败时不得绕过、忽略或以手工挑选的子命令结果替代。应修复失败，重新运行完整命令，并在 PR 中记录结果；确因基础设施故障无法验证时，不得合并，需保留错误输出并交由维护者处理。
