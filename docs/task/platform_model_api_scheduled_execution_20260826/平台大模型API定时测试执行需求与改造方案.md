# 平台大模型 API 定时测试执行需求与改造方案

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 需求名称 | 平台大模型 API 定时测试执行 |
| 需求方向 | 平台托管执行，不依赖个人 Agent |
| 需求类型 | 新增与改造 |
| 优先级建议 | P0 |
| 文档日期 | 2026-08-26 |
| 文档状态 | 需求确认稿，D01至D12、MAP Gateway复用架构A01及Trigger边界B01已确认，待研发实施 |
| 关联方案 | `AI-Agent测试执行完整改造方案.md`、`Agent双通道执行能力实现需求.md` |
| 参考资料 | [模型网关权威教程](https://map.ebcone.net/s/lib/PNyJj8JYqXJN?entry=d6133cb9cee587c175fdf727e0c9c493)、用户提供的《接入大模型不仅仅是调个API那么简单》67项检查清单 |

## 2. 背景

现有方案主要描述个人任务通过 Codex、Claude Code 或其他个人 Agent 连接 MeterSphere Remote MCP，读取测试资产、执行测试并回写结果。该模式适合临时、探索性和强交互任务，但依赖个人设备、个人 Agent 会话及用户在线状态，不适合作为稳定的定时执行基础。

本需求建设另一条独立执行通道：测试资产云平台通过 API 接入平台批准的大模型，Cron、Webhook 或平台人工触发任务后，由平台调用模型生成受控执行计划，再交给平台托管 Runner 执行。整个过程不依赖个人 Agent 和个人电脑。

## 3. 需求目标

### 3.1 目标

1. 平台支持配置可用于测试任务规划的大模型 API。
2. 支持 Cron、Webhook 和平台人工触发测试任务。
3. 定时任务能够绑定项目、测试计划、环境、用例范围、模型配置、Runner能力和执行策略。
4. 每次触发冻结测试资产、模型、提示模板、环境和策略版本。
5. 模型将自然语言用例转换为版本化、可校验的执行契约。
6. 非法、不完整或高风险计划不能直接进入 Runner。
7. 平台 Runner完成浏览器或API操作、证据采集、数据清理和结果回写。
8. 任务执行不依赖任何个人 Agent，不静默降级到个人执行通道。
9. 模型、Runner、触发器和业务结果具有统一traceId、审计和可观测性。
10. 支持模型不可用、Runner离线、凭据失效、环境异常和人工超时等失败行为。
11. 平台业务代码统一通过现有MAP Gateway调用模型，不直接调用Provider API，不散落供应商SDK和协议判断。
12. 每次模型调用可追溯到租户、业务调用方、任务、模型路由、提示词版本、用量、费用与上游尝试。

### 3.2 非目标

- 不由平台远程启动用户本机 Codex、Claude Code 或IDE Agent。
- 不托管个人大模型订阅或个人 Agent 凭据。
- 不允许模型直接执行任意JavaScript、Shell、SQL或浏览器启动参数。
- 不允许定时任务自行选择生产环境或扩大到项目全量范围。
- 首期不支持无审批的生产删除、支付、转账、退款或真实外部通知。
- 不以模型 API 取代 Browser Runner、API Runner、性能Runner或移动端执行器。
- 首期不建设Embedding、Rerank、ASR、TTS、实时语音和视频生成能力；未来出现明确测试任务后独立评审。
- 首期不要求覆盖所有供应商和所有协议，但网关内部契约不得绑定单一供应商SDK。

## 4. 双通道边界

### 4.1 个人 Agent 通道

```text
PERSONAL_MCP
→ EXTERNAL_MCP_AGENT
→ 个人 Agent 主动连接 Remote MCP
→ Claim/Lease
→ 本机工具执行
→ MCP 回写
```

### 4.2 平台任务通道

```text
PLATFORM_SCHEDULED / PLATFORM_MANUAL
→ MODEL_API_RUNNER
→ 平台模型 API 规划
→ 服务端计划校验
→ 平台 Runner Claim/Lease
→ 执行、证据、回写
```

### 4.3 必须保持的约束

| taskOrigin | executorChannel | 是否合法 |
|---|---|---|
| PLATFORM_SCHEDULED | MODEL_API_RUNNER | 是 |
| PLATFORM_MANUAL | MODEL_API_RUNNER | 是 |
| PERSONAL_MCP | EXTERNAL_MCP_AGENT | 是 |
| PLATFORM_SCHEDULED | EXTERNAL_MCP_AGENT | 否 |
| PLATFORM_MANUAL | EXTERNAL_MCP_AGENT | 否 |
| PERSONAL_MCP | MODEL_API_RUNNER | 否 |

平台模型、Runner或凭据不可用时，平台任务必须明确阻塞或失败，不能自动进入个人Agent队列。

平台Trigger属于平台控制面，只能通过MeterSphere平台UI和平台REST API管理。普通Personal Agent Token及Remote MCP不得查询完整Trigger配置，也不得调用 `trigger.create/update/fire`；直接构造隐藏工具名同样必须由服务端拒绝。如未来需要MCP管理平台自动化，必须使用独立平台服务身份和 `PLATFORM_AUTOMATION_MANAGE` Scope，不能复用个人Agent Token。

## 5. 适用任务分类

| 类型 | 是否需要模型 | 是否需要Runner | 首期建议 |
|---|---|---|---|
| 用例完整性检查 | 是 | 否 | P0 |
| 重复、过期用例分析 | 是 | 否 | P0 |
| 测试结果总结 | 是 | 否 | P0 |
| 回归范围推荐 | 是 | 否 | P0，结果需人工确认 |
| 固定API测试 | 可选 | API Runner | P0 |
| 自然语言API用例 | 是 | API Runner | P1 |
| Web UI只读冒烟 | 是 | Browser Runner | P0试点 |
| Web UI数据变更 | 是 | Browser Runner | P1，需数据治理 |
| 性能测试 | 可选 | 性能Runner | 后续 |
| 移动端测试 | 是或可选 | Appium/设备池 | 后续 |

建议先交付“纯平台AI任务”和“只读Web冒烟”，再扩展到修改数据的无人值守执行。

## 6. 总体架构

```text
用户配置定时任务
        │
        ▼
Trigger Service
  ├─ CRON
  ├─ EVENT/Webhook
  └─ MANUAL
        │ 幂等创建
        ▼
Execution Preflight
  ├─ 范围
  ├─ 环境
  ├─ 模型
  ├─ Runner
  ├─ 凭据
  ├─ 数据
  └─ 风险
        │
        ▼
Platform Model Planning
  ├─ 读取冻结资产
  ├─ 通过现有MAP Gateway调用模型
  │    ├─ appCaller/Service Key鉴权
  │    ├─ PromptPolicy
  │    ├─ Model Profile/Offering路由
  │    ├─ 限流、预算、重试、熔断
  │    └─ Usage、费用、审计和Trace
  ├─ 生成Execution Contract
  └─ Schema/策略校验
        │
        ▼
Platform Runner Pool
  ├─ Browser Runner
  ├─ API Runner
  └─ 其他Runner
        │
        ▼
被测环境
        │
        ▼
事件、证据、结果、评价和回写
```

## 7. 完整业务流程

### 7.1 配置模型 API

管理员配置平台可用模型：

- Provider类型；
- API Base URL；
- 逻辑模型PublicId、实际模型ID及Offering；
- Provider Credential引用；
- 平台定时测试专用appCaller；
- 测试、预发布环境独立的Gateway Service Key引用；
- 模型池、优先级、权重和故障转移策略；
- 组织、项目和环境范围；
- 调用超时；
- 最大重试次数；
- 并发、RPM和Token配额；
- 最大上下文和输出长度；
- 是否支持结构化输出、Vision和Tool Calling；
- 费用与预算限制；
- 启用状态和健康状态。

Provider API Key与Gateway Service Key是两类凭据：前者仅供网关访问上游，后者标识“谁在调用网关”。二者都必须存入平台密钥系统或外部Secret Provider，不得进入任务快照、模型Prompt、普通日志或前端响应。业务任务只引用Gateway Service Key，不得获得Provider API Key。

`appCaller`是稳定的业务用途身份，不是用户账号、密钥或模型。建议至少按任务类型拆分：

```text
metersphere.scheduled-test::planning
metersphere.scheduled-test::result-analysis
metersphere.scheduled-test::vision
```

它用于绑定团队、环境、PromptPolicy、模型能力、预算、RPM和统计口径。每个环境、业务用途和调用客户端应使用独立Service Key，避免一把主Key跨环境、跨业务共用。

### 7.2 创建定时任务

用户配置：

```yaml
name: 每日用户管理P0冒烟
trigger:
  type: CRON
  expression: "0 0 2 * * ?"
  timezone: Asia/Shanghai
  concurrencyPolicy: FORBID
  missedPolicy: FIRE_ONCE

scope:
  projectId: project-customer
  testPlanId: plan-daily-smoke
  environmentId: env-test
  caseFilter:
    priority: [P0]
    tags: [scheduled-ready]

planning:
  modelProfileId: model-test-planner
  promptTemplateId: web-contract-v1
  contractVersion: v1

execution:
  runnerType: BROWSER
  requiredCapabilities: [WEB_UI, SCREENSHOT]
  browserType: chromium
  credentialProfileId: credential-test-admin

policy:
  maxCases: 20
  maxStepsPerCase: 30
  taskTimeoutMinutes: 30
  retry: 1
  highRiskAction: BLOCK
  humanTimeoutMinutes: 120

evidence:
  screenshotMode: FAILURE_ONLY
  networkSummary: true
  consoleSummary: true
```

### 7.3 触发与幂等

每次触发使用稳定幂等键：

```text
triggerId + scheduledAt + triggerVersion
```

要求：

- 同一Cron窗口只能创建一个任务；
- 重复Webhook事件ID只处理一次；
- 手工重跑产生新attempt，但保留原始触发关系；
- `FORBID`策略下前一任务未结束时不创建并行任务；
- `ALLOW`只能在数据、凭据和Runner支持隔离时开放；
- `SKIP`和`FIRE_ONCE`有明确历史记录；
- 服务器重启后能够恢复nextFireAt，不重复补跑。

### 7.4 执行前检查

创建Runner任务前执行Preflight：

```text
SCOPE_RESOLVED
ASSET_SNAPSHOT_READY
ENVIRONMENT_READY
TARGET_ALLOWED
MODEL_AVAILABLE
MODEL_QUOTA_AVAILABLE
RUNNER_AVAILABLE
RUNNER_CAPABILITY_MATCHED
CREDENTIAL_AVAILABLE
TEST_DATA_READY
CLEANUP_POLICY_READY
RISK_POLICY_PASSED
```

失败时返回明确分类：

```text
BLOCKED_SCOPE
BLOCKED_ENVIRONMENT
BLOCKED_MODEL
BLOCKED_RUNNER
BLOCKED_CREDENTIAL
BLOCKED_DATA
WAITING_CONFIRMATION
```

### 7.5 冻结执行上下文

每次任务冻结：

- 项目和测试计划；
- 用例及步骤快照；
- 文档、环境、页面对象和数据集版本；
- 模型配置和模型版本；
- 提示模板版本和hash；
- 执行参数；
- 风险和审批策略；
- Runner能力要求；
- 凭据引用ID，不包含真实Secret；
- 幂等键、创建者、触发来源和traceId。

资产在执行过程中发生修改，不影响已创建任务。

### 7.6 模型生成执行计划

输入：

```text
用例步骤和预期
+ 环境非敏感信息
+ 业务文档
+ 页面对象
+ 数据引用
+ 允许动作
+ 风险策略
```

输出必须符合版本化Schema：

```json
{
  "contractVersion": "v1",
  "action": {
    "type": "CLICK",
    "target": {
      "strategy": "TEST_ID",
      "testId": "submit"
    },
    "timeoutMs": 10000,
    "retryable": true,
    "riskLevel": "LOW"
  },
  "assertions": [
    {
      "type": "VISIBLE",
      "target": {
        "strategy": "TEST_ID",
        "testId": "success-message"
      },
      "timeoutMs": 10000
    }
  ]
}
```

### 7.7 计划校验

后端必须校验：

- Schema版本；
- 动作和断言白名单；
- 目标Origin；
- 最大用例数和步骤数；
- 超时；
- 定位器结构；
- 上传文件目录；
- `valueRef`来源；
- 禁止任意脚本；
- 高风险动作；
- 凭据不能内联；
- 断言不能为空；
- 模型返回的页面或接口不能超出冻结资产范围。

模型输出解析失败时不能生成默认成功计划，应进入有限重试或 `NEEDS_REVIEW`。

### 7.8 Runner执行

平台Runner：

1. 按能力、网络区域、浏览器和并发余量匹配；
2. 原子Claim任务；
3. 获取短期Lease并持续心跳；
4. 创建隔离Browser Context或API执行上下文；
5. 解析平台凭据引用并运行时取密；
6. 登录被测系统；
7. 执行动作和确定性断言；
8. 上传脱敏证据；
9. 提交步骤和用例结果；
10. 执行数据清理；
11. 关闭上下文并完成任务。

Runner断线、Lease过期或任务取消后必须停止写入。

### 7.9 结果处理

统一结果：

```text
PASSED
PRODUCT_FAILED
ENV_FAILED
DATA_FAILED
AUTH_FAILED
AGENT_PLAN_FAILED
RUNNER_FAILED
BLOCKED
NEEDS_REVIEW
CANCELED
```

只有证据充分的 `PRODUCT_FAILED` 默认允许生成缺陷草稿。环境、数据、认证、模型和Runner失败不应直接创建产品缺陷。

## 8. 功能改造清单

### 8.1 前端

#### 平台模型配置

新增或完善：

```text
系统设置/组织设置
→ AI模型
→ Provider和模型配置
```

需要处理：

- 模型列表；
- 创建、编辑、启停；
- Secret引用；
- 连接测试；
- 能力和配额；
- 项目授权；
- 失败信息和traceId；
- 不显示完整API Key。

#### 定时任务配置

建议入口：

```text
测试计划 → 定时AI执行
Agent/AI执行 → 触发器
```

表单包括：

- Cron/Webhook/人工触发；
- 时区；
- 测试计划；
- 环境；
- 用例筛选；
- 模型Profile；
- 提示模板；
- Runner类型和能力；
- 凭据Profile；
- 并发和错过策略；
- 超时、重试和风险策略；
- 证据策略；
- 通知和人工超时策略。

#### 执行详情

展示：

- 触发来源和时间；
- 测试范围；
- 冻结资产版本；
- 模型调用状态和requestId；
- 计划Schema校验；
- Runner和Lease；
- 步骤时间线；
- 人工请求；
- 截图和证据；
- 数据清理；
- 结果和写回状态；
- traceId。

### 8.2 后端

需要完善或新增：

- 模型Profile管理服务；
- 模型Secret引用解析；
- Trigger Service；
- Scheduler扫描和补偿；
- Webhook签名、重放保护和轮换；
- Execution Preflight；
- Asset Snapshot；
- Model Planning Service；
- Execution Contract Validator；
- Runner Dispatcher；
- 统一Claim/Lease应用服务；
- Human Request；
- Evidence和Artifact；
- Result Writeback；
- Evaluation和失败分类；
- Trigger、Model、Runner和Writeback统一Trace。

### 8.3 数据库

现有模型需要确认或扩展：

```text
ai_execution_task
ai_execution_case
ai_execution_step
ai_task_trigger
ai_task_trigger_history
ai_runner
ai_runner_lease
ai_execution_event
ai_execution_artifact
ai_human_request
ai_credential_reference
```

建议增加或完善：

```text
ai_model_profile
ai_model_invocation
ai_prompt_template_version
ai_execution_preflight
ai_test_data_lease
ai_test_data_cleanup
```

关键索引和约束：

- 触发器窗口幂等唯一键；
- Webhook事件唯一键；
- 单任务有效Lease唯一性；
- 事件序列唯一性；
- 附件幂等键；
- 项目、状态和nextFireAt索引；
- 组织、项目、环境的数据隔离字段；
- 乐观锁版本字段。

### 8.4 平台Runner

需要：

- 容器化部署；
- 注册、心跳和健康检查；
- 能力标签；
- 网络区域标签；
- 最大并发；
- 浏览器或API运行时；
- Origin白名单；
- Secret Provider；
- 自动登录Profile；
- 任务级隔离；
- 证据脱敏；
- 数据清理；
- 取消和Lease失效处理；
- 资源泄漏监控。

### 8.5 配置和运维

需要配置：

- 模型调用连接、首字和整体超时；
- 模型有限重试；
- Provider并发和配额；
- Scheduler扫描周期；
- Lease时长和心跳周期；
- 事件批次和频率；
- 附件大小和类型；
- 任务、证据和日志保留期；
- 人工请求超时；
- Runner镜像和浏览器版本；
- 对象存储；
- 告警和健康检查。

## 9. API建议

### 9.1 模型配置

```text
GET    /ai/model-profiles
POST   /ai/model-profiles
GET    /ai/model-profiles/{id}
PUT    /ai/model-profiles/{id}
POST   /ai/model-profiles/{id}/verify
POST   /ai/model-profiles/{id}/enable
POST   /ai/model-profiles/{id}/disable
GET    /ai/model-profiles/{id}/capabilities
GET    /ai/model-profiles/{id}/health
GET    /ai/prompt-policies
POST   /ai/prompt-policies/{id}/versions
POST   /ai/prompt-policies/{id}/preview
POST   /ai/prompt-policies/{id}/rollback
GET    /ai/model-usage
GET    /ai/model-invocations/{id}
```

上述为测试资产平台面向管理员和任务详情的业务API；Provider、Offering、模型池、appCaller和Service Key若由独立Gateway治理，平台只保存其稳定引用和冻结快照，不复制Gateway密钥明文或底层配置表。

### 9.2 触发器

```text
GET    /ai/execution/triggers
POST   /ai/execution/triggers
GET    /ai/execution/triggers/{id}
PUT    /ai/execution/triggers/{id}
POST   /ai/execution/triggers/{id}/fire
POST   /ai/execution/triggers/{id}/rotate-secret
GET    /ai/execution/triggers/{id}/history
POST   /ai/execution/triggers/{id}/webhook
```

### 9.3 Preflight和执行

```text
POST   /ai/execution/preflight
POST   /ai/execution/tasks
GET    /ai/execution/tasks/{id}
POST   /ai/execution/tasks/{id}/cancel
POST   /ai/execution/tasks/{id}/pause
POST   /ai/execution/tasks/{id}/resume
GET    /ai/execution/tasks/{id}/events
GET    /ai/execution/tasks/{id}/artifacts
```

Runner内部API继续复用统一Claim、Lease、事件、步骤、证据和终态服务，不能另建一套状态机。

## 10. 权限设计

建议权限：

```text
AI_MODEL:READ
AI_MODEL:MANAGE
AI_MODEL:VERIFY
AI_TRIGGER:READ
AI_TRIGGER:MANAGE
AI_EXECUTION:READ
AI_EXECUTION:RUN
AI_EXECUTION:CONFIRM
AI_EXECUTION:CANCEL
AI_RUNNER:READ
AI_RUNNER:MANAGE
AI_EVIDENCE:READ
AI_CREDENTIAL:READ_METADATA
AI_CREDENTIAL:MANAGE
```

触发器创建者的权限不能永久固化为无限授权。每次触发至少重新校验：

- 创建者或服务主体是否仍有效；
- 项目是否仍有效；
- 测试计划和环境是否可执行；
- 模型和凭据是否仍授权；
- 高风险策略是否变化。

## 11. 模型调用治理

### 11.1 接入原则和对象边界

首期统一复用现有MAP Gateway。测试资产平台不建设第二套模型网关，也不重复建设Provider、Offering、模型池、Service Key、费用和审计系统；测试资产平台负责定时任务、测试资产、执行编排和结果闭环，并通过专用 `appCaller + Service Key + 逻辑模型PublicId` 调用MAP Gateway。

MAP Gateway是模型接入和治理的唯一事实源。测试资产平台只保存Gateway对象的稳定ID、展示快照和本次任务冻结版本；配置变更、Key签发与轮换、路由、模型健康、Usage和费用明细均以MAP Gateway为准。Gateway暂时不可用时任务进入阻塞或失败状态，不允许绕过Gateway直连Provider。

对象职责必须分离：

| 对象 | 回答的问题 | 变更影响 |
|---|---|---|
| Service Key | 谁、从哪个环境调用 | 可独立轮换和撤销，不改变路由 |
| appCaller | 为什么调用、属于哪个测试业务 | 绑定策略、预算、限流和统计 |
| 逻辑模型PublicId | 业务需要哪类稳定模型能力 | 对业务屏蔽实际供应商名称 |
| Offering | 同一逻辑模型有哪些供应线路 | 记录Provider、Endpoint、协议和限额 |
| 模型池 | 未显式选择逻辑模型时去哪里 | 仅作为按用途默认路由，不猜测业务意图 |
| PromptPolicy | 本业务使用哪版提示规则 | 版本化、灰度、回滚和审计 |

### 11.2 统一内部协议和能力目录

平台定义供应商无关的内部请求/响应契约，由Adapter转换OpenAI Chat/Responses、Claude Messages、Gemini GenerateContent及兼容接口。`OpenAI Compatible`只能表示接入起点，不能视为完整兼容。

能力目录至少记录：

- Streaming、Reasoning、Vision、Tool Calling、并行Tool Call；
- JSON Mode、JSON Schema和严格结构化输出遵从等级；
- 上下文窗口、稳定有效上下文、最大输出和Tokenizer；
- 支持的Temperature、TopP、TopK、Seed、Stop及Penalty参数；
- Prompt Cache、上下文缓存及命中计费；
- Preview、Beta、Deprecated、EOL生命周期；
- 区域、数据驻留、Zero Data Retention和供应商SLA。

不支持或语义不同的参数必须拒绝、转换或使用明确默认值，不允许静默透传。定时规划优先使用非流式结构化输出；如果启用SSE，必须统一事件、TTFT、中断和Finish Reason语义，不能依赖或持久化模型隐藏思考过程。

### 11.3 PromptPolicy治理

提示词分为平台安全规则、测试业务规则、任务模板和用例/用户输入四层。优先级和拼接位置固定，低层内容不得覆盖高层安全与权限规则。网页、RAG、文件和测试资产均视为不可信数据，必须与指令边界隔离并进行Prompt Injection检测。

PromptPolicy必须支持：

- 预览合成结果和变量；
- 新版本保存，禁止原地覆盖；
- 当前版本、历史版本、hash及回滚；
- 按appCaller、租户或任务类型灰度；
- Prompt迁移回归和A/B质量比较；
- 每个任务冻结policyId、version、hash和拼接策略版本；
- 普通日志只记录id、version和hash，不记录含敏感信息的完整正文。

### 11.4 路由、降级和模型生命周期

模型平替必须按能力而非按名称。路由综合考虑任务类型、结构化输出质量、上下文、Vision/Tool能力、健康、P95延迟、RPM/TPM、并发、预算、地域和SLA。

模型Profile必须解析为本次任务冻结的：

```text
logicalModelPublicId
offeringId
provider
actualModelVersion
adapterVersion
capabilitySnapshotHash
routingPolicyVersion
```

上游替换、`latest`漂移、模型下线和Profile内部切换不得改变运行中任务。新路由或新模型须通过Benchmark、Prompt迁移回归、结构化输出合同测试和小流量灰度后才能提升权重。Provider或Offering故障时，仅能切换到能力、合规和数据策略等价的候选；不存在合格候选时任务阻塞并通知执行用户，不得降级到个人Agent。

### 11.5 限流、重试、熔断和幂等

统一错误码至少包括：鉴权失败、权限不足、模型不存在、参数不支持、上下文溢出、内容审核、限流、余额不足、上游5xx、网络异常、连接超时、首字超时、整体超时和结构化输出无效。

- 429按Retry-After或指数退避重试；
- 5xx、连接中断和可恢复超时有限重试；
- 4xx参数、权限、审核和余额错误默认不盲目重试；
- 按Provider/Offering维护成功率、P95、429和5xx健康；
- 连续异常触发熔断和半开探测；
- 租户、appCaller、Service Key、Provider和模型各层设置并发、RPM、TPM及队列优先级；
- 分别设置连接、首字、整体生成和工具调用超时。

每次逻辑模型调用生成稳定 `modelInvocationId`；网络重试属于同一调用尝试链，不能重复创建任务、追加步骤、发送三方通知、回写结果或创建缺陷。模型工具调用只生成执行契约，不直接执行外部副作用。

### 11.6 结构化输出和上下文控制

模型输出必须通过JSON Schema校验。对Markdown包裹JSON、可安全修复的字段格式错误允许一次受控修复；JSON截断、字段缺失、未知枚举、越权动作和范围超限必须拒绝或重新规划，并保留原始错误类别。不得吞掉错误后返回成功。

上下文按重要性组装：冻结规则与范围、当前用例、必要资产片段、相关历史摘要。采用Token预估、检索、摘要和截断策略，保留安全规则、权限边界、断言与清理要求。长输出采用分批用例规划和可校验分段，不依赖无界续写。

### 11.7 Usage、费用和预算

内部Usage统一记录输入、输出、Reasoning、Cached Token及供应商原始字段，同时保存Tokenizer、价格表和币种快照。每条业务任务可汇总实际发生的模型调用次数、重试、修复调用、Token和费用。

费用证据区分：

```text
ESTIMATED   按请求时价格快照估算
ACTUAL      供应商返回或账单提供的实际费用
UNKNOWN     缺少价格或匹配证据，不能写成0
RECONCILED  同币种或有审计汇率凭证后完成对账
```

租户、项目、appCaller、任务和模型均可设日/月预算，任务还要设置最大模型调用次数、最大Token、最大费用、最大修复次数和最大工具调用次数。余额、信用额度或预算接近阈值时提前告警；跨币种无审计汇率时分币种展示，不虚构合计。

### 11.8 Key、隐私、安全和合规

- 不同租户、环境、业务用途和客户端使用独立Service Key；
- Key只展示一次，数据库只保存不可逆校验材料和遮盖前缀；
- 轮换采用“新建、双轨观察、确认切换、撤销旧Key”，不原地覆盖；
- 通过ServiceKeyId证明客户端已切换，撤销后进行401负向验证；
- Prompt、Response、附件、图片和日志按字段分类脱敏，禁止密码、Token、Cookie和业务Secret进入模型；
- Provider数据使用、训练、保留期、地域和出境条件必须在启用前通过合规审批；
- 内容审核拒绝映射为统一业务状态，不得通过切换供应商绕过安全策略。

### 11.9 可观测性、审计和故障排查

一次任务Trace至少串联：

```text
taskId → planningAttemptId → modelInvocationId → gatewayRequestId
→ appCaller → ServiceKeyId → logicalModel/Offering → ProviderRequestId
→ contractVersion → runnerExecutionId → resultWritebackId
```

指标至少包括Provider、逻辑/实际模型、Offering、Request ID、TTFT、总耗时、Token、Cache Hit、Retry、Finish Reason、错误码、费用和回退路径。审计回答“谁、何时、对什么、做了什么、结果如何”，但不得记录Service Key明文、Provider Key、PromptPolicy正文或敏感响应。

故障按固定顺序定位：先保存requestId和时间，再依次检查Service Key、appCaller、逻辑模型/模型池、Offering、Provider和协议Adapter；一次只改变一个因素。没有真实回退样本时必须标记“尚无回退证据”，不能通过破坏生产配置制造证明。

### 11.10 分阶段边界

| 能力 | P0的MAP Gateway接入要求 | P1/P2增强 | 当前不纳入 |
|---|---|---|---|
| 协议 | 统一内部契约、至少一个Provider Adapter | 四协议保真、更多Provider | 业务代码直接依赖供应商SDK |
| 路由 | 受控逻辑模型/Profile、版本冻结 | 多Offering、健康回退、成本路由 | 按模型名称盲目平替 |
| Prompt | 版本、hash、回滚、任务冻结 | 灰度、A/B、热修正 | 日志保存完整敏感正文 |
| 稳定性 | 分类超时、有限重试、预算和幂等 | 熔断、资源池、区域容灾 | 无界Agent循环 |
| 费用 | Usage统一、任务预算、估算/未知 | 账单导入和对账 | 无凭证跨币种合计 |
| 模态 | 文本规划，按需Vision | 图片预处理和视觉评测 | Embedding、Rerank、语音、视频 |

## 12. 人工协同设计

平台定时任务不能依赖个人Agent，但可以使用平台内异步人工请求。

触发条件：

- MFA或验证码；
- 高风险动作；
- 多个测试范围候选；
- 模型无法生成合法计划；
- 断言证据不确定；
- 数据清理失败；
- 缺陷结论需要审核。

状态：

```text
WAITING_HUMAN
→ 平台通知
→ 用户在平台审批或补充
→ 继续/跳过/失败
```

定时任务必须设置人工等待超时和超时行为，不能无限占用Runner。

每个定时任务必须配置3名具备任务处置权限的责任人。产生人工请求时同时向3人推送；任意1人的首个有效决定即完成该请求并驱动任务继续、跳过或终止，其余两人的待处理通知自动关闭。后续重复提交返回“请求已处理”，不得覆盖首个决定。平台应记录3名接收人、送达状态、首个处理人、决定、处理时间和通知关闭结果；责任人失效或不足3人时，Preflight不通过并阻止定时任务开始。

## 13. 与个人Agent人机协同方案的兼容项

以下内容可以共享，不构成冲突：

| 能力 | 共享方式 |
|---|---|
| 执行任务、用例、步骤 | 同一任务事实模型，使用taskOrigin区分 |
| 状态机 | 共用状态定义和转换服务 |
| Claim/Lease | 共用应用服务，按executorChannel限制领取者 |
| 事件和证据 | 共用事件、附件和完整性协议 |
| 结果回写 | 共用计划用例结果和评价服务 |
| 测试资产快照 | 共用版本化资产和hash |
| Human Request | 共用数据结构，通知和交互入口不同 |
| 权限和审计 | 共用组织、项目、任务对象级校验 |
| traceId | 共用全链路追踪字段 |

## 14. 冲突分析与已确认决策

以下事项会改变个人Agent方案或平台任务的产品行为。2026-08-26已完成产品决策，实施和验收必须遵循本章。

### D01 平台任务失败后是否允许个人Agent接管

**冲突：**个人Agent方案支持MCP领取；平台任务需要无人值守。如果平台Runner不可用时允许个人Agent领取，会破坏通道隔离，并可能把平台凭据和责任转移到个人设备。

**推荐：**不允许。平台任务保持 `MODEL_API_RUNNER`，失败后阻塞并通知用户，不创建或转入 `PERSONAL_MCP` 任务。

**已确认：**暂不允许个人Agent接管，也不自动复制诊断任务。平台任务进入阻塞状态，同时通知执行用户，完整记录执行日志、阻塞阶段、阻塞原因、错误码、traceId和可重试建议。

### D02 定时任务是否允许MANUAL登录

**冲突：**个人任务可以边执行边等待用户登录；凌晨定时任务若使用MANUAL可能长时间等待并占用Runner。

**推荐：**定时任务默认禁止MANUAL。只允许 `CREDENTIAL_REF` 或预授权Runner Session；MFA场景转异步人工请求并释放执行资源。

**已确认：**按推荐方案执行。定时任务禁止MANUAL登录；只允许平台可解析的 `CREDENTIAL_REF` 或预授权Runner Session。MFA转异步人工请求并释放执行资源。

### D03 高风险动作的默认行为

**冲突：**个人Agent可以即时请求确认；定时任务通常无人在线。

**推荐：**定时任务默认 `BLOCK` 或 `SKIP_AND_REVIEW`，不进入无限等待。只有明确配置负责人和SLA时才允许 `WAITING_HUMAN`。

**已确认：**按推荐基线执行，采用 `SKIP_AND_REVIEW`：跳过当前高风险用例，记录原因并转人工审核，其他低风险用例继续执行。若高风险步骤是后续用例的必要前置条件，受影响用例标记为阻塞，不得伪造通过结果。

### D04 凭据引用是否允许两个通道复用

**冲突：**个人Agent的凭据可能来自用户本机；平台Runner需要平台可访问的Secret Provider。相同 `credentialProfileId` 不一定能在两个执行位置解析。

**推荐：**凭据Profile增加 `allowedChannels` 和Provider可达性；平台任务只允许 `MODEL_API_RUNNER`凭据。业务角色可以相同，Secret引用建议分开。

**已确认：**按推荐方案执行。凭据可共用业务角色等非敏感元数据，但必须通过 `allowedChannels` 限制执行通道，并按通道绑定不同Secret引用；平台任务不得解析个人本机凭据。

### D05 执行计划是预生成还是运行中持续调用模型

**冲突：**个人Agent擅长执行中观察和动态规划；平台定时任务若每步都调用模型，会增加不可预测性、成本和循环风险。

**推荐：**首期采用“执行前完整规划+Runner确定性执行”。只允许有限、受预算控制的定位自愈，不允许自由Agent循环。

**已确认：**按推荐方案执行。首期采用执行前完整规划和Runner确定性执行；仅允许受预算、轮数和动作白名单限制的定位自愈，不允许自由Agent循环或任意追加步骤。

### D06 两条通道是否共用一个任务列表

**冲突：**共用列表便于统一治理，但可能让用户误以为平台任务可由个人Agent领取。

**推荐：**共用事实表和详情页；UI默认分“平台执行”和“个人Agent”视图，并明确显示来源和执行通道。

**已确认：**按推荐方案执行。共用任务事实表和详情能力，前端默认拆分“平台执行”和“个人Agent”视图，并醒目标注 `taskOrigin` 与 `executorChannel`。

### D07 人工请求由谁负责

**冲突：**个人任务的请求对象通常是当前用户；定时任务可能由已离职、停用或无权限的创建者建立。

**推荐：**定时任务必须配置 `ownerUserId/ownerGroupId`，每次触发验证责任人；不可用时转项目管理员并告警。

**已确认：**每个定时任务配置3名有效责任人并同时推送，任意1人确认即可完成请求。采用首个有效决定生效的幂等机制；其余两人的待处理请求自动关闭且不得覆盖决定。若责任人不足3名、被停用或失去项目权限，Preflight失败并通知任务维护者。

### D08 定时任务运行时是否允许自动扩大范围

**冲突：**个人Agent可以建议并由用户确认扩展范围；定时任务无人确认时自动扩展可能带来成本和风险。

**推荐：**禁止。只执行冻结测试计划和筛选结果；模型推荐的新用例进入建议列表，下一次人工确认后生效。

**已确认（覆盖推荐）：**允许在受控边界内自动扩大约15%的范围。以本次冻结的基础用例数 `N` 为基数，最多新增 `ceil(N × 15%)` 条关联用例。扩展用例必须属于同一项目、同一执行环境、同一授权边界，只允许低风险动作，不得引入新的账号权限、外部系统、生产环境或破坏性操作。平台必须保存扩展理由、来源、风险评级以及扩展前后的范围快照；超出上限的建议仅进入待审核建议列表。若 `N=0`，不得通过扩展机制凭空生成执行范围。

### D09 测试计划变更何时生效

**冲突：**使用最新计划便于维护，但定时结果的范围可能每天变化；使用固定版本则需要人工发布新版本。

**推荐：**触发器引用“已发布计划版本”；草稿修改不影响定时任务。每次执行冻结实际版本。

**已确认：**按推荐方案执行。触发时使用最新已发布计划版本，草稿不生效，并为每次执行冻结实际版本与内容hash。

### D10 模型配置变更何时生效

**冲突：**个人Agent由用户选择模型；平台任务由平台承担质量、费用和稳定性责任。

**推荐：**触发器绑定受控模型Profile；Profile内部可换固定模型，但要版本化、审计并经过回归。运行中的任务继续使用冻结版本。

**已确认：**按推荐方案执行。触发器绑定受控模型Profile，Profile变更必须版本化、审计并完成回归；运行中任务继续使用启动时冻结的实际模型和Profile版本。

### D11 生产环境是否允许定时AI执行

**冲突：**个人任务可以即时确认每个操作；定时任务无人值守风险更高。

**推荐：**首期禁止生产环境；后续仅允许白名单只读用例、独立凭据、单独Runner池和强审批。

**已确认：**按推荐方案执行。首期仅允许测试和预发布环境，生产环境请求由服务端强制拒绝；后续如需开放必须另行评审。

### D12 Human Request等待时是否占用Runner Lease

**冲突：**个人Agent可以保留本机会话；平台Runner长时间保持浏览器会消耗资源，Lease超时还可能产生竞态。

**推荐：**短等待可以续租；长等待保存安全检查点、销毁浏览器并释放Lease，人工完成后重新领取恢复。不得持久化明文凭据和未脱敏Cookie。

**已确认：**按推荐方案执行。短等待允许续租；长等待保存脱敏且可验证的安全检查点、销毁浏览器并释放Lease，人工处理后由Runner重新领取恢复。不得持久化明文凭据或未脱敏Cookie。

## 15. 已确认决策基线

第一阶段统一采用：

```text
平台任务禁止个人Agent接管
定时任务禁止MANUAL登录
高风险步骤默认跳过并转人工审核
平台和个人凭据按通道隔离
执行前完整规划，Runner确定性执行
共用任务事实表，UI分视图
配置3名有效责任人并行通知，首个有效决定生效
允许在同项目、同环境、同权限和低风险边界内扩展不超过ceil(N×15%)条用例
使用最新已发布计划版本并冻结
绑定受控模型Profile并冻结实际版本
首期仅测试/预发布环境
人工等待释放Runner，首期不做长会话恢复
```

## 16. 需求追踪矩阵

| 编号 | 需求 | 前端入口 | 后端服务 | 数据/配置 | 验收 | 当前判断 |
|---|---|---|---|---|---|---|
| PS-01 | MAP Gateway模型配置引用 | AI模型设置/跳转Gateway | MAP Gateway Client/Profile Query | gateway profile/service key ref | 连接、权限、脱敏、禁止直连Provider | 部分具备 |
| PS-02 | Cron/Webhook触发 | 触发器页面 | Trigger Service | trigger/history | 幂等、签名、错过策略 | 已有基础 |
| PS-03 | 双通道与控制面隔离 | 任务列表、触发器 | Origin/Channel及Trigger权限校验 | task origin/channel、调用身份 | 非法组合及Personal Token的Trigger查询/写入拒绝 | 部分具备 |
| PS-04 | Preflight | 创建/详情 | Preflight Service | preflight结果 | 模型、环境、Runner、凭据 | 未完整 |
| PS-05 | 资产冻结 | 任务详情 | Snapshot Service | asset version/hash | 运行中资产变更不影响 | 部分具备 |
| PS-06 | 模型计划 | 无独立入口 | Planning Service | model/prompt snapshot | Schema、超时、非法输出 | 部分具备 |
| PS-07 | Runner执行 | Runner管理 | Dispatcher/Claim/Lease | runner/lease | 领取、心跳、取消 | 部分具备 |
| PS-08 | 平台自动登录 | 凭据配置 | Secret Provider/Login | credential ref | 成功、过期、MFA、泄露 | 未完成 |
| PS-09 | 数据生命周期 | 测试数据/任务详情 | Data Lease/Cleanup | data lease | 并发、清理、崩溃 | 未完成 |
| PS-10 | 三方人工确认 | 触发器责任人、任务详情、通知中心 | Human Request/Notification | 三名责任人、决定版本、送达记录 | 三方并发、首人确认、重复提交、失效责任人 | 未完成 |
| PS-11 | 受控范围扩展 | 触发器策略、任务范围对比 | Scope Expansion Policy | 基础/扩展快照、理由、风险 | 15%边界、N=0、越权、生产/破坏性动作拒绝 | 未完成 |
| PS-12 | 人工等待与恢复 | 人工请求 | Human Request/Checkpoint | human request/checkpoint | 超时、释放Lease、恢复 | 部分具备 |
| PS-13 | 证据与回写 | 执行详情 | Artifact/Writeback | event/artifact/result | 脱敏、幂等、部分失败 | 已有基础待E2E |
| PS-14 | 可观测性 | 执行详情/运维 | Trace/Health | logs/metrics | task到模型和Runner | 部分具备 |
| PS-15 | 部署可用 | Runner/运维 | 健康和调度 | 镜像/配置 | build、startup、health | 未验证 |
| PS-16 | Gateway调用身份 | AI模型设置、调用方详情 | Gateway Client/appCaller | appCaller、ServiceKeyId、环境 | 独立身份、轮换、撤销、401负验 | 未完成 |
| PS-17 | PromptPolicy治理 | 提示词策略 | Prompt Policy Service | policy/version/hash | 预览、版本、灰度、回滚、冻结 | 未完成 |
| PS-18 | 能力路由和生命周期 | 模型Profile、健康页 | Capability/Router | Offering、能力和版本快照 | 平替回归、EOL、健康回退 | 未完成 |
| PS-19 | 模型稳定性治理 | 运维看板、任务详情 | Retry/Circuit/Queue | attempt、health、quota | 分类重试、熔断、超时、幂等 | 未完成 |
| PS-20 | Usage、费用和对账 | 用量与预算 | Usage/Billing | usage、price snapshot、bill | 四状态、预算、原币种、追溯 | 未完成 |
| PS-21 | 模型安全与合规 | Provider合规配置 | Redaction/Policy | retention、region、audit | 注入、脱敏、数据政策、审核 | 未完成 |

## 17. 测试与验收

### 17.1 单元测试

- Origin/Channel合法组合；
- Cron和时区；
- 调度窗口幂等；
- Webhook签名、过期和重放；
- Preflight检查项；
- 模型输出Schema；
- 风险和Origin校验；
- Claim并发和Lease；
- 事件序列和幂等；
- 结果分类；
- 人工超时策略；
- 三名责任人同时通知、首个有效决定生效及重复提交幂等；
- `ceil(N × 15%)` 范围扩展上限、`N=0`和整数取整边界；
- Prompt四层拼接优先级、版本冲突、回滚和冻结；
- 不同Adapter的参数支持、Finish Reason和错误码映射；
- Token、费用四状态、预算预占和幂等释放；
- 重试不重复创建通知、步骤、缺陷和结果回写。

### 17.2 集成测试

- Trigger到任务创建；
- 任务到模型API；
- 模型计划到步骤持久化；
- Runner领取、心跳和完成；
- Secret Provider和登录；
- 数据准备和清理；
- 证据上传；
- 测试计划结果回写；
- 模型、Runner、对象存储部分失败恢复。
- Gateway鉴权、appCaller、Service Key、逻辑模型和Offering完整链路；
- 新旧Service Key双轨切换、撤销及401负向验证；
- Provider故障时能力等价回退，无等价候选时阻塞；
- PromptPolicy预览、发布、任务冻结、灰度和回滚；
- Usage从任务到Gateway调用、重试和供应商账单的汇总追溯。

### 17.3 权限与安全测试

- 跨项目和跨组织任务不可见；
- 平台任务不可被个人MCP领取；
- 个人任务不可被平台Runner领取；
- Personal Agent Token不得查询完整平台Trigger配置或调用trigger.create/update/fire；
- 模型API Key和业务Secret不进入日志；
- 撤销凭据立即失效；
- 禁止越过Origin；
- 高风险动作无审批不执行；
- 扩展用例不得跨项目、环境、授权边界或引入高风险动作；
- 已停用创建者不继续获得权限；
- 三名责任人不足、停用或失去权限时Preflight失败；
- Webhook重放无副作用。
- Prompt Injection不能覆盖系统安全、范围和权限规则；
- 跨租户猜测appCaller、模型、调用记录和账单不可见；
- 模型或Provider切换不能绕过内容安全、地域和数据保留策略。

### 17.4 核心E2E

```text
创建测试计划和定时任务
→ Cron触发
→ 幂等创建PLATFORM_SCHEDULED任务
→ Preflight通过
→ 调用平台模型API
→ 生成并校验Execution Contract
→ 平台Runner领取
→ 安全取密登录测试环境
→ 执行只读Web用例
→ 上传截图
→ 回写计划结果
→ UI展示完整时间线和traceId
```

关键失败路径：模型不可用、模型输出非法、Runner离线、凭据过期、MFA、Origin不匹配、数据清理失败、重复触发、取消与完成竞态、三方并发确认、范围扩展越界，以及确认不会降级到个人Agent。

## 18. 实施阶段

### P0：平台定时AI任务闭环

1. 将已确认的D01至D12转化为接口、数据约束和自动化验收用例；
2. 双通道字段和约束确认；
3. 平台模型Profile和Secret管理；
4. Trigger幂等和历史；
5. Preflight；
6. 模型计划Schema和校验；
7. 平台Runner领取和只读执行；
8. 证据与结果回写；
9. 一条真实测试环境E2E。
10. MAP Gateway集成闭环：专用appCaller/Service Key、模型Profile、Prompt版本、Usage、预算和Trace。

### P1：无人值守稳定执行

1. 自动登录和MFA转人工；
2. 测试数据租约和清理；
3. 人工请求超时和恢复；
4. Runner资源池和区域调度；
5. 模型费用、配额和预算；
6. 失败分类和缺陷草稿；
7. 告警和运维看板。
8. 多Offering健康路由、熔断、等价降级和Key轮换。

### P2：持续测试治理

1. 需求和代码变更选例；
2. 模型Profile灰度和回归；
3. 不稳定用例治理；
4. Runner弹性伸缩；
5. 定时任务质量趋势；
6. 成本优化和容量规划。
7. 模型Benchmark、Prompt迁移回归、A/B灰度、账单对账和模型EOL迁移。

## 19. 完成定义

只有满足以下条件才能标记完成：

1. 平台模型、Trigger、Preflight、Runner、证据和回写链路完整；
2. 平台任务不依赖或降级到个人Agent；
3. 前端入口、权限、状态和错误处理完整；
4. 后端参数、对象权限、项目隔离和业务规则校验完整；
5. 数据迁移、Secret、Runner和对象存储配置完整；
6. 前后端字段、枚举、状态和错误协议一致；
7. 单元、集成、类型检查、lint和生产构建通过；
8. 平台和Runner镜像可构建、启动并通过健康检查；
9. 核心E2E和重要失败路径通过；
10. 没有未披露TODO、Mock、固定成功、空函数或明文Secret。

任意一项缺少证据时，状态必须是“部分完成”或“阻塞”。

## 20. 参考资料复核与补充结论

### 20.1 教程带来的关键补充

重新复核模型网关权威教程后，原方案需要补充以下内容：

1. 将Service Key、appCaller、逻辑模型/Offering和默认模型池拆开治理，分别回答调用身份、业务用途和路由位置。
2. MAP管理员、Gateway控制台用户和应用Service Key是三条身份链，不得互换；定时测试只使用应用调用身份。
3. PromptPolicy需要预览、版本、hash、应用证据和回滚，日志不能保存敏感正文。
4. test与production、不同业务用途和不同客户端必须分Key；轮换需要新旧双轨、请求证据和撤销负验。
5. 费用不能只有一个金额，要区分估算、实际、未知和已对账，并保留原币种及价格快照。
6. 请求详情需串起Key、appCaller、逻辑模型/池、Offering、Provider、Prompt、费用和回退。
7. 安全连通性测试与真实协议保真验收是两道门；dry-run成功不能证明上游路由、流式、Vision或Tool协议正确。
8. 租户与团队范围必须由服务端会话或Service Key解析，不能信任请求自报tenantId。
9. 生产切换必须有独立身份、小流量、观察和可执行回滚，不能直接复用测试Key。

### 20.2 67项网关检查清单的取舍

67项内容并非都应直接变成定时测试P0需求：

- **P0必须吸收：**统一协议Adapter、Prompt版本与回滚、能力目录、结构化输出修复、分类超时/重试、幂等、预算、上下文控制、Key管理、脱敏、统一错误、Trace和生命周期冻结。
- **P1/P2建设：**多Provider资源池、健康路由、熔断、缓存治理、成本路由、账单对账、Benchmark、A/B灰度、能力探测、EOL迁移和区域容灾。
- **当前不适用：**Embedding、Rerank、ASR、TTS、实时语音和视频生成；除非未来定时测试任务明确需要，不进入本次改造范围。

### 20.3 与已确认双通道决策的关系

本次补充不改变D01至D12，也不与个人Agent人机协同方案冲突：

- 平台定时任务仍是 `PLATFORM_SCHEDULED → MODEL_API_RUNNER`；
- MAP Gateway只承接模型接入与治理，不替代Runner，也不接管个人Agent；
- Gateway故障或无合格模型时任务阻塞、通知执行用户并记录原因，不转入 `PERSONAL_MCP`；
- 三方确认、15%受控扩展、非生产环境限制和Runner Lease策略保持不变。

**架构决策A01（已确认）：复用现有MAP Gateway。** 测试资产平台不得另建或维护第二套Provider、Offering、模型池、Service Key、PromptPolicy、Usage、费用和网关审计事实源。平台侧仅提供业务配置入口或跳转、保存稳定引用与冻结快照，并通过MAP Gateway开放的受控API完成调用和查询。

## 21. 当前结论

该需求合理、可实现，并且与现有双通道方向一致。当前仓库已经具备任务来源/执行通道、Trigger、模型规划、Runner租约、事件、证据和回写等基础，但完整无人值守能力仍是部分完成。

D01至D12、架构决策A01及Trigger边界B01均已确认。平台定时任务统一复用现有MAP Gateway，并保持业务代码与供应商解耦；平台Trigger只通过平台UI和REST API管理，普通Personal Agent Token不得查询完整配置或执行写操作。后续技术设计需要确定MAP Gateway的调用API、身份映射、网络连通、SLA及故障契约，但不得重新引入直连Provider、平台内第二套网关或个人MCP控制平台Trigger。
