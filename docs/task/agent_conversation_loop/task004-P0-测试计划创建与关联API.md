# task004 - P0 测试计划创建与关联 API

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：task001、task003  
> **阻塞任务**：task010  
> **关联方案**：§7.3、§12#3

---

## 2. 任务清单

- [x] `test-plan/create`（可带 caseIds）、`associate-cases`、`GET /{id}`（`PLAN_WRITE`）  
- [x] 审计：`TEST_PLAN_CREATE`、`TEST_PLAN_ASSOCIATE`  

---

## 3. 验收标准

- [x] 静态核对通过  
- [ ] search 返回 `testPlanCaseId`（运行时）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已核对**；运行时待 task010 |
