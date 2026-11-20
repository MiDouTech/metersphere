# task003 - P0 state / callback / 用户匹配 / Shiro 会话

> **阶段**：P0  
> **预估工期**：2 天  
> **前置依赖**：[task001](task001-P0-接入确认单与配置项.md)、[task002](task002-P0-米多开放API签名客户端.md)、[community_rebuild task007–008](../community_rebuild/task007-P2-组织架构同步引擎.md)  
> **阻塞任务**：task004、task006、task007、task008  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §3、§6.2–6.3

---

## 1. 任务目标

实现米多 SSO 核心登录链路：`state` 生成与校验 → `callback` 收 exchange token → 调米多 validate → 按 `wecom_userid` 匹配用户 → 建立 MeterSphere Shiro Session。

---

## 2. 现状参考

| 组件 | 路径 | 复用方式 |
|------|------|----------|
| 用户匹配 | `ExtUserMapper.selectByWecomUserid` | 直接调用 |
| 建会话 | `LocalRealm` + `UserLoginService` | `authenticate=MIDUO` 走免密分支 |
| 组织切换 | `UserLoginService.autoSwitch()` | callback 成功后调用 |
| 企微用户来源 | `UserSyncHandler` | SSO **不**调用 createUser |

---

## 3. 任务清单

### 3.1 扩展 UserSource

**路径**：`backend/framework/sdk/src/main/java/io/metersphere/sdk/constants/UserSource.java`

```java
public enum UserSource {
    LOCAL, LDAP, CAS, OIDC, OAUTH2, QR_CODE, MIDUO
}
```

### 3.2 MiduoSsoStateService

**路径**：`.../sso/miduo/MiduoSsoStateService.java`

| 方法 | 说明 |
|------|------|
| `generateState()` | 随机串 ≥32 字节，写 Redis `miduo:sso:state:{state}`，TTL 10min |
| `consumeState(String state)` | 一次性校验并删除；失败抛 `MSException` |

可选：同时写 HttpOnly Cookie `miduo_sso_state` 做双重校验。

### 3.3 MiduoSsoApplicationService

**路径**：`.../sso/miduo/MiduoSsoApplicationService.java`

| 方法 | 说明 |
|------|------|
| `handleCallback(String token, String state)` | 主流程 |
| `matchUser(String weworkUserid)` | 调 `selectByWecomUserid`，校验 enable/deleted |
| `establishSession(UserDTO user)` | 设 `authenticate=MIDUO`，建 Shiro Session |

**callback 流程**：

1. `consumeState(state)`  
2. `miduoSsoClient.validateLoginToken(token)`  
3. 取 `wework_userid`（**禁止**读 URL 中 mobile/name）  
4. `matchUser` → 不存在则拒绝，文案：「请先同步企业微信成员」  
5. `session.setAttribute("authenticate", UserSource.MIDUO.name())`  
6. `UsernamePasswordToken(userId, "")` + `subject.login(token)`  
7. `SessionUtils.putUser` + `autoSwitch`  
8. 返回 `SessionUser`（**不含** sessionToken）  

### 3.4 MiduoSsoAuthController

**路径**：`.../sso/miduo/MiduoSsoAuthController.java`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/miduo/status` | enabled、syncReady（见 task008） |
| GET | `/auth/miduo/state` | 返回 `{ state }` |
| POST | `/auth/miduo/callback` | Body `{ token, state }` |
| GET | `/auth/miduo/bridge-url` | P0 可先返回固定 URL，P1 完善 |

**FORM_POST 支持**：若米多 `tokenDeliveryMode=FORM_POST`，增加 `POST /auth/miduo/landing` 接收表单再 302 到前端 hash 页（可选，按确认单）。

### 3.5 单元 / 集成测试

**路径**：`MiduoSsoApplicationServiceTest.java`

- [ ] state 过期拒绝  
- [ ] state 重复使用拒绝  
- [ ] validate `valid=false` 拒绝  
- [ ] 无 `wecom_userid` 用户拒绝  
- [ ] 禁用用户拒绝  
- [ ] 成功用户 Session 中 `authenticate=MIDUO`  

Mock `MiduoSsoClient`，不依赖真实米多环境跑 CI。

---

## 4. 用户匹配说明

`selectByWecomUserid` 为**全局**查询（无 organizationId 过滤）。

| 场景 | 策略 |
|------|------|
| 单 Corp / 单组织（当前默认） | 直接使用 |
| 多组织同 userid（极少） | P2 增加组织成员校验 |

---

## 5. 验收标准

- [ ] `GET /auth/miduo/state` 返回 state 并写入 Redis  
- [ ] `POST /auth/miduo/callback` 对已同步用户建立 Session  
- [ ] 未同步用户返回明确错误码与文案  
- [ ] 响应体不含 `sessionToken`  
- [ ] `GET /is-login` 登录后返回完整 `SessionUser`  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
