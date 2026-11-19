# task002 - P0 米多开放 API 签名客户端

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task001](task001-P0-接入确认单与配置项.md)  
> **阻塞任务**：task003、task004、task007  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §5

---

## 1. 任务目标

实现 `MiduoSsoClient`：按米多规范生成 HMAC 签名，封装 `validate-login-token`、`refresh-session-token`、`revoke-session-token` 三个开放 API，以及 bridge URL 拼装辅助。

---

## 2. 现状缺口

| 能力 | 现状 |
|------|------|
| HMAC 签名 | **无** |
| 米多 SSO HTTP 调用 | **无** |
| 响应解析（valid / success） | **无** |

---

## 3. 任务清单

### 3.1 DTO

**路径**：`backend/services/system-setting/src/main/java/io/metersphere/system/dto/sso/miduo/`

| 类 | 说明 |
|----|------|
| `MiduoValidateRequest` | `{ "token": "exch_xxx" }` |
| `MiduoValidateResponse` | `valid`, `sessionToken`, `weworkUserid`, `expiresAt` 等 |
| `MiduoRefreshRequest` | `{ "sessionToken": "..." }` |
| `MiduoRefreshResponse` | `success`, `sessionToken`, `expiresAt` |
| `MiduoRevokeRequest` | `{ "sessionToken": "..." }` |
| `MiduoRevokeResponse` | `success` |
| `MiduoOpenApiResult<T>` | 统一包装 `return_code` / `return_data` / `return_msg` |

### 3.2 MiduoSsoClient

**路径**：`.../sso/miduo/MiduoSsoClient.java`

| 方法 | 说明 |
|------|------|
| `validateLoginToken(String exchangeToken)` | POST `/api/open/sso/validate-login-token` |
| `refreshSessionToken(String sessionToken)` | POST `/api/open/sso/refresh-session-token` |
| `revokeSessionToken(String sessionToken)` | POST `/api/open/sso/revoke-session-token` |
| `buildBridgeUrl()` | GET `/api/sso/bridge/redirect-url` 或按米多文档拼装 |
| `sign(String signedValue)` | 生成 nonce、timestamp、signature |

**签名实现**：

```text
canonical = appCode + "\n" + timestamp + "\n" + nonce + "\n" + signedValue
signature = Base64(HMAC_SHA256(appSecret, canonical))
```

请求头：`X-App-Code`、`X-App-Timestamp`、`X-App-Nonce`、`X-App-Signature`。

### 3.3 业务判定（禁止只认 return_code）

| 接口 | 成功条件 |
|------|----------|
| validate | `return_data.valid == true` |
| refresh | `return_data.success == true` |
| revoke | `return_data.success == true` |

失败时抛出 `MiduoSsoException`，message 脱敏（不含 token 全量）。

### 3.4 HTTP 实现

- 使用项目已有 `RestTemplate` / `HttpClient`（与 `WecomContactClient` 风格一致）  
- 超时：connect 5s / read 15s  
- 重试：validate 不重试；refresh 最多 1 次（幂等谨慎）  

### 3.5 单元测试

**路径**：`MiduoSsoClientTest.java`

- [ ] 签名 canonical 与参考向量一致（使用 mock appSecret）  
- [ ] `valid=false` 时抛业务异常  
- [ ] `return_code=0` 但 `valid=false` 时判定为失败  
- [ ] 日志不包含 appSecret / token 全量  

---

## 4. 验收标准

- [ ] 三个开放 API 可调通（对接米多测试环境）  
- [ ] 签名被米多侧接受  
- [ ] 响应解析覆盖 `wework_userid`、`sessionToken`  
- [ ] 单测通过，覆盖率 ≥ 核心签名逻辑  

---

## 5. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
