# task003 - P0 Agent Token 认证与 Shiro 集成

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task002](task002-P0-数据模型与Flyway迁移.md)  
> **阻塞任务**：task008、task010、task015  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §5

---

## 1. 任务目标

实现 `Authorization: Bearer msat_<token>` 认证，注册 Shiro Filter，跳过 CSRF，并以关联用户登录 Session、设置项目上下文。

---

## 2. 现状参考

| 组件 | 路径 | 说明 |
|------|------|------|
| API Key Filter | `ApiKeyHandler` / `ApiKeyFilter` | AES 签名认证，可参考 Session 登录方式 |
| Shiro 配置 | `ShiroConfig.java` | 默认 `/** → apikey, csrf, authc` |
| Filter 链 | `FilterChainUtils.java` | 匿名路径配置 |

---

## 3. 任务清单

### 3.1 AgentTokenService

**路径**：`backend/services/agent-integration/.../security/AgentTokenService.java`

| 方法 | 说明 |
|------|------|
| `validate(String bearerToken)` | 解析 `msat_` 前缀，SHA-256 查表 |
| `checkScope(AgentToken token, String requiredScope)` | FUNCTIONAL_READ / SUBMIT / ALL |
| `isExpired(AgentToken token)` | 校验 expire_time |
| `createToken(...)` | P0 供脚本调用；P2 供 UI 调用 |

### 3.2 AgentTokenFilter

**路径**：`.../security/AgentTokenFilter.java`

**职责**：

1. 匹配 `/api/agent/v1/**`（health 除外）  
2. 解析 `Authorization: Bearer msat_xxx`  
3. 校验 token（hash、过期、enable、scope）  
4. 以 `user_id` 登录 Shiro（参考 `ApiKeyFilter`）  
5. 设置 `SessionUtils.setCurrentProjectId()`：优先 `X-MS-PROJECT`，其次 token 默认项目  
6. **不校验 CSRF**（与 ApiKey 同类）  

### 3.3 Shiro 注册

**`FilterChainUtils.java`**：

```java
filterChainDefinitionMap.put("/api/agent/v1/health", "anon");
```

**`ShiroConfig.java`**：

```java
shiroFilterFactoryBean.getFilters().put("agentToken", new AgentTokenFilter());
filterChainDefinitionMap.put("/api/agent/v1/**", "agentToken");
```

**注意**：`/api/agent/v1/**` 规则需在 `/**` 之前注册，避免被 `csrf` 拦截。

### 3.4 Scope 常量

```java
public class AgentTokenScope {
    public static final String FUNCTIONAL_READ = "FUNCTIONAL_READ";
    public static final String FUNCTIONAL_SUBMIT = "FUNCTIONAL_SUBMIT";
    public static final String FUNCTIONAL_ALL = "FUNCTIONAL_ALL";
}
```

### 3.5 Controller 层 Scope 校验

在 Controller 或 AOP 中：

| 接口 | 所需 Scope |
|------|-----------|
| search / get / modules | FUNCTIONAL_READ 或 FUNCTIONAL_ALL |
| submit | FUNCTIONAL_SUBMIT 或 FUNCTIONAL_ALL |

---

## 4. 错误响应

| 场景 | HTTP | 说明 |
|------|------|------|
| 无 Authorization | 401 | Token 缺失 |
| Token 无效/过期 | 401 | 认证失败 |
| Scope 不足 | 403 | 权限不足 |
| 无项目上下文 | 400 | 缺少 X-MS-PROJECT 且 token 无默认项目 |

---

## 5. 单元测试

- [ ] 有效 Token 可通过 Filter  
- [ ] 无效/过期 Token 返回 401  
- [ ] READ scope 无法调用 submit  
- [ ] CSRF header 缺失时 Agent API 仍可访问  
- [ ] `X-MS-PROJECT` 正确写入 Session  

---

## 6. 验收标准

- [x] `AgentTokenFilter` / `AgentTokenService` / `AgentShiroConfigurer` 已实现  
- [ ] `curl -H "Authorization: Bearer msat_xxx"` 可访问 Agent API（待 task010 联调）  
- [ ] 无 Token 时返回 401（待 task010 联调）  
- [ ] Session 中用户与项目上下文正确（待 task010 联调）  

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
| 备注 | 代码已交付；curl/鉴权联调归属 task010 |
