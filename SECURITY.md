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
