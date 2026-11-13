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
2. 按实际环境修改 `opc-admin/src/main/resources/application-*.yml`。
3. 使用 Java 17 和 Maven 构建：

```bash
mvn -DskipTests package
```

4. 启动 `opc-admin/target/opc-admin.jar`，默认端口为 `8080`。

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认地址为 `http://127.0.0.1:5173`。

## 许可证

本项目遵循 MIT License。上游项目的版权与许可证声明见 [LICENSE](LICENSE)。
