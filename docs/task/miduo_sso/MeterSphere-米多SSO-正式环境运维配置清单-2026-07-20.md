# MeterSphere 米多星球 SSO — 正式环境运维配置清单

> **日期**：2026-07-20  
> **环境**：正式 `https://msp.ebcone.cn`（阿里云运维平台部署，**非 CDS**）  
> **米多正式**：`https://admin.ebcone.cn`  
> **对象**：运维 / 产品（米多应用开通 + MS 配置注入 + 重启 + 验收）  
> **标注**：【AI生成】待人工审核确认；`MIDUO_SSO_APP_SECRET` 仅线下交接，**禁止写入 Git / 工单明文群发**

---

## 0. 与测试环境的关键区别（必读）

| 项 | 测试（历史联调） | 正式（本清单） |
|----|------------------|----------------|
| MeterSphere | `https://msp.ebcone.net` 或测试域 | **`https://msp.ebcone.cn`** |
| 米多开放/登录桥 | `https://admin.t.ebcone.cn` | **`https://admin.ebcone.cn`** |
| appCode | 测试应用 `APP00016` | **须在正式米多新建/启用应用**（勿假定测试编码在正式可用） |
| redirect 白名单 | 测试交付单白名单 | **必须含** `https://msp.ebcone.cn`（字符级一致） |

**当前故障对照（2026-07-20）**

浏览器已跳到：

```text
https://admin.ebcone.cn/sso/bridge?appCode=APP00016&redirect_uri=https://msp.ebcone.cn&state=...
```

米多页面报错：**「应用不存在或未启用」**。

含义：正式米多 `admin.ebcone.cn` 上 **没有启用** 当前配置的 `appCode`（截图中为测试编码 `APP00016`）。  
这是 **米多正式侧应用未开通/未启用或仍填测试 appCode**，不是 MeterSphere 业务代码缺陷。

---

## 1. 正式侧前置（产品 / 米多管理员）

在 **正式米多**（`https://admin.ebcone.cn`）完成后再改 MS 配置：

1. 为「测试资产云平台 / MeterSphere」创建第三方应用（或启用已有正式应用）。
2. 记录正式交付信息：
   - `appCode`（正式编码，**可能不是** `APP00016`）
   - `appSecret`（仅后端）
   - `shortcutId`（若走工作台快捷入口）
3. 登录桥 **redirectUri 白名单** 增加（勿多尾斜杠、勿擅自加 path）：

```text
https://msp.ebcone.cn
```

4. 确认应用状态为 **已启用**。
5. 将正式 `appCode` / `appSecret` / `shortcutId` 线下交给运维（密钥走安全渠道）。

> 若坚持复用测试 `APP00016`：必须在 **正式** `admin.ebcone.cn` 为该编码开通并启用，且白名单含 `https://msp.ebcone.cn`。多数情况下测试库与正式库隔离，**更推荐正式单独应用**。

---

## 2. MeterSphere 后端必配项

应用：MeterSphere **后端**容器 / 服务（对外 `/api`）。

| 环境变量名 | 正式建议值 | 说明 |
|------------|------------|------|
| `MIDUO_SSO_ENABLED` | `true` | 开关 |
| `MIDUO_SSO_BASE_URL` | `https://admin.ebcone.cn` | **正式**米多根地址（不要用 `admin.t.ebcone.cn`） |
| `MIDUO_SSO_APP_CODE` | `<正式 appCode>` | 来自正式米多交付；勿照搬测试 `APP00016`（除非正式已开通同码） |
| `MIDUO_SSO_APP_SECRET` | `<正式 appSecret>` | **仅后端**；勿入库 |
| `MIDUO_SSO_REDIRECT_URI` | `https://msp.ebcone.cn` | 与米多白名单 **字符级完全一致** |
| `MIDUO_SSO_SHORTCUT_ID` | `<正式 shortcutId>` | 无快捷入口可留空（以交付单为准） |
| `MIDUO_SSO_ORGANIZATION_ID` | `<现网企微同步组织 ID>` | 与正式企微同步组织一致（历史测试曾用 `100001`，以现网为准） |

### 2.1 推荐：阿里云运维平台 — 容器环境变量

1. 打开正式 MeterSphere **后端**应用配置。
2. 写入上表全部键值（Secret 用平台密钥/密文变量）。
3. **重建或重启后端容器**（只保存不重启通常不生效）。
4. 保持 `SPRING_PROFILES_ACTIVE=local`，并确认 `/opt/metersphere/conf` 已挂载。

### 2.2 备选：挂载配置文件

在 `/opt/metersphere/conf/metersphere.properties` 中追加（占位勿提交真实 Secret）：

```properties
miduo.sso.enabled=true
miduo.sso.base-url=https://admin.ebcone.cn
miduo.sso.app-code=<正式appCode>
miduo.sso.app-secret=<正式appSecret>
miduo.sso.redirect-uri=https://msp.ebcone.cn
miduo.sso.shortcut-id=<正式shortcutId>
miduo.sso.organization-id=<现网组织ID>
```

发布后 **重启后端**。

---

## 3. 操作顺序

```text
① 正式米多开通/启用应用 + 白名单 https://msp.ebcone.cn
② 线下交接正式 appCode / appSecret / shortcutId
③ 运维平台注入 MIDUO_SSO_*（BASE_URL=admin.ebcone.cn，REDIRECT=msp.ebcone.cn）
④ 重启后端容器
⑤ 执行第 4 节验收
⑥ 业务验证：https://msp.ebcone.cn/#/login 可跳转米多并回跳登录
```

---

## 4. 验收标准

### 4.1 SSO 状态

```bash
curl -sS "https://msp.ebcone.cn/api/auth/miduo/status"
```

期望：

```json
{
  "data": {
    "enabled": true,
    "ready": true,
    "reason": "OK",
    "message": "ready"
  }
}
```

| reason | 含义 | 处理 |
|--------|------|------|
| `DISABLED` | 未启用或配置不完整 | 核对 7 项变量并重启 |
| `WECOM_SYNC_NOT_CONFIGURED` | 组织未配企微通讯录 | 业务配置企微同步 |
| `NO_SYNCED_USERS` | 无已同步企微成员 | 执行通讯录同步 |

### 4.2 登录桥

```bash
curl -sS "https://msp.ebcone.cn/api/auth/miduo/bridge-url"
```

期望：`data.url` 为 HTTPS，且包含：

- 主机：`admin.ebcone.cn`（或正式米多约定域名）
- `appCode=<正式编码>`
- `redirectUri=https://msp.ebcone.cn`（或米多侧等价参数名）

### 4.3 页面

1. 打开 `https://msp.ebcone.cn/#/login` → 应跳转正式米多登录/扫码。  
2. 成功后回跳 `https://msp.ebcone.cn`（可带 `token`），完成系统登录。  
3. 运维账密入口（如有）：`https://msp.ebcone.cn/#/login/admin` 仍可用。

### 4.4 失败对照

| 现象 | 优先排查 |
|------|----------|
| 米多页「应用不存在或未启用」 | 正式米多未开通/未启用该 appCode；或 MS 仍配置测试编码 |
| 回跳被拒 / redirect 相关错误 | 白名单与 `MIDUO_SSO_REDIRECT_URI` 不一致（`.cn` vs `.net`、尾斜杠、多余 path） |
| status 为 DISABLED | 变量未注入或未重启 |
| validate 失败 | Secret 错误、机器时间偏差、出网访问 `admin.ebcone.cn` 不通 |

---

## 5. 回滚

任选其一并重启后端：

- `MIDUO_SSO_ENABLED=false`（或删除全部 `MIDUO_SSO_*`）
- 挂载配置文件：`miduo.sso.enabled=false`

回滚后登录页不再强制走米多桥；不影响既有运维账密入口（以现网前端为准）。

---

## 6. 安全与注意事项

1. **禁止**将正式 `APP_SECRET` 提交 Git、写进镜像构建明文参数、公开工单。  
2. `MIDUO_SSO_REDIRECT_URI` 必须与米多白名单一致：`https://msp.ebcone.cn`。  
3. 容器需能访问 `https://admin.ebcone.cn`（出网 / 代理 / 防火墙）。  
4. **禁止**用仓库 `cds-compose.yml` 默认值（`MIDUO_SSO_ENABLED=false`）覆盖正式配置。  
5. **禁止**把测试文档中的 `admin.t.ebcone.cn` + 测试 `APP00016` 原样当作正式配置（除非正式米多已明确开通同码）。

---

## 7. 参考与联系

| 项 | 说明 |
|----|------|
| 历史线上说明（旧域 `.net` / 测试米多） | `docs/task/miduo_sso/MeterSphere-米多SSO-线上运维配置说明-2026-07-17.md` |
| 测试启用清单 | `docs/task/miduo_sso/APP00016-启用清单.md` |
| 配置示例（无 Secret） | `docs/task/miduo_sso/miduo-sso.properties.example` |
| 正式 Secret / appCode | 正式米多交付单（线下索取） |

---

## 8. 运维签字确认（可选）

| 检查项 | 结果 | 操作人 | 时间 |
|--------|------|--------|------|
| 正式米多应用已启用 |  |  |  |
| 白名单含 `https://msp.ebcone.cn` |  |  |  |
| 已注入正式 `MIDUO_SSO_*`（BASE=`admin.ebcone.cn`） |  |  |  |
| 已重启后端 |  |  |  |
| `/api/auth/miduo/status` 为 enabled+ready |  |  |  |
| `/api/auth/miduo/bridge-url` 返回正确 url |  |  |  |
| 业务确认扫码/跳转登录成功 |  |  |  |
