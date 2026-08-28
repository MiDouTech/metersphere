# MeterSphere 与 Agent 连接协作冗余剥离与冲突分析

## 1. 文档目的

本文用于为 MeterSphere 与外部 Agent/Codex Runner 连接协作能力的后续剥离、削减和协议收敛提供依据。

本文不直接要求删除现有代码，而是完成以下工作：

1. 明确当前已经存在的连接、任务和执行通道。
2. 区分必要能力、按需能力、不必要能力和重复能力。
3. 识别冗余能力与必要主链路之间已经存在或可能产生的行为冲突。
4. 给出目标架构、保留边界、分阶段剥离顺序和验收条件。

本文基于当前目标进行判断：

> MeterSphere 创建和管理测试任务；Codex/Agent Runner 可以定时扫描、人工拉取或在平台通知后领取任务；Runner 独立执行任务，并向 MeterSphere 写回状态、事件、结果和证据。

如果未来仍需支持“普通用户将个人电脑上的 Codex/Cursor 订阅作为交互式 AI 资源接入 MeterSphere”，该能力应作为独立的 BYO Desktop Agent 产品边界维护，不应继续作为测试任务执行的默认通道。

## 2. 当前能力地图

### 2.1 任务与 Runner 主链路

当前 `agent-integration` 已实现或部分实现以下能力：

- Agent Token、Runner Token、Scope、项目访问控制和限流。
- Remote Streamable HTTP MCP。
- 测试任务创建、查询、状态机、暂停、恢复和取消。
- 待执行任务搜索、能力匹配和原子领取。
- Lease Token、Lease 心跳、超时和释放。
- 冻结上下文、上下文 Hash 和固定资产版本。
- 批量事件、步骤结果、执行结果、证据和附件。
- 人工介入请求与响应。
- Runner 注册、能力声明、Runner 心跳和执行评价。
- Cron、签名 Webhook 和人工触发。

主要实现位置：

- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentTaskController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentRunnerInternalController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentExecutionController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentMcpStreamableController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentTaskClaimService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionStateMachine.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionContextService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionArtifactService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentTaskTriggerService.java`

### 2.2 Agent Bridge 通道

当前 `agent-bridge` 和 `system-setting` 中还存在另一套以个人设备为中心的通道：

- 一次性配对码和设备身份。
- Bridge Challenge/Authenticate。
- Bridge WSS 常驻连接、心跳和断线重连。
- Redis 多节点设备路由和跨节点消息总线。
- 平台向指定设备发送 `execution.start`、`execution.cancel`。
- Bridge 在用户设备上启动 `codex exec --json` 或其他 Provider CLI。
- CLI JSONL 到平台流事件的转换。
- 外部会话 ID 和 CLI Resume。
- 平台触发本地 Provider 登录。
- Bridge 安装包上传、激活、下载和版本检查。
- 个人中心设备配对、在线状态和安装引导。

主要实现位置：

- `agent-bridge/src/main.mjs`
- `agent-bridge/src/providers.mjs`
- `agent-bridge/src/protocol-handler.mjs`
- `backend/services/system-setting/src/main/java/io/metersphere/system/service/ai/agent/bridge/AgentBridgeSessionRegistry.java`
- `backend/services/system-setting/src/main/java/io/metersphere/system/service/ai/agent/BridgeUserAgentConnector.java`
- `backend/services/system-setting/src/main/java/io/metersphere/system/controller/AiUserAgentController.java`
- `backend/services/system-setting/src/main/java/io/metersphere/system/controller/AiAgentBridgePackageController.java`
- `frontend/src/components/business/ms-personal-drawer/components/userAgent.vue`

### 2.3 其他执行与数据通道

当前还存在：

- 独立 Node 版 `metersphere-mcp`。
- 后端原生 Remote MCP。
- Agent REST API。
- `ai-browser-runner` 浏览器执行器。
- AI 模型 API/用户 Agent 双资源选择和交互式用例生成链路。

这些能力并非全部冗余，但需要明确“协议入口可以多个，业务事实和状态机只能有一个”。

## 3. 必要能力及保留边界

| 能力 | 结论 | 保留边界 |
| --- | --- | --- |
| Agent/Runner 身份认证 | 必要 | 人工 MCP 客户端使用 Agent Token；无人值守 Runner 使用 Runner Credential。 |
| 项目 Scope 与权限 | 必要 | 所有查询、领取、上下文和写回均执行相同项目权限校验。 |
| 统一测试任务模型 | 必要 | 任务状态、业务结论和执行记录以统一模型为唯一事实来源。 |
| 原子 Claim | 必要 | 任一任务同一时刻最多存在一个有效执行租约。 |
| Lease、心跳和回收 | 必要 | Runner 崩溃、网络中断和超时后可以安全重试。 |
| 冻结上下文 | 必要 | 执行输入绑定固定资产版本和 Hash，避免重跑漂移。 |
| MCP/Runner API | 必要 | 提供任务领取、业务数据读取和结果写回能力。 |
| 业务级执行事件 | 必要 | 只持久化任务、用例、步骤、人工介入和结果等业务事件。 |
| 结果、证据和附件 | 必要 | 所有正式执行结论必须可追溯到任务、用例和步骤。 |
| 取消与人工介入 | 必要 | 平台可以表达控制意图，Runner 通过控制查询或通知获取并执行。 |
| Cron/Webhook/人工触发 | 必要 | 只负责创建任务或发出“有新任务”通知，不直接绕过 Claim 启动执行。 |
| Codex CLI 执行适配 | 必要 | 属于 Runner 内部执行器，不属于 MeterSphere 任务协议。 |
| 进程隔离、并发、超时和取消 | 必要 | 由 Runner 负责，不由业务后端直接管理本地 PID。 |

## 4. 按需能力

| 能力 | 保留条件 | 不满足条件时的处理 |
| --- | --- | --- |
| Browser Runner | 存在独立浏览器资源池、浏览器安全隔离或非 Codex 执行需求 | 合并为 Codex Runner 的 `browser/playwright` 能力。 |
| 实时进度推送 | UI 必须低延迟展示步骤状态 | 使用业务事件/SSE，不传输完整 Codex 原始事件流。 |
| Runner 注册中心 | 存在多个异构 Runner 和动态能力调度 | 固定单 Runner 场景可使用静态配置。 |
| 执行评价和自动准入 | 已有足够样本、成本和一致性指标 | 在样本不足时只展示，不自动封禁或路由。 |
| 即时唤起通知 | 轮询延迟不能接受 | 使用消息队列或轻量 Webhook 通知，最终仍走 Claim。 |
| BYO Desktop Agent | 明确支持个人电脑、个人订阅和 NAT 后设备 | 与测试任务 Runner 分产品边界、分开 Feature Flag 和协议。 |
| CLI 会话 Resume | 明确是连续对话而非可复现测试执行 | 测试任务默认禁用。 |

## 5. 不必要内容与建议处理

### 5.1 WSS 作为测试任务主下发通道

**当前行为**：平台通过 WSS 向指定 Bridge 设备发送 `execution.start`，设备立即启动 Provider CLI。

**判断**：对测试任务主链路不必要。

**原因**：现有任务搜索、Claim 和 Lease 已经能完成任务分配。WSS 再次承担下发会形成第二套执行入口。

**建议**：

- 测试任务只允许通过 Claim 获得执行权。
- 即时唤起只发送 task-available 通知，不携带执行授权。
- Runner 收到通知后仍调用 Claim。
- WSS 仅在 BYO Desktop Agent 产品边界内保留。

### 5.2 Redis Bridge 设备路由和跨节点消息总线

**当前行为**：Redis 保存设备所在 Gateway 节点，并转发 WSS 上下行消息。

**判断**：对 Pull/Claim Runner 完全不必要。

**建议**：

- 测试任务调度使用数据库租约或消息队列消费者组。
- 不再为测试任务定位特定 WSS 会话。
- 如果保留 BYO Desktop Agent，则 Redis 路由只能服务该独立通道。

### 5.3 Bridge 设备配对、挑战认证与在线状态

**判断**：中央部署或受控 Runner 不需要；个人设备场景按需保留。

**建议**：

- 受控 Runner 使用 Runner Credential 注册和心跳。
- Agent MCP 客户端使用个人 Agent Token。
- 不使用 Bridge Device Token 访问任务协议。

### 5.4 Bridge 安装包管理和个人中心安装引导

**判断**：对集中部署 Runner 不必要。

**建议**：

- Runner 使用 Docker、Kubernetes、CI Runner 或企业终端管理系统部署。
- Bridge 包管理迁移到独立 BYO Desktop Agent 模块，或在该产品方向取消后删除。

### 5.5 平台发起本地 `codex login`

**判断**：对无人值守 Runner 不必要。

**建议**：

- 登录和凭据初始化属于部署运维步骤。
- MeterSphere 只接收 `READY`、`AUTH_EXPIRED`、`QUOTA_EXCEEDED` 等脱敏状态。
- 只有个人设备交互式授权场景允许打开本地登录 UI。

### 5.6 测试任务默认恢复 Codex 历史会话

**判断**：不必要且影响可复现性。

**建议**：

- 每个 executionId 创建独立 Codex 执行。
- 重试使用同一冻结上下文，但创建新的 attempt/executionId。
- 仅连续对话场景使用 CLI Resume。

### 5.7 Codex 原始流事件长期落库

**判断**：不必要。

**建议**：

- 原始 JSONL 仅用于 Runner 本地诊断，或脱敏后作为限期附件上传。
- MeterSphere 持久化业务级事件，不持久化 Token 级增量和内部推理过程。

## 6. 明确冗余内容

### 6.1 Runner Pull 与 Bridge Push 双任务通道

这是当前最严重的架构冗余。

两套通道都可以触发同一类 Agent 执行：

```text
通道 A：Runner → Search → Claim → Execute
通道 B：Platform → WSS execution.start → Bridge → Execute
```

必须收敛为：

```text
任务执行权唯一来源 = Claim 成功产生的有效 Lease
```

任何 Webhook、WSS、消息队列或人工操作都只能创建任务或通知 Runner，不能直接授予执行权。

### 6.2 `AgentTaskController` 与 `AgentRunnerInternalController` 重复

当前两处都包含 Lease 心跳、事件上报、状态和附件相关接口。

**建议边界**：

- `/runners/*`：Runner 注册、Runner 自身心跳、能力和健康状态。
- `/tasks/*`：任务搜索、Claim、Lease 心跳、控制、事件、附件、完成和释放。
- 删除或废弃重复的 Internal Lease 接口。

### 6.3 REST、后端 Remote MCP 与 Node MCP 重复

**允许存在的差异**：协议表现层可以有 REST 和 MCP。

**不允许存在的差异**：权限、幂等、状态机、字段校验和写回逻辑分别实现。

**建议**：

```text
REST Controller ─┐
MCP Tool ────────┼→ 同一个 Application Service → 同一个领域状态机
Runner API ──────┘
```

`metersphere-mcp` Node 工程只作为兼容客户端或开发代理，不再持有独立业务规则。待后端 Remote MCP 覆盖验证完成后决定归档或删除。

### 6.4 Agent Token、Runner Token 与 Bridge Device Token 重叠

**建议身份域**：

- Agent Token：有人值守的 MCP/CLI 客户端。
- Runner Credential：无人值守服务和执行器。
- Bridge Device Token：仅用于 BYO Desktop Agent WSS，不具有通用任务访问权。

如果取消 BYO Desktop Agent，Bridge Device Token 及相关数据模型应整体删除，而不是与 Runner Token 合并。

### 6.5 Codex 原始事件、执行日志、业务事件和步骤结果重复

建议分为三层：

1. **原始运行日志**：Runner 本地诊断数据，短期保存。
2. **业务执行事件**：MeterSphere 持久化的状态变化和关键节点。
3. **正式执行结果**：用例、步骤、结论、证据和附件。

同一文本不应同时作为 `content.delta`、执行日志、事件和结果正文重复存储。

### 6.6 Browser Runner 与 Codex Runner 的执行职责重叠

Browser Runner 不应拥有另一套任务生命周期。

**建议**：

- 复用统一任务、Claim、Lease、事件和结果协议。
- Browser Runner 只声明浏览器能力并实现浏览器执行。
- 如果 Codex Runner 已提供等价的受控 Playwright 能力，则评估合并执行器。

## 7. 冗余能力与必要主链路的冲突

### 7.1 执行权冲突

**冲突双方**：Bridge `execution.start` 与 Task Claim。

**风险**：

- 同一任务被 Push 和 Pull 同时执行。
- Bridge 已启动进程，但平台没有有效 Lease。
- Lease 已被另一个 Runner 获取，WSS 仍启动指定设备。
- 无法明确哪一个执行结果有效。

**约束**：任何执行器启动前必须持有有效 Lease；平台 Push 不得绕过 Claim。

### 7.2 状态事实来源冲突

**冲突双方**：Bridge 在线/执行状态、Runner 状态、统一任务状态机。

**风险**：

- Bridge 进程在线被误认为 Agent 可执行。
- CLI 进程启动被误认为任务已运行成功。
- WSS `execution.completed` 与任务最终写回顺序不一致。
- Runner OFFLINE、Bridge ONLINE 等状态在 UI 中互相矛盾。

**约束**：

- Runner 健康状态只表示执行器可用性。
- 任务状态只由统一状态机维护。
- Provider/Bridge 状态只能作为诊断信息，不能直接推进任务终态。

### 7.3 取消语义冲突

**冲突双方**：WSS `execution.cancel`、任务控制接口、Lease 回收。

**风险**：

- 平台任务已取消，但本地进程继续运行。
- WSS 断线导致取消命令丢失。
- 本地进程被终止，但任务仍为 RUNNING。
- 取消和完成竞态导致终态覆盖。

**约束**：

- 平台先记录取消意图和状态版本。
- Runner 通过控制轮询或可靠通知接收取消。
- Runner 终止进程后回报取消确认。
- 状态机使用版本/CAS 阻止终态互相覆盖。

### 7.4 重试与幂等冲突

**冲突双方**：Bridge 断线重连/重新发送、Runner Lease 重试、触发器幂等。

**风险**：同一 taskId/requestId 创建多个本地进程或多个正式结果。

**约束**：

- taskId 标识业务任务。
- executionId 标识一次执行。
- attempt 标识重试次数。
- leaseId 标识本次执行授权。
- idempotencyKey 标识写操作去重。
- requestId 不得同时承担以上全部语义。

### 7.5 会话状态与冻结上下文冲突

**冲突双方**：Codex Resume 与任务上下文快照。

**风险**：历史会话中包含快照之外的信息，导致相同任务输入得到不可审计的执行行为。

**约束**：测试任务禁止默认 Resume；连续聊天必须标记为独立 execution mode。

### 7.6 权限冲突

**冲突双方**：Agent Token Scope、Runner Credential、Bridge 用户/设备绑定。

**风险**：

- 设备配对用户与任务所属项目用户不一致。
- Bridge 凭证间接获得超出 Agent Token Scope 的数据。
- Runner 使用个人凭证执行无人值守任务。
- 撤销一类凭证后另一类通道仍可访问。

**约束**：任务上下文和写回权限由平台按任务与 Lease 决定；Provider 登录只决定能否运行 Codex，不授予 MeterSphere 项目权限。

### 7.7 事件顺序和重复存储冲突

**冲突双方**：Bridge sequence、Runner 批量事件、执行日志和正式结果。

**风险**：

- 两套 sequence 无法全局排序。
- 重连重放导致重复事件。
- Token 增量拼接结果与正式结果不一致。
- 日志、事件和结果保存多份敏感信息。

**约束**：业务事件以 executionId + eventSequence 唯一；原始 Provider sequence 只作为诊断元数据。

### 7.8 触发器与执行器职责冲突

**冲突双方**：Cron/Webhook 直接启动 Agent 与任务队列。

**风险**：触发历史成功但任务未创建，或 Agent 已启动但平台无任务记录。

**约束**：触发成功的定义是“成功创建或幂等命中任务”；执行成功由任务状态机单独判断。

### 7.9 配置与功能开关冲突

**冲突双方**：Bridge Feature Flag、Provider Flag、Runner 能力和任务路由规则。

**风险**：关闭 Bridge 后任务仍被路由至桌面 Agent，或 Provider 标记可用但 Runner 不具备相应能力。

**约束**：任务调度只读取 Runner 能力；Bridge/Provider Flag 只影响 BYO Desktop Agent 资源列表。

## 8. 目标架构

```text
┌─────────────────────────────────────────────────────┐
│ MeterSphere                                         │
│                                                     │
│ Trigger → Unified Task → Context Snapshot           │
│                       ↓                             │
│               Search / Atomic Claim                 │
│                       ↓                             │
│     Lease / Control / Events / Result / Artifact    │
└──────────────────────────┬──────────────────────────┘
                           │ MCP / Runner API
                           │
┌──────────────────────────▼──────────────────────────┐
│ Codex Runner                                        │
│                                                     │
│ Register → Poll/Notify → Claim → codex exec --json  │
│                         ↓                           │
│ Process isolation / timeout / cancellation          │
│                         ↓                           │
│ Business events / evidence / final result           │
└─────────────────────────────────────────────────────┘
```

可选即时通知：

```text
MeterSphere → Queue/Webhook → Runner receives task hint → Claim
```

可选 BYO Desktop Agent 必须与主链路隔离：

```text
MeterSphere Interactive AI → WSS Bridge → Personal Provider CLI
```

它不得直接执行统一测试任务，除非先以 Runner 身份 Claim 并取得 Lease。

## 9. 分阶段剥离计划

### 阶段 0：冻结扩展与建立观测

- 暂停新增 Bridge 任务协议和 Bridge 包管理功能。
- 为所有执行记录 `dispatchChannel`：`RUNNER_PULL`、`RUNNER_NOTIFY`、`BRIDGE_PUSH`。
- 统计各通道真实调用量、成功率、重复任务和取消失败。
- 建立 taskId/executionId/attempt/leaseId 的统一日志字段。

**完成条件**：可以确认生产环境是否仍有真实 Bridge Push 用户和依赖方。

### 阶段 1：确立 Claim 为唯一执行授权

- 所有 Runner 和 Bridge 执行前必须 Claim。
- `execution.start` 降级为 task-available 通知。
- 未携带有效 Lease 的事件、结果和附件全部拒绝。
- 增加重复执行和终态覆盖测试。

**完成条件**：不存在绕过 Lease 启动并写回正式结果的路径。

### 阶段 2：收敛 Runner API

- 合并 `AgentTaskController` 与 `AgentRunnerInternalController` 的重复 Lease 接口。
- Runner 自身心跳与任务 Lease 心跳分离。
- MCP Tool、REST Controller 和 Runner API 复用同一应用服务。
- 固化错误码、幂等和状态转换契约。

**完成条件**：相同业务操作只有一个服务实现和一组状态规则。

### 阶段 3：收敛事件与数据通道

- 定义业务事件白名单。
- 停止长期保存 Token 级 `content.delta`。
- 原始 Codex JSONL 改为短期诊断附件。
- 评估并归档 Node `metersphere-mcp` 中已由后端 Remote MCP 覆盖的逻辑。

**完成条件**：任务详情中的状态、事件和最终结果不存在互相矛盾的数据来源。

### 阶段 4：隔离或下线 BYO Desktop Agent

- 若保留：拆分模块、路由、Feature Flag、UI 和权限域，并明确其仅服务交互式 AI。
- 若下线：依次停用 WSS Push、Redis 路由、设备配对、Bridge 包管理和个人中心安装引导。
- 删除前提供设备和连接数据导出、迁移或失效说明。

**完成条件**：测试任务主链路不依赖 `agent-bridge` 和 `system-setting` Bridge 模块。

### 阶段 5：Runner 与 Browser Runner 统一

- Browser Runner 接入统一 Claim/Lease/事件/结果协议。
- 对比 Codex Runner 内置 Playwright 与独立 Browser Runner 的隔离和资源能力。
- 能力等价时合并执行器；不等价时保留独立 Runner 类型。

**完成条件**：不存在第二套浏览器任务状态机和结果模型。

## 10. 删除前依赖检查

删除或停用任一 Bridge 模块前，必须确认：

- 是否仍有前端页面调用对应接口。
- 是否仍有数据库表、权限迁移和菜单依赖。
- 是否有真实设备在线或历史会话需要查询。
- 是否有 Feature Flag、配置项、部署文件和环境变量引用。
- 是否有自动化测试把 Bridge 作为默认执行路径。
- 是否有用户依赖个人 ChatGPT/Codex 订阅而没有 Runner 替代方案。
- 是否已完成 Runner 真实环境端到端验证。
- 是否已验证取消、超时、重试、断网和重复通知。

## 11. 不建议直接删除的内容

以下代码虽然目前位于 Bridge 中，但其能力应迁移到 Runner，而不是直接删除：

- Codex CLI 安装和登录状态检测。
- `codex exec --json` 适配和事件解析。
- 子进程树终止。
- 临时工作目录和路径隔离。
- 并发控制、超时和资源限制。
- 错误脱敏和 Provider 错误映射。
- Provider 能力声明。

建议将这些内容提取为 `codex-runner` 执行器模块。

## 12. 验收矩阵

| 场景 | 预期结果 |
| --- | --- |
| 两个 Runner 同时领取一个任务 | 只有一个获得有效 Lease。 |
| Runner 收到两次新任务通知 | 只启动一次 execution。 |
| Runner 领取后崩溃 | Lease 到期，任务进入允许重试的状态。 |
| 平台取消任务但通知丢失 | Runner 下次控制查询获取取消，并停止进程。 |
| 取消与完成同时发生 | 状态机按版本和终态规则只接受一个合法结果。 |
| 同一事件批次重放 | executionId + eventSequence 幂等。 |
| 同一 Artifact 重传 | Idempotency Key/Hash 阻止重复正式记录。 |
| 重试执行 | 新 executionId/attempt，复用冻结上下文，不恢复旧 Codex 会话。 |
| Runner Token 被撤销 | Runner 无法继续领取、心跳或写回。 |
| Bridge 功能关闭 | Runner Pull/Claim 主链路不受影响。 |
| Remote MCP 不可用 | 任务失败原因明确，不自动改走未授权 Bridge 通道。 |
| Browser Runner 执行 | 使用相同 Claim、Lease、事件和结果协议。 |

## 13. 决策摘要

1. 统一任务、Claim、Lease、上下文和结果写回是必要主链路。
2. MCP/Runner API 是 MeterSphere 与 Agent 的正式数据协作通道。
3. Codex CLI 是 Runner 内部执行方式，不是 MeterSphere 的任务协议。
4. Bridge WSS、Redis 路由、设备配对、安装包和本地登录属于 BYO Desktop Agent 能力，对测试任务不是必要条件。
5. 当前最需要优先解决的冲突是 Bridge Push 绕过或重复于 Runner Claim。
6. 任何即时唤起机制都只应通知 Runner，执行权必须由 Claim 产生。
7. 协议入口可以有 REST、MCP 和通知，但业务状态机、权限规则和幂等实现必须唯一。
8. 剥离时应迁移 Codex CLI 执行和进程隔离能力，不能把有价值的 Runner 执行器能力随 Bridge 一起删除。
