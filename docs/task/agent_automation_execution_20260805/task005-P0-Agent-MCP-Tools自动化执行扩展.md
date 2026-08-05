# task005 - P0 - Agent MCP Tools 自动化执行扩展

## 状态

已实现，待真实 MCP 调用联调

## 执行记录（2026-08-05）

- 已在后端内置 MCP 注册：
  - `metersphere.test_plan.search`
  - `metersphere.test_plan.cases`
  - `metersphere.execution.resolve`
  - `metersphere.execution.create`
  - `metersphere.execution.get`
  - `metersphere.execution.events`
  - `metersphere.execution.cancel`
  - `metersphere.execution.resume`
- 已按方案补充 Scope：`PLAN_READ`、`AI_EXECUTION_READ/RUN/CANCEL/LOGIN/ADMIN`，并补继承关系。
- 已同步独立 `metersphere-mcp` 包，新增同名 `metersphere.*` 工具并保留旧 snake_case 工具，避免破坏已有客户端。
- 已验证：后端编译通过；`metersphere-mcp npm run build` 通过。
- 未完成验证：尚未用 Cursor/Claude Desktop 真实 MCP 客户端调用验证工具 schema 和 token scope 拦截。

## 目标

扩展 MeterSphere MCP 工具集，使外部 Agent 能通过授权工具完成测试计划检索、计划用例获取、执行任务创建、状态查询、事件读取、取消和恢复。

## 实现范围

- 新增 MCP Tools：
  - `metersphere.test_plan.search`
  - `metersphere.test_plan.cases`
  - `metersphere.execution.create`
  - `metersphere.execution.get`
  - `metersphere.execution.events`
  - `metersphere.execution.cancel`
  - `metersphere.execution.resume`
- Tool 权限映射：
  - `metersphere.test_plan.search` → `PLAN_READ`
  - `metersphere.test_plan.cases` → `FUNCTIONAL_READ`
  - `metersphere.execution.create` → `AI_EXECUTION_RUN`
  - `metersphere.execution.get` / `events` → `AI_EXECUTION_READ`
  - `metersphere.execution.cancel` / `resume` → `AI_EXECUTION_RUN`
- MCP 参数 schema 必须明确项目、计划、用例、分页、游标、幂等键等字段。
- MCP 实现保持薄封装，只调用 MeterSphere 后端服务，不承载业务选择规则。
- 扩展 Agent Token Scope、限流、幂等记录和审计。

## 不应实现的内容

- 不允许 MCP Tool 直接访问数据库。
- 不允许 MCP Tool 自行随机选择计划或用例。
- 不允许把敏感凭据、Cookie、Token 返回给模型。

## 验收标准

- 外部 Agent 可通过 MCP 查询候选计划和计划用例。
- 外部 Agent 可创建任务并获取任务状态、统计、待处理动作和事件。
- 外部 Agent 只能访问 Token Scope 授权范围内的数据。
- Tool 输入错误、权限不足、范围歧义时返回明确错误或确认要求。

## 验证要求

- MCP Tool schema 校验。
- Token Scope 权限测试。
- 计划检索和计划用例分页测试。
- execution create/get/events/cancel/resume 集成测试。
