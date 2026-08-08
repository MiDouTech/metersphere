# AI Web UI Execution Contract V1

## 1. 兼容原则

- 契约版本为 `v1`，未知字段应被兼容忽略，未知枚举必须拒绝执行并返回 `UNSUPPORTED_CONTRACT_VALUE`。
- Runner 只上报事实事件；任务最终状态由 MeterSphere 服务端聚合和对账后决定。
- 时间统一使用 Unix epoch milliseconds，ID 使用 MeterSphere `IDGenerator`。
- 事件在单个任务内使用严格递增 `sequence`，重复的 `taskId + sequence` 幂等忽略，序号缺口必须报告。
- 任务创建、Runner 事件和结果回写分别使用独立幂等键。

## 2. 状态契约

### 2.1 任务状态

```text
CREATED → RESOLVING_SCOPE → WAITING_CONFIRMATION → QUEUED
→ PREPARING_BROWSER → WAITING_LOGIN → RUNNING ↔ PAUSED
→ WRITING_BACK → SUCCESS / PARTIAL_SUCCESS / FAILED
任意非终态 → CANCELED；租约长期不可恢复 → EXPIRED
```

允许按条件跳过可选状态，例如已确认的手工选例可以从 `CREATED` 进入 `QUEUED`，不需要登录时可以从 `PREPARING_BROWSER` 进入 `RUNNING`。

### 2.2 用例和步骤状态

```text
PENDING → RUNNING → HEALING
→ SUCCESS / FAILED / BLOCKED / SKIPPED / NEEDS_REVIEW / ERROR
```

`SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED/EXPIRED` 为任务终态；用例和步骤的六种结果状态为终态。

## 3. 动作契约

动作类型白名单：`NAVIGATE`、`CLICK`、`FILL`、`SELECT`、`CHECK`、`UPLOAD`、`KEYBOARD`、`WAIT`、`SCROLL`。

```json
{
  "contractVersion": "v1",
  "type": "FILL",
  "target": {
    "strategy": "ROLE_NAME",
    "role": "textbox",
    "name": "用户名"
  },
  "valueRef": "credential.username",
  "timeoutMs": 10000,
  "retryable": true,
  "riskLevel": "LOW"
}
```

定位策略白名单：`TEST_ID`、`ROLE_NAME`、`LABEL`、`PLACEHOLDER`、`TEXT`、`SEMANTIC`、`CSS`、`XPATH`。模型不得输出可执行 JavaScript。

## 4. 断言契约

断言类型白名单：`TEXT`、`VISIBLE`、`ENABLED`、`CHECKED`、`ATTRIBUTE`、`COUNT`、`URL`、`TITLE`。

断言结果包含 `passed`、`actual`、`expected`、`confidence` 和证据 ID。确定性断言不填写模型置信度；模型辅助判断低于项目阈值时必须转 `NEEDS_REVIEW`。

## 5. 事件契约

每条事件至少包含：

```text
contractVersion / eventId / taskId / caseId / stepId / attempt
sequence / eventTime / level / eventType / message
artifactIds / sanitizedMetadata
```

核心事件：`TASK_ACCEPTED`、`BROWSER_READY`、`LOGIN_REQUIRED`、`CASE_STARTED`、`STEP_STARTED`、`ACTION_COMPLETED`、`ASSERTION_FAILED`、`HEALING_STARTED`、`HEALING_COMPLETED`、`STEP_COMPLETED`、`CASE_COMPLETED`、`RUNNER_FAILED`、`TASK_EXECUTION_COMPLETED`。

## 6. 错误分类

- `SCOPE_*`：范围、项目、用例或权限错误。
- `RUNNER_*`：Runner 离线、租约、版本或浏览器错误。
- `NAVIGATION_*`：域名、导航、超时和证书错误。
- `LOCATOR_*`：未找到、不唯一、不可见或不可操作。
- `ASSERTION_*`：确定性断言失败或判断置信度不足。
- `SECURITY_*`：越权、SSRF、提示注入、敏感数据或高风险动作。
- `ARTIFACT_*`：证据采集、上传、关联或脱敏错误。
- `WRITEBACK_*`：结果回写、幂等或对账错误。

## 7. 完成语义

任务只有在全部用例进入终态、必需证据已持久化、结果回写完成且统计对账一致时才允许标记 `SUCCESS`。浏览器执行完成但证据或回写失败时必须为 `PARTIAL_SUCCESS` 或 `FAILED`。
