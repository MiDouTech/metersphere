# task005 - P0 Shiro 匿名链与 CDS `/auth/` 路由

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：[task001](task001-P0-接入确认单与配置项.md)  
> **阻塞任务**：task006、task009（灰度部署）  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §6.1

---

## 1. 任务目标

注册 `/auth/miduo/**` 为 Shiro 匿名可访问；在 CDS 灰度配置中将 `/auth/` 路由到 Java 后端，避免 callback 落到 Vite 静态资源（同类问题：`display/info`）。

---

## 2. 现状参考

| 组件 | 路径 | 说明 |
|------|------|------|
| Agent 扩展范例 | `AgentShiroFilterChainExtender.java` | 实现 `ShiroFilterChainExtender` |
| 基础匿名链 | `FilterChainUtils.java` | `/login`、`/display/info` 等 |
| CDS 路由 | `cds-compose.yml` | `cds.path-prefixes` 控制网关 |

**当前 CDS `path-prefixes`（节选）**：

```yaml
cds.path-prefixes: "/organization/,/project/,/system/,/login,/sign/,/api/,.../display/,/authsource/,/front/"
```

**缺失**：`/auth/` → 导致 `/auth/miduo/callback` 公网 404 或返回前端 HTML。

---

## 3. 任务清单

### 3.1 MiduoSsoShiroFilterChainExtender

**路径**：`backend/services/system-setting/src/main/java/io/metersphere/system/config/MiduoSsoShiroFilterChainExtender.java`

```java
@Component
public class MiduoSsoShiroFilterChainExtender implements ShiroFilterChainExtender {
    @Override
    public void extend(Map<String, Filter> filters, Map<String, String> chain) {
        chain.put("/auth/miduo/**", "anon");
    }
}
```

**注意**：优先用 Extender，**不**直接改 `FilterChainUtils`（与 Agent 模块一致）。

### 3.2 CDS compose 更新

**路径**：`cds-compose.yml`

```yaml
cds.path-prefixes: ".../display/,/auth/,/authsource/,/front/"
```

`app-metersphere` 的 `cds.path-prefix` 可增加 `/auth`（若 CDS 支持单 profile 前缀）。

### 3.3 前端 Vite 代理（开发态）

**路径**：`frontend/config/vite.config.dev.ts`

```typescript
'/auth': {
  target: process.env.VITE_DEV_DOMAIN,
  changeOrigin: true,
  rewrite: (path: string) => path.replace(/^\/front\/auth/, '/auth'),
},
```

与 `/front` 通用代理配合：前端请求 `/front/auth/miduo/state` → 后端 `/auth/miduo/state`。

### 3.4 冒烟脚本（可选）

**路径**：`scripts/verify-miduo-sso.ps1`

```powershell
# GET health + GET /auth/miduo/status（enabled 时）
curl https://v3-x-metersphere.miduo.org/front/auth/miduo/status
```

---

## 4. 测试用例

| 场景 | 预期 |
|------|------|
| 未登录 GET `/auth/miduo/state` | 200，非 401 |
| 公网 GET `/front/auth/miduo/status` | 到达 Java（非 Vite HTML） |
| 已登录访问业务 API | 不受影响 |

---

## 5. 验收标准

- [ ] `MiduoSsoShiroFilterChainExtender` 注册成功（启动无 Shiro 链冲突）  
- [ ] CDS 灰度 `/front/auth/miduo/status` 返回 JSON  
- [ ] 本地 dev：`/front/auth/miduo/state` 代理正确  
- [ ] Agent API `/api/agent/v1/**` 不受影响  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
