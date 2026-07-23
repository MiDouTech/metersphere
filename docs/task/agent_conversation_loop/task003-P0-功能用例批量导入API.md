# task003 - P0 功能用例批量导入 API

> **阶段**：P0  
> **预估工期**：1.5 天  
> **前置依赖**：task001  
> **阻塞任务**：task004、task005、task006、task010  
> **关联方案**：§7.2

---

## 1. 任务目标

支持 Agent 对话生成的结构化用例 JSON 写入平台。

---

## 2. 任务清单

- [x] `module/create`、`case/create`、`case/batch-create`（`CASE_WRITE`）  
- [x] `moduleId` 优先于 `modulePath`；`failFast`；`created`/`errors` 响应  
- [x] 复用 `FunctionalCaseService`，`aiCreate=false`  
- [x] 审计：`CASE_BATCH_CREATE`  

---

## 3. 验收标准

- [x] 静态核对通过  
- [ ] 批量 ≥5、`errors` 空（运行时 task010）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已核对**；运行时待 task010 |
