# task006 - P0 测试计划文档 Tab 接入

> **阶段**：S2  
> **预估工期**：1.5–2.5 天  
> **前置依赖**：task002、task003  
> **阻塞任务**：task009（部分）  
> **关联方案**：§3.5 S2；`test_plan_document`  
> **提测**：对齐 T1–T10（计划文档路径）  

---

## 1. 任务目标

测试计划详情「测试计划」Tab（规划文档）接入自动保存 / Undo / 锁。

---

## 2. 任务清单

- [x] 实现 `TEST_PLAN_DOCUMENT` 适配器（content + 必要元数据）  
- [x] `detail/plan` 接入 SDK；保留显式保存  
- [x] 大文档防抖与保存 loading 体验（1.8s + 状态条）  
- [x] 锁粒度：按 **`test_plan_id`**（文档与计划 1:1）  
- [x] 与计划其他 Tab 切换时离开拦截生效（`onBeforeRouteLeave`）  

---

## 3. 验收标准

- [ ] 长文档失焦/防抖保存稳定；Undo 两步内容正确（待联调）  
- [ ] 他人打开只读提示正确（待联调）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成，待联调验收** |
| 备注 | resourceId=`planId`；payload=`{testPlanId,projectId,content,contentType}` |

---

## 5. 实现要点

- BE：`TestPlanDocumentResourceEditAdapter`
- FE：`frontend/src/views/test-plan/testPlan/detail/plan/index.vue`
- 归档计划 / 无 UPDATE 权限：不加锁、不自动保存
