# task002 - P0 - 项目可用模型与会话模型切换

## 状态

进行中。

已新增项目可用模型查询、后端二次校验和会话模型切换接口。当前能力声明采取保守策略：流式按现有 Provider 能力开放，原生工具能力在 Provider 契约完成前不宣称支持。

## 目标

为【生成用例】提供项目级可用模型接口，使用户只能选择自己有权使用、项目允许且满足 Agent 能力要求的模型，并将模型选择持久化到会话。

## 依赖

- task001 会话数据模型。

## 当前基础

- 已有系统模型和个人模型配置。
- 已有全局模型名称列表。
- 已有 `ai_project_governance.allowed_model_ids` 和 fallbackModelId。
- 当前前端使用全局模型列表，后端生成时才校验项目白名单。

## 实现范围

### 1. 可用模型计算

服务端计算：

```text
启用模型
∩ 用户可访问模型
∩ 组织允许模型
∩ 项目模型白名单
∩ 支持聊天能力的模型
```

明确白名单为空的产品语义。默认推荐继承系统或组织配置集合，不默认开放全部模型。

### 2. 能力声明

每个模型至少返回：

- id、name、provider、personal。
- supportsStream、supportsTools、supportsVision。
- contextWindow、maxOutputTokens。
- connectionStatus、disabledReason。

能力不能统一写死，无法确认时使用 false 或 UNKNOWN。

### 3. 接口

```http
GET  /functional/case/ai/agent/models?projectId={projectId}
POST /functional/case/ai/agent/conversation/model
```

模型切换请求携带 projectId、conversationId、modelSourceId，并校验会话所有者和模型权限。

### 4. 默认模型

- 新会话优先使用项目默认模型。
- 项目默认不可用时，返回明确错误或选择第一个允许模型，行为需固定并有审计。
- 会话切换模型只影响后续消息，历史消息保留实际模型 ID。

### 5. 前端接入准备

定义前端模型类型和接口，不在本任务完成聊天页面改造。

## 验收标准

- 用户看不到无权访问或项目不允许的模型。
- 后端再次校验模型，不能通过伪造请求绕过。
- 模型切换持久化到会话。
- 历史消息能够展示实际使用模型。
- 模型不可用时返回标准原因，不在生成阶段才模糊失败。
- 项目切换后模型列表和默认选择正确刷新。

## 测试要求

- 系统模型、个人模型、禁用模型和无权模型过滤测试。
- 项目白名单为空、包含、不包含模型测试。
- 会话模型切换和跨用户切换越权测试。
- 模型能力序列化测试。
- 项目默认模型失效测试。

## 非目标

- 不实现 OAuth、Gateway 或 Provider 凭据配置页面。
