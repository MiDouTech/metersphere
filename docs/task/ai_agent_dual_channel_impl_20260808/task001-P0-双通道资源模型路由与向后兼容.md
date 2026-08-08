# task001 - P0 - 双通道资源模型、路由与向后兼容

## 状态

实现中：统一资源模型、路由和旧字段兼容代码已落地；完整模型 API 回归、跨用户/跨项目集成测试未完成。

## 目标

在现有模型 API 资源之外增加用户 Agent 资源，建立统一选择 DTO 和双通道路由，同时保证旧接口、旧会话和现有模型调用完全兼容。

## 当前基础

- `AiCaseAvailableModelService` 从 `ai_model_source` 查询可用模型。
- `AiCaseAgentConversationController` 提供 `/models`、会话切换和聊天接口。
- `AiCaseAgentOrchestrator` 当前按 `modelSourceId` 调用 `AiProviderAdapter`。
- 前端 `caseGenerate/index.vue` 只维护模型 ID。

## 实现范围

### 1. 统一资源类型

新增：

```text
AiResourceType.MODEL_API
AiResourceType.USER_AGENT
AiSelectableResourceDTO
AiResourceCapabilities
AiResourceUnavailableReason
```

统一 DTO 至少包含：资源 ID、类型、Provider、显示名称、个人/系统标识、在线状态、流式/工具/文件/取消能力、不可用原因。

### 2. 统一资源查询

新增：

```http
GET /functional/case/ai/agent/resources?projectId={projectId}
```

服务端组合：

```text
现有可用模型
+ 当前用户已连接的用户 Agent
∩ 项目允许的资源类型
∩ 项目允许的 Agent Provider
∩ 当前场景所需能力
```

原 `/models` 保留，不改变响应结构和过滤规则。

### 3. 会话资源路由

聊天请求新增可选字段：

```text
resourceType
resourceId
```

兼容规则：

- 旧请求只传 `modelSourceId`，解释为 `MODEL_API`。
- 新请求传 `resourceType/resourceId` 后不得同时传矛盾的 `modelSourceId`。
- 后端根据资源类型选择 `AiProviderAdapter` 或 `UserAgentConnector`。
- 资源权限必须在创建会话、切换资源、聊天、重试时重复校验。

### 4. Feature Flag

新增：

```text
MS_AI_USER_AGENT_ENABLED=false
MS_AI_USER_AGENT_WORKBUDDY_ENABLED=false
MS_AI_USER_AGENT_CODEX_ENABLED=false
MS_AI_USER_AGENT_CURSOR_ENABLED=false
```

总开关关闭时：

- 不注册或不暴露用户 Agent 资源。
- `/resources` 可以仅返回模型，或按版本策略返回 404；行为固定并测试。
- 原 `/models`、聊天和模型切换无差异。

## 建议代码落点

- `backend/services/case-management/.../dto/AiSelectableResourceDTO.java`
- `backend/services/case-management/.../service/AiCaseAvailableResourceService.java`
- `backend/services/case-management/.../service/AiCaseAgentOrchestrator.java`
- `backend/services/case-management/.../controller/AiCaseAgentConversationController.java`
- `backend/services/case-management/.../request/AiCaseAgentChatRequest.java`

## 验收标准

- `/resources` 能稳定分组返回模型和用户 Agent。
- 旧请求无需修改即可继续调用模型 API。
- 伪造 `resourceType`、`resourceId` 或冲突字段被拒绝。
- 资源切换只影响后续消息，历史消息保留实际资源。
- Feature Flag 关闭后不存在新增链路副作用。

## 测试要求

- 旧请求 JSON 反序列化和回归测试。
- 模型、Agent、禁用、离线、无权资源过滤测试。
- 资源字段组合参数化测试。
- 跨用户和跨项目资源切换越权测试。
- Feature Flag 开关测试。
- 原 Provider 流式、取消、重试和回退回归测试。

## 非目标

- 本任务不实现 Bridge、真实 Agent 或前端管理页面。
