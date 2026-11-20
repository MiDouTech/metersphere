# task004 - P0 sessionToken Redis 存储与 logout revoke

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task002](task002-P0-米多开放API签名客户端.md)、[task003](task003-P0-state-callback与用户匹配.md)  
> **阻塞任务**：task007、task008、task009  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §6.4

---

## 1. 任务目标

validate 成功后将米多 `sessionToken` 存入 Redis；用户登出时调用 revoke；为 P1 refresh 预留读取接口。

---

## 2. 现状缺口

| 能力 | 现状 |
|------|------|
| sessionToken 持久化 | **无** |
| logout revoke | `LoginController.signout` 仅清 Shiro Session |
| MIDUO 登出识别 | **无** |

---

## 3. 任务清单

### 3.1 MiduoSsoSessionStore

**路径**：`.../sso/miduo/MiduoSsoSessionStore.java`

| 方法 | 说明 |
|------|------|
| `save(String userId, String sessionToken, Long expiresAt)` | Key: `miduo:sso:session:{userId}` |
| `get(String userId)` | 返回 sessionToken |
| `delete(String userId)` | 登出时删除 |
| `markNeedReauth(String userId)` | refresh 失败标记（P1 用） |

**Redis 值结构（JSON）**：

```json
{
  "sessionToken": "***",
  "expiresAt": 1710000000000,
  "needReauth": false
}
```

TTL：对齐米多 `expiresAt` 或配置 `miduo.sso.session-ttl-seconds`。

### 3.2 callback 挂钩

在 `MiduoSsoApplicationService.handleCallback` 成功分支：

```java
sessionStore.save(user.getId(), validateResult.getSessionToken(), validateResult.getExpiresAt());
```

### 3.3 logout revoke

**方案 A（推荐）**：扩展 `MiduoSsoAuthController`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/miduo/logout` | revoke + 删 Redis + `SecurityUtils.getSubject().logout()` |

**方案 B**：在 `LoginController.signout` 内检测 `authenticate=MIDUO` 时委托 `MiduoSsoApplicationService.logout()`。

**流程**：

1. 从 Redis 取 sessionToken  
2. `miduoSsoClient.revokeSessionToken(token)`（失败仍清本地 Session，记 warn 日志）  
3. `sessionStore.delete(userId)`  
4. Shiro logout  

### 3.4 前端登出挂钩

**路径**：`frontend/src/store/modules/user/index.ts`

```typescript
// logout() 中：若 session.authenticate === 'MIDUO'，先 POST /auth/miduo/logout
```

或通过 `GET /is-login` 返回的 `authenticate` 字段判断。

### 3.5 安全要求

- [ ] Redis value 不出现在 API 响应  
- [ ] 日志仅打印 token 前 6 位  
- [ ] revoke 失败不阻断本地登出  

---

## 4. 测试用例

| 场景 | 预期 |
|------|------|
| callback 成功 | Redis 有 session 记录 |
| `/auth/miduo/logout` | revoke 被调用 + Redis 删除 |
| revoke 超时 | 本地仍登出成功 |
| 非 MIDUO 用户登出 | 不调 revoke |

---

## 5. 验收标准

- [ ] callback 后 Redis 可查到 sessionToken（仅后端）  
- [ ] 登出后 Redis 记录清除  
- [ ] 米多侧重登入需重新 SSO（revoke 生效，需联调确认）  
- [ ] 前端 `userStore.logout()` 对 MIDUO 用户走 revoke 链路  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
