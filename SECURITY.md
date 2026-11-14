# 安全配置

数据库、Redis、JWT 和 Druid 管理凭据不应写入源码。启动后端前请通过环境变量注入：

```text
OPC_DB_URL=jdbc:mysql://127.0.0.1:3306/opc?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
OPC_DB_USERNAME=opc
OPC_DB_PASSWORD=请替换为强密码
OPC_REDIS_PASSWORD=请替换为强密码
OPC_TOKEN_SECRET=至少64位随机字符串
OPC_BOOTSTRAP_ADMIN_PASSWORD=仅首次启动使用的一次性强密码
```

开发环境默认使用 `dev` profile，并默认关闭 Druid。仅在确有本地排障需要时设置
`OPC_DRUID_ENABLED=true`，且必须同时提供非空、无默认值的 `OPC_DRUID_USERNAME` 和
`OPC_DRUID_PASSWORD`；不要复用数据库或管理员凭据。

生产环境必须设置 `OPC_PROFILE=prod`、非空 `OPC_REDIS_PASSWORD`，并将
`OPC_CORS_ALLOWED_ORIGINS` 限定为实际 HTTPS 前端域名。生产环境禁止启用 Swagger UI、
OpenAPI API docs、Druid Web 控制台和 DevTools，相关开关在 `prod` profile 中固定为关闭。

首次部署通过 `OPC_BOOTSTRAP_ADMIN_PASSWORD` 引导管理员密码。引导成功后立即从环境中删除该变量，
使用新密码登录并再次轮换密码；后续启动不得继续保留引导密码。数据库账号必须为非 root 最小权限账号。

## 漏洞报告

请通过仓库托管平台的私密安全报告渠道联系维护者，不要在公开 Issue、讨论、PR、日志或截图中披露可利用细节。报告应包含受影响版本、影响范围、最小复现步骤和建议缓解方式，但不得附带真实生产凭据或个人敏感数据。维护者确认接收后将协调分级、修复、验证和披露时间；修复发布前请保持报告私密。

## 密钥泄露响应

发现令牌、密码、私钥或其他密钥泄露时，应先吊销或轮换受影响凭据，再限制暴露面并审查访问日志；仅从 Git 历史删除内容不能使凭据恢复安全。随后应定位传播范围、更新使用该密钥的服务、验证旧值已失效，并保留不含秘密值的事件时间线和处置证据。不要在 Issue 或提交信息中重复泄露值。

## 最小权限与生产边界

应用、CI、数据库和运维身份必须使用完成任务所需的最小权限，生产身份不得与开发或测试环境共享。生产秘密只能由受控秘密管理系统或进程环境注入，不得写入源码、镜像、构建产物、前端资源或日志。

生产环境必须保持 `prod` profile 的安全边界：Swagger/OpenAPI、Druid Web 控制台和 DevTools 不得启用，CORS 仅允许明确的 HTTPS 来源，数据库不得使用 root 账户，对外网络访问和管理入口应按需限制。发布与回滚过程中不得降低这些边界来绕过故障。
