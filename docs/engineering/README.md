# OPC 工程契约索引

本目录定义 OPC 从需求、编码到生产运行的完整约束。规则优先级为：自动化门禁与安全边界 > 本目录契约 > 任务局部约定。局部约定不得降低上级规则。

| 契约 | 解决的问题 |
| --- | --- |
| [代码规范契约](code-contract.md) | Java、Vue、测试、日志和安全编码标准 |
| [团队协作契约](collaboration-contract.md) | 分支、风险、评审、交接与例外 |
| [Codex 协助开发工作流](codex-workflow.md) | AI 参与需求、实施、验证与交付的边界 |
| [平台架构契约](platform-architecture-contract.md) | C 端、B 端、服务端、模块和依赖边界 |
| [API 与数据契约](api-data-contract.md) | 接口生命周期、数据所有权、迁移与隐私 |
| [部署与容器契约](deployment-container-contract.md) | 构建产物、Docker、配置、发布和回滚 |
| [运行可靠性契约](operations-reliability-contract.md) | SLO、监控、日志、告警、容量和灾备 |
| [平台治理路线图](governance-roadmap.md) | 把契约转化为分阶段、可验证的建设任务 |

## 当前底座状态

- 已有：单体模块化 Java 服务、一个 Vue B 端管理前端、MySQL、Redis、统一基础验证、发布回滚和安全说明。
- 尚未形成：独立 C 端、Docker/Compose/Kubernetes 交付物、数据库迁移框架、端到端部署流水线、正式 SLO 与告警配置。
- 规则：尚未形成的能力是建设目标，不得在文档中描述为已实现；每次补齐能力时同步自动化门禁、运维手册和本表。

