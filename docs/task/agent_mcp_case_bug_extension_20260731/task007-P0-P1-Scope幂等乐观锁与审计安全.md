# task007 - P0/P1 Scope、幂等、乐观锁与审计安全

## 目标

为新增 MCP 工具建立最小权限 Scope、幂等控制、乐观锁和审计安全机制，确保 Agent Token 不扩大旧权限、不绕过用户 RBAC、不越权操作项目外资源。

## 范围

- `backend/services/agent-integration/constants/AgentTokenScope.java`
- Agent Token Scope 校验
- 用户 RBAC 校验
- 项目白名单校验
- 资源归属校验
- 幂等记录
- Agent 审计日志

## 新增 Scope

```java
CASE_UPDATE
CASE_DELETE
CASE_COMMENT
CASE_ATTACHMENT

BUG_DELETE
BUG_COMMENT
BUG_ATTACHMENT
BUG_RELATE
```

## 兼容策略

- `AGENT_ALL` 包含所有新增 Scope。
- `CASE_WRITE` 保持创建用例和模块的原有含义。
- 不让已有 `CASE_WRITE` Token 自动获得删除权限。
- `BUG_WRITE` 保持创建和普通字段更新能力。
- 缺陷删除、评论、附件、关联必须显式授权。
- 老 Token 不因版本升级自动扩大权限。

## 最终授权判定

```text
有效权限 =
Token Scope
∩ 用户 RBAC
∩ Token 项目白名单
∩ 资源归属校验
∩ 服务端工具策略
```

## 幂等规则

服务端记录：

```text
tokenId + toolName + requestId
```

行为：

- 相同参数重复请求：返回第一次结果。
- 相同 `requestId`、不同参数：返回 `IDEMPOTENCY_CONFLICT`。
- 创建、评论、附件关联、关联用例和批量操作必须支持 `requestId`。

## 乐观锁规则

- 用例和缺陷更新必须支持 `expectedUpdateTime`。
- 与数据库一致：允许更新。
- 不一致：返回 `VERSION_CONFLICT`。
- 响应包含当前更新时间和字段摘要。

## 审计字段

所有写操作记录：

- Token ID，不记录明文 Token
- 用户 ID
- 项目 ID
- 工具名称
- 资源 ID
- 修改前后摘要
- 附件 ID
- `requestId`
- 调用结果
- 客户端 IP
- 执行时间

## 验收标准

- 无新增 Scope 的旧 Token 不能执行新增写操作。
- 用户 RBAC 不足时，即使 Token 拥有 Scope 仍返回 403。
- Token 无法通过资源 ID 越权操作白名单外项目。
- 重复 `requestId` 不产生重复附件、评论和关联。
- 相同 `requestId` 不同参数返回 `IDEMPOTENCY_CONFLICT`。
- 更新冲突返回 `VERSION_CONFLICT`。
- 所有写操作存在业务日志和 Agent 审计记录。
