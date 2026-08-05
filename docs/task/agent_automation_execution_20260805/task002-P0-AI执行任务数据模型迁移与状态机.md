# task002 - P0 - AI 执行任务数据模型、迁移与状态机

## 状态

基础实现完成，待数据库迁移实跑与状态机单测

## 执行记录（2026-08-05）

- 已新增迁移表：`ai_execution_task`、`ai_execution_case`、`ai_execution_event`、`ai_runner_session`、`ai_credential_reference`。
- 已新增 `AgentExecutionStatus` 状态常量，区分任务终态、用例失败/阻塞/跳过状态。
- 已新增执行任务 Mapper，支持任务创建、用例快照、追加事件、状态更新、统计刷新和重试失败/阻塞用例。
- 已在执行服务中实现 `SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED` 统计刷新逻辑，避免部分失败被写成成功。
- 已验证：后端编译通过。
- 未完成验证：尚未在目标数据库执行 Flyway 迁移；尚未补状态机单元测试；Runner 会话与凭据表仅完成模型层，业务治理在后续 task008+。

## 目标

新增 AI 自动化执行任务、执行用例、事件日志、Runner 会话和凭据引用相关数据模型，支撑任务范围固定、状态流转、日志追加、结果回写和恢复重试。

## 实现范围

- 新增或等效实现数据表：
  - `ai_execution_task`
  - `ai_execution_case`
  - `ai_execution_event`
  - `ai_runner_session`
  - `ai_credential_reference`
- `ai_execution_task` 至少包含项目、测试计划、来源、状态、Runner、Provider、统计和时间字段。
- `ai_execution_case` 固定任务范围，保存 `case_id`、`test_plan_case_id`、状态、结果、顺序和重试次数。
- `ai_execution_event` 使用只追加模型，记录 `taskId/caseId/stepId/sequence/timestamp/level/type/content/artifactIds/sanitizedMetadata`。
- 实现状态机：
  - `CREATED`
  - `RESOLVING_SCOPE`
  - `WAITING_CONFIRMATION`
  - `PREPARING_BROWSER`
  - `WAITING_LOGIN`
  - `RUNNING`
  - `WRITING_BACK`
  - `SUCCESS`
  - `PARTIAL_SUCCESS`
  - `FAILED`
  - `CANCELED`
- 状态流转必须校验合法前置状态，禁止跳过确认或把部分成功写成成功。

## 不应实现的内容

- 不在表内保存密码、Cookie、Token、私钥等明文敏感数据。
- 不允许执行过程中动态扩大任务用例范围。
- 不允许通过直接 Mapper 写结果绕过领域服务。

## 验收标准

- 数据库迁移可重复执行，字段、索引、唯一约束满足查询和幂等需求。
- 任务、用例、事件均带组织/项目隔离字段或可从关联对象可靠推导。
- 任意失败步骤可保留已完成范围、证据引用和未执行原因。
- `PARTIAL_SUCCESS` 与 `SUCCESS` 在后端状态上严格区分。

## 验证要求

- 数据库迁移验证。
- 状态机单元测试。
- 幂等键和唯一约束测试。
- 敏感字段扫描与日志脱敏检查。
