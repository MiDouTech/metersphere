# MeterSphere Agent 治理策略与审计中心设计方案

> 日期：2026-07-30  
> 页面定位：系统设置 → 系统 → Agent 治理策略与审计中心  
> 可见范围：仅系统管理员  
> 关联方案：`MeterSphere-Agent集成-个人Token与多Agent-MCP接入改造方案-2026-07-30.md`  
> 文档性质：产品与技术联合设计方案，不包含本次代码实施

## 1. 建设目标

“Agent 治理策略与审计中心”是 MeterSphere 管理员管理全站 Agent/MCP 风险的控制台，解决以下问题：

1. 统一控制 Agent 服务是否开放、允许连接的客户端、Token 生命周期、调用频率和高风险操作保护。
2. 管理员可以查看个人 Token 的安全元数据并强制吊销，但不能查看明文、代用户创建个人 Token 或修改其身份。
3. 对每一次 MCP 连接、鉴权和 Tool 调用形成结构化、可检索、可追溯的审计记录。
4. 自动识别暴力尝试、异常来源、越权调用、频率异常、批量写入和高失败率等风险。
5. 支持从告警快速定位用户、Token、项目、Tool 和业务资源，并执行限流、封禁或吊销。
6. 为安全复盘、合规取证和容量评估提供报表与导出能力。

治理边界：

- 普通用户仍可自行创建 Token 并选择包括 `AGENT_ALL` 在内的权限。
- `AGENT_ALL` 不突破用户现有 RBAC 和项目权限。
- 管理员治理用于全局安全保护、审计和应急处置，不允许管理员查看或盗用个人 Token。

## 2. 核心原则

| 原则 | 设计要求 |
|---|---|
| 仅管理员可见 | 菜单、路由、API 和导出接口均执行系统管理员鉴权 |
| 策略与审计分离 | 策略是可版本化配置；审计是不可随意修改的事实记录 |
| 权限不提升 | Token scope 只做权限收窄，不能绕过用户 RBAC |
| 用户自主选权 | 普通用户可选择 `AGENT_ALL`，管理员不能篡改其 Token scope；必要时可禁用服务或吊销 Token |
| 默认可解释 | 每次拒绝、限流、告警均记录命中的策略及原因 |
| 最小敏感数据 | 不记录完整 Token、认证请求头、附件内容和大段业务正文 |
| 审计不可抵赖 | 关键日志只追加，管理员处置另记审计，不直接修改原记录 |
| 集群一致 | 限流、封禁和策略版本必须在所有服务实例一致生效 |
| 快速止损 | 提供全局熔断、用户封禁、Token 吊销、Tool 暂停和 IP 封禁 |

## 3. 信息架构

页面建议命名：

```text
Agent 治理策略与审计中心
```

一级 Tab：

```text
概览
治理策略
Token 治理
调用审计
风险告警
处置记录
```

页面右上角固定展示：

- Agent 服务状态；
- 当前生效策略版本；
- 最近策略发布时间；
- “紧急停止 Agent 服务”按钮。

“紧急停止”属于高危管理员操作，要求二次确认并输入原因；执行后只阻断个人 Token/MCP 调用，不影响 Web 用户正常使用 MeterSphere。

## 4. 概览

### 4.1 核心指标

统计时间支持最近 1 小时、24 小时、7 天、30 天和自定义区间。

指标卡：

| 指标 | 定义 |
|---|---|
| Agent 服务状态 | 正常 / 部分限制 / 已停止 |
| 有效 Token | 状态为 ENABLED 且未过期的 Token 数 |
| 活跃用户 | 时间范围内成功调用过 MCP 的去重用户数 |
| 调用总量 | `tools/call` 请求数 |
| 成功率 | 成功 Tool 调用 / Tool 调用总数 |
| 写操作量 | 非只读 Tool 调用数 |
| 高风险调用 | `risk_level = HIGH/CRITICAL` 的调用数 |
| 拒绝次数 | 401、403、429及策略拒绝数 |
| 未处理告警 | OPEN/ACKNOWLEDGED 告警数 |
| P95 延迟 | Tool 调用端到端 P95 |

### 4.2 图表

- 调用趋势：成功、失败、拒绝、限流；
- Tool 调用排行；
- 用户调用排行；
- 客户端分布：Codex、ChatGPT、Cursor、WorkBuddy、Other；
- 项目调用分布；
- 风险等级分布；
- 状态码与错误类型分布。

图表均可点击下钻到“调用审计”，自动带入时间和筛选条件。

### 4.3 风险摘要

展示最近风险：

```text
[严重] 同一 Token 10 分钟内来自 4 个国家/地区
[高]   batch_create_functional_cases 单次创建 500 条用例
[中]   某用户连续 30 次调用无权限 Tool
[低]   Token 距离过期不足 7 天
```

每条可进入告警详情或直接执行处置。

## 5. 治理策略

### 5.1 策略分组

#### A. 服务开放策略

| 策略 | 默认建议 |
|---|---|
| 允许个人 Token | 开启 |
| 允许远程 MCP | 开启 |
| 允许的客户端 | Codex、ChatGPT、Cursor、WorkBuddy、Other |
| 强制 HTTPS | 开启 |
| 最低 TLS | TLS 1.2 |
| 未识别客户端 | 允许但标记 Other，或按企业策略拒绝 |

#### B. Token 生命周期

| 策略 | 默认建议 |
|---|---|
| 每用户最大有效 Token 数 | 10 |
| 默认有效期 | 90 天 |
| 最大有效期 | 180 天 |
| 允许永久 Token | 关闭 |
| 长期未使用自动禁用 | 90 天 |
| 到期提醒 | 7 天、1 天 |
| 轮换宽限期 | 24 小时 |

普通用户仍可自行选择 scope，包括 `AGENT_ALL`。治理中心不修改用户已选 scope，但管理员可查看、吊销 Token，或在紧急情况下暂停特定 Tool/全部 Agent 服务。

#### C. 调用与限流策略

策略维度：

- 全站；
- Token；
- 用户；
- IP；
- Tool；
- 项目；
- 读/写类型。

建议默认：

| 类型 | 每分钟 | 并发 | 单次批量 |
|---|---:|---:|---:|
| 查询 Tool | 60 | 5 | 100 条 |
| 普通写 Tool | 30 | 3 | 50 条 |
| 高风险 Tool | 10 | 1 | 20 条 |
| 文件上传 | 20 | 2 | 10 个/50 MB |

具体值需结合部署规模压测。限流必须使用 Redis/网关等集中式能力，不使用当前单 JVM 内存窗口作为生产最终方案。

#### D. Tool 保护策略

每个 Tool 可配置：

- 启用/暂停；
- 风险等级；
- 是否强制幂等键；
- 单次最大对象数；
- 最大请求体；
- 是否要求客户端审批提示；
- 是否记录资源 ID；
- 是否触发告警；
- 超时时间；
- 允许调用时间段。

注意：客户端审批不是服务端安全边界。即使客户端不支持审批，服务端仍执行 RBAC、项目、scope、限流和业务规则。

#### E. 网络与来源策略

- IP allowlist/denylist；
- 可信反向代理列表；
- 是否允许公网来源；
- 国家/地区风险规则（部署具备可靠 IP 地理库时）；
- 同一 Token 最大同时来源 IP 数；
- User-Agent/clientType 一致性检测；
- 跨地域短时间跳变告警。

#### F. 审计与留存策略

| 数据 | 在线留存 | 归档建议 |
|---|---:|---:|
| 调用审计 | 90 天 | 1 年 |
| 鉴权失败 | 30 天 | 180 天 |
| 风险告警 | 1 年 | 按合规要求 |
| 管理员处置 | 2 年 | 长期归档 |
| 请求/响应摘要 | 30～90 天 | 默认不归档正文 |

管理员可配置保留期，但不得低于企业合规基线。

### 5.2 策略版本

策略采用“草稿 → 校验 → 发布”流程：

```text
编辑草稿
  ↓
差异预览
  ↓
影响评估
  ↓
填写变更原因
  ↓
发布
  ↓
生成不可变版本
```

每个版本记录：

- version；
- 完整策略快照；
- 差异；
- 发布人；
- 发布时间；
- 变更原因；
- 生效状态；
- 回滚来源版本。

支持一键回滚到上一版本。发布和回滚均写管理员处置审计。

### 5.3 策略优先级

```text
紧急全局封禁
  > Tool 暂停
  > Token/User/IP 临时封禁
  > 全局治理策略
  > Token 自选 scopes
  > 用户 RBAC 与项目权限
```

授权结果仍为所有权限条件的交集。优先级用于解释“哪条限制先拒绝”，不表示上层策略可以授予下层没有的权限。

## 6. Token 治理

### 6.1 列表

管理员只查看元数据：

- Token 名称；
- 脱敏前缀；
- 所有者 ID、姓名和状态；
- 客户端；
- scopes；
- 项目范围；
- Token 状态；
- 创建/到期/最后使用时间；
- 调用次数；
- 最近来源 IP；
- 当前风险标签。

严禁返回：

- 完整 Token；
- `secret_hash/token_hash`；
- Authorization 或 X-API-Key。

### 6.2 筛选

- 用户/部门；
- Token 状态；
- 客户端；
- scope（含 `AGENT_ALL`）；
- 项目；
- 创建时间；
- 到期时间；
- 最后使用时间；
- 风险标签；
- 是否旧版 Token；
- 是否从未使用。

### 6.3 管理操作

允许：

- 强制禁用；
- 强制吊销；
- 解除管理员临时禁用；
- 标记要求用户轮换；
- 查看相关调用和告警；
- 批量吊销已离职/禁用用户 Token；
- 导出脱敏元数据。

不允许：

- 查看明文；
- 为用户创建个人 Token；
- 修改所有者；
- 修改用户选择的 scopes；
- 使用 Token 代替用户调用 MCP。

处置弹窗要求：

- 选择原因；
- 填写说明；
- 选择临时/永久；
- 批量操作显示影响 Token 和用户数量；
- 严重操作二次确认。

## 7. 调用审计

### 7.1 审计事件分类

| 事件 | 示例 |
|---|---|
| TOKEN_LIFECYCLE | 创建、轮换、禁用、启用、删除、管理员吊销 |
| AUTHENTICATION | Token 鉴权成功/失败、过期、用户停用 |
| MCP_SESSION | initialize、会话建立、会话结束、协议错误 |
| TOOL_CALL | tools/call 请求与结果 |
| POLICY_DECISION | scope/RBAC/项目/限流/封禁拒绝 |
| ADMIN_ACTION | 策略发布、回滚、封禁、解除、导出 |
| SECURITY_ALERT | 告警创建、确认、关闭、升级 |

### 7.2 Tool 调用审计字段

| 字段 | 说明 |
|---|---|
| `event_id` | 全局唯一事件 ID |
| `trace_id` | 一次 MCP 请求链路 ID |
| `session_id` | MCP 会话 ID，可空 |
| `event_type` | 事件类型 |
| `occurred_at` | 发生时间 |
| `user_id` | 实际用户 |
| `token_id` | Token 内部 ID |
| `token_prefix` | 脱敏前缀 |
| `client_type` | 客户端类型 |
| `client_version` | 可识别时记录 |
| `source_ip` | 来源 IP，按策略脱敏 |
| `user_agent` | 截断并清洗 |
| `project_id` | 目标项目 |
| `tool_name` | MCP Tool |
| `tool_risk_level` | LOW/MEDIUM/HIGH/CRITICAL |
| `operation_type` | READ/CREATE/UPDATE/DELETE/EXECUTE |
| `resource_type` | CASE/PLAN/REVIEW/BUG/PROJECT 等 |
| `resource_ids` | 目标资源 ID数组，限制长度 |
| `request_summary` | 脱敏摘要 |
| `response_summary` | 脱敏摘要 |
| `result` | SUCCESS/FAILED/DENIED/THROTTLED |
| `http_status/mcp_error_code` | 状态码 |
| `duration_ms` | 耗时 |
| `policy_version` | 当时生效策略版本 |
| `decision_reason` | 拒绝或告警原因码 |
| `idempotency_key_hash` | 幂等键哈希，可空 |

### 7.3 数据脱敏

请求/响应不应全量入库。采用 Tool 级审计投影器：

```java
interface AgentAuditProjector {
    AuditSummary projectRequest(String toolName, Object input);
    AuditSummary projectResponse(String toolName, Object output);
}
```

规则：

- Token、密码、Cookie、Authorization、X-API-Key 永久删除；
- 评论/描述等正文默认只记录长度和哈希；
- 文件只记录文件名、大小、类型、附件 ID，不记录内容；
- 用户列表可记录人数，不默认记录全部个人信息；
- 批量资源 ID 限制最多 100 个，超出记录数量和摘要哈希；
- 错误堆栈只存服务端内部日志，审计表存错误码和安全摘要。

### 7.4 查询与详情

筛选条件：

- 时间；
- 用户；
- Token；
- 客户端；
- IP；
- 项目；
- Tool；
- 风险等级；
- 操作类型；
- 结果；
- 状态码；
- trace ID；
- 资源 ID；
- 策略拒绝原因。

详情页展示调用时间线：

```text
10:00:00.001 鉴权成功
10:00:00.008 项目范围校验通过
10:00:00.010 Scope/RBAC 校验通过
10:00:00.015 Tool 开始执行
10:00:00.240 业务操作成功
10:00:00.245 审计落库完成
```

不应显示秘密或未经脱敏的业务正文。

### 7.5 导出

- 仅系统管理员；
- 必须选择时间范围，单次最大 31 天；
- 大数据量异步生成；
- 导出文件脱敏；
- 下载链接短期有效；
- 导出动作本身记入管理员审计；
- 支持 CSV/XLSX，取证场景可增加签名 JSONL。

## 8. 风险告警

### 8.1 内置规则

#### 凭据风险

- 单 Token 连续鉴权失败；
- 已禁用/已过期 Token 持续调用；
- 同一 Token 短时间多 IP；
- Token 来源国家/地区快速跳变；
- 旧版 Token 仍高频使用；
- 长期 Token 即将到期或长期未轮换。

#### 权限风险

- 连续调用 scope 不允许的 Tool；
- 连续项目越权；
- 用户权限已撤回但客户端持续重试；
- `AGENT_ALL` Token 的高风险调用量突增；
- 高风险 Tool 在非工作时间被调用。

#### 行为风险

- 调用速率突增；
- 大量批量创建/更新；
- 失败率显著升高；
- 单用户跨大量项目访问；
- 同一幂等键冲突；
- 同一资源被短时间重复修改；
- 大附件或异常文件类型上传。

#### 系统风险

- MCP P95 延迟异常；
- 审计写入失败；
- Redis 限流不可用；
- 策略版本分发不一致；
- Tool 错误率超过阈值；
- 数据库连接或队列积压。

### 8.2 风险等级

| 等级 | 示例 | 默认动作 |
|---|---|---|
| LOW | Token 7 天后过期 | 页面提醒 |
| MEDIUM | 高频 403/429 | 创建告警、通知管理员 |
| HIGH | 多 IP 异常、高风险批量操作 | 临时限流或暂停 Token |
| CRITICAL | 疑似泄露、批量破坏、审计失效 | 自动吊销/全局熔断并通知 |

自动处置默认仅对 HIGH/CRITICAL 开放，并由管理员显式启用。

### 8.3 告警生命周期

```text
OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
  └───────────────────────────────→ FALSE_POSITIVE
```

记录：

- 规则和版本；
- 证据事件；
- 关联用户/Token/IP/项目；
- 风险评分；
- 自动动作；
- 处理人；
- 处理意见；
- 关闭原因。

### 8.4 通知

支持复用 MeterSphere 通知能力：

- 站内通知；
- 邮件；
- 企业微信；
- Webhook。

通知内容不得包含 Token、认证头或敏感请求正文。

## 9. 应急处置

### 9.1 处置动作

| 动作 | 范围 | 是否可恢复 |
|---|---|---|
| 全局停止 Agent 服务 | 全站 MCP/Agent Token | 是 |
| 暂停 Tool | 指定 Tool | 是 |
| 临时封禁 Token | 单 Token | 是 |
| 永久吊销 Token | 单 Token | 否，用户需新建 |
| 临时封禁用户 Agent 能力 | 用户全部 Token | 是 |
| IP 封禁 | 来源 IP/CIDR | 是 |
| 项目 Agent 隔离 | 指定项目 | 是 |
| 强制轮换 | Token | 旧 Token 在宽限期后失效 |
| 降低限流阈值 | 全局或指定对象 | 是 |

### 9.2 全局熔断

熔断状态：

```text
NORMAL
READ_ONLY
STOPPED
```

- `NORMAL`：按正常策略执行。
- `READ_ONLY`：只允许 `readOnlyHint=true` 的 Tool。
- `STOPPED`：拒绝所有个人 Token/MCP Tool 调用，仅保留管理员恢复和健康检查。

切换为 `READ_ONLY/STOPPED` 必须：

- 二次确认；
- 输入原因；
- 记录操作者；
- 通知管理员；
- 生成处置事件；
- 支持设置自动恢复时间。

## 10. 数据模型

### 10.1 `agent_governance_policy`

| 字段 | 说明 |
|---|---|
| `id/version` | 策略与版本 |
| `status` | DRAFT/ACTIVE/ARCHIVED |
| `policy_json` | 完整策略 |
| `change_summary` | 变更摘要 |
| `create_user/create_time` | 创建审计 |
| `publish_user/publish_time` | 发布审计 |

### 10.2 `agent_audit_event`

按 §7.2 建模。建议按月分区或按时间归档，核心索引：

```text
(occurred_at)
(user_id, occurred_at)
(token_id, occurred_at)
(tool_name, occurred_at)
(project_id, occurred_at)
(result, occurred_at)
(trace_id)
```

### 10.3 `agent_security_alert`

保存规则、证据、风险评分、状态、处置和处理人。

### 10.4 `agent_admin_action`

记录策略发布、回滚、Token 吊销、封禁、解封、导出和熔断。

### 10.5 `agent_temporary_block`

统一保存 Token/User/IP/Project/Tool 的临时封禁及到期时间。

### 10.6 现有日志迁移

当前 `agent_exec_log` 同时承担执行记录和高危写审计，字段语义不足：

- `case_id` 被复用为任意资源 ID；
- `last_exec_result` 被复用为动作码；
- 缺少 Token、Tool、项目、IP、耗时、策略和结果字段；
- `content` 可能包含过量请求正文。

改造方式：

1. `agent_exec_log` 继续只保存功能用例执行证据，避免破坏已有附件关系。
2. 新增 `agent_audit_event` 保存全部结构化 MCP/Agent 审计。
3. 过渡期执行回写可同时写两表：业务执行证据写 `agent_exec_log`，通用调用事实写 `agent_audit_event`。
4. 现有高危 `audit()` 方法迁移到统一 `AgentAuditService`。
5. 历史高危日志可离线映射为兼容审计事件，无法推导的字段留空。

## 11. 策略执行架构

```text
MCP 请求
  ↓
AgentCredentialFilter
  ├─ Token/用户状态
  └─ 来源信息
  ↓
AgentPolicyEnforcementPoint
  ├─ 全局熔断
  ├─ 临时封禁
  ├─ Tool 状态
  ├─ Redis 限流
  ├─ Token scope
  ├─ RBAC/项目权限
  └─ 请求大小/批量/幂等
  ↓
Tool Handler → 既有业务 Service
  ↓
AgentAuditPublisher
  ├─ 审计事件
  ├─ 指标
  └─ 风险检测
```

核心组件：

- `AgentGovernancePolicyService`
- `AgentPolicyCache`
- `AgentPolicyEnforcementPoint`
- `AgentDistributedRateLimiter`
- `AgentAuditService`
- `AgentAuditSanitizer`
- `AgentRiskDetectionService`
- `AgentIncidentResponseService`

策略发布后通过 Redis Pub/Sub、消息总线或配置中心通知所有实例刷新；每条审计记录写入当时的 `policy_version`。

## 12. API 设计

统一前缀：

```text
/api/admin/agent-governance
```

### 12.1 概览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/overview` | 指标与状态 |
| GET | `/trends` | 趋势图 |
| GET | `/rankings` | Tool/用户/项目排行 |

### 12.2 策略

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/policies/active` | 当前生效策略 |
| GET | `/policies` | 版本列表 |
| POST | `/policies/draft` | 保存草稿 |
| POST | `/policies/validate` | 校验与影响评估 |
| POST | `/policies/{version}/publish` | 发布 |
| POST | `/policies/{version}/rollback` | 回滚 |

### 12.3 Token 治理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/tokens/page` | Token 元数据分页 |
| POST | `/tokens/{id}/disable` | 管理员禁用 |
| POST | `/tokens/{id}/revoke` | 永久吊销 |
| POST | `/tokens/{id}/require-rotation` | 要求轮换 |
| POST | `/tokens/batch-revoke` | 批量吊销 |

### 12.4 审计与告警

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/audit/page` | 审计分页 |
| GET | `/audit/{id}` | 审计详情 |
| POST | `/audit/export` | 异步导出 |
| POST | `/alerts/page` | 告警分页 |
| GET | `/alerts/{id}` | 告警详情 |
| POST | `/alerts/{id}/acknowledge` | 确认 |
| POST | `/alerts/{id}/resolve` | 关闭 |
| POST | `/alerts/{id}/false-positive` | 标记误报 |

### 12.5 应急处置

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/PUT | `/circuit-breaker` | 查询/切换熔断状态 |
| POST | `/blocks` | 新增临时封禁 |
| DELETE | `/blocks/{id}` | 解除封禁 |
| GET | `/actions` | 管理员处置记录 |

所有接口仅允许系统管理员，并记录管理员操作审计。

## 13. 权限设计

新增独立权限域：

```text
AGENT_GOVERNANCE:READ
AGENT_GOVERNANCE:POLICY
AGENT_GOVERNANCE:TOKEN_REVOKE
AGENT_GOVERNANCE:AUDIT_EXPORT
AGENT_GOVERNANCE:INCIDENT
```

但菜单基础可见条件必须同时满足：

```text
系统管理员身份
AND AGENT_GOVERNANCE:READ
```

即使某普通用户意外获得单个权限点，也不能看到治理中心或调用治理 API。

建议职责拆分：

- 系统管理员：全部权限；
- 安全审计员：只读审计与告警、可导出，不可改策略或吊销；
- 应急响应管理员：可处置，不可改长期策略。

上述细分职责也必须限定在系统管理员身份范围内。若首期不建设细分角色，则仅系统管理员全量开放。

## 14. 性能与可靠性

- 审计主链路采用异步事件，但高风险写操作必须保证最少一次落库。
- 审计队列不可用时：
  - 只读调用可按策略降级并记录本地补偿；
  - 高风险写操作建议 fail-closed，避免无审计执行。
- Redis 不可用时：
  - 限流进入保守本地模式；
  - HIGH/CRITICAL Tool 可暂时拒绝。
- 审计表按月分区并冷热分层。
- 概览使用分钟级聚合表或时序指标，不直接扫描明细大表。
- 审计事件写入目标 P95 小于 50 ms（异步入队），治理决策增加延迟目标小于 20 ms。

## 15. 实施拆分

| 阶段 | 内容 | 预估 |
|---|---|---:|
| G1 | 管理员权限、页面骨架、全局服务状态与基础策略 | 3～4 人日 |
| G2 | 独立审计模型、脱敏投影、查询与详情 | 5～7 人日 |
| G3 | 分布式策略执行、限流、封禁、Tool 管控 | 5～7 人日 |
| G4 | Token 治理、处置记录、策略版本发布/回滚 | 4～6 人日 |
| G5 | 风险规则、告警生命周期、通知和应急熔断 | 5～8 人日 |
| G6 | 指标聚合、报表导出、性能/安全/灾备测试 | 4～6 人日 |

合计约 26～38 人日。可与个人 Token/MCP 主方案的后期阶段并行，但审计事件模型和策略执行接口需要先冻结。

## 16. 验收标准

### 16.1 可见性与权限

- 非系统管理员看不到菜单和路由。
- 非系统管理员直接请求治理 API 返回 403。
- 管理员不能获取 Token 明文或密钥哈希。
- 管理员所有策略、导出和处置操作均形成审计。

### 16.2 策略

- 策略草稿不影响线上。
- 发布后所有服务实例在目标时间内使用同一版本。
- 每次拒绝能展示命中策略和原因。
- 回滚后新请求使用目标历史版本。
- 普通用户仍可选择 `AGENT_ALL`，但不能突破 RBAC 和项目权限。

### 16.3 审计

- 所有 Token 生命周期、鉴权、MCP 会话和 Tool 调用均可追踪。
- trace ID 可关联一次调用的完整时间线。
- 审计中不存在 Token、认证头、附件正文等秘密。
- 业务执行证据与通用调用审计职责分离。
- 导出有范围限制、脱敏和下载审计。

### 16.4 告警与处置

- 内置风险规则可稳定触发并去重。
- 告警可确认、调查、关闭和标记误报。
- 自动处置具备开关、原因和恢复机制。
- Token、用户、IP、项目、Tool 和全局熔断均可生效。
- `READ_ONLY` 模式只允许只读 Tool；`STOPPED` 模式阻断全部 Agent 调用。

### 16.5 集群与故障

- 多实例限流结果一致。
- 策略版本分发失败能够告警。
- Redis、消息队列或审计存储异常时按设计降级。
- 高风险写操作不会在完全无审计条件下静默成功。

## 17. 首期建议范围

首期必须完成：

- 管理员专属入口与权限；
- 服务总开关和 `NORMAL/READ_ONLY/STOPPED`；
- Token 元数据查询与强制吊销；
- 结构化 Tool 调用审计；
- Redis 分布式限流；
- 策略版本发布；
- 基础告警：鉴权失败、越权、限流、多 IP、高风险批量调用；
- 管理员处置记录。

二期可增加：

- IP 地理异常；
- 行为基线与动态风险评分；
- SIEM/SOC 推送；
- 审计签名与不可篡改存储；
- 复杂分级管理员角色；
- 自动化事件响应编排。

## 18. 与个人 Agent 页的边界

| 能力 | 个人 Agent 与 API | Agent 治理策略与审计中心 |
|---|---|---|
| 可见用户 | 所有正常用户 | 仅系统管理员 |
| 创建个人 Token | 是，只能创建自己的 | 否 |
| 选择 scopes/AGENT_ALL | 是 | 只查看，不修改 |
| 查看 Token 明文 | 仅创建/轮换时一次 | 永远不可见 |
| 禁用本人 Token | 是 | 可强制禁用任意 Token |
| 全局策略 | 不可见 | 可配置 |
| 调用审计 | 可选展示本人摘要 | 查看全站脱敏审计 |
| 风险告警 | 仅本人安全提醒 | 全站调查和处置 |
| 全局熔断 | 否 | 是 |
