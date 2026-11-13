# 安全配置

数据库、Redis、JWT 和 Druid 管理凭据不应写入源码。启动后端前请通过环境变量注入：

```text
OPC_DB_URL=jdbc:mysql://127.0.0.1:3306/opc?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
OPC_DB_USERNAME=opc
OPC_DB_PASSWORD=请替换为强密码
OPC_REDIS_PASSWORD=请替换为强密码
OPC_TOKEN_SECRET=至少64位随机字符串
```

生产环境还应设置 `OPC_CORS_ALLOWED_ORIGINS` 为实际前端域名，并保持
`OPC_DRUID_ENABLED=false`、`OPC_DEVTOOLS_ENABLED=false`。首次部署必须替换初始化管理员密码，
并在数据库层使用非 root 账号和最小权限。
