# AI 测试执行双通道详细研发任务拆解

## 1. 文档定位

本文把以下两份已确认方案转换为可直接进入研发排期的字段、函数、文件位置和验收项级任务：

- `docs/task/ai_agent_test_execution_solution_20260825/AI-Agent测试执行完整改造方案.md`
- `docs/task/platform_model_api_scheduled_execution_20260826/平台大模型API定时测试执行需求与改造方案.md`

本文是研发任务清单，不代表功能已经实现。任务状态以仓库静态检查为准：已有基础标记为“改造”，没有真实链路标记为“新增”。

## 2. 不可变更的总体边界

| 编号 | 约束 | 落实位置 |
|---|---|---|
| C01 | `PERSONAL_MCP` 只能由 `EXTERNAL_MCP_AGENT` 执行 | 任务创建、Claim、MCP Tool 授权 |
| C02 | `PLATFORM_SCHEDULED/PLATFORM_MANUAL` 只能由 `MODEL_API_RUNNER` 执行 | Trigger、任务创建、Runner Claim |
| C03 | 平台模型或 Runner 不可用时阻塞并通知执行用户，不降级到个人 Agent | Preflight、调度、通知 |
| C04 | 平台模型调用只经过既有 MAP Gateway，禁止直连 Provider | Model Planning、依赖扫描测试 |
| C05 | 普通 Personal Agent Token 禁止 Trigger 的 list/create/update/fire | Tool Registry、Tool 执行入口、Scope |
| C06 | 普通 Personal Agent 不允许接管平台任务 | Claim 查询与原子领取 SQL |
| C07 | 定时任务不允许 MANUAL 登录；MFA/验证码导致阻塞 | Preflight、Login Profile、Runner |
| C08 | 高风险动作执行 `SKIP_AND_REVIEW`；生产环境首期禁止 | Contract Validator、Runner |
| C09 | 三位责任人同时通知，一位合法响应即完成人工请求 | Human Request、通知、CAS 更新 |
| C10 | AI 可扩大范围最多 `ceil(原始用例数 × 15%)`，且限同项目、环境、认证上下文和低风险用例 | Scope Resolver、Contract Validator |
| C11 | 冻结最新已发布资产版本、受控 Model Profile、Prompt 版本和策略快照 | Snapshot、Planning、Trigger |
| C12 | 长人工等待进入检查点并释放 Lease | Human Request、状态机、Lease |

## 3. 当前代码基线与复用原则

### 3.1 已有且必须复用

| 能力 | 当前实现位置 | 处理方式 |
|---|---|---|
| 执行 REST API | `backend/services/agent-integration/.../controller/AgentExecutionController.java` | 扩展，不另建任务控制器 |
| Trigger REST API | `.../controller/AgentTaskTriggerController.java` | 扩展平台控制面能力 |
| 任务创建/查询/控制 | `.../service/AgentExecutionService.java` | 拆分校验与编排，保留统一状态机 |
| Trigger 调度 | `.../service/AgentTaskTriggerService.java` | 增强幂等、冻结、Preflight |
| Claim/Lease | `.../service/AgentTaskClaimService.java`、`AgentTaskExecutionApplicationService.java` | 统一复用并增加通道断言 |
| 计划与上下文 | `AgentExecutionPlanningService.java`、`AgentExecutionContextService.java`、`AgentExecutionSnapshotService.java` | 接入 Gateway、形成执行契约 |
| Web 契约校验 | `AgentWebExecutionContractValidator.java` | 扩展完整契约与风险策略 |
| 事件/步骤/证据/回写 | `AgentExecutionStepResultService.java`、`AgentExecutionArtifactService.java`、`AgentExecutionWritebackService.java` | 补幂等、脱敏、查询闭环 |
| Human Request | `AgentHumanRequestService.java` | 改造成多接收人、一人完成 |
| MCP | `AgentMcpStreamableService.java`、`BuiltinAgentMcpToolConfig.java` | 严格 Schema、授权、结构化结果 |
| 资产版本 | `TestAssetCatalogService.java`、`TestAssetVersionService.java`、`TestAssetMapper.*` | 扩资产类型与关系 |
| 前端工作台 | `frontend/src/views/agent/*.vue` | 增加 Profile、Preflight、详情页与平台 Trigger 表单 |
| 前端 API | `frontend/src/api/modules/ai-execution.ts` | 统一补充 DTO 和真实接口 |

### 3.2 禁止重复建设

- 不新建第二套任务状态机、Runner Lease、事件、证据或回写表。
- 不在 MeterSphere 内复制 MAP Gateway 的 Provider、Offering、模型池、Service Key、Usage 和账单事实表。
- 不允许业务模块引入供应商 SDK 或直接访问 Provider Endpoint。
- 不把 Provider API Key、Gateway Service Key 或目标系统密码写入任务快照、Prompt、事件、日志和前端 DTO。

## 4. 任务依赖图

```text
DT-01 合同与权限 ─┬─ DT-02 数据库
                  ├─ DT-03 环境 Profile ─┬─ DT-05 Preflight/Snapshot
                  ├─ DT-04 凭据与取密 ───┤
                  └─ DT-06 资产关系 ──────┘

DT-07 MAP Gateway ─ DT-08 模型规划与契约 ─ DT-09 Trigger ─ DT-10 Runner执行
DT-01/05/06 ─────── DT-11 MCP资产与协议 ── DT-12 MCP控制与边界
DT-04/10 ────────── DT-13 登录、人工协同与检查点
DT-10/13 ────────── DT-14 数据、证据、结果与缺陷
全部后端任务 ────── DT-15 前端闭环 ─ DT-16 观测运维 ─ DT-17 测试交付
```

---

## DT-01 统一合同、枚举、权限和安全错误

**优先级/性质：** P0，新增与改造；其余任务前置。

### 字段与枚举

1. 新增/统一 Java 枚举，位置 `backend/services/agent-integration/src/main/java/io/metersphere/agent/enums/`：
   - `AgentTaskOrigin`: `PLATFORM_SCHEDULED`, `PLATFORM_MANUAL`, `PERSONAL_MCP`。
   - `AgentExecutorChannel`: `MODEL_API_RUNNER`, `EXTERNAL_MCP_AGENT`。
   - `AgentExecutionStatus`: 保留现状并覆盖 `CREATED`, `RESOLVING_SCOPE`, `WAITING_CONFIRMATION`, `QUEUED`, `PREPARING_BROWSER`, `WAITING_LOGIN`, `WAITING_HUMAN`, `RUNNING`, `PAUSED`, `WRITING_BACK`, `SUCCESS`, `PARTIAL_SUCCESS`, `FAILED`, `CANCELED`, `EXPIRED`。
   - `AgentBusinessVerdict`: `PASSED`, `PRODUCT_FAILED`, `ENV_FAILED`, `DATA_FAILED`, `AUTH_FAILED`, `LOCATOR_FAILED`, `ASSERTION_FAILED`, `AGENT_PLAN_FAILED`, `AGENT_FAILED`, `RUNNER_FAILED`, `BLOCKED`, `NEEDS_REVIEW`, `CANCELED`。
   - `AgentBlockedReason`: `BLOCKED_SCOPE`, `BLOCKED_ENVIRONMENT`, `BLOCKED_MODEL`, `BLOCKED_RUNNER`, `BLOCKED_CREDENTIAL`, `BLOCKED_DATA`, `BLOCKED_POLICY`, `WAITING_CONFIRMATION`。
   - `AgentRiskActionPolicy`: `BLOCK`, `SKIP_AND_REVIEW`, `REQUIRE_APPROVAL`；平台首期默认 `SKIP_AND_REVIEW`。
2. 前端镜像类型放入 `frontend/src/api/modules/ai-execution.ts`，删除页面内字符串散落判断。
3. 统一错误 DTO：`AgentApiErrorDTO { code, message, details, traceId }`；`details` 只能包含安全字段。

### 函数与位置

- 新增 `AgentExecutionChannelPolicy.assertCreatePair(taskOrigin, executorChannel)`。
- 新增 `AgentExecutionChannelPolicy.assertClaimable(task, actorType)`。
- 新增 `AgentExecutionChannelPolicy.assertControllable(task, actor)`。
- 新增 `AgentSafeErrorMapper.toApiError(Throwable, traceId)` 和 `toMcpError(...)`。
- 改造 `AgentExecutionService.create()`、`createPersonalMcp()`：必须显式写入并校验通道组合。
- 改造 `AgentTaskClaimService.claim()`、`search()`：SQL 前置过滤加服务层二次断言。
- 改造 `AgentExecutionController` 与 `AgentMcpStreamableService`：所有未知异常只返回稳定错误码、安全消息和 `traceId`。
- 在权限初始化迁移中增加：`AI_MODEL:READ/MANAGE/VERIFY`、`AI_TRIGGER:READ/MANAGE`、`AI_EXECUTION:READ/RUN/CONFIRM/CANCEL`、`AI_RUNNER:READ/MANAGE`、`AI_EVIDENCE:READ`、`AI_CREDENTIAL:READ_METADATA/MANAGE/VERIFY`；预留但不授予个人 Token 的 `PLATFORM_AUTOMATION_MANAGE`。

### 验收

- 六种来源/通道组合逐项测试，只有 C01/C02 三种合法组合成功。
- REST/MCP 未知异常响应不包含类名、SQL、路径和上游原文。
- 菜单权限、Controller 权限和对象级项目权限一致。

## DT-02 数据库迁移与领域对象

**优先级/性质：** P0，改造。迁移位置统一为 `backend/framework/domain/src/main/resources/migration/<目标版本>/ddl/`，禁止修改已经发布的旧迁移。

### 复用表的新增字段

1. `ai_execution_task`：已有 `task_origin`, `executor_channel`, `plan_schema_version`, `model_snapshot`, `prompt_template_snapshot`, `execution_parameter_snapshot`, `trace_id`；补充：
   - `environment_profile_id VARCHAR(64) NULL`
   - `environment_profile_version INT NULL`
   - `credential_reference_id VARCHAR(64) NULL`
   - `model_profile_id VARCHAR(64) NULL`
   - `prompt_template_version_id VARCHAR(64) NULL`
   - `preflight_id VARCHAR(64) NULL`
   - `execution_contract MEDIUMTEXT NULL`
   - `execution_contract_hash VARCHAR(128) NULL`
   - `blocked_reason VARCHAR(64) NULL`
   - `blocked_detail VARCHAR(1000) NULL`
   - `original_scope_count INT NOT NULL DEFAULT 0`
   - `expanded_scope_count INT NOT NULL DEFAULT 0`
   - `scope_expansion_rate DECIMAL(6,4) NOT NULL DEFAULT 0`
2. `ai_task_trigger`：补 `trigger_version INT NOT NULL DEFAULT 1`, `model_profile_id`, `prompt_template_id`, `environment_profile_id`, `credential_reference_id`, `runner_type`, `required_capabilities TEXT`, `policy_json MEDIUMTEXT`, `evidence_policy_json TEXT`, `notification_policy_json TEXT`, `responsible_user_ids TEXT`。
3. `ai_task_trigger_history`：补 `trigger_version`, `idempotency_key`, `event_id`, `attempt_no`, `trace_id`, `blocked_reason`；唯一索引 `(trigger_id, scheduled_at, trigger_version)`，Webhook 唯一索引 `(trigger_id, event_id)`。
4. `ai_execution_human_request`：保留 `assigned_to` 兼容读取；补 `resolution_version INT DEFAULT 0`, `resolved_reason`, `checkpoint_id`, `trace_id`。
5. `ai_execution_artifact`：复用现有 `idempotency_key`, `expected_sha256`, `trace_id`；增加 `redaction_status`, `retention_until`。

### 新表及全部首期字段

1. `ai_environment_execution_profile`：
   `id, organization_id, project_id, environment_id, name, base_url, allowed_origins, network_zone, environment_type, login_profile_id, default_credential_reference_id, runner_type, required_capabilities, production_allowed, enabled, version, create_user, update_user, create_time, update_time`。
2. `ai_credential_reference`：
   `id, organization_id, project_id, environment_id, name, credential_type, business_role, provider_type, secret_ref, secret_version, username_hint, status, expires_at, last_verified_at, last_verify_status, last_verify_message, enabled, version, create_user, update_user, create_time, update_time`。
3. `ai_login_profile`：
   `id, organization_id, project_id, environment_profile_id, name, login_type, login_url, username_locator, password_locator, submit_locator, success_assertion, session_validation, mfa_policy, timeout_ms, version, enabled, create_user, update_user, create_time, update_time`。
4. `ai_execution_preflight`：
   `id, task_id, project_id, trace_id, status, checks_json, scope_hash, asset_snapshot_hash, environment_profile_version, credential_secret_version, model_profile_version, runner_capability_hash, blocked_reason, blocked_detail, started_at, finished_at, create_time`。
5. `ai_model_profile`（仅存 Gateway 引用，不复制 Gateway 事实）：
   `id, organization_id, project_id, name, gateway_app_caller, gateway_service_key_ref, logical_model_public_id, prompt_policy_id, required_capabilities, request_timeout_ms, max_output_tokens, max_cost_amount, currency, enabled, version, last_verified_at, last_verify_status, create_user, update_user, create_time, update_time`。
6. `ai_model_invocation`：
   `id, task_id, execution_id, trace_id, gateway_request_id, model_profile_id, logical_model_public_id, resolved_offering_snapshot, prompt_version_id, request_hash, status, finish_reason, input_tokens, output_tokens, reasoning_tokens, cached_tokens, cost_amount, currency, retry_count, ttft_ms, duration_ms, error_code, error_message, create_time, finish_time`。
7. `ai_prompt_template_version`：
   `id, prompt_template_id, organization_id, name, version_no, system_template, business_template, variable_schema, output_schema_version, content_hash, status, published_by, published_at, create_user, create_time`。
8. `ai_human_request_recipient`：
   `id, request_id, user_id, notify_status, notified_at, response_status, responded_at, create_time`；唯一索引 `(request_id,user_id)`。
9. `ai_execution_checkpoint`：
   `id, task_id, execution_id, checkpoint_version, state_snapshot, state_hash, reason, resume_token_hash, status, created_at, resumed_at, resumed_by`。
10. `ai_page_object`：
    `id, organization_id, project_id, name, route_pattern, allowed_origins, status, version, create_user, update_user, create_time, update_time`。
11. `ai_page_element`：
    `id, page_object_id, name, strategy, selector_value, fallback_locators, sensitive, risk_level, version, create_user, update_user, create_time, update_time`。
12. `ai_test_data_lease`：
    `id, task_id, execution_id, project_id, dataset_id, data_key, namespace, status, lease_token_hash, expires_at, released_at, version, create_time, update_time`。
13. `ai_test_data_cleanup`：
    `id, lease_id, cleanup_type, status, attempt_count, next_retry_at, error_code, error_message, created_at, finished_at`。
14. `ai_step_approval`：
    `id, task_id, execution_id, step_id, action_hash, environment_id, status, expires_at, consumed_at, approved_by, created_at`；唯一索引 `(task_id,step_id,action_hash,environment_id)`。

### Mapper/领域对象位置

- 领域对象及 Example/Mapper 生成物：`backend/framework/domain/src/main/java/io/metersphere/agent/domain/` 与对应 mapper 资源位置，遵循项目生成规范。
- 业务查询写入 `backend/services/agent-integration/src/main/java/io/metersphere/agent/mapper/`，分别新增 `AgentEnvironmentProfileMapper`, `AgentCredentialReferenceMapper`, `AgentPreflightMapper`, `AgentModelProfileMapper`, `AgentModelInvocationMapper`, `AgentCheckpointMapper`, `AgentPageObjectMapper`, `AgentTestDataMapper`。
- 每个 Mapper 至少实现 `insert`, `selectByIdAndProject`, `updateByIdAndVersion`, `page/list`, `softDisable`；Lease/人工请求额外提供 CAS SQL。

### 验收

- 新库初始化和代表性旧库升级均成功；唯一索引能阻止重复触发、重复响应和重复 Lease。
- 所有 Secret 字段只存引用或密文，查询 DTO 不返回 `secret_ref` 全值。

## DT-03 环境 AI 执行 Profile

**优先级/性质：** P0，新增。

### 后端 DTO

- `AgentEnvironmentProfileRequest`：`id?, projectId, environmentId, name, baseUrl, allowedOrigins[], networkZone, environmentType, loginProfileId?, defaultCredentialReferenceId?, runnerType, requiredCapabilities[], productionAllowed=false, enabled, version?`。
- `AgentEnvironmentProfileDTO`：请求字段 + `organizationId, createUser, updateUser, createTime, updateTime`；凭据只返回 ID、名称、状态摘要。
- `AgentEnvironmentVerifyRequest`：`profileId, targetUrl?, runnerId?`。
- `AgentEnvironmentVerifyResult`：`reachable, originAllowed, dnsResolved, tlsValid, runnerMatched, checks[], traceId`。

### 后端文件与函数

- 新增 `controller/AgentEnvironmentProfileController.java`：
  `list(projectId)`, `get(id)`, `create(request)`, `update(id,request)`, `verify(id,request)`, `enable(id)`, `disable(id)`。
- 新增 `service/AgentEnvironmentProfileService.java`：
  `create`, `update`, `getAuthorized`, `listAuthorized`, `verify`, `resolveForTask`, `assertTargetAllowed`, `freezeSnapshot`。
- `assertTargetAllowed` 校验 scheme、host、port、重定向后 Origin，拒绝 `file:`, `data:`, loopback、云元数据地址和未授权内网地址。

### 前端位置

- 新增 `frontend/src/views/agent/environment-profile/index.vue`、`components/ProfileForm.vue`、`components/VerifyResult.vue`。
- `frontend/src/router/routes/modules/agent.ts` 增加 `environment-profile` 路由，角色 `AI_EXECUTION:READ`，编辑按钮要求 `AI_EXECUTION:RUN` 或专用管理权限。
- `frontend/src/api/modules/ai-execution.ts` 增加对应类型及 `list/get/create/update/verify/enable/disableEnvironmentProfile`。
- 表单状态覆盖 loading、empty、validation、403、409、网络错误、服务端错误；`productionAllowed` 首期固定不可开启。

## DT-04 凭据引用、Secret Provider 与运行时取密

**优先级/性质：** P0，新增。

### 后端 DTO 与 SPI

- `AgentCredentialReferenceRequest`：`projectId, environmentId, name, credentialType, businessRole, providerType, secretRef, usernameHint?, expiresAt?, enabled, version?`。
- `AgentCredentialReferenceDTO`：不含完整 `secretRef`；字段为 `id, projectId, environmentId, name, credentialType, businessRole, providerType, secretVersion, usernameHint, status, expiresAt, lastVerifiedAt, lastVerifyStatus, lastVerifyMessage, enabled, version`。
- `SecretResolveContext`：`taskId, executionId, projectId, environmentId, credentialReferenceId, purpose, traceId`。
- `ResolvedSecret`：仅 Runner 进程内使用，`username, password/token, version, expiresAt`，禁止序列化和日志输出。
- 新增 `AgentSecretProvider` SPI：`supports(providerType)`, `verify(reference, context)`, `resolve(reference, context)`, `revokeLease(context)`。
- 首期实现 `EnvSecretProvider`（受控白名单）和 `VaultSecretProvider`；生产默认禁用 `env://`。

### 服务和接口

- 新增 `AgentCredentialReferenceController`: `list`, `getMetadata`, `create`, `update`, `verify`, `enable`, `disable`。
- 新增 `AgentCredentialReferenceService`: `create`, `update`, `getAuthorizedMetadata`, `verify`, `assertUsable`, `resolveForRunner`, `maskReference`, `auditAccess`。
- `AgentRunnerInternalController` 新增 `POST /internal/ai/runner/tasks/{taskId}/credentials/{referenceId}/resolve`，必须校验有效 Lease、执行通道、环境绑定、用途和 Runner 身份；响应走一次性加密通道或短期凭据，不可被普通 REST/MCP 调用。
- 日志过滤器补 `Authorization`, `Cookie`, `Set-Cookie`, `password`, `passwd`, `token`, `secret`, OAuth code 和业务敏感字段。

### 前端

- 新增 `frontend/src/views/agent/credential-reference/index.vue` 与表单/验证结果组件。
- Secret 输入只在创建/更新时提交，不回显；成功后清空 DOM 状态；列表显示状态、过期时间、最后验证时间。

### 验收

- 普通用户、Personal Token、模型 Prompt、任务快照均无法取得 Secret。
- 过期、禁用、环境不匹配、角色不匹配、Provider 超时均返回独立安全错误码并阻塞任务。

## DT-05 统一 Preflight、范围解析与不可变快照

**优先级/性质：** P0，新增与改造。

### DTO

- `AgentExecutionPreflightRequest`：`projectId, testPlanId?, caseIds?, caseFilter?, environmentProfileId, credentialReferenceId?, modelProfileId?, promptTemplateId?, runnerType, requiredCapabilities[], policy, taskOrigin`。
- `AgentPreflightCheckDTO`：`code, status(PASSED/BLOCKED/WARNING), message, details, checkedAt`。
- `AgentExecutionPreflightDTO`：`id, status, checks[], resolvedCaseIds[], originalScopeCount, expandedScopeCount, scopeExpansionRate, snapshotHash, blockedReason, traceId, expiresAt`。

### 服务函数

- 新增 `AgentExecutionPreflightService.preflight(request, actor)`。
- 内部分解：`resolveScope`, `checkPublishedAssets`, `checkEnvironment`, `checkTarget`, `checkModel`, `checkQuota`, `checkRunner`, `checkCredential`, `checkTestData`, `checkCleanupPolicy`, `checkRiskPolicy`, `persistResult`。
- `AgentExecutionSnapshotService.freezeAssets(...)`：冻结用例、步骤、文档、环境、页面对象、数据集的最新 `PUBLISHED` 版本。
- `AgentExecutionSnapshotService.freezePolicies(...)`：冻结 Model Profile、Prompt、Runner 能力、证据、风险和通知策略，不含 Secret。
- `AgentExecutionContextService.build(...)` 输出版本化上下文包，计算 SHA-256；读取时再次校验 hash。
- `AgentExecutionService.create()` 必须接收有效 `preflightId`；核验请求 hash、有效期、项目和用户一致后才建任务。
- `AgentExecutionController` 新增 `POST /ai/execution/preflight`。

### 15% 扩围算法

- `maxAdded = ceil(originalScopeCount * 0.15)`。
- 仅允许同 `projectId + environmentProfileId + credentialReferenceId`。
- 候选必须是已发布、低风险、满足 Runner 能力、未超 `maxCases` 的用例。
- 保存 `originalCaseIds`, `addedCaseIds`, `reasonByCase`, `scopeExpansionRate`；超限直接 `BLOCKED_SCOPE`，不截断后静默执行。

## DT-06 测试资产、页面对象、业务流与可执行性

**优先级/性质：** P0/P1，扩展。

### 资产字段

- 扩展资产类型：`CASE`, `DOCUMENT`, `PLAN`, `ENVIRONMENT`, `DATASET`, `PAGE_OBJECT`, `BUSINESS_FLOW`, `COMMON_STEP`, `API_DEFINITION`。
- 用例可执行属性 DTO：`automationReadiness(NOT_READY/PARTIAL/READY), environmentProfileId, credentialRole, pageObjectIds[], datasetIds[], businessFlowId?, riskLevel, missingItems[], lastCheckedAt, checkerVersion`。
- 页面对象字段见 DT-02；业务流版本内容至少包含 `nodes[], edges[], entryNodeId, exitConditions[], allowedActions[]`。

### 后端函数和位置

- 扩展 `TestAssetController`：资产类型分页、详情、版本、关系接口保持统一。
- 扩展 `TestAssetCatalogService`: `searchByTypes`, `getExecutableContext`, `assertProjectAccess`。
- 扩展 `TestAssetVersionService`: `latestPublished`, `publish`, `deprecate`, `freezeVersions`。
- 新增 `AgentPageObjectService`: `createPage`, `updatePage`, `addElement`, `updateElement`, `publish`, `resolveLocator`。
- 新增 `AgentCaseExecutabilityService`: `check(caseId, environmentProfileId)`, `batchCheck`, `calculateReadiness`, `listMissingItems`。
- `TestAssetMapper.java/.xml` 增加按项目、类型、版本状态、关系方向的游标分页查询，所有查询带组织/项目条件。

### 前端

- 测试资产模块增加“页面对象”和“AI 可执行性”入口；复用现有用例详情，不创建孤立页面。
- 用例列表显示 readiness 与缺失项；批量检查只提交 ID，不把用例正文回传前端再计算。

## DT-07 MAP Gateway 复用适配

**优先级/性质：** P0，新增适配；不得建设第二网关。

### 内部 DTO

- `GatewayPlanningRequest`：`appCaller, logicalModelPublicId, promptPolicyId, promptVersionId, messages, outputSchema, temperature?, maxOutputTokens, timeoutMs, idempotencyKey, traceId, metadata{tenantId,projectId,taskId,businessType}`。
- `GatewayPlanningResponse`：`gatewayRequestId, content, structuredOutput, resolvedOffering, modelVersion, finishReason, usage{inputTokens,outputTokens,reasoningTokens,cachedTokens}, cost{amount,currency}, retries, ttftMs, durationMs`。
- 不把 Service Key 放入 DTO；由 `gatewayServiceKeyRef` 在服务端连接层解析。

### 文件与函数

- 新增 `service/gateway/MapGatewayClient.java`: `invokeStructured`, `health`, `capabilities`。
- 新增 `service/gateway/MapGatewayRequestMapper.java`: `toGatewayRequest`, `normalizeResponse`, `mapError`。
- 新增 `AgentModelProfileController`: `list`, `get`, `create`, `update`, `verify`, `enable`, `disable`, `capabilities`, `health`。
- 新增 `AgentModelProfileService`: `resolveAuthorized`, `freeze`, `verify`, `assertCapabilities`, `assertBudget`。
- 新增 `AgentModelInvocationService`: `start`, `recordGatewayAccepted`, `recordUsage`, `recordFailure`, `finish`。
- 配置项建议位于 agent-integration 配置：`mapGateway.baseUrl`, `connectTimeoutMs`, `requestTimeoutMs`, `healthTimeoutMs`；Service Key 只配置引用。
- 增加架构测试：`agent-integration` 业务包不得依赖 OpenAI/Claude/Gemini SDK，不得出现 Provider Base URL 调用。

## DT-08 模型规划、Prompt 版本和执行契约

**优先级/性质：** P0，改造。

### Execution Contract v1 字段

- 根：`contractVersion, taskId, snapshotHash, scope{caseIds,addedCaseIds}, environmentProfileVersion, credentialRole, runnerRequirements, cases[], generatedAt`。
- Case：`caseId, assetVersionId, name, steps[], cleanupActions[]`。
- Step：`stepId, action, assertions[], onFailure, evidencePolicy`。
- Action：复用 `AgentWebActionDTO` 并补 `id, valueRef, fileRef, idempotencyKey`；禁止任意脚本字段。
- Locator：复用 `AgentWebLocatorDTO`，`strategy` 限 `TEST_ID/ROLE/LABEL/PLACEHOLDER/TEXT/CSS`；CSS 受限，禁止 XPath 首选。
- Assertion：复用 `AgentWebAssertionDTO`，至少一个确定性断言；类型白名单。

### 函数与位置

- 改造 `AgentExecutionPlanningService.plan(...)` 为：
  `preparePlanningInput`, `resolvePromptVersion`, `invokeGateway`, `parseStructuredOutput`, `repairOnceIfAllowed`, `validateContract`, `persistContract`, `blockTask`。
- 新增 `AgentPromptTemplateService`: `createVersion`, `publish`, `preview`, `rollback`, `latestPublished`, `freeze`。
- 扩展 `AgentWebExecutionContractValidator`：
  `validateSchemaVersion`, `validateActions`, `validateAssertions`, `validateOrigins`, `validateLocators`, `validateValueRefs`, `validateUploads`, `validateTimeouts`, `validateScopeExpansion`, `validateRisk`, `validateNoInlineSecret`。
- JSON Schema 文件放在 `backend/services/agent-integration/src/main/resources/ai-contract/execution-contract-v1.schema.json`。
- 解析失败、字段缺失、Schema 错误只允许受预算限制的单次修复；仍失败则 `AGENT_PLAN_FAILED/NEEDS_REVIEW`，绝不生成默认成功计划。

## DT-09 平台 Trigger、Scheduler、Webhook 与冻结

**优先级/性质：** P0，改造。

### 请求字段

- 扩展 `AgentTaskTriggerRequest`：`projectId, name, triggerType, cronExpression?, timezone?, eventType?, eventFilter?, concurrencyPolicy, missedPolicy, testPlanId, caseFilter, environmentProfileId, modelProfileId, promptTemplateId, contractVersion, runnerType, requiredCapabilities[], credentialReferenceId?, policy, evidencePolicy, notificationPolicy, responsibleUserIds[3], enabled, version?`。
- `AgentTaskTriggerDTO` 同步展示字段，但不返回 Webhook Secret；仅 `secretConfigured` 和轮换时间。

### 函数改造

- `AgentTaskTriggerService.create/update`：校验项目权限、Cron/时区、三位责任人、Profile 绑定、生产禁用和版本乐观锁。
- `manualFire(id)` 使用 `PLATFORM_MANUAL + MODEL_API_RUNNER`。
- `fireDueCronTriggers()` 使用 `PLATFORM_SCHEDULED + MODEL_API_RUNNER`，调用统一 `fire(trigger,scheduledAt,eventId)`。
- 新增内部函数：`buildIdempotencyKey`, `checkConcurrencyPolicy`, `resolveMissedFire`, `freezeTriggerVersion`, `runPreflight`, `createHistory`, `notifyBlockedUsers`。
- Webhook 增加 `verifySignature`, `verifyTimestampWindow`, `claimEventId`, `rotateSecret`；重复事件返回原历史结果。
- `AgentTaskTriggerMapper` 增加 `selectDueForUpdate`、`compareAndSetNextFireAt`、`insertHistoryIdempotently`。

### 前端

- 从 `frontend/src/views/agent/queue.vue` 拆出 `trigger/TriggerList.vue`, `TriggerForm.vue`, `TriggerHistory.vue`，避免单文件继续膨胀。
- 表单增加环境、模型、Prompt、Runner、凭据、策略、证据、三位责任人；保存前调用 Preflight preview。

## DT-10 平台 Runner 调度和确定性执行

**优先级/性质：** P0，改造。

### Runner 字段与匹配

- Runner DTO 补：`runnerType, capabilities[], networkZone, browserTypes[], maxConcurrency, activeCount, isolationMode, version, healthStatus, lastHeartbeatAt`。
- `AgentRunnerService.match(task)` 按通道、类型、能力、网络区、浏览器、隔离模式和容量匹配。
- `AgentTaskClaimService.claim()`：Runner 只能领取 `MODEL_API_RUNNER`；个人 MCP 只能领取 `EXTERNAL_MCP_AGENT`。

### 执行函数

- `AgentTaskExecutionApplicationService.startExecution(leaseId, token)` 创建 attempt 并校验冻结契约 hash。
- `resolveCredential`, `prepareContext`, `executeAction`, `evaluateAssertions`, `captureEvidence`, `submitStepResult`, `runCleanup`, `completeExecution`。
- 每次写入都校验 `taskId + executionId + leaseId + leaseToken + requestId`；Lease 失效后拒绝事件、证据和结果。
- 高风险步骤：不执行，写入 `SKIPPED_REVIEW_REQUIRED`，创建 Human Request；禁止自动重试。
- 取消、超时、Lease 过期必须关闭 Browser Context/API Context 并触发清理。

## DT-11 MCP 资产发现、严格 Schema 与结构化响应

**优先级/性质：** P0，改造。

### 新增工具

- `metersphere.asset.catalog.search`
- `metersphere.asset.get`
- `metersphere.asset.version.get`
- `metersphere.asset.relation.list`
- `metersphere.environment.profile.list`
- `metersphere.environment.profile.get`
- `metersphere.credential.metadata.list`
- `metersphere.execution.preflight`
- P1：`metersphere.page_object.get`, `metersphere.business_flow.get`, `metersphere.dataset.metadata.get`

### Tool 输入字段

- 所有查询：`projectId` 必填；分页统一 `cursor?, limit<=100`。
- 资产搜索：`assetTypes[], keyword?, status=PUBLISHED, updatedAfter?`。
- 详情：`assetType, assetId, versionId?`。
- 凭据元数据：`environmentProfileId, businessRole?`，输出严禁 `secretRef`。
- Preflight：复用 DT-05 请求，Personal 通道强制 `taskOrigin=PERSONAL_MCP`。

### 代码改造

- `BuiltinAgentMcpToolConfig` 为每个 Tool 提供 `additionalProperties:false`、required、enum、长度/数值限制及 output schema。
- `AgentMcpToolHandler` 增加 `outputSchema()`、`readOnlyHint()`, `destructiveHint()`, `idempotentHint()`, `openWorldHint()`。
- `AgentMcpStreamableService.handle()` 返回 `content` 与 `structuredContent`；协议协商不硬编码单一版本，记录客户端版本。
- 工具列表按 Token Scope、项目授权和通道过滤；执行时再次做同样授权，不能依赖隐藏。
- 分页结果统一：`items, nextCursor, hasMore, traceId`。

## DT-12 MCP 控制闭环、Scope、幂等和 Trigger 边界 B01

**优先级/性质：** P0/P1，改造。

### B01 必改位置

- 从普通 Personal Token 的 `tools/list` 移除现有：
  `metersphere.execution.trigger.create/update/list/fire`。
- `BuiltinAgentMcpToolConfig.executionTriggerCreateTool/updateTool/listTool/fireTool` 不得仅靠 UI 隐藏；改为只注册到独立平台服务身份 Registry，或在 handler 首行调用 `assertPlatformAutomationManage()`。
- `AgentTokenScope` 增加 `PLATFORM_AUTOMATION_MANAGE`，普通个人 Token 创建/更新接口禁止选择该 Scope。
- `AgentMcpStreamableService` 收到直接构造的 Trigger tool name 时返回 `MCP_TOOL_FORBIDDEN` 并记录审计。
- 可选新增只读 `metersphere.automation.summary.list`，输出仅 `triggerId,name,projectId,type,enabled,nextFireAt,lastStatus,scopeSummary`。

### Scope 修正

- `execution.cancel` 要求 `AI_EXECUTION_CANCEL`，不再使用 `AI_EXECUTION_RUN`。
- 登录恢复相关 Tool 要求 `AI_EXECUTION_LOGIN`。
- Claim/heartbeat/release/step/complete 要求执行型 Scope，并校验 Lease owner。
- 资产读取要求 `AI_ASSET_READ`；凭据只允许 `AI_CREDENTIAL_READ_METADATA`。

### 强制幂等

- 以下写 Tool 必须有 `requestId`/`Idempotency-Key`：task create、claim、release、event batch、step submit、complete/fail、artifact prepare/commit、human request create/respond、case/bug writeback。
- `AgentIdempotencyService.execute(scope, actorId, toolName, key, requestHash, supplier)`；同键同体返回原结果，同键异体返回 `IDEMPOTENCY_CONFLICT`。

### 控制查询工具

- 补 `execution.pause`, `execution.checkpoint.create`, `execution.retry`, `human_request.list/respond`, `artifact.list`, `result.get`, `writeback.status.get`。
- 所有查询检查 task 的 `PERSONAL_MCP + EXTERNAL_MCP_AGENT` 归属；不得查看平台任务敏感配置。

## DT-13 自动登录、人工协同、三人通知与检查点

**优先级/性质：** P0/P1，新增与改造。

### 登录

- `AgentLoginProfileService.resolve`, `validate`, `freeze`, `verifySession`。
- Runner 执行 `loginUrl -> username/password locator -> submit -> success assertion`；值只来自运行时 Secret。
- 定时任务遇到 MFA、验证码、MANUAL 登录立即：`WAITING_HUMAN/BLOCKED_CREDENTIAL`，写日志与原因，通知责任人，保存检查点，释放 Lease；不得等待个人 Agent。

### Human Request

- 请求 DTO 补 `recipientUserIds`（必须恰好 3 个不同且有效用户）、`checkpointRequired`, `expiresAt`, `actionHash?`。
- `AgentHumanRequestService.createForRecipients(...)` 同事务写主表和三条 recipient。
- `respondFirstWins(requestId,userId,response,expectedVersion)` 用 CAS：仅 `PENDING` 可完成；第二、三人收到 `ALREADY_RESOLVED` 和首位响应摘要。
- `expirePendingRequests(now)`：过期后任务按策略进入 `BLOCKED/EXPIRED`，不得自动批准。
- 通知调用平台既有通知服务：`notifyAllRecipients`, `notifyResolution`, `notifyBlockedExecutor`。

### 检查点

- `AgentExecutionCheckpointService.create`, `verifyHash`, `resume`, `expire`。
- 创建检查点后 `AgentTaskClaimService.release(..., HUMAN_WAIT)`；恢复时重新 Preflight 凭据、环境、策略和权限，再创建新 execution attempt。

## DT-14 测试数据、证据、结果、评价与缺陷闭环

**优先级/性质：** P1，改造与新增。

### 数据租约函数

- `AgentTestDataLeaseService.acquire(taskId,datasetId,key,ttl)`, `heartbeat`, `release`, `reclaimExpired`。
- `AgentTestDataCleanupService.enqueue`, `execute`, `retryDue`, `markFailed`。
- 数据和凭据必须按 task/execution 隔离；清理失败不得把产品用例误判为通过。

### 证据

- 扩展 `AgentExecutionArtifactService.prepare/commit/list/download`：校验 MIME、大小、SHA-256、幂等键、Lease 和保留期。
- 新增 `AgentEvidenceRedactionService.redactHeaders`, `redactBody`, `redactScreenshot`, `scanBeforePersist`。
- 截图敏感选择器打码；HAR/Console 在上传前脱敏。

### 结果与缺陷

- `AgentExecutionStepResultService.submit` 实施 requestId 唯一和重复终态保护。
- `AgentExecutionWritebackService.writeback` 以 `taskId+caseId+executionId` 幂等。
- 新增 `AgentFailureClassifier.classify(stepResults, artifacts, runtimeErrors)`，输出业务结论与证据充分度。
- 只有 `PRODUCT_FAILED` 且证据充分才能调用 `AgentBugWriteService` 创建缺陷草稿；其他失败进入对应处理队列。
- `AgentEvaluationService` 增加模型规划失败率、Runner 失败率、人工介入率、扩围率、证据完整率。

## DT-15 前端完整用户链路

**优先级/性质：** P0/P1，改造。

### 路由与页面

- 修改 `frontend/src/router/routes/modules/agent.ts`，加入：环境 Profile、凭据引用、模型 Profile、Prompt 版本、执行详情；Trigger 仅平台权限可见。
- `frontend/src/views/agent/queue.vue` 保留任务队列壳，拆出 Trigger、Preflight、任务表格。
- 新增 `frontend/src/views/agent/execution/detail.vue`：范围与扩围、冻结版本、模型 invocation、Schema 校验、Runner/Lease、步骤、人工请求、证据、清理、结果、traceId。
- `frontend/src/views/agent/capability.vue` 展示 Runner 与模型能力，不展示 Secret。
- `frontend/src/views/agent/access.vue` 明确 Personal Token 无 Trigger 管理 Scope。

### API 函数

在 `frontend/src/api/modules/ai-execution.ts` 增加：

- `preflightAiExecution`, `list/create/update/verifyEnvironmentProfile`
- `list/create/update/verifyCredentialReference`
- `list/create/update/verifyModelProfile`
- `listPromptPolicies`, `publish/preview/rollbackPromptVersion`
- `getModelInvocation`, `getModelUsage`
- `getExecutionContract`, `getExecutionPreflight`, `getWritebackStatus`
- `pause/resume/retryExecution`, `list/respondHumanRequest`

每个函数在 `frontend/src/api/requrls/ai-execution.ts` 有唯一 URL 常量，接口字段与 Java DTO 完全一致。

### 交互验收

- 所有页面覆盖 loading、empty、success、validation-error、permission-denied、conflict、network-error、timeout、server-error。
- Preflight 不通过时禁用创建，并展示用户可理解的阻塞原因和 `traceId`。
- Trigger 表单强制三位责任人；生产环境不可选；MANUAL 登录 Profile 对定时任务不可选。
- Secret 永不回显，不进入 DOM、URL、本地存储或埋点。

## DT-16 可观测性、审计、预算、告警与运维

**优先级/性质：** P0/P1，新增与改造。

### 统一 Trace 字段

- 全链路记录：`traceId, taskId, executionId, triggerId, gatewayRequestId, runnerId, leaseId, actorType, actorId, requestId`。
- 模型指标：逻辑模型、Offering 快照、TTFT、总耗时、四类 Token、缓存命中、重试、费用、finishReason、错误码。
- Runner 指标：在线数、容量、领取延迟、Lease 过期率、步骤吞吐、Browser 泄漏、清理积压。
- 业务指标：成功率、失败分类、人工介入、扩围、证据完整、回写失败。

### 运维函数

- `AgentExecutionMetrics.record*`；`AgentExecutionAuditService.record(actor,action,target,before,after,traceId)`。
- `AgentBudgetGuard.checkBeforeInvoke`, `recordAfterInvoke`, `blockOnExceeded`；以 Gateway Usage 为准并保存任务归因。
- `AgentExecutionReconciler.reconcileStaleTasks`, `reconcileExpiredLeases`, `reconcilePendingArtifacts`, `reconcileCleanupBacklog`。
- 告警：Gateway 不可用/余额或预算不足、Runner 离线、凭据即将过期、任务阻塞、人工超时、清理积压、回写失败。

### 配置项

- Scheduler scan interval、Lease TTL/heartbeat、Preflight TTL、模型连接/整体超时、最大规划重试、人工超时、Artifact 大小/MIME/保留期、数据清理重试、Webhook 时间窗。
- 所有配置提供默认值、环境覆盖说明和启动校验；非法配置启动失败并给出安全消息。

## DT-17 自动化测试、构建、容器与真实 E2E

**优先级/性质：** P0，交付门禁。

### 后端单元/集成测试文件

- 扩展 `AgentExecutionServiceTests`, `AgentTaskClaimServiceTests`, `AgentTaskTriggerServiceTests`, `AgentMcpStreamableServiceTests`。
- 新增 `AgentExecutionChannelPolicyTests`, `AgentExecutionPreflightServiceTests`, `AgentCredentialReferenceServiceTests`, `AgentEnvironmentProfileServiceTests`, `MapGatewayClientContractTests`, `AgentExecutionPlanningServiceTests`, `AgentWebExecutionContractValidatorTests`, `AgentHumanRequestConcurrencyTests`, `AgentTestDataLeaseServiceTests`, `AgentEvidenceRedactionServiceTests`。
- Controller 集成测试覆盖真实数据库、权限、统一错误结构和 traceId。

### 必测场景

1. 三个合法通道组合和三个非法组合。
2. Personal Token 对 Trigger list/create/update/fire：工具不展示且直接调用被拒绝并审计。
3. Trigger Cron 窗口、Webhook eventId、写 Tool requestId 并发幂等。
4. 15% 扩围的 0、边界、ceil、越界、跨项目/环境/认证场景。
5. Gateway 超时、429、余额不足、无结构化输出、Schema 修复失败；均不得直连 Provider。
6. Runner 离线、能力不匹配、Lease 过期后继续写入、取消和重领。
7. 凭据过期、错误环境、MFA；定时任务阻塞、通知并释放 Lease。
8. 三人并发响应只允许一人成功。
9. 截图/HAR/日志 Secret 脱敏与 Artifact hash 校验。
10. 只有证据充分的 `PRODUCT_FAILED` 创建缺陷草稿。

### 前端与 E2E

- 前端类型检查、lint、生产构建；组件测试覆盖表单校验和九类 UI 状态。
- 核心 E2E A：平台 Trigger → Preflight → MAP Gateway 规划 → Runner → 证据 → 回写。
- 核心 E2E B：Personal MCP → 资产发现 → Preflight → Claim/Lease → 本地工具执行 → MCP 回写。
- 核心 E2E C：MFA/高风险 → 三人通知 → 首人响应/阻塞 → 检查点恢复。
- Docker 构建平台与 Runner 镜像，启动依赖并执行健康检查；记录准确命令、退出码和报告路径。

---

## 5. REST/MCP 接口最终清单

### 平台 REST 控制面

| 资源 | 接口 |
|---|---|
| 环境 Profile | `GET/POST /ai/environment-profiles`, `GET/PUT /{id}`, `POST /{id}/verify|enable|disable` |
| 凭据引用 | `GET/POST /ai/credential-references`, `GET/PUT /{id}`, `POST /{id}/verify|enable|disable` |
| 模型 Profile | `GET/POST /ai/model-profiles`, `GET/PUT /{id}`, `POST /{id}/verify|enable|disable`, `GET /{id}/capabilities|health` |
| Prompt | `GET /ai/prompt-policies`, `POST /{id}/versions|preview|rollback` |
| Trigger | 复用 `/ai/execution/triggers` 的 list/create/get/update/fire/rotate-secret/history/webhook |
| 执行 | `POST /ai/execution/preflight`, 复用 task create/search/get/control/events/human/artifacts |
| 模型审计 | `GET /ai/model-invocations/{id}`, `GET /ai/model-usage` |

### Personal Remote MCP 数据面

| 类别 | 允许 | 禁止 |
|---|---|---|
| 资产 | 资产/版本/关系/环境非敏感配置/凭据元数据读取 | Secret 读取、Gateway 配置读取 |
| 执行 | PERSONAL_MCP 的 resolve/preflight/create/search/get/claim/lease/events/step/artifact/human/result | 平台任务 Claim/控制 |
| 自动化 | 可选脱敏 `automation.summary.list` | Trigger list/create/update/fire |

## 6. 方案冲突检查结论

两份方案不存在必须由用户再次决策的业务冲突，前提是实现严格遵守双通道边界。需要防止的实现冲突如下：

| 潜在冲突 | 处理结论 |
|---|---|
| 两套方案都需要执行任务 | 共用 `ai_execution_task` 和状态机，用 `task_origin/executor_channel` 隔离 |
| 两套方案都需要 Runner/Lease | 共用 Lease 协议，但 Personal MCP 的 owner 是个人 Token，平台任务 owner 是 Runner |
| 两套方案都读取环境/资产/凭据 | 共用事实与 Preflight；REST 为平台视图，MCP 为脱敏视图 |
| MCP 曾暴露 Trigger | 按 B01 从普通 Personal Token 完全禁止，平台继续通过 UI/REST 管理 |
| 平台模型规划与个人 Agent 推理 | 个人 Agent 不经过平台模型规划；平台任务必须经过 MAP Gateway |
| 人工等待可能长期占用 Runner | 统一检查点后释放 Lease，恢复时重新校验 |

## 7. 研发批次与完成口径

### 批次 A：安全主链路（P0）

DT-01～DT-05、DT-07～DT-12、DT-13 的登录阻塞部分、DT-15 的 P0 页面、DT-16 基础 Trace、DT-17 核心 E2E A/B。完成后仅可宣称“只读测试双通道主链路完成”。

### 批次 B：稳定闭环（P1）

DT-06 页面对象/可执行性、DT-13 完整人工协同、DT-14、DT-15 完整详情、DT-16 告警对账、DT-17 E2E C。

### 批次 C：规模化治理（P2）

智能选例、容量调度、凭据轮换、失败聚类、趋势、协议兼容矩阵和事件推送评估。

任一任务只有在字段、后端、前端、权限、迁移、测试、构建、容器和相应 E2E 均有证据时才可标记完成；否则必须标记“部分完成”或“阻塞”。

## 8. 需求追踪与当前状态

| 原方案需求 | 细化任务 | 前端入口 | 后端主位置 | 数据/配置 | 测试证据要求 | 当前状态 |
|---|---|---|---|---|---|---|
| 双通道隔离 | DT-01、DT-10、DT-12 | Agent 队列/集成 | `AgentExecutionService`、`AgentTaskClaimService`、MCP Registry | `ai_execution_task.task_origin/executor_channel` | 6 组合权限测试 | 部分完成 |
| 环境与目标定位 | DT-03、DT-05 | 环境 Profile | 新 Profile Controller/Service | `ai_environment_execution_profile` | Origin/SSRF/重定向 | 未完成 |
| account_secret 安全使用 | DT-04、DT-13 | 凭据引用 | Credential Service、Secret Provider SPI、Runner internal API | `ai_credential_reference`、Vault | 取密、过期、泄露扫描 | 未完成 |
| 资产、页面对象、业务流 | DT-06、DT-11 | 测试资产/用例详情 | `TestAsset*`、Page Object、MCP Tool | 资产版本/关系、page object | 版本/权限/分页 | 部分完成 |
| Preflight 与冻结 | DT-05 | 创建任务/Trigger 表单 | Preflight、Snapshot、Context | `ai_execution_preflight`、task snapshot | 全检查项和 hash | 未完成 |
| MAP Gateway 模型规划 | DT-07、DT-08 | 模型 Profile/调用详情 | Gateway Client、Planning Service | model profile/invocation/prompt version | Gateway 合同和故障 | 未完成 |
| 平台定时/事件/手工触发 | DT-09 | 调度队列/Trigger | `AgentTaskTriggerController/Service/Mapper` | trigger/history 扩展 | Cron/Webhook 幂等 | 部分完成 |
| Runner 确定性执行 | DT-10 | Runner/执行详情 | Runner、Claim、Application Service | attempt/lease/step result | Lease 过期/取消/清理 | 部分完成 |
| MCP 资产与执行闭环 | DT-11、DT-12 | Token Scope/执行详情 | Streamable Service、Builtin Tools | 幂等与审计 | MCP 合同/授权 | 部分完成 |
| B01 Trigger 边界 | DT-12 | Agent 集成 | Tool Registry、Scope Assert | Scope/审计 | 隐藏+直接调用拒绝 | 已确认待实施 |
| 三人协同与检查点 | DT-13 | 人工请求 | Human Request、Checkpoint、Lease | recipient/checkpoint | 三人并发 CAS | 未完成 |
| 数据、证据、结果、缺陷 | DT-14 | 执行详情 | Data/Artifact/Writeback/Classifier | lease/cleanup/artifact | 脱敏/幂等/分类 | 部分完成 |
| 统一观测和告警 | DT-16 | 执行/模型调用详情 | Metrics/Audit/Reconciler | invocation/trace/config | 指标和告警演练 | 部分完成 |
| 可交付验证 | DT-17 | 全链路 | 全模块 | 迁移/容器 | 单元、集成、构建、E2E | 未执行 |

### 8.1 研发领取规则

每个 DT 拆成后端、前端、迁移、测试四个子任务时，必须保留同一 DT 编号，例如 `DT-05-BE`、`DT-05-FE`、`DT-05-DB`、`DT-05-QA`。后端子任务不得在依赖的迁移和权限合同未合入时标记完成；前端子任务不得以 Mock 或固定成功响应验收；QA 子任务必须记录命令、退出码、报告或截图位置。
