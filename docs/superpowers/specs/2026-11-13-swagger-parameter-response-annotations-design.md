# Swagger 参数与返回数据注解设计

## 目标

在现有 24 个 Web Controller 已具备中文分组和接口摘要的基础上，补齐 Swagger 中缺失的请求参数、请求体字段、公共返回字段和特殊媒体类型说明，使接口文档可以直接用于联调。

本次仍只调整 OpenAPI 元数据，不改变接口路径、HTTP 方法、权限、校验规则、序列化结果、数据库操作或业务逻辑。

## 范围

覆盖 Springdoc 实际扫描到的全部 24 个 Web Controller：`opc-admin` 下的系统、认证、通用、监控接口，以及 `opc-generator` 和 `opc-quartz` 下的代码生成、定时任务、调度日志接口。

### 请求参数

- 为 `@PathVariable`、`@RequestParam`、查询对象中的公开字段补充中文 `@Parameter` 或字段级 `@Schema`。
- 为 `@RequestBody`、`MultipartFile`、文件列表和导入参数补充请求说明；明确必填性、用途和常见取值。
- 不在同一字段同时重复添加冲突的参数注解；Spring 自动推断的类型、默认值和校验约束继续作为事实来源。

### 请求模型字段

为 Controller 直接使用的请求/查询模型补充字段级 `@Schema`，包括系统用户、角色、部门、菜单、岗位、字典、参数、公告、认证、代码生成表/字段、定时任务和调度日志等模型。继承的公共审计字段只在公共基类说明一次，避免文档重复。

### 返回数据

为公共返回类型补充 `@Schema`：

- `AjaxResult`：说明 `code`、`msg`、`data` 三个公共字段；保留其 Map 序列化行为。
- `TableDataInfo`：说明 `code`、`msg`、`total`、`rows` 分页字段；保留列表响应结构。
- `R<T>`：说明示例接口的状态码、消息和泛型数据。

Controller 方法使用 `@ApiResponse` / `@Content` 时，只补充描述和可安全推断的公共响应模型，不伪造 AjaxResult 动态 `data` 的具体业务类型。业务返回实体本身通过 `@Schema` 生成字段列表。

### 特殊接口

- 文件下载和批量代码下载标记二进制响应媒体类型，不声明为 JSON。
- 文件上传、头像上传、验证码接口说明 multipart 或图片响应类型。
- 导出接口说明下载文件行为，但不改变现有响应头和文件格式。

## 方案与兼容性

采用 `io.swagger.v3.oas.annotations` 当前项目已使用的 OpenAPI 3 注解，不新增完整 Springdoc UI 依赖。注解放在 Controller 参数、DTO 字段和公共响应模型上，运行时仅由 Springdoc 读取。

不创建新的运行时响应 DTO，不把 `AjaxResult` 改造成泛型类型，不修改 getter/setter 或 JSON 注解。这样可确保前端现有响应解析和后端调用方完全兼容。

## 质量规则

- 所有新增 `description`、`title`、`example` 使用准确中文；密码、Token、密钥等敏感字段不得填写真实示例值。
- `required` 只根据现有 Spring 校验注解、路径参数规则和代码行为确定，不凭猜测扩大必填范围。
- 日期、枚举、布尔值、数组和文件字段注明格式或取值含义；不改变 Java 类型。
- 公共返回字段说明与实际 JSON 键名保持一致：`code`、`msg`、`data`、`total`、`rows`。

## 验证

1. 静态检查所有 24 个 Controller 的参数和请求模型均有中文说明；公共返回模型字段均有 `@Schema`。
2. Maven 全模块编译和测试通过。
3. 启动后读取 `/v3/api-docs`，检查每个接口的 `parameters`、`requestBody`、`responses` 均能生成有效 OpenAPI 结构。
4. 检查关键接口的参数名称、类型、必填标志与源码一致，响应公共字段完整。
5. 检查文件上传、下载、验证码和代码生成下载接口的媒体类型正确。
6. 打开后端和前端代理 Swagger UI，确认参数模型和返回模型可展开且无英文自动分组或空说明。

## 非目标

- 不改变任何接口行为、权限、路径、数据库和前端代码。
- 不承诺为动态 `AjaxResult.data` 在静态文档中猜测所有运行时分支类型。
- 不把内部服务、Mapper、工具类字段暴露为接口模型。
