# 开发摘要日志 — task008 REST Controller 四层接口

> **日期**：2026-07-07  
> **任务**：[task008-P0-REST-Controller四层接口.md](../../task/metersphere_agent/task008-P0-REST-Controller四层接口.md)  
> **前置**：task003、006、007 ✅  
> **状态**：✅ 已完成

---

## 1. 本次目标

暴露 Agent REST API，统一前缀 `/api/agent/v1/functional`。

---

## 2. API 清单

| 方法 | 路径 | Scope |
|------|------|-------|
| GET | `/health` | anon |
| POST | `/search` | READ |
| GET | `/{caseId}` | READ |
| GET | `/modules` | READ |
| POST | `/submit` | SUBMIT |

---

## 3. Controller

`AgentFunctionalCaseController`：

- `@Tag(name = "Agent Functional Case")` 供 OpenAPI 分组  
- `assertScope()` 基于 `AgentTokenContext` 校验 Scope  

---

## 4. 变更文件

| 文件 | 说明 |
|------|------|
| `controller/AgentFunctionalCaseController.java` | 新建 |

---

## 5. 联调参考

[`docs/task/metersphere_agent/curl-examples.md`](../../task/metersphere_agent/curl-examples.md)
