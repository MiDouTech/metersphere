# task005 - P0 缺陷详情接入

> **阶段**：S2  
> **预估工期**：2–2.5 天  
> **前置依赖**：task002、task003  
> **阻塞任务**：task009（部分）  
> **关联方案**：§3.5 S2  
> **提测**：对齐 T1–T10（缺陷路径）  

---

## 1. 任务目标

缺陷详情接入同一套自动保存 / Undo / 锁；附件不进 Undo。

---

## 2. 任务清单

- [x] 实现 `BUG` 适配器（标题、描述、自定义字段、处理人等白名单）  
- [x] 详情 / 抽屉接入 SDK 与状态条（`bugDetailTab.vue` 内容编辑态）  
- [x] 加锁与只读冲突提示  
- [x] 保留手动保存；失败拦截关抽屉（路由离开由 SDK 拦截）  
- [x] 富文本图片引用与现有下载路径兼容（沿用原 upload/preview）  

---

## 3. 验收标准

- [ ] 与用例侧同等 T1–T10 行为一致（待联调）  
- [x] 人工审核：多处理人等字段序列化（`handleUser` JSON 数组）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成，待联调验收** |
| 备注 | BE：`BugResourceEditAdapter`；FE：抽屉内容 Tab；侧栏 `basicInfo` 仍用原 300ms 保存 |

---

## 5. 实现要点

- `backend/services/bug-management/.../bug/edit/BugResourceEditAdapter.java`
- `frontend/.../bug-management/components/bugDetailTab.vue`
- 快照排除：`deleteLocalFileIds` / `linkFileIds` / 用例关联等
