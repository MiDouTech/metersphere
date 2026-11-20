# Agent 双通道执行能力实现需求

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 需求名称 | Agent 双通道执行能力实现 |
| 需求类型 | 新增与改造 |
| 优先级 | P0 |
| 目标版本 | 待排期 |
| 前置文档 | `docs/task/agent_connection_redundancy_reduction_20260818/Agent连接协作冗余剥离与冲突分析.md` |
| 关联需求 | `02-Agent旧执行通道与冗余能力下线需求.md` |
| 当前状态 | 待实现 |

## 2. 背景与目标

Agent 模块调整为两个职责明确、授权边界不同的执行通道：

1. **平台任务通道**：平台定时巡检、定时执行和平台人工触发任务，通过平台配置并治理的模型 API 生成执行计划，再由受控 Runner 执行。
2. **个人 Agent 通道**：用户在自己的 Codex、Claude Code 或 IDE Agent 中主动连接 MeterSphere Remote MCP，读取或领取任务、访问授权数据，并回写事件、证据和结果。

目标链路：

```text
平台任务
触发器 → 幂等建任务 → 模型 API 规划 → 计划校验 → Runner Claim/Lease
→ 浏览器/API 执行 → 事件与证据 → 结果回写 → 执行评价

个人任务
个人 Agent → Remote MCP 鉴权 → 查询/领取任务 → Lease/Heartbeat
→ 本机执行 → MCP 写入事件与证据 → 完成/失败 → 结果与评价
```

本需求不包含旧 Bridge、设备配对和平台主动调用个人 CLI 的物理删除；这些工作由关联的下线需求负责。

## 3. 核心设计原则

1. 两个通道共享同一个任务模型、状态机和业务事实源。
2. 任何执行者必须成功 Claim 并持有有效 Lease 后才能提交执行数据。
3. REST、Runner API 和 MCP 只是协议入口，不得分别实现业务状态机。
4. 平台任务只能使用平台批准的模型 API，不得静默降级到个人 Agent。
5. 个人 Agent 必须主动连接并调用 MCP，平台不得远程启动用户本机 CLI。
6. 不采集或要求上传模型思维链，只保存操作事件、工具调用摘要、证据和业务结论。
7. 所有写操作必须鉴权、幂等、可审计，并具备项目级数据隔离。

## 4. 需求范围

### 4.1 统一任务来源和执行通道

新增或等价表达以下字段：

```text
taskOrigin:
- PLATFORM_SCHEDULED
- PLATFORM_MANUAL
- PERSONAL_MCP

executorChannel:
- MODEL_API_RUNNER
- EXTERNAL_MCP_AGENT
```

要求：

- 存量数据具备明确、可重复执行的回填规则。
- 后端校验合法组合，例如 `PLATFORM_SCHEDULED` 不允许使用 `EXTERNAL_MCP_AGENT`。
- 前后端枚举、数据库约束、OpenAPI/接口 DTO 和事件载荷保持一致。
- 旧 `RUNNER/AGENT` 字段在兼容期内只做转换，不作为新逻辑的唯一依据。

### 4.2 统一任务执行应用服务

建立唯一的任务执行服务边界，至少提供：

```text
claim
heartbeatLease
release
appendEvents
submitStepResult
prepareArtifact
commitArtifact
complete
fail
cancel
```

要求：

- Claim 原子化，同一任务同一时刻只能存在一个有效执行租约。
- Lease 到期后可回收；旧 Lease 的迟到写入必须被拒绝。
- 状态转换统一调用现有状态机，不允许 Controller 或 MCP Handler 直接改状态。
- 重试、取消、超时和完成并发时有确定结果。
- 每个写操作支持幂等键或单调递增序列号。

### 4.3 平台定时 AI 执行

#### 4.3.1 任务创建与模型选择

- Cron、Webhook 和人工触发只负责幂等创建任务。
- 模型选择限定在组织/项目已批准的 Provider、模型和配额内。
- 任务冻结模型、提示模板、测试资产版本、环境和执行参数。
- 没有可用模型时任务进入明确失败或阻塞状态，并显示用户可理解的原因。
- 禁止将平台任务自动转交给个人 Agent。

#### 4.3.2 计划生成与校验

- 模型输出必须符合版本化 JSON Schema。
- 校验目标地址、浏览器、最大步骤数、超时、允许动作和敏感操作。
- 非法或不完整计划不得进入 Runner 队列。
- 计划生成需设置调用超时、有限重试、费用和 Token 用量记录。
- 高风险步骤按照治理策略进入人工确认，而不是直接执行。

#### 4.3.3 Runner 执行

- Runner 通过能力、隔离方式、浏览器类型和并发余量匹配任务。
- Runner 必须 Claim 后执行，并持续续租。
- 执行过程提交结构化步骤事件、截图、网络或控制台证据摘要。
- Runner 断连、租约过期或取消后必须停止继续写入正式结果。
- 执行结束统一生成业务结论和评价。

### 4.4 个人 Agent Remote MCP

在现有原生 Remote MCP 基础上补齐任务生命周期工具。工具名称可按现有命名规范调整，但能力不得缺失。

| MCP 能力 | 用途 | 关键约束 |
| --- | --- | --- |
| `task.search/list` | 查询本人可见、可领取任务 | 项目权限、分页、状态过滤 |
| `task.get` | 获取冻结上下文与控制状态 | 不返回密钥和无权资产 |
| `task.claim` | 原子领取任务 | 能力匹配、唯一 Lease、冲突返回 |
| `task.lease.heartbeat` | 延长租约 | 仅当前执行者和有效 Lease |
| `task.release` | 主动释放 | 记录原因，遵循状态机 |
| `execution.events.batch` | 批量写入结构化事件 | 顺序、幂等、大小和频率限制 |
| `execution.step.submit` | 回写步骤结果 | 关联任务、用例和步骤 |
| `execution.complete/fail` | 提交最终结果 | 完整性校验、单次终态 |
| `artifact.prepare/commit` | 上传并登记证据 | 类型、大小、摘要、完整性校验 |
| `human_request.create/get` | 发起并读取人工介入 | 权限、超时、审计 |

所有 MCP Handler 必须复用统一任务执行服务，不能复制 Runner REST 的业务逻辑。

### 4.5 权限与 Agent 约束

最终权限按交集计算：

```text
登录用户权限 ∩ Agent Token Scope ∩ 项目范围 ∩ 当前任务授权
```

建议的细粒度 Scope：

- `TASK_READ`
- `TASK_CLAIM`
- `TASK_EVENT_WRITE`
- `TASK_RESULT_WRITE`
- `ARTIFACT_WRITE`
- `CASE_WRITE`
- `BUG_WRITE`
- `PLAN_EXECUTE`

必须满足：

- 默认拒绝未声明工具和 Scope。
- Token 支持有效期、项目范围、撤销、轮换、最后使用时间和调用限流。
- 服务端执行对象级权限与租户/组织/项目隔离，不能只依赖前端。
- 删除、发布、批量修改等高风险操作需要二次确认或草稿/确认两阶段协议。
- 参数、权限、冲突、限流和服务异常使用稳定错误码；响应不得泄漏堆栈、SQL、密钥或内部路径。

### 4.6 执行记录、日志与可观测性

执行事件至少包含：

```json
{
  "taskId": "string",
  "executionId": "string",
  "attempt": 1,
  "leaseId": "string",
  "sequence": 12,
  "eventType": "STEP_FINISHED",
  "actorType": "EXTERNAL_MCP_AGENT",
  "actorId": "string",
  "toolName": "metersphere.execution.step.submit",
  "requestId": "string",
  "traceId": "string",
  "timestamp": "ISO-8601",
  "payload": {}
}
```

平台需要提供：

- 任务详情中的执行时间线；
- 步骤、事件类型、执行者、失败状态和时间范围过滤；
- SSE 或现有等价机制的实时进度查看；
- 截图、附件和证据完整性信息；
- MCP 工具调用审计，包括脱敏参数摘要、结果摘要、耗时和错误码；
- 通过 `traceId` 关联前端请求、后端服务、Runner/MCP 和模型调用；
- 日志导出、敏感字段脱敏及分类保留周期。

正式业务结果与技术日志分开存储。个人 Agent 原始日志只允许作为可选、脱敏、短期保留的附件。

### 4.7 前端调整

#### 平台任务入口

- 定时任务配置只展示平台模型和受控 Runner 所需配置。
- 不再让用户选择个人设备或个人 Agent Provider。
- 完整处理 loading、empty、success、校验失败、权限不足、冲突、网络错误、超时和服务异常。

#### 个人 Agent 入口

- 提供 Remote MCP 地址和配置指引。
- 提供 Agent Token 创建、Scope、项目范围、有效期、撤销和轮换。
- 提供可领取任务列表、执行者、Lease、最后心跳和执行时间线。
- 可提供“复制任务链接/提示给 Agent”，但不由平台启动用户 CLI。

### 4.8 配置、数据和运维

- 增加双通道字段、索引、约束和可重复执行的数据库迁移。
- 配置模型调用超时、重试、并发、配额和计划 Schema 版本。
- 配置 Lease 时长、续租间隔、事件批次大小、附件限制和日志保留期。
- 健康检查区分模型 Provider、任务服务、对象存储和 Runner 可用性。
- 配置缺失时启动或操作必须明确失败，不能返回伪成功。

## 5. 前后端完整链路

| 用户能力 | 前端入口 | 前端请求 | 后端入口 | 核心服务/持久化 | 结果展示 |
| --- | --- | --- | --- | --- | --- |
| 配置定时 AI 任务 | 定时任务页面 | Trigger API | Trigger Controller | 触发器、任务、冻结上下文 | 下次执行、最近状态 |
| 查看平台执行 | Agent 队列/任务详情 | Execution API/SSE | Execution Controller | 状态机、事件、附件、评价 | 时间线、证据、结论 |
| 配置个人 Agent | Agent 接入页面 | Token API | Personal Agent Token Controller | Token、Scope、项目范围 | MCP 配置和使用状态 |
| 个人 Agent 领取任务 | 无平台 UI 操作，MCP 客户端调用 | Streamable HTTP MCP | MCP Controller/Handler | Claim、Lease、审计 | 执行者和租约状态 |
| 个人 Agent 回写 | MCP 客户端调用 | MCP Tools | MCP Handler | 事件、步骤、附件、结果 | 实时进度和最终结论 |

## 6. 异常与边界条件

- 同一 Cron 窗口重复触发只能创建一个任务。
- 模型限流、超时、无余额、模型下线和输出不合法均需可区分。
- 两个 Agent 同时 Claim 时只允许一个成功，另一个收到冲突错误。
- Lease 过期后原执行者提交事件或结果必须失败。
- 取消与完成并发时按状态机和版本号决定唯一终态。
- 重复事件、重复完成和附件重复提交不得产生重复业务数据。
- Token 被撤销后，已建立的 MCP 会话在下一次调用时失效。
- 跨项目任务 ID 枚举不得暴露任务是否存在。
- 大批量事件、超大附件和高频心跳必须限流。
- 数据库成功但对象存储失败等部分失败必须可恢复、可追踪。

## 7. 需求追踪与验收标准

| 编号 | 需求 | 验收标准 | 状态 |
| --- | --- | --- | --- |
| IMP-01 | 双通道建模 | 新旧数据均能得到唯一合法的来源和执行通道 | 待实现 |
| IMP-02 | 统一执行服务 | REST、Runner API、MCP 共用状态机与 Claim/Lease 实现 | 待实现 |
| IMP-03 | 平台模型 API 链路 | 定时触发可完成计划、执行、证据、回写和评价 | 待实现 |
| IMP-04 | 无个人 Agent 降级 | 模型不可用时明确失败，个人设备不会收到任务 | 待实现 |
| IMP-05 | 任务型 MCP | 外部 Agent 可完成领取、续租、事件、附件和终态提交 | 待实现 |
| IMP-06 | 权限约束 | Token、Scope、项目和任务权限交集在服务端生效 | 待实现 |
| IMP-07 | 并发与幂等 | 抢占、重放、迟到写入和取消竞态均符合约束 | 待实现 |
| IMP-08 | 可观测性 | UI 可查看统一时间线、证据、审计和 traceId | 待实现 |
| IMP-09 | 安全错误协议 | 用户只看到可理解消息和 traceId，技术详情只入日志 | 待实现 |
| IMP-10 | 部署可用 | 迁移、构建、容器、健康检查和两条 E2E 链路通过 | 待实现 |

## 8. 测试与验证要求

### 8.1 自动化测试

- 状态机、字段组合、计划 Schema 和权限单元测试。
- Claim 并发、Lease 到期、心跳、释放和取消竞态集成测试。
- MCP 鉴权、Scope、跨项目、Token 撤销、限流和幂等测试。
- 模型超时、限流、非法计划及禁止个人 Agent 降级测试。
- 事件序列、重复提交、附件摘要与结果完整性测试。
- 前端类型检查、Lint、生产构建和关键组件测试。

### 8.2 端到端测试

至少覆盖：

1. 定时触发 → 模型 API → Runner → 浏览器执行 → 证据 → 结果 → 评价。
2. 个人 Agent → Remote MCP → Claim → Heartbeat → 回写 → 完成。
3. 第二个 Agent 抢占同一任务失败。
4. Lease 过期后的写入被拒绝并可重新领取。
5. 越权项目访问和已撤销 Token 被拒绝。
6. 模型不可用时任务失败且没有进入个人 Agent 通道。

### 8.3 发布验证

- 后端单元测试和集成测试通过。
- 后端编译/打包通过。
- 前端类型检查、Lint 和生产构建通过。
- 数据库空库迁移、存量升级和必要回滚演练通过。
- Docker 镜像构建、容器启动和健康检查通过。
- 主流 MCP 客户端兼容矩阵至少覆盖当前声明支持的 Codex、Claude Code 或 IDE Agent。

## 9. 交付顺序

1. 数据模型和迁移。
2. 统一任务执行应用服务。
3. 平台模型 API 规划与 Runner 链路。
4. 任务型 MCP 工具与权限。
5. 前端入口和执行时间线。
6. 自动化、兼容性和 E2E 验证。
7. 启用新通道并进入旧通道观测期。
8. 满足下线门槛后启动关联的冗余能力下线需求。

## 10. 非目标

- 平台不托管用户个人 Codex/Claude 订阅凭证。
- 平台不远程控制、安装或升级用户个人 CLI。
- 本需求不保证任意第三方 Agent 都兼容，只支持通过兼容矩阵验证的客户端。
- 本需求不物理删除 Bridge 和旧字段，只负责提供可替代的新链路。

## 11. 完成定义

只有 IMP-01 至 IMP-10 全部有实现和验证证据，且相关测试、构建、迁移、容器健康检查及两条核心 E2E 通过，才能标记为“完成”。任何环节缺少证据时必须标记为“部分完成”或“阻塞”。
