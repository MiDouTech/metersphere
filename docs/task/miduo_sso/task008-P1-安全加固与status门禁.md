# task008 - P1 安全加固与 status 门禁

> **阶段**：P1  
> **预估工期**：1 天  
> **前置依赖**：[task003](task003-P0-state-callback与用户匹配.md)、[task004](task004-P0-sessionToken存储与logout.md)  
> **阻塞任务**：task009  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §8

---

## 1. 任务目标

补齐 SSO 安全基线：status 门禁（企微同步就绪才开放）、日志脱敏、nonce 去重、时钟偏差校验；完善 `/auth/miduo/status` 供前端决定是否展示米多入口。

---

## 2. 任务清单

### 2.1 status 门禁

**路径**：`MiduoSsoApplicationService.getStatus()`

**响应示例**：

```json
{
  "enabled": true,
  "ready": false,
  "reason": "WECOM_SYNC_NOT_CONFIGURED",
  "message": "请先完成企微通讯录配置并同步成员"
}
```

| 字段 | 判定 |
|------|------|
| `enabled` | `miduo.sso.enabled` |
| `ready` | 目标组织 `org_wecom_sync_config` 已配置 **且** 存在 `wecom_userid` 非空启用用户 |
| `reason` | 枚举：`DISABLED` / `WECOM_SYNC_NOT_CONFIGURED` / `NO_SYNCED_USERS` / `OK` |

**组织范围**：P1 默认当前部署的「主组织」或配置 `miduo.sso.organization-id`；多组织 P2 扩展。

### 2.2 日志脱敏

| 数据 | 规则 |
|------|------|
| appSecret | 禁止打印 |
| sessionToken / exchange token | 仅前 6 位 + `***` |
| wework_userid | 可打（非 PII 敏感级，按公司规范） |
| state | 禁止全量（可后 4 位） |

**实现**：`MiduoSsoLogUtils.maskToken(String)` 统一使用。

### 2.3 nonce 去重（防重放）

**路径**：`MiduoSsoClient` 请求前

```text
Redis SET miduo:sso:nonce:{nonce} 1 EX 300 NX
```

若 key 已存在 → 拒绝请求并记 security warn。

### 2.4 时钟偏差

校验 `X-App-Timestamp` 与服务器时间差 ≤ 5 分钟（米多侧验签时同理；出站请求使用 `System.currentTimeMillis()`）。

### 2.5 审计日志

| 事件 | 记录 |
|------|------|
| SSO 登录成功 | userId、wework_userid 后 4 位 |
| SSO 登录失败 | reason 枚举，无 token |
| revoke | userId |
| refresh 失败 | userId |

写 `operation_log` 或专用 `miduo_sso_audit`（P2）。

### 2.6 评审必查清单（内嵌验收）

- [ ] appSecret 未出现在前端 Network  
- [ ] 未信任 URL PII 登录  
- [ ] validate 判 `valid` 而非仅 `return_code`  
- [ ] 未匹配用户不自动建号  
- [ ] redirectUri 与白名单一致  

---

## 3. 测试用例

| 场景 | 预期 |
|------|------|
| 无企微配置 | `ready=false` |
| 有配置无用户 | `ready=false`, `NO_SYNCED_USERS` |
| 有同步用户 | `ready=true` |
| 重复 nonce | 客户端拒绝发送 |

---

## 4. 验收标准

- [ ] `/auth/miduo/status` 准确反映就绪状态  
- [ ] 未 ready 时 callback 可拒绝或仅 warn（产品决策：建议拒绝并提示）  
- [ ] 全链路日志无明文 secret/token  
- [ ] 安全评审 P0/P1 项勾选通过  

---

## 5. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
