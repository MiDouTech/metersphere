# task006 - P0 前端 Callback 页与路由白名单

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task003](task003-P0-state-callback与用户匹配.md)、[task005](task005-P0-Shiro扩展与CDS路由.md)  
> **阻塞任务**：task009  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §6.1–6.2

---

## 1. 任务目标

新增米多 SSO 回调落地页：从 URL 读取 `token` + `state`，POST 后端 `/auth/miduo/callback` 建立会话后跳转首页；路由加入白名单避免未登录守卫拦截。

---

## 2. 现状缺口

| 项 | 现状 |
|----|------|
| SSO callback 路由 | **无** |
| `WHITE_LIST` | 无 `sso` 相关项 |
| 米多登录 API 封装 | **无** |

当前 `userLoginInfo` 守卫：非白名单且未登录 → 重定向 `/login`，会打断 callback。

---

## 3. 任务清单

### 3.1 API 封装

**路径**：`frontend/src/api/modules/sso/miduo.ts`

| 方法 | 后端路径 |
|------|----------|
| `getMiduoSsoStatus()` | `GET /auth/miduo/status` |
| `getMiduoSsoState()` | `GET /auth/miduo/state` |
| `postMiduoSsoCallback(data)` | `POST /auth/miduo/callback` |
| `postMiduoSsoLogout()` | `POST /auth/miduo/logout` |
| `getMiduoBridgeUrl()` | `GET /auth/miduo/bridge-url` |

**路径前缀**：走现有 `MSR` 封装，开发态自动加 `/front` 前缀（与 `GetPageConfigUrl` 一致）。

**requrls**：`frontend/src/api/requrls/sso/miduo.ts`

### 3.2 Callback 页面

**路径**：`frontend/src/views/login/sso/MiduoCallback.vue`

**流程**：

1. `onMounted`：从 `route.query` 读取 `token`、`state`（QUERY 模式）  
2. 缺参 → 展示错误 + 返回登录页  
3. `postMiduoSsoCallback({ token, state })`  
4. 成功：`setToken(sessionId, csrfToken)` + `userStore.setInfo` + 跳转 `redirect` 或工作台  
5. 失败：展示后端 message（如「请先同步企业微信成员」）  

**UI**：Loading + 错误态，风格对齐 `login/index.vue`。

### 3.3 路由注册

**路径**：`frontend/src/router/index.ts`

```typescript
{
  path: '/sso/miduo/callback',
  name: 'ssoMiduoCallback',
  component: () => import('@/views/login/sso/MiduoCallback.vue'),
  meta: { requiresAuth: false },
},
```

### 3.4 白名单

**路径**：`frontend/src/router/constants.ts`

```typescript
{ name: 'ssoMiduoCallback', path: '/sso/miduo/callback', children: [] },
```

确保 `isWhiteListPage()` 在 callback 页返回 true。

### 3.5 i18n

**路径**：`frontend/src/views/login/locale/zh-CN.ts`、`en-US.ts`

| Key | 文案示例 |
|-----|----------|
| `login.miduo.callback.loading` | 正在登录… |
| `login.miduo.callback.error` | 米多登录失败 |
| `login.miduo.callback.noUser` | 未找到对应成员，请联系管理员同步企微通讯录 |

### 3.6 FORM_POST 模式（按确认单）

若米多为 FORM_POST：后端 `landing` 页 302 到 `/#/sso/miduo/callback?token=...&state=...`，本页逻辑不变。

---

## 4. 与登录页关系（P0 最小）

P0 **可不**在 `login-form.vue` 增加「米多登录」按钮（主入口为米多工作台跳转）。

P2 在 [task009](task009-P2-端到端验收与登录入口.md) 补充站内入口。

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 带 token+state 访问 callback | 登录成功跳转 |
| 缺 token | 错误提示 |
| 未同步用户 | 展示同步提示 |
| 直接访问 callback 无 query | 不无限 loading |

---

## 6. 验收标准

- [ ] `/#/sso/miduo/callback?token=...&state=...` 可完成登录  
- [ ] 未登录态不被重定向到 `/login`（白名单生效）  
- [ ] 登录后 `userStore` 与 `setToken` 与密码登录一致  
- [ ] 中英文文案齐全  

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
