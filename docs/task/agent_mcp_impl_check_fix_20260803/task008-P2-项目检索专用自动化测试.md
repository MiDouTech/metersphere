# task008 - P2 项目检索专用自动化测试

> 问题：MCP-010
> 依赖：task005
> 状态：待开始

## 目标

为项目模糊检索建立专用自动化测试，覆盖匹配、权限、分页、注入与归档边界。

## 范围

- `AgentProjectServiceTests`（及必要 Mapper IT）
- 测试数据构造：多项目、同名、近似名、归档/删除/禁用
- Agent 自动化执行侧补充测试：Runner 协议、真实执行、凭据注入、域名白名单、步骤级治理、实时事件、执行产物与结果回写链路

## 必测清单

### 项目检索

- 中文、英文、大小写
- 完全匹配、前缀匹配、包含匹配
- 多个同名/近似名项目
- 用户 RBAC ∩ Token 项目白名单
- 无权限项目不可枚举
- 分页稳定性与空结果
- `%` `_` `\`、超长输入、SQL 注入字符
- 归档、删除、禁用项目边界（随 `includeArchived` 定义）

### Agent 自动化执行侧

#### Browser/Desktop Runner 协议和真实执行

- Browser Runner 与 Desktop Runner 均需覆盖协议握手、任务下发、步骤执行、心跳、取消、超时、失败上报与正常结束。
- 校验 Runner 注册信息包含类型、版本、能力集、并发数、运行环境、可用浏览器/桌面能力，并在服务端做兼容性校验。
- 覆盖协议版本不兼容、Runner 离线、重复注册、心跳丢失、执行中断、任务被取消、任务超时等异常路径。
- 覆盖真实执行链路，不只做接口 Mock：至少包含 Browser 打开页面、输入、点击、断言、截图；Desktop 打开应用/窗口定位、输入、点击、断言、截图。
- 校验执行步骤与结果顺序一致，服务端可根据 `runId`、`caseId`、`stepId`、`runnerId` 追踪完整链路。
- 校验并发执行隔离：不同任务之间的浏览器上下文、桌面会话、下载目录、临时文件、凭据和产物不得串用。

#### 凭据本地解密/注入

- 凭据密文只允许在 Runner 本地按需解密，服务端日志、事件流、执行产物和错误信息不得出现明文凭据。
- 覆盖账号密码、Token、Cookie、Header、环境变量、文件型密钥等常见凭据类型的注入。
- 覆盖凭据缺失、凭据过期、解密失败、权限不足、凭据与项目/环境不匹配等失败路径。
- 校验凭据注入作用域：仅对目标步骤、目标域名、目标 Runner 会话生效，执行结束后需要清理。
- 校验敏感字段脱敏规则：日志、SSE/WebSocket 事件、截图 OCR 文本、HAR、附件元数据、失败堆栈均不得泄露明文。

#### 域名白名单

- 自动化访问外部地址前必须校验项目/环境维度的域名白名单。
- 覆盖精确域名、子域名、通配符、端口、协议、IP、localhost、内网地址、重定向后的目标地址。
- 覆盖页面跳转、iframe、弹窗、新标签页、下载、XHR/fetch、WebSocket、资源加载等不同访问入口。
- 白名单拒绝时应中止对应步骤并记录可定位原因，不能继续访问或上传相关产物。
- 校验 DNS rebinding、URL 编码、大小写、尾点域名、用户名密码 URL、短链/多跳重定向等绕过场景。

#### 高风险动作的步骤级治理

- 治理粒度必须落到步骤级，不能只依赖用例名称关键词。
- 高风险动作识别至少覆盖删除、提交、支付、发布、审批、发消息、发邮件、改权限、批量导入/导出、执行脚本、调用外部 Webhook。
- 每个步骤需标记风险等级、风险类型、治理策略、是否需要人工确认、确认人、确认时间与确认结果。
- 覆盖执行前拦截、人工确认后继续、确认超时、拒绝执行、执行中取消、重试后仍需确认等路径。
- 校验治理策略来源和优先级：系统默认、组织策略、项目策略、环境策略、用例/步骤配置。
- 校验绕过防护：步骤名称改写、自然语言同义词、脚本/API 隐式高危操作、批量操作、组合步骤不得绕过治理。

#### SSE/WebSocket 实时事件

- 覆盖执行生命周期事件：排队、Runner 领取、开始、步骤开始、步骤日志、截图/附件生成、步骤成功/失败/跳过、暂停确认、取消、超时、结束。
- 事件需包含稳定的关联字段：`runId`、`caseId`、`stepId`、`projectId`、`runnerId`、`eventId`、`timestamp`、`sequence`。
- 校验事件顺序、去重、断线重连、断点续传、心跳、客户端取消订阅、服务端背压和大日志分片。
- 校验 SSE 与 WebSocket 至少一种主链路可用；如两者都支持，需要验证事件语义一致。
- 事件内容需遵守权限过滤和敏感信息脱敏，跨项目用户不得订阅或读取执行事件。

#### 截图、视频、HAR、附件和保留策略

- 覆盖成功、失败、断言失败、异常中断、取消、超时场景下的截图生成策略。
- 覆盖视频录制开关、失败时保留、全量保留、禁用录制、录制失败不影响主执行等场景。
- HAR 采集需覆盖请求/响应头、状态码、耗时、失败请求、重定向链路，并对 Cookie、Authorization、Token、密码字段脱敏。
- 附件需覆盖截图、视频、HAR、下载文件、日志、Runner 诊断信息，校验大小限制、格式限制、上传失败重试和断点续传。
- 保留策略需覆盖按项目、环境、执行类型、成功/失败状态、存储空间阈值和手动锁定保留。
- 校验产物权限：只有有权访问对应项目/执行记录的用户可查看、下载或删除产物。
- 校验清理任务：过期产物可被清理，未过期或被锁定产物不得误删，清理记录可审计。

#### 真实结果回写链路和全链路验收

- 覆盖从任务创建、Runner 执行、事件推送、产物上传、结果聚合、状态流转到测试计划/报告/缺陷入口回写的完整链路。
- 回写结果需包含执行状态、步骤结果、断言信息、错误原因、耗时、Runner 信息、环境信息、产物链接和治理记录。
- 覆盖成功、失败、阻塞、跳过、取消、超时、Runner 异常退出、产物上传部分失败等状态映射。
- 校验重复回写、乱序回写、延迟回写、Runner 重试、服务端重启后的幂等性。
- 校验结果对项目检索场景的联动：通过检索定位项目后创建/触发执行，最终结果必须回写到正确项目、正确用例、正确执行批次。
- 全链路验收需至少包含一个 Browser Runner 真实用例和一个 Desktop Runner 真实用例，并能在 CI 或受控测试环境中稳定运行。
- 验收报告需可从 UI/API 查询到完整执行证据：实时事件、最终状态、步骤明细、截图/视频/HAR/附件、治理记录和回写记录。

## 验收标准

- 上述场景有自动化用例且通过。
- 失败信息可定位到匹配规则或权限过滤。
- Agent 自动化执行侧补充场景有对应自动化测试或可重复执行的集成验收脚本。
- Browser/Desktop Runner 至少各有一条真实执行链路通过，不以纯 Mock 结果替代。
- 凭据、域名白名单、高风险动作治理、事件流、执行产物和结果回写均有正向、反向和异常场景覆盖。
- 高风险动作治理必须基于步骤级元数据和执行行为校验，不能仅检查用例名称关键词。
- 全链路验收可通过 `runId` 追踪到任务下发、Runner 执行、实时事件、产物上传、最终结果回写和报告展示。

## 与 `agent_automation_execution_20260805` 需求的实现偏差分析

> 分析日期：2026-08-06
> 对照目录：`docs/task/agent_automation_execution_20260805`
> 实现检查范围：`backend/services/agent-integration`、`backend/framework/domain/src/main/resources/migration/3.7.2/ddl`、`frontend/src/views/bug-management/automationExecution`、`frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`、`metersphere-mcp`

### 总体判断

- 当前实现已具备 AI 执行任务的基础管理闭环：权限入口、任务数据表、范围解析、任务创建/确认/暂停/取消/重试、游标事件查询、MCP 工具薄封装、结果回写状态对账。
- 当前实现尚未具备真实自动化执行闭环：Browser/Desktop Runner 协议、真实页面/桌面操作、Runner 任务领取与上报、凭据本地解密注入、域名白名单、步骤级高风险治理、SSE/WebSocket 实时推送、截图/视频/HAR 采集和保留策略、真实端到端验收均未完成或只有占位。
- 因此自动化测试不能只验证任务 CRUD 和项目检索，还必须验证“未实现能力不会被误标为完成”：没有真实 Runner、没有证据、没有回写时不得 `SUCCESS`，高风险判断不得只依赖用例名称。

### 任务级偏差

| 任务 | 需求重点 | 当前实现状态 | 主要偏差 | 对 task008 测试补充要求 |
| --- | --- | --- | --- | --- |
| task001 权限/菜单/路由 | `AI_EXECUTION:*` 权限、菜单入口、路由拦截 | 已有权限常量、接口注解、前端入口 | 缺真实无权限账号验证；直连路由/接口越权未形成专项用例 | 增加无权限用户访问 `/bug-management/automation-execution`、`/api/ai/execution/*` 的端到端/接口断言 |
| task002 数据模型/状态机 | 任务、用例、事件、Runner、凭据表；完整状态机 | 迁移已建 `ai_execution_task/case/event/runner_session/credential_reference`，后端常量包含 `PAUSED/WRITING_BACK/PARTIAL_SUCCESS` | 迁移注释未同步 `PAUSED/RESOLVING_SCOPE`；缺状态机合法前置状态单测；Runner/凭据表只有占位 | 增加迁移字段/索引快照校验、非法状态跳转、暂停/恢复/终态不可写测试 |
| task003 范围解析 | 项目、计划、用例解析；多候选、大范围、高风险确认 | 已接 `resolve` 和项目权限解析，支持候选计划/用例与阈值确认 | 自然语言解析较弱；计划执行权限过滤和计划状态规则仍不完整；高风险只按名称关键词 | 增加多项目/多计划/大范围/无计划降级/计划状态不可执行/无执行权限过滤用例 |
| task004 创建/确认/取消/恢复/重试接口 | 幂等创建、确认门槛、登录恢复、取消、重试 | 已有接口和状态更新，创建后会进入准备/等待登录 | 无真实 Runner 时无法继续真实执行；`login-ready` 只是状态推进；缺逐接口真实角色验证 | 增加幂等创建、确认前不可执行、取消后不可继续写入、重试仅限失败/阻塞、无 Runner 进入 `WAITING_LOGIN` |
| task005 MCP Tools | MCP 查询/创建/事件/取消/恢复，薄封装后端 | `metersphere.execution.*` 和 `metersphere.test_plan.*` 已在 `metersphere-mcp` 中定义 | 缺真实 MCP 客户端 Token 调用；缺 Scope 端到端拦截验证 | 增加 MCP schema、Token Scope、项目白名单、分页游标、错误返回结构测试 |
| task006 回写幂等/PARTIAL_SUCCESS | 复用正式回写链路，逐条幂等，部分失败不回滚 | 已有 `executionTaskId`、幂等表和 SUCCESS 证据/回写对账 | 无 `idempotencyKey` 时仍可能走原链路；附件证据未真实关联；缺集成测试 | 增加计划内/计划外回写、重复回写、乱序回写、部分失败、无证据不得成功测试 |
| task007 用例列表 AI 执行入口 | 批量入口、确认弹窗、稳定 `caseId` 提交 | 前端已加入口和确认信息，后端复核范围 | 跨页全选被阻断；环境/浏览器/登录只是提示，未形成可执行配置 | 增加选中/未选中/无权限/超阈值确认/后端复核失败/跳转恢复测试 |
| task008 工作台 | AI 对话、范围确认、执行视窗、日志、证据、回写状态 | 工作台已接 resolve/create/get/events/confirm/pause/cancel/retry，事件用 3 秒轮询 | 无 Runner 实时画面；无 OAuth/Agent 网关连接测试；无证据预览；非 SSE/WebSocket | 增加 `executionTaskId` 恢复、轮询游标、日志下载脱敏、暂停/登录恢复、无证据提示测试 |
| task009 实时事件/审计 | 结构化事件、SSE/WebSocket、断线续传、审计 | 后端只提供 `GET events?cursor`，前端 `setInterval` 轮询；关键动作写事件/审计 | 未实现 SSE/WebSocket；断线重连、背压、事件权限、脱敏测试缺失 | 增加游标递增/去重/权限隔离/脱敏测试；SSE/WebSocket 未实现需标记阻塞或独立任务 |
| task010 Browser/Desktop Runner | Runner 协议、任务令牌、真实执行、会话接管、登录恢复 | 仅有 `ai_runner_session` 表和 active session 查询；事件提示“真实页面操作仍依赖 Runner” | 无 Runner 注册、心跳、领取、租约、任务令牌、上报、真实 Browser/Desktop 执行 | 增加协议契约测试清单；当前实现只能验证“无 Runner 时进入 WAITING_LOGIN”，不能宣称真实执行通过 |
| task011 凭据/域名/高风险治理 | 凭据引用、本地解密注入、白名单、高风险审批、配额 | 仅有 `ai_credential_reference` 表和凭据引用计数；高风险为用例名关键词 | 无本地解密/注入；无域名白名单；无步骤级治理；无配额；无审批链路 | 增加凭据不明文、白名单拦截、步骤级高风险、配额测试；当前关键词检查只能作为临时弱校验 |
| task012 证据附件/HAR/保留策略 | 截图、视频、HAR、DOM、MinIO、保留与清理 | 表有 `artifact_ids` 字段，SUCCESS 对账检查证据事件 | 未采集截图/视频/HAR；Mapper 插入事件时 `artifact_ids` 写 `NULL`；无保留策略和权限下载 | 增加证据上传、附件关联、脱敏、过期清理、权限下载测试；当前应验证无证据时只能 `PARTIAL_SUCCESS` |
| task013 全链路验收 | MCP/页面/列表入口 → Runner → 事件 → 证据 → 回写 | 未完成真实验收 | 无真实 Runner、无真实执行证据、无隔离环境验收记录 | 增加最小 E2E 验收脚本：1 条 Browser Runner、1 条 Desktop Runner、计划内/计划外各一条回写 |

### 关键代码证据

- 后端接口已存在：`AgentExecutionController` 暴露 `resolve/task/get/events/confirm/login-ready/pause/cancel/retry`，并使用 `AI_EXECUTION:*` 权限注解。
- 任务创建已做基础确认：`AgentExecutionService#create` 会按数量阈值、目标环境/地址缺失、用例名称高风险关键词决定是否进入 `WAITING_CONFIRMATION`。
- Runner 不是实执行：`AgentExecutionService#advanceAfterPrepare` 只统计活动 Runner 会话和凭据引用；命中 Runner 会话后直接进入 `RUNNING`，事件文案也声明真实页面操作仍依赖 Runner。
- 凭据未注入：发现凭据引用时仍进入 `WAITING_LOGIN`，说明当前没有 Runner 本地解密/注入链路。
- 证据未采集：事件表有 `artifact_ids`，但 Mapper 插入事件固定写 `NULL`；服务端通过 `RECONCILE_EVIDENCE` 防止无证据任务标记 `SUCCESS`。
- 实时订阅未实现：工作台页面使用 `setInterval(..., 3000)` 轮询 `events`，未见 `EventSource` 或 WebSocket 专用于 AI 执行事件。
- 测试缺口明确：`backend/services/agent-integration/src/test` 下未发现 `AgentExecution*`、Runner、凭据、证据、SSE/WebSocket 专项测试；仅已有 `AgentProjectServiceTests` 覆盖项目检索部分基础场景。

### 必须优先补齐的偏差测试

1. `P0-状态与回写安全`：任务创建幂等、确认门槛、取消/暂停/重试状态机、计划内/计划外回写、重复/部分失败回写、无回写或无证据不得 `SUCCESS`。
2. `P0-权限与范围`：用户 RBAC、Agent Token Scope、项目白名单、计划状态、功能用例删除/最新版本、大范围确认、无项目不执行。
3. `P1-事件与工作台`：事件游标递增、断点续读、权限隔离、日志导出脱敏、`executionTaskId` 恢复、轮询退化链路；SSE/WebSocket 未实现项需保持未完成状态。
4. `P1/P2-Runner 与安全治理`：Browser/Desktop Runner 协议契约、任务令牌、会话接管、无 Runner 进入 `WAITING_LOGIN`、凭据本地解密/注入、域名白名单、高风险步骤级审批。
5. `P2-证据与保留`：截图、视频、HAR、附件上传、产物脱敏、产物权限、保留期和容量清理；当前只可验证证据缺失对 SUCCESS 的阻断。

### 当前不得通过测试宣称完成的能力

- 不得宣称 Browser/Desktop Runner 已真实执行。
- 不得宣称凭据已本地解密/注入。
- 不得宣称域名白名单已拦截访问。
- 不得宣称高风险动作已步骤级治理；当前只是用例名称关键词弱检测。
- 不得宣称 SSE/WebSocket 实时订阅已完成；当前为游标轮询。
- 不得宣称截图、视频、HAR、附件保留策略已完成。
- 不得宣称已完成 task013 的真实全链路验收。

## 非目标

- 不替代真实客户端联调（task009）。
- 不要求在本任务内实现完整 Browser/Desktop Runner，只要求补齐自动化测试、联调验收用例和必要的测试桩/受控 Runner。
