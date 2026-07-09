# task008 - P2 同步 API 与定时任务

> **阶段**：P2  
> **预估工期**：2 天  
> **前置依赖**：[task007](task007-P2-组织架构同步引擎.md)  
> **阻塞任务**：task009、task010  
> **关联文档**：[组织架构模块设计摘要.md](../../summary/组织架构模块设计摘要.md) §6.2

---

## 1. 任务目标

暴露组织架构同步的 HTTP API（手动触发、状态查询、日志分页），并实现基于 Cron 的定时同步任务。

---

## 2. API 清单

### 2.1 手动同步

| 项 | 内容 |
|----|------|
| 方法 | POST |
| 路径 | `/org-wecom/sync/manual` |
| 参数 | `organizationId`（query 或 body） |
| 权限 | 系统管理员 / 组织管理员 |
| 行为 | 异步或同步执行 `WecomOrgSyncApplicationService.syncManual()` |

**响应示例**：

```json
{
  "syncLogId": "log_xxx",
  "syncStatus": "SUCCESS",
  "deptSuccess": 12,
  "deptFailed": 0,
  "userSuccess": 85,
  "userFailed": 0,
  "durationMs": 3200
}
```

### 2.2 最近同步状态

| 项 | 内容 |
|----|------|
| 方法 | GET |
| 路径 | `/org-wecom/sync/status` |
| 参数 | `organizationId` |
| 响应 | 最近一条 `org_sync_log` + `config.last_sync_time` |

### 2.3 同步日志分页

| 项 | 内容 |
|----|------|
| 方法 | GET |
| 路径 | `/org-wecom/sync/log/page` |
| 参数 | `organizationId`, `current`, `pageSize`, `syncStatus`（可选） |

### 2.4 保存企微同步配置

| 项 | 内容 |
|----|------|
| 方法 | POST |
| 路径 | `/org-wecom/config/save` |
| Body | corpId, contactSecret, scheduleEnabled, scheduleCron, retryTimes |
| 说明 | 详细 UI 在 task010；本任务先提供 API |

---

## 3. Controller 实现

**路径**：`backend/services/system-setting/src/main/java/io/metersphere/system/controller/OrgWecomSyncController.java`

```text
OrgWecomSyncController
  ├── POST /org-wecom/sync/manual
  ├── GET  /org-wecom/sync/status
  ├── GET  /org-wecom/sync/log/page
  └── POST /org-wecom/config/save
```

**权限注解**：

- 系统级：`@RequiresPermissions(PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ_UPDATE)`  
- 组织级：`@CheckOwner(resourceType = "organization")`  

---

## 4. 定时任务 — WecomOrgSyncJob

### 4.1 实现方式

MeterSphere 已有 Quartz 调度（`quartz-starter`），二选一：

| 方案 | 说明 |
|------|------|
| A. Quartz Job | 新建 `WecomOrgSyncJob implements Job` |
| B. @Scheduled | Spring `@Scheduled(cron = "...")` 扫描 config 表 |

**推荐方案 A**：与 MS 现有 `SchedulerStarter` 体系一致。

### 4.2 调度逻辑

```
WecomOrgSyncJob.execute()
  1. 查询 org_wecom_sync_config WHERE schedule_enabled = 1
  2. 逐条判断 cron 是否到期（或使用 Quartz 动态注册）
  3. 获取组织级锁
  4. 调用 syncManual(organizationId, "system")
  5. 失败按 retry_times 重试
```

### 4.3 Cron 动态更新

- 保存 config 时刷新 Quartz Trigger  
- 或 Job 每分钟扫描 + 判断 `last_sync_time` 与 cron 间隔  

---

## 5. 并发控制

| 机制 | 说明 |
|------|------|
| Redis 锁 | Key: `org_sync_lock:{organizationId}`，TTL 30min |
| 手动 vs 定时 | 同 Key，后到者 skip 或排队 |
| 前端防重复 | 手动同步按钮 loading + 禁用 |

---

## 6. 登录兜底（可选，P2 末期）

**路径**：现有企微登录流程

当用户扫码登录且 `wecom_userid` 在本地不存在时：

1. `WecomContactClient.getUser(token, userId)`  
2. 创建 user + org_member  
3. 关联部门  

**参考**：myTapd `AuthApplicationService` 登录兜底。  
**优先级**：可在 task007 完成后作为子任务实现。

---

## 7. 测试要求

| 用例 | 预期 |
|------|------|
| POST manual 成功 | 返回 SUCCESS + 统计 |
| 同步中再次 manual | 409 或跳过提示 |
| GET status | 返回最近日志 |
| GET log/page | 分页正确 |
| Cron 触发 | 自动写入 MANUAL/SCHEDULE 模式日志 |
| 无 config | 返回友好错误 |

---

## 8. 验收标准

- [x] 四个 API 可用且权限正确  
- [x] 手动同步与 task007 引擎联调通过  
- [x] 定时任务按 cron 执行（Quartz 动态注册 + 启动初始化）  
- [x] 同步日志可分页查询  
- [x] 组织级锁防止并发同步（409 CONFLICT）  

---

## 9. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-06 |
| 完成日期 | 2026-07-06 |
