# task006 - P0 - 执行结果回写、幂等与 PARTIAL_SUCCESS 处理

## 状态

部分完成

## 执行记录（2026-08-05）

- 已扩展单条/批量回写请求支持 `executionTaskId`。
- 已复用原 `AgentFunctionalCaseSubmitService` 计划内/计划外正式回写链路，不直接写正式执行结果表。
- 单条回写成功后会同步更新 `ai_execution_case` 和 `ai_execution_task` 统计。
- 批量回写中单条失败时会记录失败用例并刷新任务为 `PARTIAL_SUCCESS` 或 `FAILED`，不回滚已成功项。
- 已新增迁移 `V3.7.2_27__ai_execution_writeback_idempotency.sql`，并在提交前回查/成功后写入 `taskId + caseId + idempotencyKey` 去重。
- 已验证：后端相关代码已落地；尚未补集成测试与真实环境重复提交验证。
- 未完整实现：证据保留策略（task012）；无 `idempotencyKey` 时仍按原链路直接回写。

## 目标

复用 MeterSphere 现有功能用例结果提交链路，将 Agent 自动化执行的步骤结果、最终结果、说明和附件按计划内/计划外场景正确回写。

## 实现范围

- 计划内用例使用 `testPlanId + testPlanCaseId` 调用现有计划执行链路。
- 计划外用例使用 `caseId` 调用现有计划外提交链路。
- 复用或扩展：
  - `AgentFunctionalCaseSubmitService`
  - `TestPlanFunctionalCaseService`
  - `metersphere.functional.submit`
  - `submit_functional_results_batch`
  - `upload_execution_attachment`
- 批量回写必须逐条幂等，支持 `executionTaskId` 和幂等键。
- 单条失败不回滚已成功项，任务必须标记 `PARTIAL_SUCCESS` 并列出失败项。
- 回写内容包括步骤结果、最终结果、实际结果、评论/说明、附件和执行历史。

## 不应实现的内容

- 不允许直接 Mapper 插入或更新正式执行结果。
- 不允许浏览器操作成功但回写失败时显示成功。
- 不允许把未执行步骤写成通过。

## 验收标准

- 计划内结果进入测试计划执行记录与报告，并同步用例最近结果。
- 计划外结果更新功能用例最近结果、实际结果、Agent 日志和附件。
- 回写失败任务准确显示 `PARTIAL_SUCCESS` 或 `FAILED`。
- 重复回写请求不会产生重复执行记录或重复附件关联。

## 验证要求

- 计划内回写集成测试。
- 计划外回写集成测试。
- 批量部分失败测试。
- 幂等重复提交测试。
- 附件关联验证。
