# AI Agent 测试执行完整改造方案

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 文档名称 | AI Agent 测试执行完整改造方案 |
| 适用系统 | MeterSphere 测试资产云平台及 AI Browser Runner |
| 文档日期 | 2026-08-25 |
| 文档状态 | 方案评审稿 |
| 建设目标 | 使 Agent 能够基于测试计划和测试资产，在受控环境中完成范围解析、执行前检查、浏览器执行、人工协同、证据采集和结果回写 |

## 2. 背景与问题

当前系统已经具备测试计划、功能用例、测试环境、测试资产目录、AI 执行任务、Runner 租约、步骤规划、证据上传和结果回写等基础能力，但“从测试资产到可可靠执行任务”的链路尚未闭合。

直接将测试用例交给 AI，只能回答“测试什么”，不能自动回答以下问题：

- 到哪个环境、哪个地址执行；
- 使用什么业务角色和登录方式；
- 凭据如何安全获取和注入；
- 业务流程、页面对象和测试数据从哪里获得；
- 本轮测试范围如何确定；
- 哪些操作允许自动执行，哪些必须人工审批；
- 如何判定通过、失败、阻塞或需要人工复核；
- 如何清理数据、采集证据并回写执行结果。

因此，本方案不把 MCP、Agent 或 Runner 单独视为完整测试能力，而是构建“测试资产驱动、计划约束、Agent 规划、Runner 执行、人机协同、证据闭环”的完整体系。

## 3. 建设目标与非目标

### 3.1 建设目标

1. 测试资产云平台能够结构化管理执行所需的非敏感前置条件。
2. 平台只保存凭据引用，真实 Secret 保存在外部密钥系统。
3. Agent 能通过 MCP 定位项目、测试计划、用例、环境、业务文档、数据集和凭据元数据。
4. 平台在创建执行任务前完成范围、环境、Runner、凭据、数据和风险检查。
5. AI 将自然语言步骤转换为受约束的动作和断言契约。
6. Runner 在隔离浏览器中完成取密、登录、操作、断言、证据采集和资源清理。
7. MFA、验证码、高风险动作和不确定结论能够进入人工协同流程。
8. 执行结果、证据、错误分类和 traceId 能够回写测试计划和功能用例。
9. 全链路具备权限隔离、审计、幂等、脱敏、超时和恢复能力。

### 3.2 非目标

- 不允许模型读取或保存明文密码、Token、Cookie、TOTP 种子和私钥。
- 不允许 AI 绕过 MFA、验证码、扫码或硬件密钥。
- 不允许根据自然语言自动选择生产环境或扩大到项目全量执行。
- 不允许执行模型生成的任意 JavaScript、Shell、SQL 或未注册插件代码。
- 首期不自动执行支付、转账、退款、生产删除等不可接受风险操作。

## 4. 设计原则

### 4.1 资产与任务分离

- 稳定信息存入测试资产：环境、业务文档、页面对象、数据模板。
- 本轮信息存入测试计划或执行任务：范围、版本、执行策略、审批策略。
- 创建任务时冻结资产版本和内容哈希，执行期间不跟随资产变化。

### 4.2 AI 与 Secret 分离

- Agent 可看到凭据配置 ID、名称、角色、环境和可用状态。
- Runner 可以在运行时通过 `secret_ref` 获取真实 Secret。
- 真实 Secret 不进入模型上下文、MCP 响应、任务快照、事件、日志和证据。

### 4.3 决策与执行分离

- Agent 负责范围解析、步骤规划和工具调度。
- Runner 负责真实浏览器操作和确定性断言。
- 后端负责权限、范围、策略、任务状态和结果对账。
- 人负责范围确认、高风险批准、MFA 和业务歧义判断。

### 4.4 默认拒绝

- 环境不明确时不执行。
- 地址与环境不一致时不执行。
- 凭据不可用时不降级为匿名执行。
- 步骤或预期缺失时标记 `NEEDS_REVIEW`。
- 高风险操作无有效批准时不执行。
- 结果证据不足时不得标记通过。

## 5. 当前能力基线

| 能力 | 当前状态 | 说明 |
|---|---|---|
| 项目、测试计划、功能用例 MCP 工具 | 已具备基础 | 可检索计划、读取用例和提交结果 |
| 执行范围解析 | 已具备基础 | 支持 `metersphere.execution.resolve` |
| 执行任务创建 | 已具备基础 | 支持环境、地址、浏览器、登录模式、资产引用和策略快照字段 |
| 测试资产目录 | 已具备基础 | 支持文档、用例、数据集、环境等资产类型和版本快照 |
| AI 步骤规划 | 已具备基础 | 可生成白名单动作和断言契约，无法规划时要求人工复核 |
| Browser Runner | 部分具备 | 支持 Chromium、人工登录等待、动作、断言、截图和有限自愈 |
| Runner 租约和事件 | 已具备基础 | 支持注册、心跳、领取、事件上报和完成 |
| 凭据引用数据表 | 仅基础模型 | 有 `ai_credential_reference`，缺少完整产品链路 |
| `CREDENTIAL_REF` 登录 | 未完成 | 前端有选项，Runner 尚未真正取密和登录 |
| 测试数据生命周期 | 未完成 | 缺少实例化、租约、隔离、清理和回收 |
| MCP 资产发现 | 部分具备 | 用例和计划工具较完整，环境、数据集、关系和凭据元数据工具不足 |

整体状态：部分完成，不能将当前系统描述为已经支持安全、无人值守的完整 AI 测试执行。

## 6. 目标总体架构

```text
测试人员 / 测试负责人
        │
        │ 创建计划、选择环境、确认范围与风险
        ▼
MeterSphere 测试资产与编排层
  ├─ 文档 / 用例 / 数据集 / 环境 / 页面对象
  ├─ 测试计划 / 执行策略 / 审批策略
  ├─ 凭据引用 / Runner / 人工请求
  └─ 执行任务 / 证据 / 结果 / 审计
        │
        │ Streamable HTTP MCP
        ▼
AI Agent
  ├─ 发现资产
  ├─ 解析范围
  ├─ 执行前检查
  ├─ 编译动作与断言
  └─ 创建或领取执行任务
        │
        │ 冻结的无 Secret 任务契约
        ▼
受控 Browser Runner
  ├─ 校验目标 Origin
  ├─ 通过 secret_ref 运行时取密
  ├─ 登录 / MFA 转人工
  ├─ 执行动作与断言
  ├─ 采集脱敏证据
  └─ 清理 Context、临时数据和凭据
        │
        ▼
被测系统 + 外部 Secret Provider
```

## 7. 测试资产存放模型

| 内容 | 推荐模块 | 存储内容 | 是否允许存 Secret |
|---|---|---|---|
| 目标地址和运行参数 | 测试资产 → 环境 → AI 执行配置 | Web/API 地址、Origin、浏览器、登录方式 | 否 |
| 业务流程和规则 | 测试资产 → 文档 | 状态机、权限矩阵、异常规则、术语 | 否 |
| 页面位置 | 测试资产 → 页面对象 | 路由、元素语义和稳定定位器 | 否 |
| 用例特有条件 | 功能用例 | 前置条件、步骤、预期、角色、风险、清理要求 | 否 |
| 公共数据 | 测试资产 → 测试数据 | 模板、基准数据、生成和清理策略 | 否 |
| 本轮范围 | 测试计划 | 版本、模块、用例、优先级、排除标签 | 否 |
| 执行和审批策略 | 测试计划 → AI 执行配置 | 并发、重试、证据、禁止和审批规则 | 否 |
| 凭据元数据 | 项目设置 → AI 执行 → 凭据引用 | 角色、环境、域名、`secret_ref`、有效期 | 只允许引用 |
| 真实凭据 | Vault/Secret Manager/Runner 密钥库 | 密码、Token、Client Secret | 是，平台外保存 |

## 8. 模块改造设计

### 8.1 环境执行配置

在现有环境详情中增加“AI 执行配置”页签。

建议配置结构：

```json
{
  "webBaseUrl": "https://test.example.com",
  "loginUrl": "https://test.example.com/login",
  "apiBaseUrl": "https://test.example.com/api",
  "allowedOrigins": ["https://test.example.com"],
  "browserType": "chromium",
  "locale": "zh-CN",
  "timezone": "Asia/Shanghai",
  "defaultLoginMode": "CREDENTIAL_REF",
  "credentialProfileId": "credential-test-admin",
  "dataNamespace": "ai-test",
  "environmentRisk": "TEST",
  "externalNotificationAllowed": false
}
```

后端必须以 `environmentId` 为可信输入，从环境配置解析目标地址；客户端传入的 `targetUrl` 只能用于兼容或经过授权的临时覆盖。若二者不一致，应返回稳定错误码 `ENV_TARGET_MISMATCH`。

### 8.2 凭据引用管理

新增项目级入口：

```text
项目设置 → AI 执行 → 凭据引用
```

建议字段：

| 字段 | 说明 |
|---|---|
| `id/name` | 凭据配置标识和名称 |
| `projectId/environmentId` | 项目和环境范围 |
| `domain` | 允许使用的目标域名 |
| `credentialType` | PASSWORD、API_TOKEN、OAUTH_CLIENT 等 |
| `roleCode` | 业务角色 |
| `usernameHint` | 脱敏账号提示 |
| `providerType` | ENV、VAULT、CLOUD_SECRET 等 |
| `secretRef` | 外部密钥引用 |
| `secretVersion` | 密钥版本 |
| `expiresAt` | 到期时间 |
| `lastVerifiedAt` | 最近验证时间 |
| `policy` | 使用、导出、生产和轮换策略 |
| `status` | ACTIVE、DISABLED、EXPIRED、INVALID |

禁止增加明文密码列。列表、详情和审计接口不得返回实际 Secret。

### 8.3 Secret Provider

Runner 侧定义可插拔 Provider：

```ts
interface SecretProvider {
  supports(ref: string): boolean;
  resolve(ref: string, context: ResolveContext): Promise<ResolvedCredential>;
}
```

首期建议：

- `env://`：本地或受控 Runner 验证；
- `vault://`：企业部署；
- 云 Secret Manager：后续按部署需要扩展。

Provider 必须实施超时、最小权限、审计、引用白名单、内存生命周期控制和日志脱敏。认证失败不得无限重试，避免锁定业务账号。

### 8.4 登录 Profile

环境关联结构化登录 Profile，避免模型每次猜测登录页面：

```json
{
  "loginUrl": "/login",
  "usernameLocator": {"strategy": "TEST_ID", "testId": "username"},
  "passwordLocator": {"strategy": "TEST_ID", "testId": "password"},
  "submitLocator": {"strategy": "ROLE_NAME", "role": "button", "name": "登录"},
  "successAssertion": {"type": "URL", "operator": "CONTAINS", "expected": "/dashboard"},
  "mfaDetection": {"strategy": "TEST_ID", "testId": "mfa-code"}
}
```

### 8.5 Browser Runner 登录状态机

```text
PREPARING_BROWSER
  ├─ MANUAL → WAITING_LOGIN → 人工确认 → 登录态验证
  ├─ CREDENTIAL_REF → 取密 → 自动登录 → 登录态验证
  └─ RUNNER_SESSION → 请求用户授权 → 接管会话 → 登录态验证
        ↓
      RUNNING
```

MFA、验证码、扫码和硬件密钥一律转 `HUMAN_REQUEST`，不自动绕过。

### 8.6 页面对象资产

新增 `PAGE_OBJECT` 资产类型：

```yaml
page: user-management
route: /system/users
elements:
  search-input:
    strategy: TEST_ID
    testId: user-search
  disable-button:
    strategy: ROLE_NAME
    role: button
    name: 禁用
  status:
    strategy: TEST_ID
    testId: user-status
```

用例引用页面对象，任务创建时解析并冻结定位器版本。业务前端应优先提供稳定的 `data-testid` 和可访问性标签。

### 8.7 用例 AI 可执行属性

用例级增加：

```yaml
automationLevel: L0|L1|L2|L3
requiredRole: system_admin
environmentTags: [web, test]
pageRefs: [user-management]
dataRefs: [normal-active-user]
cleanupRequired: true
riskLevel: MEDIUM
```

定义等级：

| 等级 | 含义 |
|---|---|
| L0 | 仅人工执行 |
| L1 | AI 辅助，步骤持续人工确认 |
| L2 | 可自动执行，高风险或不确定步骤转人工 |
| L3 | 可无人值守执行，异常进入人工队列 |

用例详情和测试计划增加“检查 AI 可执行性”入口。

### 8.8 测试数据生命周期

建立数据生命周期：

```text
RESERVE → PREPARE → USE → VERIFY → CLEANUP → RELEASE
```

建议新增：

- `ai_test_data_lease`：数据租约；
- `ai_test_data_instance`：运行数据实例；
- `ai_test_data_cleanup`：清理记录和重试状态。

需要支持并发隔离、超时回收、Runner 崩溃后清理、固定基准数据只读保护和清理失败告警。

### 8.9 测试计划 AI 执行配置

测试计划保存本轮执行约束：

```yaml
environmentId: env-test
credentialProfiles:
  system_admin: credential-test-admin
requiredCapabilities: [WEB_UI, SCREENSHOT]
scopePolicy:
  allowPlanOnly: true
  allowProjectWide: false
riskPolicy:
  production: FORBIDDEN
  delete: HUMAN_APPROVAL
  payment: FORBIDDEN
evidencePolicy:
  screenshotMode: FAILURE_ONLY
  networkCapture: true
dataPolicy:
  isolation: PER_RUN
  cleanupRequired: true
```

创建任务时将其冻结到 `policySnapshot`、`approvalPolicy`、`requiredCapabilities` 和 `assetRefs`。

### 8.10 执行前检查

新增统一 Preflight 服务，检查：

- 项目、计划、用例归属与权限；
- 环境和目标地址；
- Runner 在线状态及能力；
- 登录模式和凭据可用性；
- Secret 有效期和最近验证状态；
- 用例步骤、预期、页面对象和数据引用；
- 数据准备和清理策略；
- 高风险动作和审批要求；
- 环境网络可达性和 Origin 白名单。

结果示例：

```json
{
  "executable": false,
  "checks": [
    {"code": "RUNNER_AVAILABLE", "status": "PASSED"},
    {"code": "TARGET_REACHABLE", "status": "PASSED"},
    {"code": "CREDENTIAL_AVAILABLE", "status": "FAILED", "message": "目标环境未配置可用管理员凭据"}
  ],
  "traceId": "..."
}
```

## 9. MCP 工具设计

### 9.1 现有工具继续复用

```text
metersphere.project.search
metersphere.test_plan.search
metersphere.test_plan.get
metersphere.test_plan.cases
metersphere.functional.get
metersphere.execution.resolve
metersphere.execution.create
metersphere.task.search
metersphere.task.claim
metersphere.task.lease.heartbeat
metersphere.execution.events.batch
metersphere.execution.step.submit
metersphere.artifact.prepare
metersphere.artifact.commit
metersphere.human_request.create
metersphere.execution.complete
metersphere.execution.fail
metersphere.functional.submit
```

### 9.2 建议新增工具

```text
metersphere.asset.search
metersphere.asset.get
metersphere.asset.relations
metersphere.environment.search
metersphere.environment.get_execution_profile
metersphere.credential_profile.search
metersphere.credential_profile.get_metadata
metersphere.execution.preflight
```

凭据元数据工具不得返回真实 Secret，只能返回 ID、名称、角色、环境、域名、状态、到期时间和是否可用。

### 9.3 推荐 Agent 调用流程

```text
1. project.search
2. test_plan.search / get
3. test_plan.cases
4. asset.relations
5. environment.get_execution_profile
6. credential_profile.get_metadata
7. execution.resolve
8. execution.preflight
9. 如需确认，创建 human_request 并等待用户
10. execution.create
11. task.claim / lease.heartbeat
12. Runner 执行并上报事件、步骤和证据
13. execution.complete 或 execution.fail
14. functional.submit 回写计划用例结果
```

平台默认 MCP 地址：

- 测试环境：`https://msp.ebcone.net/api/mcp`
- 生产环境：`https://msp.ebcone.cn/api/mcp`

Agent Token 通过 `Authorization: Bearer ${METERSPHERE_AGENT_TOKEN}` 或 `X-API-Key` 注入，不能写入方案、提示词或代码仓库。

### 9.4 MCP执行控制和查询闭环

当前MCP已具备Claim、Lease心跳、事件、步骤结果、证据上传和终态提交，但控制与结果查询仍不完整。建议补充：

```text
metersphere.execution.pause
metersphere.execution.retry
metersphere.execution.checkpoint.save
metersphere.execution.checkpoint.get
metersphere.human_request.list
metersphere.human_request.cancel
metersphere.human_request.acknowledge
metersphere.artifact.list
metersphere.artifact.get_metadata
metersphere.execution.result.get
metersphere.execution.evaluation.get
metersphere.execution.case_results
metersphere.execution.writeback_status
```

人工请求的最终 `respond` 仍由平台UI或用户API执行，不允许执行Agent替用户批准自己发起的高风险操作。Agent只能创建请求、查询送达和处理状态、确认决定已生效，并根据返回的恢复策略继续、跳过或释放Lease。

证据查询默认只返回元数据、hash、用途、大小、脱敏状态和受控短期下载引用，不直接在MCP响应中返回大文件Base64。暂停与恢复必须保存可验证的安全检查点，重试不得重复写结果、创建缺陷或提交相同外部副作用。

### 9.5 严格Schema和结构化返回

所有Tool必须提供严格JSON Schema：

- 明确必填字段、类型、枚举、长度、数量和嵌套结构；
- 默认 `additionalProperties: false`，仅确需开放扩展字段时例外；
- 字段说明必须标明ID类型、项目范围、是否敏感和幂等要求；
- 分页工具统一 `page/pageSize/items/total/hasMore`；
- 写工具统一返回对象ID、版本、状态、traceId和幂等结果；
- 为稳定工具提供输出Schema或等价的版本化响应契约。

MCP响应建议同时提供人类可读摘要和机器可读内容：

```json
{
  "content": [{"type": "text", "text": "操作已完成"}],
  "structuredContent": {
    "code": "SUCCESS",
    "data": {},
    "traceId": "..."
  },
  "isError": false
}
```

禁止要求Agent从嵌套在 `content[].text` 中的JSON字符串再次解析核心业务结果。

### 9.6 工具注解、Scope和强制幂等

所有工具统一声明：

```text
readOnlyHint
destructiveHint
idempotentHint
requiresConfirmation
riskLevel
allowedTaskOrigins
allowedExecutorChannels
scope
contractVersion
```

Scope必须与操作语义一致：

- 创建执行任务：`AI_EXECUTION_RUN`；
- 取消任务：`AI_EXECUTION_CANCEL`；
- 登录完成或人工恢复：`AI_EXECUTION_LOGIN`；
- Claim和Lease：`TASK_CLAIM`；
- 事件上报：`TASK_EVENT_WRITE`；
- 步骤和终态结果：`TASK_RESULT_WRITE`；
- 证据写入：`ARTIFACT_WRITE`。

所有非只读Tool必须强制提供 `Idempotency-Key` 或契约定义的稳定请求ID。幂等作用域至少包含Token主体、项目、Tool名称和请求hash；同一键配不同请求体返回冲突，不能继续执行。幂等记录需要明确保留期、并发唯一约束和审计字段。

### 9.7 MCP错误、安全和协议兼容

MCP错误统一返回安全错误码、用户可理解消息和traceId。未知异常不得把异常类名、数据库错误、内部路径或第三方原始响应直接写入JSON-RPC `error.message`；技术详情只进入脱敏日志。

至少区分：

```text
VALIDATION_ERROR
AUTHENTICATION_REQUIRED
PERMISSION_DENIED
PROJECT_SCOPE_VIOLATION
NOT_FOUND
CONFLICT
RATE_LIMITED
LEASE_EXPIRED
IDEMPOTENCY_CONFLICT
SECRET_UNAVAILABLE
INTERNAL_ERROR
```

服务端与客户端不能各自长期硬编码不同的MCP协议版本。需要建立协议版本协商、客户端兼容矩阵和合同测试。首期继续使用无状态Streamable HTTP和事件游标轮询时，必须定义 `retryAfterMs`、游标续读、断线恢复和高频轮询限制；是否增加服务端事件推送可在后续评估。

### 9.8 平台Trigger跨通道边界（B01已确认）

当前Remote MCP注册了：

```text
metersphere.execution.trigger.create
metersphere.execution.trigger.update
metersphere.execution.trigger.list
metersphere.execution.trigger.fire
```

这些工具调用平台Trigger服务，最终创建 `PLATFORM_SCHEDULED/PLATFORM_MANUAL → MODEL_API_RUNNER` 任务，而不是个人 `PERSONAL_MCP → EXTERNAL_MCP_AGENT` 任务。个人Agent虽然不会领取平台任务，但可以配置或触发平台执行，形成权限跨界。

**已确认决策：**普通Personal Agent Token不得查询完整Trigger配置，也不得调用平台Trigger写工具。`trigger.create/update/fire`只通过平台UI和平台REST API开放；现有 `trigger.list` 不再向普通Personal Agent Token暴露。即使调用者直接构造已隐藏的工具名，服务端也必须拒绝，不能只从 `tools/list` 隐藏。

如个人Agent后续确有了解自动化覆盖情况的需求，可另行提供脱敏只读 `metersphere.automation.summary.list`，仅返回Trigger ID、名称、项目、类型、启用状态、下次执行时间、最近状态和脱敏范围摘要，不返回任务模板、凭据Profile、Webhook、模型底层配置或修改版本信息。

未来如果需要外部系统通过MCP管理平台自动化，必须另行立项并使用独立 `PLATFORM_AUTOMATION_MANAGE` Scope、平台服务Token和控制平面身份，配套显式确认、预算、频率限制和完整审计；不得复用Personal Agent Token。

## 10. 核心执行时序

```text
用户             MeterSphere          Agent              Runner          Secret Provider       被测系统
 │ 创建执行任务意图    │                  │                   │                   │                 │
 ├───────────────────>│                  │                   │                   │                 │
 │                    │<──读取计划/资产──│                   │                   │                 │
 │                    │──返回无Secret资产>│                   │                   │                 │
 │                    │<──resolve/preflight──────────────────│                   │                 │
 │<────范围与风险确认──│                  │                   │                   │                 │
 │────确认────────────>│                  │                   │                   │                 │
 │                    │<──create─────────│                   │                   │                 │
 │                    │────冻结任务─────────────────────────>│                   │                 │
 │                    │                  │                   │──resolve(ref)────>│                 │
 │                    │                  │                   │<──临时凭据────────│                 │
 │                    │                  │                   │────登录────────────────────────────>│
 │                    │                  │                   │<──登录结果──────────────────────────│
 │                    │                  │                   │────执行动作与断言──────────────────>│
 │                    │<──事件/证据/结果─────────────────────│                   │                 │
 │                    │──回写计划和用例──>│                   │                   │                 │
 │<────执行报告────────│                  │                   │                   │                 │
```

## 11. 权限与安全设计

### 11.1 权限建议

```text
AI_EXECUTION:READ
AI_EXECUTION:RUN
AI_EXECUTION:CONFIRM
AI_EXECUTION:CANCEL
AI_CREDENTIAL:READ_METADATA
AI_CREDENTIAL:MANAGE
AI_CREDENTIAL:VERIFY
AI_RUNNER:READ
AI_RUNNER:MANAGE
AI_ASSET:READ
AI_EVIDENCE:READ
```

菜单可见权限与接口权限必须一致，所有写操作都要在服务端进行组织、项目和对象级校验。

### 11.2 SSRF 和目标控制

- Runner只允许访问环境配置中的 `allowedOrigins`；
- 拒绝 `file:`、`data:`、本机管理地址和未授权内网目标；
- 页面重定向后再次校验 Origin；
- 上传文件只允许Runner受控目录；
- 禁止模型提供任意浏览器启动参数。

### 11.3 脱敏

统一屏蔽：

- Authorization；
- Cookie / Set-Cookie；
- password / passwd；
- token / secret；
- OAuth code；
- 登录表单敏感选择器；
- 业务定义的个人敏感字段。

截图前应对敏感选择器打码，HAR 和网络日志在上传前完成请求头和正文脱敏。

### 11.4 高风险审批

审批绑定：

```text
taskId + stepId + actionHash + environmentId + expiresAt
```

审批只允许单次使用，不允许自动重试。生产支付、转账、退款和不可恢复删除可继续保持绝对禁止。

## 12. 状态和错误协议

### 12.1 任务状态

```text
CREATED
RESOLVING_SCOPE
WAITING_CONFIRMATION
QUEUED
PREPARING_BROWSER
WAITING_LOGIN
WAITING_HUMAN
RUNNING
PAUSED
WRITING_BACK
SUCCESS
PARTIAL_SUCCESS
FAILED
CANCELED
EXPIRED
```

### 12.2 业务结论

```text
PASSED
PRODUCT_FAILED
ENV_FAILED
DATA_FAILED
AUTH_FAILED
LOCATOR_FAILED
ASSERTION_FAILED
AGENT_FAILED
RUNNER_FAILED
BLOCKED
NEEDS_REVIEW
CANCELED
```

只有 `PRODUCT_FAILED` 默认允许生成缺陷草稿。环境、数据、认证、Runner 和Agent问题应进入各自处理队列。

### 12.3 统一错误结构

```json
{
  "code": "CREDENTIAL_UNAVAILABLE",
  "message": "目标环境未配置可用的测试账号",
  "details": {"environmentId": "env-test"},
  "traceId": "..."
}
```

禁止向前端返回异常堆栈、数据库错误、内部文件路径和原始Secret Provider错误。

## 13. 前后端接口对应关系

| 用户入口 | 前端行为 | 后端接口 | 数据/外部依赖 |
|---|---|---|---|
| 环境 → AI执行配置 | 编辑、校验、测试连通性 | 环境执行Profile查询/保存/验证 | environment/environment_blob |
| AI执行 → 凭据引用 | CRUD、启停、验证、轮换提示 | credential-reference CRUD/verify | ai_credential_reference、Secret Provider |
| 用例 → AI可执行性 | 展示缺失项和等级 | case executability check | 功能用例、页面对象、数据关系 |
| 测试计划 → AI执行配置 | 配置范围、环境、策略 | plan execution profile | 测试计划扩展配置 |
| 用例列表 → AI执行 | 选择用例和环境，预检后创建 | execution.resolve/preflight/create | 执行任务和资产快照 |
| AI执行工作台 | 观察、暂停、取消、确认 | execution get/events/control | 任务、事件、人工请求、证据 |
| Runner管理 | 注册、状态、能力、吊销 | runner register/list/revoke | ai_runner、Runner Token |

前端必须分别处理 loading、empty、validation-error、permission-denied、conflict、network-error、timeout 和 server-error。

## 14. 数据库改造建议

### 14.1 扩展表

- 扩展 `ai_credential_reference`：类型、名称、角色、Provider、版本、有效期、验证时间和状态；
- 环境执行Profile可扩展现有环境配置，避免重复保存基础URL；
- 测试计划增加AI执行Profile或独立扩展表。

### 14.2 新增表

```text
ai_login_profile
ai_page_object
ai_page_element
ai_test_data_lease
ai_test_data_instance
ai_test_data_cleanup
ai_execution_preflight
ai_step_approval
```

迁移必须具备：

- 可重复执行保护；
- 兼容默认值；
- 现有环境和任务不受影响；
- 合理索引和唯一约束；
- 代表性旧库升级验证；
- 回滚或恢复说明。

## 15. 实施路线图

### 15.1 阶段一：安全可运行（P0）

1. 环境执行配置和统一环境选择组件；
2. 凭据引用CRUD和权限；
3. Secret Provider SPI，首期支持受控 `env://` 和Vault；
4. Runner实现 `CREDENTIAL_REF`；
5. 登录Profile和登录态验证；
6. `execution.preflight`；
7. MCP资产、环境和凭据元数据查询；
8. MCP严格输入Schema、结构化返回、错误协议和Scope修正；
9. MCP写工具强制幂等；
10. 落实已确认的Trigger跨通道边界B01，禁止Personal Agent Token查询完整配置及执行写操作；
11. 一条真实只读Web用例全链路。

阶段验收：Agent读取计划后自动解析测试环境，Runner安全取密登录，执行只读用例，上传证据并回写结果。

### 15.2 阶段二：稳定可复用（P1）

1. 页面对象资产；
2. 用例AI可执行属性和检查器；
3. 文档、数据、页面对象和用例关系；
4. 测试数据租约与清理；
5. 测试计划AI执行配置；
6. 失败分类、人工审核和步骤级审批。
7. MCP暂停、检查点、重试、Human Request状态、证据和结果查询闭环。

### 15.3 阶段三：受控无人值守（P2）

1. 定时和事件触发；
2. 基于变更的智能选例；
3. Runner容量调度；
4. 凭据到期和轮换；
5. 数据清理积压监控；
6. 失败聚类、不稳定用例治理和质量趋势。
7. MCP协议兼容矩阵、游标恢复和可选事件推送评估。

## 16. 建议任务拆分

| 编号 | 优先级 | 任务 | 主要产出 |
|---|---|---|---|
| T01 | P0 | 环境AI执行Profile | 前后端入口、接口、存储、校验 |
| T02 | P0 | 凭据引用管理 | CRUD、权限、审计、迁移 |
| T03 | P0 | Secret Provider SPI | Provider接口、Vault/env实现、测试 |
| T04 | P0 | Runner凭据登录 | CREDENTIAL_REF状态机、脱敏、MFA转人工 |
| T05 | P0 | 执行前检查 | Preflight服务、错误码、UI结果 |
| T06 | P0 | MCP资产发现 | 资产、环境、关系和凭据元数据工具 |
| T07 | P0 | MCP契约治理 | 严格Schema、structuredContent、注解、错误码和版本合同 |
| T08 | P0 | MCP授权与幂等 | Scope纠正、强制幂等、跨通道限制和审计 |
| T09 | P0 | 首条真实E2E | 计划到回写的真实证据 |
| T10 | P1 | 页面对象资产 | PAGE_OBJECT模型、管理入口、解析器 |
| T11 | P1 | 用例可执行性 | 字段、检查器、等级和批量分析 |
| T12 | P1 | 数据生命周期 | 租约、实例化、清理和回收 |
| T13 | P1 | 测试计划执行配置 | 环境、范围、策略和能力绑定 |
| T14 | P1 | 步骤级审批 | actionHash审批、一次性授权 |
| T15 | P1 | MCP控制查询闭环 | 暂停、检查点、重试、人工请求、证据、结果和写回状态 |
| T16 | P2 | 持续回归治理 | 调度、选例、轮换、聚类和趋势 |
| T17 | P2 | MCP协议演进 | 兼容矩阵、游标恢复、轮询治理和推送评估 |

现有 `task006-P0-Playwright-Browser-Runner-MVP` 和 `task010-P1-Browser-Desktop-Runner协议会话接管与登录恢复` 应纳入 T04/T09，不重复创建另一套Runner协议。

## 17. 需求追踪矩阵

| 需求 | 前端入口 | 前端实现 | 后端接口/服务 | 数据/配置 | 测试 | 当前状态 |
|---|---|---|---|---|---|---|
| 自动解析目标环境 | 环境、用例执行、测试计划 | 统一环境选择和只读地址预览 | 环境Profile、create校验 | environment配置 | 正向、不一致、越权 | 部分完成 |
| 凭据引用管理 | 项目设置→AI执行 | CRUD、启停、验证 | credential-reference接口 | ai_credential_reference | 权限、脱敏、到期 | 未完成 |
| Runner安全取密登录 | 执行详情 | 登录状态和人工请求 | Runner协议、Provider | Runner配置、Vault | 成功、失败、MFA、泄露 | 未完成 |
| MCP发现执行资产 | 无独立UI要求 | 无 | asset/environment/credential工具 | 工具权限和Schema | 分页、权限、无Secret | 部分完成 |
| MCP契约可靠性 | 无独立UI要求 | 无 | Tool Registry、Streamable Service | Schema/响应/协议版本 | 输入、输出、客户端兼容 | 未完成 |
| MCP写操作幂等 | 无独立UI要求 | 无 | Idempotency Service | 唯一键、保留期、请求hash | 重试、并发、键冲突 | 部分完成 |
| MCP权限语义 | Agent Token管理 | Scope选择与风险提示 | Scope Assert、Tool注解 | scope/通道策略 | cancel、login、claim、跨通道 | 部分完成 |
| MCP控制与查询闭环 | 执行详情、人工请求 | 状态、暂停、恢复、结果入口 | execution/human/artifact/result工具 | checkpoint、查询索引 | 断线、恢复、重复结果 | 未完成 |
| MCP安全错误 | 无独立UI要求 | 客户端展示安全消息 | MCP Error Mapper | traceId、错误码 | 异常脱敏、稳定映射 | 未完成 |
| Trigger跨通道边界B01 | Agent Token/触发器 | 普通个人Token不展示完整Trigger入口 | Tool Registry、Trigger Service | 禁用策略、可选脱敏摘要 | list/create/update/fire均拒绝、直接构造工具名、审计 | 已确认待实施 |
| 执行前检查 | 创建任务弹窗 | 检查结果和缺失项 | execution.preflight | 预检记录 | 环境、Runner、凭据、数据 | 未完成 |
| 页面对象复用 | 测试资产 | 页面和元素编辑 | 页面对象服务 | 新资产和版本 | 版本、定位、权限 | 未完成 |
| 测试数据隔离清理 | 测试数据、执行详情 | 数据状态和清理告警 | 数据租约服务 | 新数据表 | 并发、崩溃、超时 | 未完成 |
| 计划约束范围和风险 | 测试计划 | AI执行配置 | plan profile | 计划扩展 | 范围、禁止、审批 | 部分完成 |
| 证据和结果回写 | 执行详情 | 事件、证据、结果 | artifact、writeback | 事件和附件 | 脱敏、幂等、部分失败 | 已有基础待E2E验证 |

## 18. 测试与验收方案

### 18.1 单元测试

- 环境与目标URL一致性；
- 凭据引用格式、权限、状态和到期；
- Secret Provider选择、超时和错误映射；
- 登录Profile契约；
- 动作、定位器、断言和风险校验；
- Preflight全部检查项；
- 数据租约并发和超时回收；
- 事件、步骤和结果幂等。
- MCP严格Schema、工具注解和输出契约；
- MCP Scope与操作语义映射；
- 强制幂等键、同键异体冲突和并发重放；
- 安全错误映射和traceId；
- 检查点版本、暂停恢复和重复终态保护。

### 18.2 集成测试

- 凭据CRUD到数据库；
- 环境Profile到任务快照；
- MCP工具到真实服务和权限；
- Runner领取、心跳、事件、证据和完成；
- Vault测试实例取密；
- 数据准备和清理；
- 计划用例结果回写。
- MCP控制、人工请求、证据和结果查询闭环；
- MCP协议版本协商及主要客户端合同；
- Personal Agent Token不能调用未授权的平台Trigger写工具。

### 18.3 前端测试

- 从真实菜单进入环境、凭据、测试计划和执行页；
- loading、empty、成功和各类错误状态；
- 环境切换后地址和凭据联动；
- 未通过Preflight时禁止提交；
- 权限不足时菜单和直接路由一致；
- 密码和Token不出现在DOM、日志和请求体。

### 18.4 容器和运行验证

- 后端和前端生产构建；
- Browser Runner镜像构建；
- 平台、Runner、Vault测试实例启动；
- 健康和就绪检查；
- Runner断线、租约过期和资源清理；
- 目标Origin白名单和网络不可达场景。

### 18.5 核心E2E

```text
MeterSphere UI创建测试计划
→ 选择测试环境和只读用例
→ Preflight通过
→ 创建并冻结任务
→ Runner领取任务
→ 通过secret_ref取密
→ 登录真实测试系统
→ 执行动作和断言
→ 上传脱敏截图
→ 回写计划用例结果
→ UI展示证据、分类和traceId
```

重要失败路径：凭据过期、目标地址不匹配、Runner离线、MFA、定位失败、数据清理失败、高风险动作未批准、MCP幂等键冲突、Lease过期后重放、未知异常脱敏，以及Personal Agent Token越权调用平台Trigger。

## 19. 完成定义

只有同时满足以下条件，才可以将本方案对应功能标记为完成：

1. 环境、凭据、用例、计划、Agent和Runner形成真实完整链路；
2. 前端入口、路由、权限、状态和错误处理完整；
3. 每个前端请求都有真实后端接口；
4. 后端实施参数、权限、项目归属、业务规则和安全校验；
5. 迁移、配置、依赖和Secret Provider部署完整；
6. 前后端字段、类型、枚举、状态和错误协议一致；
7. 单元、集成、类型检查、lint和生产构建通过；
8. 平台和Runner镜像可构建、启动并通过健康检查；
9. 核心E2E及重要失败路径通过；
10. MCP工具具有严格Schema、结构化响应、安全错误、正确Scope和写操作强制幂等；
11. Personal Agent Token不能越权操作平台任务或平台Secret，B01已决策并有自动化证据；
12. 不存在未披露的TODO、Mock、占位成功、空函数或明文Secret。

任意一项不满足时，状态必须是“部分完成”或“阻塞”。

## 20. 风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| AI生成错误定位器 | 误操作或失败 | 页面对象、唯一定位、有限自愈、证据和人工复核 |
| Secret泄露 | 账号被滥用 | 外部取密、模型隔离、脱敏、短期凭据和审计 |
| 环境选择错误 | 影响生产数据 | 环境解析地址、Origin白名单、生产禁止和审批 |
| 测试数据冲突 | 用例不稳定 | 数据租约、命名空间、并发隔离和清理 |
| Runner断线 | 状态不一致 | 租约、心跳、幂等事件、超时回收和对账 |
| AI扩大测试范围 | 时间和风险失控 | 测试计划边界、resolve/preflight和人工确认 |
| 高风险动作重复 | 不可恢复副作用 | 一次性步骤审批、高风险禁重试和幂等键 |
| 缺陷误报 | 扰乱研发流程 | 失败分类，仅产品失败生成缺陷草稿 |
| MCP宽松Schema | Agent误传字段或错误理解结果 | 严格输入/输出契约、版本化Schema和合同测试 |
| MCP未知异常泄露 | 暴露数据库、路径或第三方细节 | 安全错误映射、traceId和脱敏日志 |
| 个人Token管理平台Trigger | 双通道权限边界混乱 | B01已确认禁止完整查询和Trigger写工具；未来管理能力使用独立平台服务身份 |
| 写Tool缺少幂等键 | 重复任务、通知、缺陷或结果 | 强制Idempotency-Key、请求hash和并发唯一约束 |

## 21. 交付判定

本文件是完整改造方案，不代表上述功能已经全部实现。

当前建议状态：**部分完成**。

优先交付主链路：

```text
环境执行配置
→ 凭据引用
→ Secret Provider
→ Runner凭据登录
→ 执行前检查
→ MCP资产发现
→ MCP契约、Scope、幂等和跨通道隔离
→ 真实E2E闭环
```

在该主链路通过真实构建、容器和E2E验证前，不应宣称系统能够安全、稳定地自动执行测试资产中的所有用例。
