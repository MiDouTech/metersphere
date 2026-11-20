# task007 - P1 refresh 策略与登录桥回退

> **阶段**：P1  
> **预估工期**：1.5 天  
> **前置依赖**：[task004](task004-P0-sessionToken存储与logout.md)  
> **阻塞任务**：task009  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §6.4

---

## 1. 任务目标

在后端对米多 `sessionToken` 做 refresh；失败时标记需重新 SSO，并通过登录桥 URL 引导用户回米多重认证。

---

## 2. 现状缺口

| 能力 | 现状 |
|------|------|
| refresh 调用 | **无** |
| 登录桥跳转 | **无** |
| `/is-login` 重认证标记 | **无** |

---

## 3. 任务清单

### 3.1 MiduoSsoRefreshService

**路径**：`.../sso/miduo/MiduoSsoRefreshService.java`

| 方法 | 说明 |
|------|------|
| `refreshIfNeeded(String userId)` | 读 Redis sessionToken，临近过期时 refresh |
| `handleRefreshFailure(String userId)` | 标记 `needReauth=true`，可选清 Shiro |

**触发时机（二选一或组合）**：

| 方案 | 说明 |
|------|------|
| A. 请求拦截 | Filter：MIDUO 用户每次 API 请求检查（注意性能） |
| B. 定时 + `/is-login` | `@Scheduled` 批量 refresh；`/is-login` 读标记 |

**推荐 P1**：方案 B — 在 `LoginController.isLogin` 中若 `authenticate=MIDUO` 且 token 将过期，尝试 refresh。

### 3.2 refresh 逻辑

1. 从 `MiduoSsoSessionStore` 取 sessionToken  
2. `miduoSsoClient.refreshSessionToken(token)`  
3. 成功：更新 Redis 中 token / expiresAt  
4. 失败：`markNeedReauth(userId)`，返回 `needMiduoReauth: true`  

### 3.3 登录桥

**路径**：`MiduoSsoAuthController`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/miduo/bridge-url` | 返回 `{ url: "https://miduo.../api/sso/bridge/redirect-url?..." }` |

前端收到 `needMiduoReauth` 时：

```typescript
const { url } = await getMiduoBridgeUrl();
window.location.href = url;
```

### 3.4 前端全局处理

**路径**：`frontend/src/api/http/checkStatus.ts` 或 `store/modules/user/index.ts`

- `isLogin` 响应含 `needMiduoReauth=true` → 跳 bridge  
- 避免与 401 本地登录冲突：优先处理 MIDUO 重认证  

### 3.5 测试用例

| 场景 | 预期 |
|------|------|
| token 未过期 | 不 refresh |
| refresh 成功 | Redis 更新 |
| refresh 失败 | `needMiduoReauth=true` |
| 跳 bridge | 浏览器离开 MS 到米多 |

---

## 4. 验收标准

- [ ] 米多 session 续期可延长用户免登时间（联调确认）  
- [ ] refresh 失败后用户被引导至登录桥，而非卡在 401  
- [ ] refresh 不在前端持有 sessionToken  
- [ ] 日志无 token 明文  

---

## 5. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
