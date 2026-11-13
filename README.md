# OPC

OPC 是一个基于 Java 与 Vue 的前后端分离管理平台，适合作为后台管理系统、SaaS 和业务平台的基础项目。

## 技术栈

- 后端：Java 17、Spring Boot、Spring Security、MyBatis、Redis、JWT
- 前端：Vue 3、Vite、Element Plus、Pinia
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
4. 使用 Java 17 和 Maven 构建：

```bash
mvn clean package
```

其中 `clean` 会先删除上次构建输出，避免已删除或已改名的配置资源残留在 `target` 和最终 JAR 中。

5. 启动 `opc-admin/target/opc-admin.jar`，默认端口为 `8080`。

### 生产部署

生产环境必须设置 `OPC_PROFILE=prod`，并显式提供 `OPC_DB_URL`、`OPC_DB_USERNAME`、`OPC_DB_PASSWORD`、`OPC_REDIS_PASSWORD`、`OPC_TOKEN_SECRET` 和 `OPC_CORS_ALLOWED_ORIGINS`。`prod` profile 固定关闭 Swagger/OpenAPI、Druid Web 控制台和 DevTools，不能通过 `OPC_SPRINGDOC_ENABLED`、`OPC_DRUID_ENABLED` 或 `OPC_DEVTOOLS_ENABLED` 重新开启。

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认地址为 `http://127.0.0.1:5173`。

## 许可证

本项目遵循 MIT License。上游项目的版权与许可证声明见 [LICENSE](LICENSE)。
