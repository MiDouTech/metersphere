# task002 - P0 编辑锁与快照通用 API

> **阶段**：S1  
> **预估工期**：2.5–3.5 天  
> **前置依赖**：task001  
> **阻塞任务**：task003–006、task008  
> **关联方案**：§3.3  
> **提测**：锁相关 T7/T8；Undo 契约单测  

---

## 1. 任务目标

提供与业务解耦的横切服务：加锁/心跳/释锁、保存成功写快照、Undo/Redo、快照元信息查询。

---

## 2. 任务清单

### 2.1 锁

- [ ] `acquire`：成功返回持锁信息；失败返回持锁人姓名/过期时间  
- [ ] `heartbeat`：刷新 `expire_time`（建议前端 1～2min）  
- [ ] `release`：仅持锁人或超时后可清  
- [ ] 15 分钟无续期视为过期（查询时惰性清理或定时任务）  

### 2.2 快照与 Undo

- [ ] 业务保存成功钩子 / 统一 `recordSnapshot(resourceType, resourceId, payload)`  
- [ ] 滚动保留：保证可 Undo **2** 步；新保存截断 redo 链  
- [ ] `undo` / `redo`：校验权限与锁（建议仅持锁人可 Undo）；回写正式数据由**业务适配器**执行  
- [ ] `GET meta`：`undoAvailable` / `redoAvailable` 步数  

### 2.3 安全与开关

- [ ] 快照读/写与资源同权限  
- [ ] 灰度配置：`resource.edit.autosave.enabled`（关则仅手动保存，仍可选手动写快照策略）  

### 2.4 适配器接口

- [ ] 定义 `ResourceEditAdapter`：`loadPayload` / `applyPayload`（用例、缺陷、计划文档各自实现于后续任务）  

---

## 3. 验收标准

- [ ] API 契约文档或 OpenAPI 注解完整  
- [ ] 单测：锁冲突、过期、Undo 两步上限、redo 截断  
- [ ] 人工审核：事务边界（保存与快照同事务 / 最终一致策略）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成** |
| 备注 | `/resource-edit/**`；含写入路径快照；适配器：CASE/BUG/PLAN_DOCUMENT 已注册 |
