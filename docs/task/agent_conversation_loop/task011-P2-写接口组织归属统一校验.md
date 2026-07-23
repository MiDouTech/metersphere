# task011 - P2 写接口组织归属统一校验

> **阶段**：P2  
> **预估工期**：1 天  
> **前置依赖**：task010（一期验收完成）  
> **关联方案**：§6.2、§15#1

---

## 1. 任务目标

将「Token 用户属于项目所属组织」校验统一到用例/计划/评审/缺陷等写接口，降低跨项目写风险。

---

## 2. 任务清单

- [ ] 抽取公共 `assertProjectOrgAccessible(projectId, userId)`（或等价）  
- [ ] 应用于：`AgentCaseWriteService`、`AgentTestPlanWriteService`、`AgentCaseReviewWriteService`、`AgentBugWriteService`（及 associate/relate）  
- [ ] 与项目创建侧 `assertOrgAccessible` 行为一致  
- [ ] 补充单测：异组织项目写 → 拒绝  
- [ ] 更新方案/本文风险表：跨项目写风险降级  

---

## 3. 验收标准

- [ ] 用户不属于项目组织时，所有一期写接口均失败且错误明确  
- [ ] 组织内写路径回归通过  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 未开始 |
