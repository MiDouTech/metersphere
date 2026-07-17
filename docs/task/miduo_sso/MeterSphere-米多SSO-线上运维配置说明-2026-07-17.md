# MeterSphere 米多星球 SSO — 线上运维配置说明

> **日期**：2026-07-17  
> **环境**：线上 `https://msp.ebcone.net`（阿里云运维平台部署，**非 CDS**）  
> **对象**：运维同学（配置注入 + 重启 + 验收）  
> **标注**：【AI生成】已按联调结论整理；`MIDUO_SSO_APP_SECRET` 由交付方线下提供，**禁止写入 Git / 工单明文群发**

---

## 1. 背景与现象

| 项 | 说明 |
|----|------|
| 现象 | 打开线上登录页无法跳转米多星球；Network 可见 `/api/is-login` 返回 401 |
| 业务侧已完成 | 组织企微通讯录同步（成员已同步） |
| 实测根因 | 线上后端 **米多 SSO 未启用**，与企微同步无关 |

线上实测（2026-07-17）：

```http
GET https://msp.ebcone.net/api/auth/miduo/status
```

响应摘要：

```json
{
  "data": {
    "enabled": false,
    "ready": false,
    "reason": "DISABLED",
    "message": "米多 SSO 未启用"
  }
}
```

```http
GET https://msp.ebcone.net/api/auth/miduo/bridge-url
```

返回业务错误：`米多 SSO 未启用或配置不完整`。

> 说明：`/api/is-login` 在未登录时返回 **401 属于正常行为**，不是本次故障根因。

---

## 2. 重要结论（请运维先读）

1. **CDS 环境变量覆盖 ≠ 线上生效**。线上通过阿里云运维平台部署，必须在该平台（或线上 Nacos）单独配置。
2. **不必为「启用 SSO」单独改业务代码并发版**（当前线上已具备 `/api/auth/miduo/**` 接口）。需要的是注入运行时配置并重启后端。
3. **Secret 禁止入库**；优先放在运维平台「容器环境变量 / 密钥管理」，其次才考虑 Nacos。

---

## 3. 必配项（请完整配置）

应用名建议：MeterSphere **后端**容器 / 服务（读 Nacos、对外提供 `/api` 的那套）。

| 环境变量名 | 值 | 说明 |
|------------|----|------|
| `MIDUO_SSO_ENABLED` | `true` | 开关，必须为 true |
| `MIDUO_SSO_BASE_URL` | `https://admin.t.ebcone.cn` | 米多开放 API 根地址 |
| `MIDUO_SSO_APP_CODE` | `APP00016` | 米多应用编码 |
| `MIDUO_SSO_APP_SECRET` | `<向研发/产品索取交付单密钥>` | **仅后端**；勿写进仓库 |
| `MIDUO_SSO_REDIRECT_URI` | `https://msp.ebcone.net` | 须与米多白名单 **字符级完全一致**（无尾斜杠、无多余 path） |
| `MIDUO_SSO_SHORTCUT_ID` | `81` | 工作台快捷入口 ID |
| `MIDUO_SSO_ORGANIZATION_ID` | `100001` | 企微同步就绪判定所用组织 ID |

### 3.1 推荐做法：阿里云运维平台 — 容器环境变量

1. 打开线上 MeterSphere **后端**应用/容器配置。
2. 在「环境变量」中新增上表全部键值（`APP_SECRET` 走平台密钥/密文变量更佳）。
3. **重建或重启后端容器**（仅保存变量不重启，通常不生效）。
4. 保持现有 Nacos 相关变量不变（示例，以平台已有值为准）：
   - `SPRING_PROFILES_ACTIVE=nacos`
   - `NACOS_SERVER_ADDR` / `NACOS_NAMESPACE` / `NACOS_GROUP` / `NACOS_USERNAME` / `NACOS_PASSWORD`

参考文档：仓库内 `deploy/publish-platform.md`。

### 3.2 备选做法：Nacos 配置中心

若平台习惯用 Nacos，在线上 Nacos 中编辑（**不要整文件用仓库模板覆盖已有生产配置**）：

| 项 | 建议值（以现网为准） |
|----|----------------------|
| namespace | `prod`（或现网实际 namespace） |
| group | `METERSPHERE` |
| dataId | `metersphere.properties` |

追加内容：

```properties
miduo.sso.enabled=true
miduo.sso.base-url=https://admin.t.ebcone.cn
miduo.sso.app-code=APP00016
miduo.sso.app-secret=<交付单密钥>
miduo.sso.redirect-uri=https://msp.ebcone.net
miduo.sso.shortcut-id=81
miduo.sso.organization-id=100001
```

发布配置后 **重启后端容器**（该类配置建议重启，勿假设热更新一定生效）。

> 安全建议：`app-secret` 仍优先放运维平台环境变量，减少 Nacos 明文接触面。

---

## 4. 操作顺序（建议照做）

```text
① 向研发索取 MIDUO_SSO_APP_SECRET（线下安全渠道）
② 在阿里云运维平台为「后端」注入 MIDUO_SSO_*（推荐）
   或写入 Nacos metersphere.properties（备选）
③ 重建 / 重启后端容器
④ 执行本文第 5 节验收接口
⑤ 通知业务方验证：打开 https://msp.ebcone.net/#/login 应跳转米多登录
```

前端静态资源：若现网前端已能打开登录页且存在米多相关接口调用，**通常无需为本次单独发前端**。若验收时 status 已 OK 但仍无法落地登录，再排查前端是否含 `#/sso/miduo/callback` 落地页（联系研发）。

---

## 5. 验收标准（配置完成后必测）

在能访问线上的网络环境执行（浏览器或 curl 均可）。

### 5.1 SSO 状态 — 必须通过

```bash
curl -sS "https://msp.ebcone.net/api/auth/miduo/status"
```

期望 `data` 类似：

```json
{
  "enabled": true,
  "ready": true,
  "reason": "OK",
  "message": "ready"
}
```

若 `enabled=true` 但 `ready=false`，按 `reason` 处理：

| reason | 含义 | 处理 |
|--------|------|------|
| `DISABLED` | 未启用或配置不完整 | 检查 7 个环境变量是否齐全、是否重启 |
| `WECOM_SYNC_NOT_CONFIGURED` | 组织未配企微通讯录 | 业务侧配置企微同步 |
| `NO_SYNCED_USERS` | 无已同步企微成员 | 业务侧执行「同步企业微信成员」 |

### 5.2 登录桥 — 必须通过

```bash
curl -sS "https://msp.ebcone.net/api/auth/miduo/bridge-url"
```

期望：`data.url` 为指向米多侧的 HTTPS 地址（含 `appCode`、`redirectUri`、`state` 等参数），且 HTTP 非 500。

### 5.3 页面验收 — 业务确认

1. 浏览器打开：`https://msp.ebcone.net/#/login`
2. 应自动跳转米多星球登录 / 授权
3. 登录成功后回跳 `https://msp.ebcone.net`（可能带 `?token=...`），最终进入系统
4. 管理员账密入口（如需）：`https://msp.ebcone.net/#/login/admin` 仍可用

---

## 6. 回滚

若启用后异常，任选其一并重启后端：

- 将 `MIDUO_SSO_ENABLED` 改为 `false`（或删除全部 `MIDUO_SSO_*`）
- Nacos 中将 `miduo.sso.enabled=false`

回滚后登录页不再走米多桥；不影响既有账密/其他登录方式（以现网前端行为为准）。

---

## 7. 安全与注意事项

1. **禁止**将 `MIDUO_SSO_APP_SECRET` 提交到 Git、镜像构建参数明文、公开工单评论。
2. `MIDUO_SSO_REDIRECT_URI` 必须与米多侧白名单完全一致：`https://msp.ebcone.net`（不要改成带 `/#/sso/...` 的形式，除非米多白名单同步修改）。
3. 容器需能访问 `https://admin.t.ebcone.cn`（出网 / 代理 / 防火墙放行），否则 validate/refresh 会失败。
4. 勿用仓库内 `cds-compose.yml` 的默认值（其中 `MIDUO_SSO_ENABLED=false`）覆盖线上配置。

---

## 8. 联系与附件

| 项 | 说明 |
|----|------|
| 研发配置依据 | `docs/task/miduo_sso/APP00016-启用清单.md` |
| 发布平台通用说明 | `deploy/publish-platform.md` |
| Secret 来源 | 米多交付单 `APP00016`（向研发/产品线下索取） |
| 组织 ID | `100001`（与现网企微同步组织一致） |

---

## 9. 运维签字确认（可选）

| 检查项 | 结果（是/否） | 操作人 | 时间 |
|--------|---------------|--------|------|
| 已注入全部 `MIDUO_SSO_*` |  |  |  |
| 已重启后端容器 |  |  |  |
| `/api/auth/miduo/status` 为 enabled+ready |  |  |  |
| `/api/auth/miduo/bridge-url` 返回 url |  |  |  |
| 业务确认 `/#/login` 可跳转米多 |  |  |  |
