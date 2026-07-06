# task007 - P2 组织架构同步引擎

> **阶段**：P2  
> **预估工期**：3 天  
> **前置依赖**：[task001](./task001-P0-社区版Xpack与License实现.md)、[task004](./task004-P1-数据模型与Flyway迁移.md)、[task006](./task006-P2-企微通讯录客户端.md)  
> **阻塞任务**：task008、task009  
> **参考项目**：myTapd `WecomSyncService.java`、`WecomSyncApplicationService.java`

---

## 1. 任务目标

实现企微通讯录 → MeterSphere 组织内部门/用户的**幂等全量同步引擎**，包含部门两阶段 upsert、员工 upsert、失活收敛与安全保护。

---

## 2. 核心原则（来自设计摘要）

| 原则 | 实现要求 |
|------|----------|
| 先部门后员工 | `syncAll()` 固定顺序 |
| 幂等 upsert | 按 `(organization_id, wecom_dept_id)` / `wecom_userid` |
| 空列表保护 | 企微返回空部门/空成员时**跳过失活** |
| 空值不覆盖 | mobile/position 仅 API 有值时更新 |
| 内置账号保护 | admin、DEV_ 前缀用户不参与失活 |

---

## 3. 模块结构

```text
backend/services/system-setting/src/main/java/io/metersphere/system/service/department/
├── WecomOrgSyncService.java              # 同步核心
├── WecomOrgSyncApplicationService.java   # 编排 + 日志
├── DepartmentSyncHandler.java            # 部门 Pass1/Pass2/失活
├── UserSyncHandler.java                  # 员工 upsert/失活
└── SyncResult.java                       # 统计结果 DTO
```

---

## 4. 同步流程

### 4.1 总编排 — WecomOrgSyncApplicationService

```
syncManual(organizationId, operatorId)
  1. 创建 org_sync_log（进行中）
  2. 读取 org_wecom_sync_config
  3. 调用 WecomOrgSyncService.syncAll()
  4. 更新 org_sync_log（SUCCESS/PARTIAL/FAILED）
  5. 更新 config.last_sync_time
  6. 返回 SyncResult
```

**状态判定**：

| 条件 | sync_status |
|------|-------------|
| 失败数 = 0 | SUCCESS |
| 有失败但部分成功 | PARTIAL |
| 异常中断 | FAILED |

### 4.2 部门同步 — DepartmentSyncHandler

**Pass 1 — Upsert**

1. `WecomContactClient.listDepartments(token)`  
2. 对每个部门：按 `(organizationId, wecomDeptId)` 查找  
3. 存在则 update，不存在则 insert（生成本地 id）  
4. 维护 `wecomDeptId → localDeptId` 映射表  

**Pass 2 — 修正 parent_id**

1. 遍历映射表，将企微 `parentid` 转为本地 `parent_id`  
2. 根部门 parent_id = null  

**Pass 3 — 失活收敛**

1. 若 Pass 1 返回空列表 → **跳过**（安全保护）  
2. 本地存在、企微不存在的部门 → `dept_status = 0`  

### 4.3 员工同步 — UserSyncHandler

1. 从根部门 `wecom_dept_id = 1` 调用 `listDepartmentUsers(token, 1, fetchChild=true)`  
2. 对每个成员：  
   - 按 `wecom_userid` 查找 user  
   - **已存在**：update name/email/phone/position/department_id（遵守空值不覆盖）  
   - **不存在**：调用 `SimpleUserService` 创建 + `OrganizationService.addMemberBySystem` 绑定 `org_member`  
3. 维护本次同步出现的 wecom_userid 集合  
4. 失活：本地有 wecom_userid、本次未出现、非 admin/DEV_ → `enable = false`  
5. 若成员列表为空 → **跳过失活**  

### 4.4 用户创建策略（MS 特有）

```text
UserSyncHandler.createUser()
  → SimpleUserService.addUser()           # task001 已解除 Xpack
  → OrganizationService.addMemberBySystem()  # 绑定 org_member
  → 设置 user.wecom_userid / department_id / sync_status
```

**默认角色**：`org_member`（一期不可配置，后续可扩展 config 表字段）。

---

## 5. 字段映射

| 企微 | MS user / department |
|------|----------------------|
| department.id | department.wecom_dept_id |
| department.parentid | department.parent_id（Pass2） |
| user.userid | user.wecom_userid |
| user.department[0] | user.department_id（映射本地 ID） |
| user.status=1 | enable=true |
| user.status=2/4 | enable=false |

---

## 6. 并发与事务

| 项 | 建议 |
|----|------|
| 单组织同步 | 组织级分布式锁（Redis），防止手动+定时并发 |
| 事务粒度 | 部门 Pass1 一批；Pass2 一批；用户逐条或分批（避免大事务） |
| 失败记录 | 单条失败计入 failed 计数，不中断全量（PARTIAL） |

---

## 7. 单元 / 集成测试

| 用例 | 预期 | 状态 |
|------|------|------|
| 首次全量同步 | 部门树 + 用户全部创建 | ✅ |
| 重复同步 | 数据不变，无重复 insert | ✅ |
| 企微删除部门 | 本地 dept_status=0 | ✅ |
| 企微删除用户 | 本地 enable=false | ✅ |
| 空列表 API | 本地数据不被失活 | ✅ |
| mobile 返回空 | 本地 phone 不被清空 | ✅ |
| 新用户 | 自动 org_member | ✅ |

**Mock**：Mockito + MockServer 单元测试。

---

## 8. 验收标准

- [x] `WecomOrgSyncService.syncAll(organizationId)` 端到端可跑通  
- [x] 同步结果统计准确（dept/user success/failed）  
- [x] org_sync_log 有完整记录  
- [x] 安全保护（空列表、空值、内置账号）全部生效  
- [ ] 与 task005 查询 API 联调：树 + 成员列表数据正确  

---

## 9. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-06 |
| 完成日期 | 2026-07-06 |
