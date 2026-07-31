# MeterSphere Agent MCP 测试用例与缺陷管理扩展方案

> 日期：2026-07-31
> 适用范围：MeterSphere `agent-integration`、功能用例管理、缺陷管理
> 目标版本：建议按 P0/P1/P2 分期交付

## 1. 背景

MeterSphere 已提供基于个人 Agent Token 的 Streamable HTTP MCP 服务，支持功能用例检索、执行结果提交、用例创建、缺陷检索、缺陷创建和更新等基础能力。

现阶段仍存在以下业务闭环缺口：

- Agent 上传的执行附件只关联到 Agent 执行日志，无法在用例详情附件区域展示。
- MCP 不支持向用例详情、用例评论、缺陷详情和缺陷评论上传附件。
- MCP 不支持更新、删除、恢复、移动、复制已有功能用例。
- MCP 不支持用例评论管理。
- 缺陷管理缺少删除、恢复、评论、附件、关联用例和解除关联等工具。
- 现有写操作 Scope 粒度较粗，不适合直接扩展删除、附件等高风险能力。
- Agent 直接调用业务 Service 时，Controller 上的日志、通知和资源归属注解不会自动执行。

本方案在不绕过用户 RBAC、项目白名单和既有业务规则的前提下，补齐测试用例与缺陷管理的 MCP 能力。

## 2. 设计目标

### 2.1 功能目标

1. 支持将 PNG、JPG、日志、压缩包等附件关联到用例详情和缺陷详情，并在现有前端直接显示。
2. 支持已有用例的更新、软删除、恢复、移动、复制和批量编辑。
3. 支持用例评论和评论附件。
4. 支持缺陷删除、恢复、评论、详情附件以及用例关联管理。
5. 支持查询模板、自定义字段、附件、评论、历史记录和回收站数据。
6. 保持项目内部 ID、界面项目编号和精确项目名称三种项目定位方式。

### 2.2 非功能目标

- 不允许 Agent Token 绕过用户 RBAC。
- 不允许通过资源 ID 越权访问 Token 白名单外项目。
- 所有写操作具备审计、幂等和乐观锁能力。
- 删除操作默认进入回收站，不默认开放永久删除。
- 复用现有业务 Service、附件表、对象存储目录和前端展示能力。
- 兼容当前已发布的 MCP 工具和 Token Scope。

## 3. 当前能力与差距

### 3.1 当前测试用例工具

- `metersphere.functional.search`
- `metersphere.functional.get`
- `metersphere.functional.modules`
- `metersphere.functional.submit`
- `metersphere.functional.module.create`
- `metersphere.functional.case.create`
- `metersphere.functional.case.batch_create`

当前缺少：

- 已有用例更新
- 用例删除和恢复
- 批量更新、移动、复制
- 评论查询和维护
- 用例详情附件查询、上传和删除
- 模板、自定义字段、历史记录查询

### 3.2 当前缺陷工具

- `metersphere.bug.search`
- `metersphere.bug.get`
- `metersphere.bug.create`
- `metersphere.bug.update`

当前缺少：

- 删除和恢复
- 评论管理
- 缺陷详情附件
- 关联用例查询、关联和解除关联
- 批量更新
- 模板和历史记录查询

### 3.3 当前附件问题

现有 Agent 附件流程如下：

```text
/api/agent/v1/functional/attachment/upload
  → agent_exec_attachment
  → metersphere.functional.submit
  → Agent 执行日志
```

该流程适用于“执行证据”，但不属于用例详情附件。前端普通用例详情读取的是 `functional_case_attachment` 及文件关联数据，因此 Agent 执行日志附件不会自动显示在用例详情。

普通前端接口 `/api/attachment/upload/file` 依赖网页登录会话。Agent Token 调用该接口会返回 401，不应通过放开普通接口鉴权解决。

## 4. 总体架构

建议采用“通用临时附件上传 + MCP 业务工具关联”的两阶段模式。

```text
AI Client
  │
  ├─ 1. 上传二进制文件
  │    POST /api/agent/v1/attachment/upload
  │
  └─ 2. MCP tools/call
       ├─ functional.attachment.attach
       ├─ functional.comment.create
       ├─ bug.attachment.attach
       └─ bug.comment.create
              │
              ▼
       Agent 领域 Facade
              │
       Scope/RBAC/白名单/归属校验
              │
              ▼
       MeterSphere 现有业务 Service
              │
       正式附件表 + 对象存储 + 操作日志
              │
              ▼
       现有前端直接展示
```

不建议将文件 Base64 直接放入 `tools/call`。Base64 会增加约三分之一体积，并显著占用模型上下文和网关请求体。

## 5. 通用附件能力设计

### 5.1 临时附件上传

新增接口：

```http
POST /api/agent/v1/attachment/upload
Authorization: Bearer <Agent Token>
X-MS-PROJECT: <projectId>
Content-Type: multipart/form-data
```

请求参数：

| 参数 | 必填 | 说明 |
|---|---:|---|
| `file` | 是 | 二进制文件 |
| `purpose` | 是 | `CASE_DETAIL`、`CASE_COMMENT`、`BUG_DETAIL`、`BUG_COMMENT`、`EXECUTION` |
| `stepNum` | 否 | 执行步骤编号，仅执行附件使用 |

响应：

```json
{
  "attachmentId": "1580259119787974657",
  "fileId": "1580259119787974656",
  "fileName": "evidence.png",
  "contentType": "image/png",
  "size": 128330,
  "purpose": "CASE_DETAIL",
  "expiresAt": 1785489999000
}
```

临时附件规则：

- 默认 24 小时失效。
- 单文件最大 5 MB，保持现有限制。
- 单次业务操作最多关联 10 个附件。
- 校验扩展名、Content-Type 和真实文件头。
- HTML、SVG 等主动内容默认不允许直接预览。
- 上传后未关联的文件由定时任务清理。
- `purpose` 必须与最终关联工具一致。

### 5.2 用例详情附件关联

新增工具：

```text
metersphere.functional.attachment.attach
```

请求：

```json
{
  "projectId": "100006",
  "caseId": "1565254823337852928",
  "attachmentIds": ["1580259119787974657"],
  "requestId": "client-request-id"
}
```

服务层调用：

```java
functionalCaseAttachmentService.uploadMinioFile(
    caseId,
    resolvedProjectId,
    fileIds,
    userId,
    CaseFileSourceType.CASE_DETAIL.toString()
);
```

关联完成后，附件进入用例详情使用的正式附件表和对象存储目录，现有前端无需修改即可显示。

### 5.3 缺陷详情附件关联

新增工具：

```text
metersphere.bug.attachment.attach
```

服务层复用 `BugAttachmentService.uploadFile` 或抽取其内部领域方法，确保写入缺陷详情正式附件记录。

### 5.4 附件删除

新增：

- `metersphere.functional.attachment.delete`
- `metersphere.bug.attachment.delete`

删除参数必须同时包含：

```json
{
  "projectId": "100006",
  "resourceId": "resource-id",
  "attachmentId": "attachment-id",
  "confirm": true,
  "requestId": "client-request-id"
}
```

必须校验附件确实属于目标资源，禁止只凭附件 ID 删除。

## 6. 测试用例 MCP 扩展

### 6.1 查询工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.functional.template.get` | `FUNCTIONAL_READ` | 获取默认模板和字段 |
| `metersphere.functional.history.list` | `FUNCTIONAL_READ` | 查询用例变更历史 |
| `metersphere.functional.comments.list` | `FUNCTIONAL_READ` | 查询用例评论 |
| `metersphere.functional.attachments.list` | `FUNCTIONAL_READ` | 查询用例详情附件 |
| `metersphere.functional.trash.list` | `CASE_DELETE` | 查询回收站用例 |

### 6.2 写入工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.functional.case.update` | `CASE_UPDATE` | 更新已有用例 |
| `metersphere.functional.case.delete` | `CASE_DELETE` | 软删除到回收站 |
| `metersphere.functional.case.restore` | `CASE_DELETE` | 从回收站恢复 |
| `metersphere.functional.case.batch_update` | `CASE_UPDATE` | 批量更新字段 |
| `metersphere.functional.case.batch_move` | `CASE_UPDATE` | 批量移动 |
| `metersphere.functional.case.batch_copy` | `CASE_WRITE` | 批量复制 |
| `metersphere.functional.comment.create` | `CASE_COMMENT` | 新增评论或回复 |
| `metersphere.functional.comment.update` | `CASE_COMMENT` | 更新本人评论 |
| `metersphere.functional.comment.delete` | `CASE_COMMENT` | 删除本人评论 |
| `metersphere.functional.attachment.attach` | `CASE_ATTACHMENT` | 关联用例详情附件 |
| `metersphere.functional.attachment.delete` | `CASE_ATTACHMENT` | 删除或解除附件 |

### 6.3 用例更新请求

```json
{
  "projectId": "100006",
  "caseId": "1565254823337852928",
  "expectedUpdateTime": 1785480787498,
  "patch": {
    "name": "商品查询存在同级码数据时展示关联卡片",
    "moduleId": "1566792971385683970",
    "priority": "P0",
    "tags": ["回归", "PDA"],
    "prerequisite": "测试商品存在同级码数据",
    "description": "用例备注",
    "steps": [
      {
        "id": "step-id",
        "num": 1,
        "desc": "进入商品查询并输入商品码",
        "expected": "展示关联信息卡片"
      }
    ],
    "customFields": {
      "field-id": "field-value"
    }
  },
  "requestId": "client-request-id",
  "reason": "根据最新需求调整步骤"
}
```

要求：

- 采用 Patch 语义；未提供的字段不修改。
- 空数组与 `null` 的含义必须明确区分。
- `expectedUpdateTime` 不一致时返回 `VERSION_CONFLICT`。
- 步骤更新必须保留已有步骤 ID，新增步骤由服务端生成 ID。

### 6.4 删除策略

- 第一阶段只开放软删除。
- `confirm=true` 为必填。
- 删除工具声明 `destructiveHint: true`。
- 批量删除限制最多 100 条。
- 永久删除放入独立 Scope，默认关闭。

## 7. 缺陷 MCP 扩展

### 7.1 查询工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.bug.template.get` | `BUG_READ` | 查询缺陷模板和字段定义 |
| `metersphere.bug.comments.list` | `BUG_READ` | 查询评论 |
| `metersphere.bug.attachments.list` | `BUG_READ` | 查询缺陷详情附件 |
| `metersphere.bug.relations.list` | `BUG_READ` | 查询关联用例 |
| `metersphere.bug.history.list` | `BUG_READ` | 查询变更历史 |
| `metersphere.bug.trash.list` | `BUG_DELETE` | 查询回收站缺陷 |

### 7.2 写入工具

| 工具 | Scope | 说明 |
|---|---|---|
| `metersphere.bug.delete` | `BUG_DELETE` | 删除至回收站 |
| `metersphere.bug.restore` | `BUG_DELETE` | 恢复缺陷 |
| `metersphere.bug.batch_update` | `BUG_WRITE` | 批量更新字段 |
| `metersphere.bug.comment.create` | `BUG_COMMENT` | 新增评论或回复 |
| `metersphere.bug.comment.update` | `BUG_COMMENT` | 更新本人评论 |
| `metersphere.bug.comment.delete` | `BUG_COMMENT` | 删除本人评论 |
| `metersphere.bug.attachment.attach` | `BUG_ATTACHMENT` | 关联缺陷详情附件 |
| `metersphere.bug.attachment.delete` | `BUG_ATTACHMENT` | 删除详情附件 |
| `metersphere.bug.case.relate` | `BUG_RELATE` | 关联功能/API/场景用例 |
| `metersphere.bug.case.unrelate` | `BUG_RELATE` | 解除用例关联 |

### 7.3 现有工具增强

`metersphere.bug.create` 和 `metersphere.bug.update` 增加：

```json
{
  "attachmentIds": ["temporary-attachment-id"],
  "addCaseIds": ["case-id"],
  "removeRelationIds": ["relation-id"]
}
```

建议仍保留独立附件和关联工具，使单次操作更容易审计、重试和授权。

### 7.4 状态与处理人

缺陷状态、处理人等模板字段继续通过 `customFields` 修改，但需要新增模板查询工具返回：

- 字段 ID
- 字段名称
- 字段类型
- 必填属性
- 可选值及可选值 ID
- 当前值

禁止 Agent 根据显示名称猜测字段 ID 或状态 ID。

## 8. Scope 与权限模型

### 8.1 新增 Scope

```java
CASE_UPDATE
CASE_DELETE
CASE_COMMENT
CASE_ATTACHMENT

BUG_DELETE
BUG_COMMENT
BUG_ATTACHMENT
BUG_RELATE
```

### 8.2 兼容策略

- `AGENT_ALL` 包含所有新增 Scope。
- `CASE_WRITE` 保持创建用例和模块的原有含义。
- 不让已有 `CASE_WRITE` Token 自动获得删除权限。
- `BUG_WRITE` 保持创建和普通字段更新能力。
- 缺陷删除、评论、附件、关联必须显式授权。
- 老 Token 不因版本升级自动扩大权限。

### 8.3 最终授权判定

```text
有效权限 =
Token Scope
∩ 用户 RBAC
∩ Token 项目白名单
∩ 资源归属校验
∩ 服务端工具策略
```

`AGENT_ALL` 也不能绕过用户 RBAC 和项目限制。

## 9. 服务层改造

建议新增以下 Agent Facade：

```text
AgentFunctionalCaseManageService
AgentFunctionalCaseCommentService
AgentFunctionalCaseAttachmentService
AgentBugManageService
AgentBugCommentService
AgentBugAttachmentService
AgentBugRelationService
AgentTemporaryFileService
```

统一调用链：

```text
MCP tools/call
  → 工具 Schema 校验
  → Token Scope 校验
  → projectId/项目编号/项目名解析
  → Token 项目白名单校验
  → 资源存在及项目归属校验
  → Agent Facade
  → 既有业务 Service
  → 业务操作日志、通知、Agent 审计
```

不得直接复用 Controller。

原 Controller 中的 `@Log`、`@SendNotice`、`@CheckOwner` 不会因为 Agent 直接调用 Service 自动执行，因此 Agent Facade 必须显式补齐对应行为，或将这些行为下沉为可复用领域服务。

## 10. MCP 工具注册改造

当前工具集中注册在 `AgentMcpStreamableService`。随着工具增加，建议拆分为 Handler 注册表：

```java
public interface AgentMcpToolHandler {
    String name();
    String requiredScope();
    Map<String, Object> inputSchema();
    Map<String, Object> annotations();
    Object execute(Map<String, Object> arguments);
}
```

按领域拆分：

```text
functional/
bug/
attachment/
project/
plan/
review/
```

`AgentMcpStreamableService` 仅负责：

- MCP 协议处理
- 工具发现
- 工具分发
- 统一异常转换
- 统一响应封装

禁止新增工具继续堆积在大型 `switch` 中。

## 11. Schema 规范

新增工具必须提供明确的 JSON Schema，不再使用：

```json
{
  "type": "object",
  "additionalProperties": true
}
```

建议：

- `additionalProperties: false`
- 明确 `required`
- 明确字符串长度
- 明确数组最大数量
- 枚举使用 `enum`
- 项目标识字段统一名为 `projectId`
- 支持内部 ID、界面编号和精确项目名
- 所有写操作支持 `requestId`
- 更新操作支持 `expectedUpdateTime`
- 高风险操作支持 `confirm`

## 12. 幂等与并发控制

### 12.1 幂等键

创建、评论、附件关联、关联用例和批量操作必须支持 `requestId`。

服务端记录：

```text
tokenId + toolName + requestId
```

行为：

- 相同参数重复请求：返回第一次结果。
- 相同 `requestId`、不同参数：返回 `IDEMPOTENCY_CONFLICT`。

### 12.2 乐观锁

用例和缺陷更新必须支持 `expectedUpdateTime`：

- 与数据库一致：允许更新。
- 不一致：返回 `VERSION_CONFLICT`。
- 响应包含当前更新时间和字段摘要。

## 13. 审计与安全

所有写操作记录：

- Token ID，不记录明文 Token
- 用户 ID
- 项目 ID
- 工具名称
- 资源 ID
- 修改前后摘要
- 附件 ID
- `requestId`
- 调用结果
- 客户端 IP
- 执行时间

附件安全要求：

- 文件名净化
- MIME 与 Magic Number 联合校验
- 路径穿越防护
- 恶意文件扫描
- 图片解码校验
- SVG/HTML 主动内容隔离
- 下载响应设置安全的 `Content-Disposition`
- 临时附件过期清理

## 14. 错误码

| 错误码 | 含义 |
|---|---|
| `PROJECT_NOT_FOUND` | 项目无法解析 |
| `PROJECT_NOT_ALLOWED` | Token 无权访问项目 |
| `RESOURCE_NOT_FOUND` | 资源不存在 |
| `RESOURCE_PROJECT_MISMATCH` | 资源不属于指定项目 |
| `SCOPE_DENIED` | Token Scope 不足 |
| `RBAC_DENIED` | 用户 RBAC 不足 |
| `VERSION_CONFLICT` | 乐观锁冲突 |
| `ATTACHMENT_EXPIRED` | 临时附件过期 |
| `ATTACHMENT_PURPOSE_MISMATCH` | 附件用途不匹配 |
| `ATTACHMENT_LIMIT_EXCEEDED` | 附件大小或数量超限 |
| `ATTACHMENT_TYPE_NOT_ALLOWED` | 文件类型不允许 |
| `IDEMPOTENCY_CONFLICT` | 幂等键参数冲突 |
| `CONFIRMATION_REQUIRED` | 高风险操作缺少确认 |

MCP 层建议统一映射为 JSON-RPC `-32001`，在 `error.data` 中携带业务错误码和结构化详情。

## 15. 实施分期

### 15.1 P0：附件和基础维护闭环

- 通用 Agent 临时附件上传
- 用例详情附件查询、关联、删除
- 缺陷详情附件查询、关联、删除
- `functional.case.update`
- 用例评论查询和新增
- 缺陷评论查询和新增
- 明确的 JSON Schema
- 前端可见性集成测试

P0 解决“上传成功但前端详情不可见”的直接问题。

### 15.2 P1：完整管理闭环

- 用例软删除和恢复
- 用例批量更新、移动、复制
- 缺陷删除和恢复
- 缺陷批量更新
- 缺陷关联和解除关联
- 评论编辑和删除
- 模板、自定义字段和历史记录查询
- 幂等键和乐观锁

### 15.3 P2：高级能力

- 文件库关联和转存
- 用例及缺陷导入导出
- 永久删除，默认关闭
- 外部缺陷平台同步，使用独立高风险 Scope
- Agent 操作日志前端展示

## 16. 代码改造范围

主要涉及：

```text
backend/services/agent-integration/
  controller/
  service/
  dto/
  constants/AgentTokenScope.java
  security/

backend/services/case-management/
  functional/service/

backend/services/bug-management/
  bug/service/

backend/framework/sdk/
  MCP 错误码、公共附件模型
```

建议新增：

```text
agent-integration/
  tool/
    AgentMcpToolHandler.java
    functional/
    bug/
    attachment/
  service/
    AgentTemporaryFileService.java
    AgentFunctionalCaseManageService.java
    AgentFunctionalCaseAttachmentService.java
    AgentFunctionalCaseCommentService.java
    AgentBugManageService.java
    AgentBugAttachmentService.java
    AgentBugCommentService.java
    AgentBugRelationService.java
```

数据库优先复用现有附件表。如需保存临时附件用途、过期时间和幂等信息，可新增：

```text
agent_temp_attachment
agent_idempotency_record
```

## 17. 测试策略

### 17.1 单元测试

- 项目标识解析
- Scope 判定
- 项目白名单
- 资源归属校验
- 乐观锁
- 幂等请求
- 附件用途和过期校验
- Patch 字段合并

### 17.2 集成测试

- PNG 上传后出现在用例详情。
- PNG 上传后出现在缺陷详情。
- 执行日志附件不混入详情附件。
- 用例更新触发业务日志和通知。
- 缺陷更新触发业务日志和通知。
- 评论附件在前端可预览。
- 删除进入回收站且可恢复。
- 无 Scope、无 RBAC、项目越权分别返回明确错误。

### 17.3 MCP 契约测试

- `tools/list` 返回完整 JSON Schema。
- `readOnlyHint`、`destructiveHint`、`idempotentHint` 正确。
- 参数非法时不进入业务 Service。
- JSON-RPC 错误结构稳定。
- 老工具行为保持兼容。

## 18. 验收标准

1. 使用 Agent Token 上传 PNG 后，可在用例详情直接预览。
2. 使用 Agent Token 上传 PNG 后，可在缺陷详情直接预览。
3. 执行日志、计划评论、用例详情和缺陷详情附件来源明确。
4. 用例和缺陷更新不会覆盖前端并发修改。
5. 没有新增 Scope 的旧 Token 不能执行新增写操作。
6. 用户 RBAC 不足时，即使 Token 拥有 Scope 仍返回 403。
7. Token 无法通过资源 ID 越权操作白名单外项目。
8. 项目内部 ID、界面编号和精确名称均可解析。
9. 重复 `requestId` 不产生重复附件、评论和关联。
10. 删除默认进入回收站，可恢复。
11. 前端、普通 REST 和 MCP 产生的数据结构一致。
12. 所有写操作均存在业务日志和 Agent 审计记录。

## 19. 推荐结论

优先交付 P0：

1. 建立通用 Agent 临时附件机制。
2. 新增用例详情和缺陷详情附件工具。
3. 新增用例更新工具。
4. 新增用例、缺陷评论查询与创建工具。
5. 拆分 MCP Tool Handler，避免继续扩展单体 `switch`。
6. 补齐显式资源归属、业务日志、通知和 Agent 审计。

P0 完成后，MeterSphere Agent 即可形成“检索—修改—提交—上传证据—前端查看”的基础闭环，并为 P1 的删除、恢复、批量操作和缺陷关联能力提供统一架构基础。

## 20. 前端体验补充改造实现方案

> 本章节补充 2026-07-31 确认的缺陷管理、测试用例列表和 Agent Token 管理页面体验改造内容。该部分与前文 MCP 能力扩展并行推进，优先解决前端展示、数据口径一致性和用户自助 Token 管理问题。

### 20.1 改造范围

本次确认进入实现的前端体验优化包含以下 5 项：

1. 缺陷管理详情抽屉标题与详情内容布局调整。
2. 缺陷管理列表状态样式调整。
3. 缺陷详情删除“评论”Tab。
4. 测试用例列表页个人执行进度数据口径修正。
5. 系统设置 - 系统 - Agent 集成页面新增用户 Token 记录管理区。

### 20.2 缺陷管理详情改造

#### 20.2.1 抽屉标题区

当前缺陷详情顶部展示缺陷名称，改为展示缺陷 ID。

目标展示：

```text
100005
```

实现规则：

- 抽屉顶部主标题只显示缺陷 ID。
- 不再在标题区显示缺陷名称。
- ID 使用列表中的展示编号，例如 `100005`，不是数据库内部 UUID 或雪花 ID。
- 详情打开、刷新、编辑态切换后，标题区仍保持缺陷 ID 展示。

#### 20.2.2 详情 Tab 内容区

将原详情区中的“缺陷信息”区块调整为“缺陷名称”。

目标结构：

```text
缺陷名称
商品查询存在同级码数据时展示异常

缺陷内容
……
```

实现规则：

- 将原区块标题“缺陷信息”修改为“缺陷名称”。
- 去掉区块内部重复的“缺陷名称”字段 label。
- “缺陷名称”标题下直接展示缺陷名称内容。
- “缺陷内容”保持在缺陷名称下方。
- 查看态和编辑态结构保持一致，避免用户在编辑时误解字段含义。

### 20.3 缺陷管理列表状态样式调整

列表状态列改为标签/按钮式样式，参考缺陷详情右侧“责任人”按钮或标签视觉。

建议状态样式：

| 状态 | 样式建议 |
|---|---|
| 新建 | 蓝色浅底圆角标签 |
| 处理中 | 橙色浅底圆角标签 |
| 已解决 | 绿色浅底圆角标签 |
| 非缺陷 | 灰色浅底圆角标签 |
| 已关闭 | 灰色或深灰浅底圆角标签 |

实现规则：

- 状态不再使用普通文本样式。
- 状态标签需具备统一高度、圆角和内边距。
- 与缺陷详情侧边栏字段的视觉体系保持一致。
- 状态颜色映射应集中维护，避免列表、详情各自维护一套样式。
- 状态样式只改变展示，不改变状态枚举、流转规则和后端字段。

### 20.4 删除缺陷详情评论 Tab

缺陷详情 Tab 调整为：

```text
详情 ｜ 用例 ｜ 变更历史
```

删除：

```text
评论
```

兼容规则：

- 前端 Tab 配置中移除评论 Tab。
- 如果用户本地缓存的 active tab 为评论 Tab，打开详情时自动回退到 `详情`。
- 后端评论接口本次不删除，避免影响历史能力、MCP 评论能力或后续恢复。
- 若未来需要评论能力，应以内嵌评论区或独立入口重新设计，不恢复为详情 Tab。

### 20.5 测试用例列表个人执行进度修正

#### 20.5.1 测试计划展示格式

测试用例列表页顶部测试计划信息明确调整为：

```text
测试计划：测试计划名称（ID）  [进度条]
```

示例：

```text
测试计划：防窜物流T3.3.4（TP-10001）  [进度条]
```

实现规则：

- 必须显示测试计划名称。
- 必须显示测试计划 ID。
- ID 使用页面可见的测试计划编号或展示 ID，不使用数据库内部主键。
- 格式固定为中文括号：`测试计划名称（ID）`。
- 进度条与测试计划名称、ID 同一行展示。
- 不允许只展示测试计划名称而缺失 ID。
- 测试计划进度条只表达测试计划进度，不与个人执行进度混用。

#### 20.5.2 个人执行进度数据口径

列表页个人执行进度必须与测试用例详情中的个人执行进度保持一致。

目标展示：

```text
个人执行进度：28/40  [分段进度条]
```

Hover 明细：

```text
通过 18
失败 4
阻塞 2
跳过 4
未执行 12
```

实现规则：

- 列表页不再单独计算个人执行进度。
- 复用详情页相同接口或相同后端聚合方法。
- 同一项目、同一用户下，列表页与详情页展示结果必须完全一致。
- 分子、分母、状态分段、颜色和 tooltip 数据均保持一致。
- 进度条颜色映射集中维护。
- 若接口返回为空或当前用户没有责任用例，展示 `个人执行进度：0/0` 或产品统一空态，不得展示错误历史值。

### 20.6 Agent 集成页面 Token 管理区改造

页面位置：

```text
系统设置 > 系统 > Agent 集成
```

#### 20.6.1 新增“我的 Agent Token”区域

页面上方新增 Token 记录管理区。

目标结构：

```text
我的 Agent Token                         [创建 Token]

名称 | Scope | 项目范围 | 创建时间 | 最后使用时间 | 状态 | 操作
```

操作项：

```text
查看 ｜ 停用/启用 ｜ 删除
```

实现规则：

- 用户只能看到并管理自己创建的 Token。
- 已创建 Token 不展示密钥明文。
- Token 密钥仍然只在创建成功时显示一次。
- 创建成功弹窗中保留一次性密钥展示和复制提醒。
- Token 列表支持状态展示，例如 `启用`、`已停用`。
- 删除 Token 前需要二次确认。
- 停用 Token 后，该 Token 不能继续调用 Agent MCP 服务。
- 重新启用 Token 时仍不展示密钥明文。

#### 20.6.2 原说明文字下移

当前页面已有说明文字移动到 Token 记录区下方。

目标结构：

```text
我的 Agent Token
Token 记录表格

使用说明
原说明文字……
```

调整目的：

- 主操作区优先展示用户自己的 Token。
- 说明信息作为辅助内容，不占据首屏核心位置。
- 降低用户找不到已创建 Token 记录的概率。

### 20.7 建议实现顺序

建议按以下顺序开发，降低返工风险：

1. 缺陷详情标题与内容区布局调整。
2. 缺陷详情删除评论 Tab，并处理旧 activeTab 兼容。
3. 缺陷列表状态样式统一。
4. 测试用例列表测试计划信息和个人执行进度口径修正。
5. Agent 集成 Token 管理区改造。

### 20.8 验收标准

- 缺陷详情顶部显示缺陷 ID，不显示缺陷名称。
- 详情 Tab 中展示以下结构：

```text
缺陷名称
缺陷名称内容

缺陷内容
缺陷内容正文
```

- 缺陷详情无“评论”Tab。
- 旧缓存评论 Tab 不导致详情空白。
- 缺陷列表状态为统一圆角标签样式。
- 测试用例列表展示以下结构：

```text
测试计划：测试计划名称（ID）  [进度条]
```

- 测试用例列表个人执行进度与详情页完全一致。
- Agent 集成页面上方展示“我的 Agent Token”记录表。
- Token 密钥只在创建成功时显示一次，列表中不展示明文密钥。
- 原说明文字位于 Token 记录区下方。
