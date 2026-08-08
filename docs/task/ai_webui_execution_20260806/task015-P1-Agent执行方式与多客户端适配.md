# task015 - P1 - Agent 执行方式与多客户端适配

## 目标

在原有 Browser Runner 执行方式之外，允许用户在 AI 自动化执行入口选择 Agent 执行，并首批支持 WorkBuddy、Cursor、Codex。

## 已实现

- 执行任务新增 `executionMode`、`agentType`、`agentGatewayId`，历史任务默认使用 `RUNNER`。
- Agent Gateway 配置新增 `agentType`，只接受 `WORKBUDDY`、`CURSOR`、`CODEX`。
- 新增 `GET /ai/execution/agents?projectId=...`，按用户与项目权限返回三个 Agent 的真实可用状态。
- 用例列表执行弹窗和自然语言执行工作台均支持选择“平台 Runner / Agent 执行”。
- 未配置或无权访问的 Agent 在前端显示为“未配置”并禁用，后端创建任务时再次校验。
- Agent 任务落库并提交事务后，通过异步事件调用对应 Gateway 的 `metersphere.webui.execute` 操作。
- 分发成功后任务进入 `PREPARING_BROWSER` 并记录 `AGENT_DISPATCH_ACCEPTED`；失败则进入 `FAILED` 并记录 `AGENT_DISPATCH_FAILED`。
- 确认范围和失败重试会重新触发 Agent 分发。

## Gateway 契约

管理员通过现有 `POST /ai/agent-gateway` 接口配置 Gateway，并设置：

- `agentType`: `WORKBUDDY`、`CURSOR` 或 `CODEX`
- `protocol`: `MCP` 或 `CUSTOM_HTTP`
- `capabilities`: 为空表示不限制；非空时必须包含 `metersphere.webui.execute`
- `projectId` / `organizationId` / `personal`: 决定连接可见范围

分发上下文包含 `executionTaskId`、`projectId`、`agentType`、目标 URL、环境、浏览器、登录模式，以及带步骤快照的用例列表。凭据明文不进入上下文。

## 验收结果

- 后端全依赖链编译通过。
- `AiAgentGatewayServiceTests`: 3/3 通过。
- `AgentExecutionModeTests`: 1/1 通过。
- 前端 `vue-tsc --noEmit --skipLibCheck`: 通过。
- 定向 ESLint: 0 error（保留原文件既有 warning）。

## 后续边界

Agent Gateway 必须具备可被服务端调用的 MCP 或 Custom HTTP 端点。桌面端产品名称、Deep Link 或本地 CLI 不会被服务端直接执行；这样可避免服务器任意命令执行、不可审计调用和假成功。
