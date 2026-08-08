# task012 - P0 - 标准错误码、幂等、超时与恢复机制

## 状态

进行中。已实现 requestId 执行幂等、草稿写工具幂等、Provider 失败脱敏、取消、重试和事件恢复；待统一全部错误码、工具超时、跨节点租约及服务重启恢复。

## 目标

统一 Agent 全链路错误语义、幂等规则、超时策略和异常恢复，使前端可以稳定区分权限、模型、配额、Provider、工具、草稿和取消错误。

## 依赖

- task004–task011。

## 实现范围

### 1. 标准错误码

至少支持：

- MODEL_NOT_ALLOWED。
- MODEL_PERMISSION_DENIED。
- MODEL_CAPABILITY_UNSUPPORTED。
- PROVIDER_RATE_LIMITED。
- TOKEN_QUOTA_EXCEEDED。
- CONCURRENCY_LIMIT_EXCEEDED。
- CONTEXT_TOO_LARGE。
- PROVIDER_TIMEOUT。
- PROVIDER_STREAM_INTERRUPTED。
- TOOL_ARGUMENT_INVALID。
- TOOL_EXECUTION_FAILED。
- TOOL_ROUND_LIMIT_EXCEEDED。
- DRAFT_VERSION_CONFLICT。
- USER_CONFIRMATION_REQUIRED。
- EXECUTION_CANCELED。
- IDEMPOTENCY_CONFLICT。

错误响应包含 code、message、requestId、retryable；不得向客户端返回敏感 cause 和堆栈。

### 2. 幂等规则

- chat 使用 clientRequestId 或服务端 requestId 防止重复提交。
- create/update/save 工具使用 toolCallId。
- 文件上传可使用 SHA-256 和业务幂等键，但重复文件是否保留独立记录需明确。
- cancel、confirm 和 retry 接口幂等。

### 3. 超时

- Agent 总执行超时。
- Provider 连接、首包和总超时。
- 单工具超时。
- 文档检索和解析超时。
- 等待用户确认超时。

所有超时产生确定状态和错误码，不能长时间停留在 RUNNING。

### 4. 恢复

- 服务重启时扫描长期 RUNNING 执行并转为可恢复失败或继续调度。
- SSE 重连从持久化状态恢复。
- 工具结果保存成功但最终消息失败时，重试不能重复写入。
- Provider 完成但 usage 写入失败时提供补偿或告警。

### 5. 前端映射

为错误码建立统一提示、是否允许重试、是否跳转配置和是否刷新资源的映射。

## 验收标准

- 相同错误在同步、SSE 和工具结果中使用相同 code。
- 重复提交不会产生重复消息、草稿或正式用例。
- 所有超时执行最终可终止且可查询。
- 服务重启后不存在永久卡死的运行中任务。
- 前端能对配额、权限、模型失效、取消和可重试错误分别处理。

## 测试要求

- 错误码契约测试。
- chat、工具、确认、保存的重复请求测试。
- 各层超时测试。
- 服务重启/执行恢复集成测试。
- usage、审计和事件写入异常补偿测试。
