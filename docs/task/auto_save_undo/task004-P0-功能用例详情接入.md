# task004 - P0 功能用例详情接入

> **阶段**：S2  
> **预估工期**：2–3 天  
> **前置依赖**：task002、task003  
> **阻塞任务**：task009（部分）  
> **关联方案**：§3.5 S2、§4 T1–T10  
> **提测**：T1–T10（用例路径）  

---

## 1. 任务目标

功能用例详情（含步骤/自定义字段等整单正文）接入自动保存、Undo/Redo、编辑锁与离开拦截；附件不进 Undo。

---

## 2. 任务清单

- [x] 实现 `FUNCTIONAL_CASE` 适配器：`loadPayload` / `applyPayload`（白名单字段）  
- [x] 详情页接入 SDK：保留原保存按钮；Ctrl+S  
- [x] 进入详情加锁；无权限只读；冲突只读提示  
- [x] 自动保存走现有 update API；成功写快照  
- [x] Undo/Redo 后刷新表单与步骤区  
- [x] 附件区明确不参与快照  
- [x] 与默认项目枢纽同步：依赖既有 hash/防抖，避免每次自动保存打爆同步（必要时加短防抖）  

---

## 3. 验收标准

- [ ] T1–T6、T7–T10 用例场景通过（待联调/提测）  
- [x] 人工审核：payload 字段白名单（适配器 + FE `serializeCaseBody`）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成，待联调验收** |
| 备注 | BE：`FunctionalCaseResourceEditAdapter`；FE：`tabDetail.vue` + `useAutoSaveEditor`（编辑态加锁/防抖 1.8s/Undo）；枢纽同步沿用 content_hash |

---

## 5. 实现要点

### 后端
- `backend/services/case-management/.../functional/edit/FunctionalCaseResourceEditAdapter.java`
- 快照字段：名称/模块/标签/编辑模式/步骤与文本/前置/备注/自定义字段/执行结果；附件关联字段置空不回放

### 前端
- `tabDetail.vue`：点击「内容编辑」后 `canEdit=true` 加锁；失焦/防抖自动保存；状态条；Ctrl+S / Ctrl+Z / Ctrl+Shift+Z
- 执行态 `props.autoSave`（非编辑锁）仍走原 600ms 静默保存
- 全页新建/编辑 `caseDetail.vue` 未纳入本任务（后续可按 task007 推广）
