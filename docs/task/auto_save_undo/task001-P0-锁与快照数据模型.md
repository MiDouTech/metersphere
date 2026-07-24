# task001 - P0 锁与快照数据模型

> **阶段**：S1  
> **预估工期**：1–1.5 天  
> **前置依赖**：无  
> **阻塞任务**：task002  
> **关联方案**：§3.2  
> **提测**：随 task002  

---

## 1. 任务目标

落地编辑锁与滚动快照表结构（Flyway），支撑跨天 Undo ≤2 步与 15 分钟锁过期。

---

## 2. 任务清单

- [x] 新增 `resource_edit_lock`（唯一 `(resource_type, resource_id)`，含 holder、expire_time）  
- [x] 新增 `resource_edit_snapshot`（按资源滚动快照，含 seq / payload / hash）  
- [x] 新增 `resource_edit_pointer`（当前 active seq）  
- [x] 索引与清理策略说明  

---

## 3. 验收标准

- [x] 迁移可重复执行说明清晰  
- [x] 表结构支持 Undo ≤2 + 滚动裁剪  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成** |
| 备注 | `V3.7.2_12__resource_edit_lock_snapshot.sql` |
