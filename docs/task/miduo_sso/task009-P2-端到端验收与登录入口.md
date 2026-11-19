# task009 - P2 端到端验收与登录入口

> **阶段**：P2  
> **预估工期**：1 天  
> **前置依赖**：[task006](task006-P0-前端Callback页与白名单.md)、[task007](task007-P1-refresh策略与登录桥.md)、[task008](task008-P1-安全加固与status门禁.md)  
> **阻塞任务**：无  
> **关联方案**：[接入方案](../../product_md/history/summary/MeterSphere-第三方SSO单点登录接入方案-2026-07-16.md) §9

---

## 1. 任务目标

完成米多工作台 → MeterSphere 全链路验收；可选增加登录页「通过米多登录」入口；输出联调报告与运维检查单。

---

## 2. 端到端验收场景

### 2.1 主路径（米多工作台）

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 米多工作台点击 MeterSphere 快捷入口 | 302 到 callback 带 token+state |
| 2 | Callback 页 POST `/auth/miduo/callback` | 200，建立 Session |
| 3 | 跳转工作台 | 组织/项目上下文正确 |
| 4 | 调用业务 API | 200 |
| 5 | 点击退出 | revoke + 本地登出 |
| 6 | 米多侧重进 | 需重新 SSO |

### 2.2 异常路径

| 场景 | 预期 |
|------|------|
| 未同步用户 | 明确错误文案 |
| state 过期 | 提示重新从米多进入 |
| 企微未配置 | status `ready=false` |
| refresh 失败（P1） | 跳登录桥 |

### 2.3 环境矩阵

| 环境 | URL | 负责人 |
|------|-----|--------|
| CDS 灰度 | `https://v3-x-metersphere.miduo.org` | |
| 生产 | `https://{prod}` | |

### 2.4 与 Agent API 正交验证

| 步骤 | 预期 |
|------|------|
| 米多 SSO 登录后发放 Agent Token | 正常 |
| Agent `/api/agent/v1/functional/health` | 不受 SSO 影响 |

---

## 3. 可选：登录页入口（P2）

**路径**：`frontend/src/views/login/components/login-form.vue`

当 `getMiduoSsoStatus().ready === true` 时展示：

```html
<a-button @click="redirectToMiduo">通过米多星球登录</a-button>
```

**跳转逻辑**：

1. `GET /auth/miduo/state`  
2. 拼米多授权 URL（按米多文档 + shortcutId）  
3. `window.location.href = ...`  

若主入口仅为工作台，本项可标记为 **跳过**。

---

## 4. 交付物

| 文档 | 路径 |
|------|------|
| 联调验收记录 | `docs/develop_logs/miduo_sso/YYYY-MM-DD-SSO联调验收.md` |
| 运维检查单 | 环境变量、白名单、Redis、CDS 路由 |
| 冒烟脚本 | `scripts/verify-miduo-sso.ps1`（若 task005 未建则本任务补齐） |

---

## 5. 验收标准（对照方案 §9）

- [ ] callback 接收 token + state（模式与米多一致）  
- [ ] state 校验后 validate  
- [ ] Session 建立，sessionToken 仅后端 Redis  
- [ ] wecom_userid 匹配；未匹配拒绝  
- [ ] logout revoke  
- [ ] refresh 失败走 bridge（P1）  
- [ ] 日志无 secret/token 全量  
- [ ] 灰度 `/front/auth/miduo/**` 可达 Java  
- [ ] 组织成员可通过「同步企业微信成员」保证账号就绪  
- [ ] 技术负责人评审签字  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
