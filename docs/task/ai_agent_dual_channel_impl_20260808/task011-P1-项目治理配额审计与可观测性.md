# task011 - P1 - 项目治理、配额、审计与可观测性

## 状态

部分实现：项目 Provider/资源白名单、Agent 并发/时长/日配额、独立用量和关键审计已落地；指标告警、全局容量、用户白名单和故障演练未完成。

## 目标

在现有 AI 项目治理基础上区分模型 API 和用户 Agent 的权限、额度、成本和运行状态，确保个人会员通道不会绕过平台安全治理，也不会错误计为平台模型成本。

## 依赖

- task001–task010。

## 当前基础

- `AiGovernanceService` 已有项目模型白名单、并发、Token、文件容量和用量记录。
- `AiAuditService` 已记录部分 Provider、OAuth 和 Gateway 操作。
- 当前治理以模型 ID 和 Token 为核心，不足以表达用户设备与会员 Agent。

## 实现范围

### 1. 项目策略

在 `ai_project_governance` 增量增加或新建扩展表：

```text
allowed_resource_types
allowed_agent_providers
allow_personal_agent
allow_local_agent_tools
max_agent_concurrent_tasks
max_agent_execution_minutes
daily_agent_execution_limit
```

默认：

- `allow_personal_agent=false`，由项目管理员显式开启；若产品决定默认开启，必须在评审中记录。
- `allow_local_agent_tools=false`。
- 单用户 Agent 并发为 1。

原 `allowed_model_ids` 继续只存模型 ID，禁止混入 Agent 连接 ID。

### 2. 额度与成本

模型 API：

- 延续真实/估算 Token、月额度、回退和平台成本统计。

用户 Agent：

- 记录执行次数、时长、成功/失败/取消和 Provider 返回用量。
- 无 Token 时标记 `estimated=true`，不伪装为供应商真实账单。
- 供应商套餐额度由供应商裁决，平台只展示错误和连接健康。
- 不把用户会员 Agent 消耗计入平台 API 成本。

### 3. 限流

- 项目、用户、连接和设备四个维度限制。
- WSS 消息速率和单消息大小限制。
- 工具调用次数、Agent 最大轮数、总执行时长限制。
- 限流使用 Redis 原子实现，Redis 不可用时采用明确的安全降级策略。

### 4. 审计

记录：

- 连接创建、授权、刷新、撤销、删除。
- 设备配对、上线、离线、升级和撤销。
- 会话资源选择与切换。
- 执行资源类型、Provider、连接、设备、结果和耗时。
- 工具名、目标资源、结果和错误码。
- 跨通道回退确认。

禁止记录完整 Token、Cookie、第三方密码、设备私钥、完整文档正文和未脱敏 stderr。

### 5. 指标与告警

至少增加：

```text
agent_bridge_online_devices
agent_bridge_connection_duration_seconds
agent_execution_first_event_seconds
agent_execution_duration_seconds
agent_execution_success_total
agent_execution_failure_total
agent_execution_cancel_total
agent_auth_expired_total
ai_api_channel_requests_total
ai_agent_channel_requests_total
```

告警：Bridge 大面积离线、授权失败率突增、首事件超时、工具拒绝突增、特定 Provider 失败率和协议版本不兼容。

### 6. 管理面

- 项目管理员只配置 Provider 类型和限制，不查看成员凭据。
- 系统管理员可全局禁用 Provider 或最低 Bridge 版本。
- 运维只查看聚合状态和脱敏错误。
- 提供应急开关立即停止新 Agent 执行，不影响模型 API。

## 验收标准

- 项目可以独立允许或禁止 WorkBuddy/Codex/Cursor。
- 项目禁止个人 Agent 后，已有连接不能在该项目继续执行。
- 模型 API 和用户 Agent 用量、成本和错误分开统计。
- Agent 并发、时长、频率和每日次数限制覆盖聊天、重试和工具循环。
- 审计可追踪执行但不泄漏敏感内容。

## 测试要求

- 项目策略组合和跨项目测试。
- 模型 Token 与 Agent 次数/时长配额边界测试。
- Redis 原子限流和故障降级测试。
- 审计敏感字段断言。
- 指标标签基数和并发准确性测试。
- 全局 Provider/版本禁用应急演练。

## 非目标

- 不推测或代替第三方供应商账单。
- 不允许管理员查看用户第三方 Token。
