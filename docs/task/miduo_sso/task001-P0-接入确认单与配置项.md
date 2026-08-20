# task001 - P0 接入确认单与配置项

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：无（需与米多侧并行确认）  
> **阻塞任务**：task002、task003、task005  
> **关联方案**：[接入方案](../../summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §4、§6.1

---

## 1. 任务目标

完成 MeterSphere ↔ 米多星球 SSO **接入确认单**填写，并在后端落地 `miduo.sso.*` 配置读取（环境变量优先），为 Client 与 callback 提供统一配置源。

---

## 2. 现状缺口

| 项 | 现状 |
|----|------|
| `miduo.sso.*` 配置 | **不存在** |
| 米多 appCode / Secret | 未交付 |
| redirectUri 白名单 | 未与米多对齐 |
| tokenDeliveryMode | 未确认 QUERY / FORM_POST |

---

## 3. 任务清单

### 3.1 接入确认单（文档化）

在 `docs/summary/` 或项目 Wiki 归档已填写确认单，至少包含：

| 字段 | 说明 | 状态 |
|------|------|------|
| `MIDUO_SSO_BASE_URL` | 米多 API 根地址 | [ ] |
| `MIDUO_SSO_APP_CODE` | 应用编码 | [ ] |
| `MIDUO_SSO_APP_SECRET` | 仅后端环境变量 | [ ] |
| `MIDUO_SSO_REDIRECT_URI` | 与米多白名单字符级一致 | [ ] |
| `tokenDeliveryMode` | QUERY / FORM_POST | [ ] |
| `MIDUO_SSO_SHORTCUT_ID` | 工作台快捷入口（可选） | [ ] |

**redirectUri 候选（须与米多确认其一）**：

| 环境 | 推荐 URI |
|------|----------|
| 灰度 CDS | `https://v3-x-metersphere.miduo.org/#/sso/miduo/callback` |
| 生产 | `https://{prod-host}/#/sso/miduo/callback` |

### 3.2 配置类

**路径**：`backend/services/system-setting/src/main/java/io/metersphere/system/config/MiduoSsoProperties.java`

```java
@ConfigurationProperties(prefix = "miduo.sso")
public class MiduoSsoProperties {
    private boolean enabled = false;
    private String baseUrl;
    private String appCode;
    private String appSecret;      // 从环境变量读取
    private String redirectUri;
    private String shortcutId;     // 可选
    private long stateTtlSeconds = 600;
    private long sessionTtlSeconds = 86400;
}
```

**`application.properties` 示例（占位，不含 Secret）**：

```properties
miduo.sso.enabled=${MIDUO_SSO_ENABLED:false}
miduo.sso.base-url=${MIDUO_SSO_BASE_URL:}
miduo.sso.app-code=${MIDUO_SSO_APP_CODE:}
miduo.sso.app-secret=${MIDUO_SSO_APP_SECRET:}
miduo.sso.redirect-uri=${MIDUO_SSO_REDIRECT_URI:}
```

### 3.3 配置校验

**路径**：`MiduoSsoConfigValidator.java`（或写在 Properties `@PostConstruct`）

- `enabled=true` 时校验 `baseUrl`、`appCode`、`appSecret`、`redirectUri` 非空  
- Secret 禁止写入 Flyway / 仓库配置文件  
- 启动日志仅打印 `appCode` 后 4 位掩码  

### 3.4 部署清单

| 环境 | 配置注入方式 |
|------|--------------|
| 本地 | `.env` / `local-runtime`（gitignore） |
| CDS 灰度 | CDS 项目 env 或 compose `environment` |
| 生产 | 发布平台环境变量 / 挂载配置文件 |

---

## 4. 与现有配置隔离

| 配置域 | 表/键 | 禁止混用 |
|--------|-------|----------|
| 企微通讯录 | `org_wecom_sync_config` | 不得作为米多 SSO Secret |
| LDAP 等 | `auth_source` | 不得复用为米多 appSecret |
| 米多 SSO | `miduo.sso.*` | 独立环境变量 |

---

## 5. 验收标准

- [ ] 确认单四项已与米多对齐并归档  
- [ ] `miduo.sso.enabled=false` 时应用正常启动，无 SSO 路由副作用  
- [ ] `enabled=true` 且缺 Secret 时启动失败并给出明确错误  
- [ ] 仓库内无 `appSecret` 明文  
- [ ] CDS / 生产环境变量清单已文档化  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
