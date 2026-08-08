# task002 - P0 - 用户 Agent 连接、设备、会话绑定与权限数据模型

## 状态

实现中：连接、设备、会话绑定、权限和迁移已落地；真实数据库升级、状态机并发和运行中任务撤销测试未完成。

## 目标

建立用户 Agent 连接、Bridge 设备、外部会话绑定和用例会话资源字段，为个人数据隔离、授权状态和流式执行提供持久化事实模型。

## 依赖

- task001 双通道资源模型、路由与向后兼容。

## 当前基础

- 已有 `ai_oauth_connection` 和 `ai_agent_gateway`，主要面向通用 OAuth 与远程 Gateway。
- 已有 `ai_case_conversation`、消息、执行和事件表。
- 当前会话只绑定 `model_source_id`。

## 实现范围

### 1. 新增表

新增迁移，禁止修改已发布 SQL：

```text
ai_user_agent_connection
ai_agent_device
ai_agent_session_binding
```

连接表至少保存：`id/user_id/provider/connection_mode/display_name/external_account_id/credential_reference/status/capabilities/device_id/expires_at/last_health_time/create_time/update_time`。

设备表至少保存：`id/user_id/device_name/public_key/certificate_fingerprint/status/bridge_version/os_type/last_heartbeat_time/create_time/update_time`。

会话绑定至少保存：`conversation_id/connection_id/external_session_id/provider/device_id/last_sequence/create_time/update_time`。

### 2. 会话兼容字段

`ai_case_conversation` 增加：

```text
resource_type
resource_id
agent_connection_id
```

保留 `model_source_id` 并允许为空。历史数据回填 `resource_type=MODEL_API`、`resource_id=model_source_id`。

### 3. 状态机

连接状态：

```text
PENDING -> CONNECTED -> OFFLINE
CONNECTED/OFFLINE -> AUTH_EXPIRED
任意非终态 -> DISABLED/REVOKED
```

设备状态：

```text
PAIRING -> ONLINE -> OFFLINE -> ONLINE
任意非终态 -> REVOKED
```

状态更新使用条件更新或乐观锁，禁止过期心跳把已撤销设备恢复在线。

### 4. 权限

新增：

```text
SYSTEM_PERSONAL_AI_AGENT:READ
SYSTEM_PERSONAL_AI_AGENT:CONNECT
SYSTEM_PERSONAL_AI_AGENT:REVOKE
```

所有个人连接查询和修改必须在 SQL 或 Repository 层带 `user_id`，不能先按 ID 查询再依赖前端隐藏。

项目管理员可以控制项目是否允许个人 Agent，但不能读取用户的 `credential_reference`、第三方账号详情或会话正文。

### 5. 清理策略

- 删除连接前检查是否存在运行中执行。
- 连接删除采用逻辑删除或先撤销后删除。
- 设备撤销立即使相关连接离线。
- 会话保留历史 Provider/资源标识，不因连接删除而丢失审计关系。

## 建议迁移文件

```text
V3.7.2_33__ai_user_agent_connection.sql
V3.7.2_34__ai_agent_bridge_device.sql
V3.7.2_35__ai_case_conversation_resource.sql
V3.7.2_36__ai_user_agent_permissions.sql
```

最终版本号以合入时仓库最新迁移为准，不得与已存在文件冲突。

## 验收标准

- 空库和历史库迁移均成功。
- 历史模型会话行为不变。
- 用户 A 无法查询、修改、使用或撤销用户 B 的连接与设备。
- 连接、设备和外部会话可以完整追踪一次 Agent 执行。
- 撤销后旧 Token、旧 WSS 和旧心跳均不能恢复连接。

## 测试要求

- Flyway 空库/升级测试。
- 状态机合法与非法迁移测试。
- 乐观锁/条件更新并发测试。
- 跨用户、跨项目隔离测试。
- 历史会话回填和回滚策略测试。
- 连接删除、设备撤销与运行中任务冲突测试。

## 非目标

- 本任务不实现 OAuth 登录、WSS 或具体 Provider。
