# task003 - P1 同步日志与缺失指标

> **阶段**：P1  
> **预估工期**：0.5 人日  
> **前置依赖**：[task002](task002-P0-UserSyncHandler字段策略.md)  
> **阻塞任务**：无（可增强 task005 验收观感）  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：§5.5  
> **状态**：✅ 已完成（2026-07-29）

---

## 1. 任务目标

把 Handler 内的缺失/占位统计透出到同步结果与运维可见面，降低「权限不足 vs 代码 bug」排查成本。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| `SyncResult` | 仅有 dept/user 的 total/success/failed/created/updated/disabled |
| `org_sync_log.error_message` | 可写摘要，长度需注意截断策略 |
| 前端 `SyncPanel` | 展示同步状态与基础计数，无缺失字段 |

---

## 3. 任务清单

### 3.1 后端结果模型（推荐低改动）

- [x] `SyncPartResult` 增加缺失统计字段（若 task002 未加则本任务补）  
- [x] `SyncResult` 增加对应字段并在 `WecomOrgSyncService` 聚合  
- [x] `WecomOrgSyncApplicationService.saveSyncLog`：将摘要写入 `error_message` 或独立备注字段（**优先追加摘要，勿覆盖真实错误**）  

摘要示例：

```text
用户联系字段：missingMobile=M, missingEmail=E, placeholderEmail=P, emailConflict=C
```

- [x] `M>0` 时应用日志 WARN（可与 task002 合并，避免重复刷屏）

### 3.2 前端（可选同批）

- [x] `SyncPanel.vue` 展示缺失手机 / 占位邮箱数量（无数据则隐藏）  
- [x] i18n 文案：提示检查通讯录「获取成员手机号/邮箱」权限  

### 3.3 不做

- [x] 不新增独立监控系统；不改脱敏展示  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `SyncPartResult.java` / `SyncResult.java` | 统计字段 |
| `WecomOrgSyncService.java` | 聚合 |
| `WecomOrgSyncApplicationService.java` | 日志摘要 |
| `frontend/.../orgStructure/components/SyncPanel.vue` | 可选展示 |
| 相关 locale | 可选文案 |

---

## 5. 验收标准

- [x] 一次全量同步后，日志/结果可看到缺失手机与占位邮箱数量  
- [x] 真实错误信息仍可读，不被摘要覆盖  
- [x]（若做面板）前端数字与后端一致  
