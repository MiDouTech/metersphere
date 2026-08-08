# task004 - P0 - Provider Agent 流式协议、Token 统计与真实取消

## 状态

进行中。

已为 Provider 请求和 usage 增加 requestId、conversationId 与 tokenEstimated 追踪，并区分 Provider 实际 usage 和平台估算值。统一 Agent 事件协议、工具流和真实取消仍在实施中。

## 目标

将现有 `Flux<String>` Provider 流升级为可承载消息、工具调用、Token、错误和取消状态的统一 Agent Provider 协议，并保证取消能够终止底层流。

## 依赖

- task001 执行与消息模型。
- task002 模型能力声明。

## 当前基础

- `AiProviderAdapter` 已有同步、流式、重试、回退和 usage 记录。
- 当前流只返回字符串，未使用聊天记忆和工具。
- 当前生成取消只更新任务状态，不能确保中断 Provider。

## 实现范围

### 1. 统一请求

定义 `AgentChatRequest`，至少包含：

- requestId、projectId、organizationId、conversationId。
- modelSourceId、systemPrompt、messages、tools。
- maxOutputTokens、temperature、timeout。

### 2. 统一流事件

定义 Provider 内部事件：

- TEXT_DELTA。
- TOOL_CALL_START/TOOL_ARGUMENT_DELTA/TOOL_CALL_COMPLETED。
- USAGE。
- PROVIDER_COMPLETED。
- PROVIDER_ERROR。

Provider 事件与对外 SSE 事件解耦。

### 3. Provider 路由

- Adapter 根据 providerType 选择，不能由单一实现假定所有模型能力一致。
- 对不支持原生工具调用的模型，允许受控 JSON 工具协议，但必须严格解析。
- 工具不支持时返回明确能力错误。

### 4. 记忆与消息

Provider 接收已构建的消息列表，不自行读取任意 conversationId，避免越权和上下文不一致。

### 5. 超时、重试与回退

- 区分连接超时、首包超时、总调用超时。
- 只有临时网络错误、429 和可重试 5xx 自动重试。
- 已输出文本或已产生写工具调用后不得从头自动重试。
- 回退仅使用项目明确配置的 fallbackModelId，并产生 warning 和审计。

### 6. Token 统计

- 优先读取 Provider 实际 usage。
- 无实际 usage 时允许估算，但必须标记 estimated。
- usage 关联 requestId、conversationId、requestedModelId 和 actualModelId。

### 7. 真实取消

- 保存 requestId 对应的 Reactor Disposable、订阅或 Provider cancel handle。
- cancel 后停止读取流、停止重试和回退。
- 取消必须幂等。
- 进程重启后通过执行状态阻止后续工具继续执行。

## 验收标准

- 同一协议支持纯文本回复和工具调用。
- 首包前临时错误可安全重试，首包后不重复输出。
- 配置回退时能记录实际模型和 warning。
- 用户取消后底层流停止，usage 和执行状态为 CANCELED。
- 不支持工具的模型不会被误报为支持。
- Provider 错误不暴露密钥、Token 或 Authorization 头。

## 测试要求

- 文本流、工具调用流、usage 和结束事件测试。
- 首包前/后错误重试测试。
- 回退模型测试。
- 连接、首包和总超时测试。
- 取消底层订阅测试。
- Token 实际值和估算值测试。
