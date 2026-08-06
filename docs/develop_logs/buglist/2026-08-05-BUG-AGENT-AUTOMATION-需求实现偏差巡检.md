# 2026-08-05 BUG-AGENT-AUTOMATION - Agent 自动化执行需求实现偏差巡检

## 文档目的

对照以下文档与当前代码，记录实现偏差。本文不是完成证明。凡未完成、未验证、部分实现内容均按偏差或风险记录，后续修复前不得标记为已完成。

- 需求方案：`docs/summary/MeterSphere_Agent自动化执行改造方案.md`
- 任务目录：`docs/task/agent_automation_execution_20260805/`

## 巡检范围

- 前端：
  - `frontend/src/views/bug-management/automationExecution/index.vue`
  - `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`
  - `frontend/src/router/routes/modules/bugManagement.ts`
  - `frontend/src/api/modules/ai-execution.ts`
- 后端：
  - `backend/services/agent-integration/.../AgentExecutionService.java`
  - `backend/services/agent-integration/.../AgentExecutionController.java`
  - `backend/services/agent-integration/.../AgentTestPlanQueryService.java`
  - `backend/services/agent-integration/.../AgentFunctionalCaseSubmitService.java`
  - `backend/services/agent-integration/.../BuiltinAgentMcpToolConfig.java`
- 数据迁移：
  - `V3.7.2_24__ai_execution_task.sql`
  - `V3.7.2_24__ai_execution_permissions.sql`
  - `V3.7.2_27__ai_execution_writeback_idempotency.sql`
- MCP 包：`metersphere-mcp/src/tools/testPlanExecution.ts`

## 总体结论

当前实现可认定为「一期 MCP/API 编排骨架 + 二期工作台半成品」。

已具备：独立 AI 执行权限常量与默认角色 DML、任务/用例快照/事件/Runner 会话/凭据引用表结构、范围解析与任务 CRUD 接口、MCP Tools 扩展、计划内/计划外回写复用、回写幂等表、用例列表【AI执行】入口、自动化执行左右栏工作台与事件轮询。

与方案完整闭环差距仍然很大，尤其是：**真正的浏览器 Runner、会话接管、凭据注入、高风险治理、SSE 实时流、证据 HAR/视频、完整状态机与验收证据**。当前不能宣称“Agent 自动化执行需求已完整实现”。

## 偏差清单

| 编号 | 优先级 | 需求 / 方案要求 | 当前实现事实 | 偏差与影响 | 建议处理 |
| --- | --- | --- | --- | --- | --- |
| AGENT-AUTO-001 | P0 | 未指定项目时只提示补充，不猜测、不执行。 | `resolve` 在缺 `projectId` 时返回确认提示且 `executable=false`。 | 方向正确；自然语言项目名解析仍弱，主要依赖显式 ID/字段。 | 补项目名称/编号候选匹配与歧义确认单测。 |
| AGENT-AUTO-002 | P0 | 仅指定项目时，按 7 条规则优先选可执行计划。 | 排除归档、可执行状态白名单、关联用例>0、进行中/最近更新排序；多计划返回候选。 | 未完整校验“当前用户/Token 对计划的执行权限”；重复执行风险提示仍弱。 | 补计划执行权限过滤与重复执行风险提示。 |
| AGENT-AUTO-003 | P0 | 无可执行计划时，降级项目全部有效用例且必须确认。 | 已返回确认原因、数量、预计耗时与高风险信号；工作台可预览。 | 自然语言复杂意图仍弱。 | 持续增强解析。 |
| AGENT-AUTO-004 | P0 | 状态机含 `WAITING_LOGIN` / `WRITING_BACK` / `SUCCESS` 等。 | 创建/确认后无 Runner 自动 `WAITING_LOGIN`；回写进 `WRITING_BACK`；`PAUSED` 已加。 | `RESOLVING_SCOPE` 仍少用；真实 Runner 附着未实现。 | 三期接 Runner 后补 RUNNING 真实流转。 |
| AGENT-AUTO-005 | P0 | 外部 Agent 仅通过 MCP/API，规则集中在后端。 | MCP Tools 与后端编排已落地；客户端薄封装。 | 缺真实 Token Scope/限流/越权联调证据。 | 补 MCP 端到端与越权测试。 |
| AGENT-AUTO-006 | P0 | 新增 MCP execution/plan tools。 | 平台内置与 `metersphere-mcp` 均已注册。 | 缺真实调用验收记录。 | 补联调证据。 |
| AGENT-AUTO-007 | P0 | 计划内/计划外回写；单条失败 `PARTIAL_SUCCESS`。 | 已复用现有回写链路。 | 缺集成测试；无幂等键时不去重。 | 强制幂等键；补测试。 |
| AGENT-AUTO-008 | P0 | 批量回写幂等。 | 已有 `ai_execution_writeback_idempotency`。 | 缺迁移实跑与回归证据。 | 跑迁移并补测试。 |
| AGENT-AUTO-009 | P0 | 用例列表【AI执行】确认弹窗字段完整。 | 已采集环境/地址/浏览器/登录；超阈值需勾选确认。 | 默认计划外；高风险主要靠后端关键词。 | 可选候选计划。 |
| AGENT-AUTO-010 | P0 | 跨页勾选基于稳定 caseId。 | 全选全部结果直接阻断。 | 无法对筛选全集执行。 | 可选后端按筛选快照。 |
| AGENT-AUTO-011 | P0 | `AI_EXECUTION:*` 角色配置可见。 | `permission.json` + i18n 已补。 | 缺角色配置页联调证据。 | 环境验证。 |
| AGENT-AUTO-012 | P1 | 工作台对话驱动 resolve/create；暂停；连接 AI。 | resolve 预览、创建、暂停、模型下拉已接。 | 无 OAuth/测试连接；无真正 LLM 编排。 | 二期增强连接态。 |
| AGENT-AUTO-013 | P1 | 右侧实时画面/步骤树。 | 仅有任务描述、用例表、事件列表。 | 无步骤树/截图区。 | 依赖 Runner。 |
| AGENT-AUTO-014 | P1 | SSE/WebSocket 事件订阅。 | HTTP 游标 + 3s 轮询。 | 非实时推送。 | 补 SSE/WS。 |
| AGENT-AUTO-015 | P1 | Browser/Desktop Runner。 | 仅有会话表与 login-ready。 | 无法真实执行页面。 | 三期实现。 |
| AGENT-AUTO-016 | P2 | 凭据引用、域名白名单、高风险治理。 | 用例名高风险关键词确认已接；表占位存在。 | 无凭据注入/白名单。 | 四期治理。 |
| AGENT-AUTO-017 | P2 | 截图/视频/HAR 与保留策略。 | SUCCESS 对账强制证据事件，采集未实现。 | 证据治理未落地。 | 补采集与策略。 |
| AGENT-AUTO-018 | P0 | 关键动作可审计。 | 任务事件 + `agent_exec_log` 审计已接关键动作。 | 未完全接入平台 OperationLog 审计中心。 | 可选增强。 |
| AGENT-AUTO-019 | P0 | 证据+回写才可 SUCCESS。 | 无证据或回写不足时强制 `PARTIAL_SUCCESS`。 | 证据采集未实现，当前几乎总会 PARTIAL。 | 接 Runner/附件事件。 |
| AGENT-AUTO-020 | P0 | 验收清单与联调证据。 | 任务文档诚实记录部分完成。 | 方案第 15 节多数未实测。 | 隔离环境闭环验收。 |

## 代码证据摘要

### 已对齐部分

- 路由 `/bug-management/automation-execution` + `AI_EXECUTION:READ`。
- 表结构覆盖方案第 10 节五张核心表，并额外有回写幂等表。
- 平台接口与方案第 11 节基本一致。
- MCP Tools 与方案第 7.2 节基本一致。
- 回写复用既有领域服务，符合“以复用现有为荣”。

### 明显未对齐部分

- 无真实 Browser/Desktop Runner 执行层（方案架构图中的 B）。
- 无 SSE/WebSocket（方案 9.1）。
- 无凭据注入与域名白名单（方案 8/12）；高风险仅有用例名关键词确认。
- 工作台已可 resolve/create，但仍非完整 LLM「连接 AI」编排。
- 证据采集未实现，完成判定会诚实落到 `PARTIAL_SUCCESS`。

## 2026-08-06 已关闭/收敛的偏差

- AGENT-AUTO-011 permission.json / i18n。
- AGENT-AUTO-009 确认弹窗字段与超阈值 confirmed 语义。
- AGENT-AUTO-004 状态机关键自动流转（WAITING_LOGIN / WRITING_BACK / PAUSED）。
- AGENT-AUTO-019 SUCCESS 证据与回写对账（无证据则 PARTIAL_SUCCESS）。
- AGENT-AUTO-012 工作台 resolve/create/pause（OAuth/Runner 仍开放）。

## 建议修复顺序

### 第一阶段：P0 可信闭环

1. 补 `permission.json` / 角色配置可见性。
2. 完善计划选择权限过滤与确认预览。
3. 修正用例列表确认弹窗字段；避免无脑 `confirmed:true`。
4. 状态机自动进入 `WAITING_LOGIN` / `WRITING_BACK`，`SUCCESS` 前做回写与证据对账。
5. 幂等迁移实跑 + 回写重复提交测试。
6. MCP/接口越权与端到端联调证据。

### 第二阶段：工作台与实时性（二期）

1. 对话驱动 `resolve/create`。
2. 共享 Provider Selector / 连接态。
3. SSE/WebSocket 事件流。
4. 步骤树与回写状态面板。

### 第三阶段：Runner（三期）

1. Runner 协议、租约、任务短令牌。
2. 会话授权接管与人工登录恢复真实链路。
3. 截图回流到工作台画面区。

### 第四阶段：治理（四期）

1. 凭据引用注入、域名白名单、高风险默认禁止。
2. 视频/HAR/容量保留策略。

## 当前不可宣称完成的内容

- 不能宣称已实现受控浏览器自动化执行。
- 不能宣称已实现会话接管与凭据安全登录。
- 不能宣称 SSE 实时日志已完成。
- 不能宣称高风险治理与证据策略已完成。
- 不能宣称方案第 15 节验收清单已通过。
- 不能将“接口和页面骨架存在”表述为“需求已完成”。

## 与任务文档一致性说明

`task000` 当前“部分完成/未开始”划分与本次巡检结论一致。本文进一步把方案级验收偏差拆成可跟踪条目（AGENT-AUTO-001～020），供后续按阶段关闭。
