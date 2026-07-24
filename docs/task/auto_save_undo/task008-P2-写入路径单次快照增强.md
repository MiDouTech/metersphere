# task008 - P2 写入路径单次快照增强

> **阶段**：S4  
> **预估工期**：1–2 天  
> **前置依赖**：task002；建议 task004 完成  
> **阻塞任务**：无  
> **关联方案**：§1.4、§3.5 S4  
> **提测**：方案 T11 扩展  

---

## 1. 任务目标

Agent / Excel·Xmind 导入 / 批量编辑**不**做自动保存；在整次写入成功后可选记录 **1** 个快照，便于整单回退一次（与人工快照共用滚动窗口，Undo ≤2）。

---

## 2. 任务清单

- [x] 写入路径快照 API：`ResourceEditService.recordWritePathSnapshot(s)`（**不要求持锁**）  
- [x] 独立灰度开关：`resource.edit.writepath.snapshot.enabled`（**默认 false**）  
- [x] 批量编辑用例成功 → 按 id 登记快照  
- [x] 批量编辑缺陷成功 → 按 id 登记快照  
- [ ] Excel/Xmind 导入：导入监听器暂无稳定 successId 列表，后续在 listener 收口后挂钩（文档化）  
- [x] 明确：**无**防抖自动保存；避免导入过程中间态入栈（仅成功后）  
- [x] 查询开关：`GET /resource-edit/writepath-snapshot-enabled`  

---

## 3. 验收标准

- [ ] T11：上述路径无自动保存中间态（开关关时零快照）  
- [ ] 打开增强后：批量成功可至少 Undo 回改前（步数上限内）  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成（默认关）；导入钩子待补** |
| 备注 | 开启方式：系统参数 `resource.edit.writepath.snapshot.enabled=true` |

---

## 5. 使用说明

```text
# 系统参数（system_parameter）
param_key   = resource.edit.writepath.snapshot.enabled
param_value = true
```

业务侧调用：

```java
resourceEditService.recordWritePathSnapshot(TYPE, id, projectId, userId);
// 或
resourceEditService.recordWritePathSnapshots(TYPE, projectId, ids, userId);
```
