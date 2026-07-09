# task015 - P2 Token 管理 UI

> **阶段**：P2  
> **预估工期**：2 天  
> **前置依赖**：[task003](task003-P0-AgentToken认证与Shiro集成.md)  
> **阻塞任务**：无  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §5.1.5

---

## 1. 任务目标

在系统设置中新增「Agent 集成 → Token 管理」页面，支持创建、启用/禁用、删除 Agent Token，替代 P0 手工 SQL 方式。

---

## 2. 后端 API

**路径**：`backend/services/agent-integration/.../controller/AgentTokenController.java`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/agent/token/add` | 系统管理员 | 创建 Token，响应含明文（仅一次） |
| GET | `/api/agent/token/page` | 系统管理员 | 分页列表（不含 hash） |
| POST | `/api/agent/token/update` | 系统管理员 | 更新 name/scopes/expire/enable |
| GET | `/api/agent/token/delete/{id}` | 系统管理员 | 删除 |

**注意**：Token 管理 API 走常规 Session 认证，**不走** AgentTokenFilter。

### 2.1 创建 Token 响应

```json
{
  "id": "token-001",
  "name": "Cursor Team",
  "token": "msat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "scopes": "FUNCTIONAL_ALL",
  "expireTime": null,
  "warning": "Token 明文仅展示一次，请妥善保存"
}
```

---

## 3. 前端页面

### 3.1 路由

**路径**：`frontend/src/views/setting/system/agentIntegration/index.vue`

**菜单**：系统设置 → Agent 集成 → Token 管理

### 3.2 页面功能

| 功能 | 说明 |
|------|------|
| 列表 | 名称、关联用户、默认项目、scopes、过期时间、状态 |
| 新建 | 弹窗：名称、用户、项目、scopes 多选、过期时间 |
| 启用/禁用 | 开关 |
| 删除 | 二次确认 |
| 创建成功 | 弹窗展示明文 Token + 复制按钮 + 警告 |

### 3.3 API 模块

**路径**：`frontend/src/api/modules/setting/agentIntegration.ts`

---

## 4. 权限

- 仅系统管理员可访问  
- 组织管理员是否可见：默认 **否**（可按需求扩展）  

---

## 5. 验收标准

- [x] UI 可创建 Token，明文仅展示一次（`frontend/.../agentIntegration/index.vue`）  
- [x] 禁用 Token 后 Agent API 返回 401（`agent_token.enable=0`）  
- [x] 列表不展示 token_hash / 明文  
- [x] 权限控制：`SYSTEM_USER:READ*`（`/api/agent/token/**` 走 Session 认证）  

---

## 6. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-08 |
| 完成日期 | 2026-07-08 |
