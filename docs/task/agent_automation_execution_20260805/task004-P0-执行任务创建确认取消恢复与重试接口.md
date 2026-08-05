# task004 - P0 - 执行任务创建、确认、取消、恢复与重试接口

## 状态

基础实现完成，待接口联调与单测

## 执行记录（2026-08-05）

- 已新增平台接口：`resolve`、`task create/get/events/confirm/login-ready/cancel/retry`。
- 已实现创建幂等键：`project_id + create_user + idempotency_key`。
- 已实现大范围任务 `WAITING_CONFIRMATION`、确认后 `PREPARING_BROWSER`、登录恢复后 `RUNNING`、取消 `CANCELED`、失败/阻塞重试。
- 已实现追加事件日志和任务统计返回。
- 已验证：后端编译通过。
- 未完整实现：无 Runner/凭据时自动进入 `WAITING_LOGIN` 的完整判断未接入；接口权限已加 Shiro 注解，但尚未使用真实角色账号逐接口验证。

## 目标

提供 AI 自动化执行任务的平台接口，支持范围解析、任务创建、任务查询、确认、人工登录恢复、取消和重试。

## 实现范围

- 新增或等效实现接口：
  - `POST /ai/execution/resolve`
  - `POST /ai/execution/task`
  - `GET /ai/execution/task/{id}`
  - `GET /ai/execution/task/{id}/events`
  - `POST /ai/execution/task/{id}/confirm`
  - `POST /ai/execution/task/{id}/login-ready`
  - `POST /ai/execution/task/{id}/cancel`
  - `POST /ai/execution/task/{id}/retry`
- 创建任务必须支持幂等键，重复提交不得重复创建或重复执行。
- 创建任务时前端或 MCP 只传稳定的 `caseId`、`testPlanId` 或确认后的范围快照，后端重新校验项目、权限、删除状态和数量限制。
- 确认接口用于大范围、高风险、人工登录、会话接管等场景。
- 取消接口必须幂等，已完成结果保留，未执行项不得写成成功。
- 重试接口只允许重试失败或阻塞用例。

## 不应实现的内容

- 不允许创建任务后绕过权限直接执行。
- 不允许客户端传入的结果范围不经后端复核直接落库。
- 不允许任务取消后继续写入未授权结果。

## 验收标准

- 所有接口均执行项目权限、功能用例读取权限和 AI 执行权限校验。
- 大范围或高风险任务进入 `WAITING_CONFIRMATION`。
- 无会话且无凭据时可进入 `WAITING_LOGIN` 并等待恢复。
- 取消、恢复、重试均记录审计事件。
- 接口返回的统计与 `ai_execution_case` 状态一致。

## 验证要求

- Controller / Service 单元测试。
- 幂等创建测试。
- 权限越权测试。
- 取消、恢复、重试状态流转测试。
