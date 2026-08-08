# task001 - P0 - 会话、消息、执行与事件数据模型

## 状态

进行中。

已新增 `V3.7.2_30__ai_case_agent_conversation.sql`，建立专用会话、消息、执行、可恢复事件和幂等工具调用表，并补充草稿、Provider usage 的会话与请求追踪字段。待完成领域访问层、迁移测试和并发状态测试后再标记完成。

## 目标

为用例 Agent 建立后端持久化事实模型，支持项目和用户隔离、消息流式状态、工具调用、执行取消、事件重放、Token 统计与审计追踪。

## 依赖

无。该任务是后续 P0 任务的基础。

## 当前基础

- 已存在通用 `ai_conversation` 和 `ai_conversation_content`，但缺少项目、模型、消息状态、工具和 Token 字段。
- 已存在 `functional_case_ai_generation`、`functional_case_ai_draft`、`ai_source_document` 和 `ai_provider_usage`。
- 当前生成页面自行创建 conversationId，且未创建 `ai_conversation` 主记录。

## 实现范围

### 1. 数据模型决策

评审并选择以下一种方案，不允许并行维护两套含义重复的会话主表：

- 方案 A：扩展 `ai_conversation`、`ai_conversation_content`，增加业务场景和项目字段。
- 方案 B：保留通用表，新增用例 Agent 扩展表和执行/事件表。

推荐优先评估方案 A，并通过 `scene=FUNCTIONAL_CASE_GENERATION` 区分业务场景。

### 2. 会话字段

至少支持：

- id、scene、project_id、organization_id、create_user。
- title、model_source_id、status。
- system_prompt_version、last_message_time。
- create_time、update_time、deleted。

### 3. 消息字段

至少支持：

- conversation_id、project_id、user_id。
- role：SYSTEM/USER/ASSISTANT/TOOL。
- content、status：STREAMING/COMPLETED/FAILED/CANCELED。
- model_source_id、request_id。
- tool_name、tool_call_id、tool_arguments、tool_result。
- input_tokens、output_tokens、error_code。
- create_time、update_time。

工具参数和结果保存前必须按字段规则脱敏。

### 4. 执行与事件

扩展 `functional_case_ai_generation` 或新增 Agent 执行表，支持：

- request_id、conversation_id、user_message_id、assistant_message_id。
- execution_type、status、cancel_requested。
- requested_model_id、actual_model_id。
- start_time、first_token_time、finish_time、duration_ms。
- input_tokens、output_tokens、error_code、retry_of_request_id。

新增可恢复事件存储，至少包含 request_id、sequence、event_type、payload、create_time。事件设置合理保留期，不永久保存高频文本 delta。

### 5. 关系补齐

- 草稿可以直接或通过生成记录稳定关联 conversationId 和 requestId。
- 来源文档与会话关系必须可查询。
- usage 记录增加 conversationId、requestId，或建立可追踪关系。

## 迁移要求

- 使用新的 Flyway DDL，禁止修改已发布迁移文件。
- 不破坏现有 AI 对话、草稿和来源文档数据。
- 历史生成记录允许 conversationId 为空。
- 提供索引、数据量估算、事件清理策略和回滚说明。

## 验收标准

- 会话、消息、执行、事件能通过 requestId 和 conversationId 完整追踪。
- 数据库层不存在生成用例消息的孤立会话记录。
- 不同项目或用户的相同 conversationId 不会互相读取或覆盖。
- 消息和执行状态可以表达流式中、成功、失败和取消。
- 事件可以从指定 sequence 继续读取。
- 历史数据迁移后原有 AI 功能不受影响。

## 测试要求

- Flyway 在空库和历史版本库升级测试。
- 会话、消息、执行和事件 Mapper/DAO 测试。
- 唯一键、索引、外键或应用约束测试。
- 同 requestId 并发插入和状态更新测试。
- 跨项目、跨用户查询隔离测试。

## 非目标

- 不在本任务实现聊天 Controller、Provider 调用和前端页面。
