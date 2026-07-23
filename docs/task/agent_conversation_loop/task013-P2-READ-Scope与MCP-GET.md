# task013 - P2 READ Scope 与 MCP GET

> **阶段**：P2  
> **预估工期**：1 天  
> **前置依赖**：task010  
> **关联方案**：§5.2 备注、§10.3、§15#3–4

---

## 1. 任务目标

将项目/计划/评审的 GET 从 WRITE Scope 解耦，并按需在 MCP 暴露查询 Tool。

---

## 2. 任务清单

- [ ] 新增 Scope：`PROJECT_READ`、`PLAN_READ`、`REVIEW_READ`（或统一 `AGENT_READ`——实现前确认）  
- [ ] `AGENT_ALL` 继续覆盖；`FUNCTIONAL_ALL` 仍不覆盖这些 READ（除非产品另定）  
- [ ] GET `/project/{id}`、`/test-plan/{id}`、`/case-review/{id}` 改挂 READ  
- [ ] MCP（可选）：`get_project`、`get_test_plan`、`get_case_review`  
- [ ] 更新 Scope 矩阵文档与 Token 发放说明  
- [ ] 兼容：仅有 WRITE 无 READ 的旧 Token 策略（文档公告或 WRITE 仍可 GET 一段时间）  

---

## 3. 验收标准

- [ ] 仅 READ Scope 可 GET、不可 POST 写  
- [ ] 仅 WRITE 的兼容策略符合公告  
- [ ] MCP 若暴露 GET，联调成功  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 未开始 |
| 备注 | Scope 命名需产品/安全确认 |
