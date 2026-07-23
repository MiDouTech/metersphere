# task001 - P0 Scope 与鉴权扩展

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：`metersphere_agent` Token 认证基线（task003）  
> **阻塞任务**：task002–006  
> **关联方案**：§3、§5

---

## 1. 任务目标

扩展 Agent Token Scope，使写闭环接口可按最小权限授权；明确 `FUNCTIONAL_ALL` 与 `AGENT_ALL` 边界。

---

## 2. 任务清单

- [x] 常量：`PROJECT_WRITE`、`CASE_WRITE`、`PLAN_WRITE`、`REVIEW_WRITE`、`BUG_WRITE`、`AGENT_ALL`（`AgentTokenScope`）  
- [x] `AgentScopeAssert`：`AGENT_ALL` 放行全部；`FUNCTIONAL_ALL` **仅**放行 `FUNCTIONAL_READ|SUBMIT`  
- [x] 各写 Controller 入口 `assertScope` 与方案矩阵一致  
- [x] 单元测试：`AgentScopeAssertTests`（含 CASE_WRITE 不可 PROJECT_WRITE）  
- [x] Token 发放文档：闭环建议 `AGENT_ALL`（onboarding / fixture）  

**核对路径**

- `backend/services/agent-integration/.../AgentTokenScope.java`  
- `.../security/AgentScopeAssert.java`  
- `.../security/AgentScopeAssertTests.java`  

---

## 3. 验收标准

- [x] 仅有 `FUNCTIONAL_ALL` 调用任意 WRITE → 单测拒绝  
- [x] 仅有 `CASE_WRITE` 不可调 `PROJECT_WRITE` → 单测覆盖  
- [x] `AGENT_ALL` 可调全部一期写 Scope → 单测覆盖  
- [ ] 运行时 403 抽测（依赖后端启动，见 task010）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **已核对通过**（单测 2026-07-23） |
| 人工审核 | Scope 与 Token 策略（方案 §14）已审 |
| 备注 | 运行时 Scope 拒绝留待 task010 |
