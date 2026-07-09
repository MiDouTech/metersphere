# 开发摘要日志 — task003 Agent Token 认证与 Shiro 集成

> **日期**：2026-07-07  
> **任务**：[task003-P0-AgentToken认证与Shiro集成.md](../../task/metersphere_agent/task003-P0-AgentToken认证与Shiro集成.md)  
> **前置**：task002 ✅  
> **状态**：✅ 已完成

---

## 1. 本次目标

实现 `Authorization: Bearer msat_*` 认证，注册 Shiro Filter，跳过 CSRF，设置项目上下文。

---

## 2. 核心实现

| 类 | 职责 |
|----|------|
| `AgentTokenService` | SHA-256 校验、过期、Scope |
| `AgentTokenFilter` | Bearer 解析、Shiro 登录、项目 Header |
| `AgentTokenContext` | ThreadLocal 当前 Token |
| `AgentShiroConfigurer` | Filter 链注册 |

---

## 3. Shiro 链路

```
/api/agent/v1/functional/health → anon
/api/agent/v1/**                → agentToken, authc  （无 csrf）
/**                             → apikey, csrf, authc（原有）
```

`AgentTokenFilter` 参考 `ApiKeyFilter`：认证后 logout，避免污染 Session。

---

## 4. Header 约定

| Header | 说明 |
|--------|------|
| `Authorization: Bearer msat_xxx` | 必填 |
| `X-MS-PROJECT` / `PROJECT` | 项目 ID，优先于 Token 默认项目 |

---

## 5. Scope 常量

`FUNCTIONAL_READ` / `FUNCTIONAL_SUBMIT` / `FUNCTIONAL_ALL`（`AgentTokenScope`）

Controller 层 `assertScope()` 校验。

---

## 6. 变更文件

| 文件 | 说明 |
|------|------|
| `agent/security/AgentTokenService.java` | 新建 |
| `agent/security/AgentTokenFilter.java` | 新建 |
| `agent/security/AgentTokenContext.java` | 新建 |
| `agent/config/AgentShiroConfigurer.java` | 新建 |
| `agent/constants/AgentTokenScope.java` | 新建 |
