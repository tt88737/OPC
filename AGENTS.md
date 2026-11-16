# OPC 仓库协作指令

本文件是本仓库内人工开发者与 Codex 的共同执行入口。详细规则见 `docs/engineering/`。

## 开工前

1. 阅读本文件、`README.md`、`CONTRIBUTING.md`，以及与任务相关的 `docs/engineering/` 文档。
2. 执行 `git status --short`，识别并保留已有修改；不得覆盖、回退或顺手格式化无关文件。
3. 先定位现有实现、测试和配置，再提出或实施变更；优先沿用现有模块边界和工具链。
4. 涉及需求歧义、数据库破坏性操作、生产配置、安全边界或外部系统写入时，先取得明确授权。

## 实施约束

- 运行时基线：Java 21、Node.js 24、仓库 Maven Wrapper、`npm ci`。
- 后端依赖方向遵循 `opc-admin -> opc-framework/opc-system -> opc-common`；公共能力只有在两个以上模块确有复用时才下沉。
- 前端 API、状态、页面和通用组件职责分离；不得在页面中复制已有请求或权限逻辑。
- 密码、令牌、私钥、真实连接串不得进入源码、日志、测试快照或文档。
- API、配置项、数据库结构和权限语义发生变化时，必须同步文档、兼容性说明和测试。
- 修复缺陷时应先增加能够复现问题的测试；无法自动化时必须记录可重复的人工验证步骤。
- 不得为了通过门禁而删除测试、降低检查级别、扩大忽略列表或吞掉异常。

## 验证与交付

- 小范围变更先运行最接近改动的检查；交付前运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\bin\verify-foundation.ps1
```

- 若完整门禁因环境或基础设施无法执行，必须明确列出已运行项目、未运行项目、原始失败原因和残余风险，不得声称“全部通过”。
- 完成说明至少包含：行为变化、关键文件、验证结果、配置/数据库影响和仍存在的风险。
- 未经用户明确要求，不创建提交、不推送、不创建或合并 PR、不修改远端仓库设置。

## 规则索引

- [工程契约总索引](docs/engineering/README.md)
- [平台架构契约](docs/engineering/platform-architecture-contract.md)
- [API 与数据契约](docs/engineering/api-data-contract.md)
- [部署与容器契约](docs/engineering/deployment-container-contract.md)
- [运行可靠性契约](docs/engineering/operations-reliability-contract.md)
- [平台治理路线图](docs/engineering/governance-roadmap.md)

