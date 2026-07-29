# MeterSphere 企微同步字段补齐 — 实现方案

> **版本**：v1.2 | **日期**：2026-07-29 | **状态**：待开发  
> **文档类型**：技术实现方案  
> **适用项目**：MeterSphere（米多二次开发版）  
> **参考项目**：米多工单系统（myTapd / ticket-platform）  
> **任务拆解**：[`docs/task/wecom_sync_fields/`](../task/wecom_sync_fields/task000-实施总览与依赖关系.md)  
> **说明**：【AI生成】产品决策已确认（v1.2：企微覆盖本地；邮箱冲突人工三选一）  

---

## 1. 背景与动机

### 1.1 Why（解决什么问题）

当前 MeterSphere 已具备企业微信通讯录**全量同步引擎**（部门 + 成员），但同步落库的邮箱、手机号等联系字段存在**失真或丢失**，无法满足组织账号管理、通知触达、选人等对主数据质量的要求。

典型现象：

| 现象 | 表现 |
|------|------|
| 邮箱失真 | 库内出现 `{userid}@wecom.sync.internal` 占位邮箱，非企微真实邮箱 |
| 邮箱丢失 | 企微侧有邮箱（常为企业邮箱 `biz_mail`），同步后本地为空或仍为占位 |
| 手机丢失 | 同步后 `user.phone` 为空 |
| 观感失真 | 详情页脱敏（如 `138****1234`）被误认为数据丢失 |

### 1.2 How（业务流程）

保持现有「手动 / 定时全量同步」主路径不变：先部门、后员工 → upsert → 失活收敛。在成员 upsert 环节补齐字段解析、落库策略与可观测性；上线后通过一次全量同步修复历史占位邮箱。

### 1.3 What（结果）

- 有通讯录权限时：`user.email` / `user.phone` / `position` / 主部门与企微一致  
- 无权限或 API 空值时：不误清空已有值，并在同步日志中可观测  
- 详情脱敏策略不变；验收以 DB 明文为准  

---

## 2. 现状与差距

### 2.1 已具备能力（无需重做）

| 能力 | 位置 |
|------|------|
| 企微通讯录 Client（Token / 部门 / 成员） | `WecomContactClient` |
| 全量同步编排（锁、日志、状态） | `WecomOrgSyncApplicationService` |
| 部门两阶段 upsert + 空列表保护 | `DepartmentSyncHandler` |
| 成员 upsert + 失活 + org_member 绑定 | `UserSyncHandler` |
| 手动 / 定时触发、配置页 | `OrgWecomSyncController` / `WecomOrgSyncJob` |
| 组织树 / 成员查询 / 详情脱敏 | `OrgStructure*` |

### 2.2 与 myTapd 对照结论

两边能力定位已对齐（企微作组织主数据源）。差距集中在**成员联系字段**：

| 维度 | myTapd | MeterSphere（当前） | 差距 |
|------|--------|---------------------|------|
| 邮箱为空时 | 允许 `email=null` | 创建强制邮箱，写入 `@wecom.sync.internal` | **失真根因** |
| `biz_mail` | 未映射 | 未映射 | 两侧均缺；MS 需补 |
| 手机空值 | 有值才更新 + 缺失告警日志 | 有值才更新，**无缺失统计** | 可观测性不足 |
| 主部门 | 优先 `main_department` | 仅 `department[0]` | 可能挂错部门 |
| 性别 / 头像 | 有同步 | 未同步 | 本期可选 / 二期 |

### 2.3 失真根因（代码级）

**占位邮箱**（创建路径）：

```text
UserSyncHandler.resolveEmail()
  → email 为空时返回：{userid}@wecom.sync.internal
```

常量：`OrgSyncConstants.WECOM_SYNC_EMAIL_SUFFIX = "@wecom.sync.internal"`。

背景：MeterSphere `user.email` 非空且全局唯一，创建用户必须走 `SimpleUserService.addUser`，故一期用占位满足约束；与「企微真实主数据」目标冲突。

**企业邮箱未解析**：

- `WecomUserDTO` 仅有 `email`，无 `biz_mail`
- 米多环境大量成员真实邮箱在企微 `biz_mail` 字段

**手机为空**：

- 企微应用未开通「获取成员手机号」时，API 不返回 `mobile`
- 新建用户直接写 null；更新路径「空值不覆盖」无法补全从未写入过的手机号

**展示层**：

- `OrgStructureMemberService.maskSensitive` 对详情脱敏属设计行为，不等于库内丢失

### 2.4 源码复核补充缺口（v1.1）

对照当前 `UserSyncHandler` / `WecomUserDTO`，除方案原文外还需明确：

| 缺口 | 现状代码 | 影响 | 本期必须 |
|------|----------|------|----------|
| **更新路径不读 `biz_mail`** | `updateUser` 仅 `trimToNull(wecomUser.getEmail())` | 仅有企业邮箱的存量用户**永远无法升级占位邮箱** | **是** |
| **创建/更新邮箱解析分裂** | 仅 `createUser` → `resolveEmail()`；更新另写一套 | 易漏升级 / 行为不一致 | **是**（统一 `resolveSyncEmail`） |
| **主部门忽略 `main_department`** | `resolveMainDepartment` 固定 `department[0]` | 多部门成员可能挂错主部门 | **是** |
| **`department` 空 + 有主部门** | `department` 空直接 return null | 极端数据下部门挂空 | **是**（主部门兜底） |
| **Jackson 下划线字段** | DTO 无 `@JsonProperty`；现有 `mobile` 等同名 | `biz_mail` / `main_department` **必须**注解，否则解析恒空 | **是** |
| **邮箱长度 max 64** | `UserCreateInfo.@Size(max=64)` | 超长真实邮箱创建失败；更新也可能踩库约束 | **是**（单用户 failed，禁止静默截断） |
| **邮箱唯一冲突** | 创建走 `addUser`；更新直接 `setEmail` | 与手工用户撞邮箱会整段失败或脏写 | **是**（预检 + **冲突队列 + 人工弹窗三选一**） |
| **缺失可观测** | `SyncPartResult` / `SyncResult` 无缺失计数 | 权限问题与代码问题难区分 | T3 |
| **下游触达依赖手机** | 缺陷企微机器人 `@` 依赖 `user.phone` / `wecom_userid` | 本需求修好手机后，通知触达质量同步提升 | 说明即可，不改通知代码 |

---

## 3. 设计原则

| 原则 | 说明 |
|------|------|
| 主数据唯一 | 部门 / 员工以企微为准；本地不做组织主数据维护 |
| 真实优先 | 能拿到真实邮箱 / 手机则落真实值；占位仅作创建兜底且可升级 |
| 空值不覆盖 | `mobile` / `position` 仅 API 有值时更新，避免权限不足误清空 |
| 幂等可运维 | 全量 upsert；失败单条计入 PARTIAL；日志可观测缺失量 |
| 最小改动 | 不重做同步引擎；不改 `user.email` 可空约束（改动面过大） |
| 身份与权限分离 | 同步补齐联系字段；RBAC / 组织角色逻辑不变 |

**本期明确不做**：

- 通讯录增量变更回调  
- 将 `user.email` 改为可空（需改创建链路与大量校验）  
- 性别字段建表（无强需求则二期）  
- 详情明文展示（脱敏保留）  

---

## 4. 目标字段映射

### 4.1 部门（维持现状）

| 企微 | 本地 `department` | 备注 |
|------|-------------------|------|
| `id` | `wecom_dept_id` | |
| `name` | `name` | |
| `parentid` | `parent_id` | Pass2 换算本地 ID |
| `order` | `sort_order` | |
| `department_leader[0]` | `leader_wecom_userid` | |

### 4.2 成员（本期补齐）

| 企微 | 本地 | 规则 |
|------|------|------|
| `userid` | `wecom_userid` | 匹配键 |
| `name` | `name` | 每次覆盖 |
| `mobile` | `phone` | **非空才写** |
| `email` / `biz_mail` | `email` | 见 §5.2；`email` 优先，空则用 `biz_mail` |
| `position` | `position` | **非空才写** |
| `main_department` 或 `department[0]` | `department_id` | **优先** `main_department` |
| `status` | `enable` | `1`→true，其它→false（维持） |
| `gender` | — | 本期不做 |
| `avatar` | `user_extend.avatar` | 建议二期 |

---

## 5. 详细设计

### 5.1 DTO / Client

**文件**：`WecomUserDTO.java`

新增字段：

```java
@JsonProperty("biz_mail")
private String bizMail;

@JsonProperty("main_department")
private Long mainDepartment;
```

现有字段保留：`userid`、`name`、`mobile`、`email`、`position`、`department`、`status`。

**单测**（`WecomContactClientTest`）：补充 JSON fixture，覆盖：

- 仅有 `biz_mail`
- 同时有 `email` + `biz_mail`
- 有 `main_department`
- `mobile` 缺失

### 5.2 邮箱策略（消「失真」）

**文件**：`UserSyncHandler.java`（**创建与更新共用**同一解析函数，禁止两套逻辑）

```text
resolveSyncEmail(wecomUser):  // 得到「API 侧期望邮箱」；不含占位
  1. trim(email) 非空 → email
  2. trim(biz_mail) 非空 → biz_mail
  3. 都空 → null

resolveCreateEmail(wecomUser):
  email = resolveSyncEmail(wecomUser)
  if email != null → 校验长度 ≤64 后返回
  else → {userid.toLowerCase()}@wecom.sync.internal

applyEmailOnUpdate(existing, wecomUser):
  apiEmail = resolveSyncEmail(wecomUser)
  if apiEmail == null:
      // API 无真实邮箱：绝不覆盖本地已有值（含真实邮箱与占位）
      return
  if length(apiEmail) > 64 → 抛业务异常（该用户 failed）
  if equalsIgnoreCase(apiEmail, existing.email) → return
  if emailOccupiedByOtherUser(apiEmail, existing.id):
      // 不自动改写；登记冲突待人工处理（见 §5.8）
      registerEmailConflict(...)
      return
  existing.email = apiEmail   // 含：占位→真实、真实A→真实B（【已确认】企微覆盖本地）
```

| 场景 | 创建 | 更新 |
|------|------|------|
| API 有真实邮箱（email 或 biz_mail） | 写真实邮箱（无跨用户冲突时） | **强制写入**（占位升级；与本地不同则以企微为准） |
| API 仍无真实邮箱 | 写占位 | **不**清空已有真实邮箱；占位可保留 |
| 邮箱唯一冲突（被其他用户占用） | **不自动抢邮箱**；登记冲突，交人工三选一（§5.8） | 同左 |
| 真实邮箱长度 > 64 | 该用户记 failed | 同左；**禁止截断** |

工具方法：

- `isPlaceholderEmail(email)`：`endsWith("@wecom.sync.internal")`（大小写不敏感）  
- 同步统计：`userMissingMobile`、`userMissingEmail`、`userPlaceholderEmail`、`userEmailConflict`（待人工处理数）

**历史数据修复**：代码上线后执行一次全量同步，由 upsert 将占位升级为真实邮箱；禁止生产裸 SQL。

**产品决策（已确认）**：

1. **同用户**本地手工邮箱与企微不一致 → **企微覆盖本地**（无开关）。  
2. **跨用户**邮箱唯一冲突 → **弹窗人工处理**，可选：跳过 / 覆盖 / 新建（语义见 §5.8）。

### 5.3 手机号策略（消「丢失」）

- 新建：`phone = trimToNull(mobile)`（允许 null）  
- 更新：仅当 `latestPhone != null` 时更新（与 myTapd / 现网一致）  
- 同步结束日志（对齐 myTapd）：

```text
企微成员 N 人，手机号缺失 M 人，邮箱缺失/占位 P 人
M > 0 → WARN：请检查通讯录「获取成员手机号」权限
```

**运维前置（非代码，必须验收）**：

- 通讯录 Secret 具备：通讯录只读、手机号、邮箱（及企业邮箱相关权限）  
- 企业可信 IP 正确  

### 5.4 主部门解析

```text
resolveMainDepartment(wecomUser, wecomDeptMap):
  wecomDeptId = main_department          // 优先
  if wecomDeptId == null && department 非空 → department[0]
  if wecomDeptId == null → return null
  local = wecomDeptMap.get(wecomDeptId)
  if local != null → return local.id
  // 主部门未在本地映射（部门同步失败/延迟）：回退 department 列表中第一个可映射项
  for d in department:
      if wecomDeptMap.contains(d) → return wecomDeptMap.get(d).id
  return null
```

### 5.5 可观测性

| 指标 | 用途 |
|------|------|
| `userMissingMobile` | 权限 / 数据质量 |
| `userMissingEmail` / `userPlaceholderEmail` | 占位与真实邮箱覆盖率 |
| （必做）`userEmailConflict` | 待人工处理的唯一邮箱冲突数 |

落地方式（择一，优先低改动）：

1. **推荐**：写入 `org_sync_log.error_message` 摘要 + 应用日志  
2. 进阶：扩展 `SyncResult` 字段并在同步面板展示（前端 `SyncPanel`）

### 5.6 连接测试增强（可选，同批或紧随）

`OrgWecomSyncConfigService` 测试连接成功后：

- 抽样拉取根部门 1 页成员  
- 返回：`hasMobileSample` / `hasEmailOrBizMailSample`  
- 前端配置抽屉展示提示文案，提前暴露权限问题  

### 5.7 展示层说明

| 接口 / 页面 | 行为 | 验收口径 |
|-------------|------|----------|
| 组织成员详情 | 继续脱敏 | 不作为「数据是否丢失」依据 |
| 成员列表 | 当前不展示 email/phone | 不变 |
| DB / 系统用户管理 | 明文 | **主验收口径** |

### 5.8 邮箱唯一冲突：登记 + 人工三选一

#### 5.8.1 检测（同步中，不自动抢邮箱）

在 `createUser` / `applyEmailOnUpdate` 写入真实邮箱前：

1. 查询是否存在 **其他** `user` 占用该 email（`deleted=0`）  
2. 若占用方是「同一 `wecom_userid`」则允许（幂等）  
3. 否则：**不**直接 failed 了事，而是：  
   - 本成员其它字段照常同步（name/phone/position/department/enable 等）  
   - 邮箱保持：更新场景保留原值；创建场景用**临时占位**完成创建约束（避免整用户建不出来）  
   - `registerEmailConflict` 写入冲突记录（组织维度）  
   - `userEmailConflict++`，全量继续  

> 定时同步**不能弹窗**；冲突一律入队。手动同步结束后若有待处理冲突，前端弹出处理面板。

#### 5.8.2 冲突记录（建议最小模型）

可落新表 `org_sync_email_conflict`（或等价），字段建议：

| 字段 | 说明 |
|------|------|
| id / organization_id / sync_log_id | 归属 |
| wecom_userid / pending_user_id | 企微侧成员；已创建则填本地 user id |
| conflict_email | 企微期望邮箱 |
| occupied_user_id / occupied_user_name | 当前占用方 |
| status | `PENDING` / `RESOLVED` |
| resolution | `SKIP` / `OVERWRITE` / `CREATE`（处理结果） |
| resolved_by / resolved_time | 审计 |

同步幂等：同一 `(organization_id, wecom_userid, conflict_email)` 已有 `PENDING` 则更新上下文，不重复堆多条。

#### 5.8.3 人工选项语义（弹窗）

弹窗展示：企微成员（userid/姓名）、期望邮箱、占用方账号（姓名/邮箱/是否已绑 wecom）。

| 选项 | 行为 |
|------|------|
| **跳过** | 不把该邮箱赋给企微成员；企微成员继续用占位或原邮箱；冲突记 `SKIP` 并关闭；占用方不变 |
| **覆盖** | 将 `conflict_email` 赋给企微成员（pending_user）；占用方邮箱改为占位 `{occupiedId或wecom}@wecom.sync.internal`（若占用方本身无 wecom 且为手工账号，占位后缀仍用其 user id）；冲突记 `OVERWRITE`；操作写审计日志 |
| **新建** | **仅创建冲突场景**有意义：确认以占位邮箱保留新建的企微用户，**不**抢占用方邮箱；冲突记 `CREATE`（实质=接受占位，与跳过在邮箱结果上接近，但明确「保留双账号」意图）。若冲突发生在**更新**路径（用户已存在），「新建」按钮禁用或灰显并提示「用户已存在，请选跳过或覆盖」 |

> 「覆盖」涉及改写占用方登录邮箱，属高风险操作：弹窗需二次确认文案（占用方将失去该邮箱登录能力，直至管理员改回）。

#### 5.8.4 API / 前端

- `GET` 组织下 `PENDING` 冲突列表  
- `POST` 单条/批量 resolve：`{ conflictId, action: SKIP|OVERWRITE|CREATE }`  
- 手动同步结束：`SyncResult.userEmailConflict > 0` → `SyncPanel` 自动打开冲突处理弹窗  
- 组织架构同步入口保留「待处理邮箱冲突」入口（有 PENDING 时红点）

> 预检查询优先复用 `ExtUserMapper` / `UserMapper`；冲突表用 Flyway 小迁移，禁止裸 SQL 修生产。

### 5.9 与下游能力的关系（只读说明）

| 下游 | 依赖字段 | 本需求收益 |
|------|----------|------------|
| 缺陷创建企微机器人 `@` 处理人 | `user.phone` / `wecom_userid` | 手机补齐后 @ 成功率上升 |
| 站内/邮件通知 | email | 真实邮箱覆盖率上升 |
| 米多 SSO / 企微登录 | `wecom_userid` | **不改匹配键** |

本期**不改** `WeComClient`（Webhook）与 SSO 登录逻辑。

### 5.10 联调区分：权限问题 vs 代码问题

全量同步开始时（DEBUG 或 INFO 一次采样即可）：

- 对成员列表前 N 条（建议 N=20）统计：`mobile` / `email` / `biz_mail` / `main_department` 非空比例  
- 写入应用日志一行摘要；若四者皆接近 0，优先判定**通讯录权限/可信 IP**，而非 Handler bug  

---

## 6. 涉及文件清单

| 层级 | 路径 | 变更 |
|------|------|------|
| DTO | `.../dto/wecom/WecomUserDTO.java` | 增 `bizMail`、`mainDepartment` |
| 同步 | `.../department/UserSyncHandler.java` | 邮箱解析/升级、主部门、统计、冲突登记 |
| 冲突 | `org_sync_email_conflict` + Service/API | 登记与 resolve（跳过/覆盖/新建） |
| 常量 | `.../department/OrgSyncConstants.java` | 可增 `isPlaceholder` 辅助方法或保持后缀常量 |
| 结果 | `.../dto/department/SyncResult.java`（及 PartResult） | 含 `userEmailConflict` 等 |
| 编排 | `.../WecomOrgSyncApplicationService.java` | 日志摘要；返回冲突数 |
| 配置测试 | `.../OrgWecomSyncConfigService.java` | 可选字段抽样 |
| 前端 | `SyncPanel` + 冲突处理弹窗 | 缺失数 + 人工三选一 |
| 单测 | `UserSyncHandlerTest`、`WecomContactClientTest`、冲突 resolve 单测 | 必补 |

不改动：`notice/utils/WeComClient`（Webhook）、登录 SSO 匹配逻辑（仍以 `wecom_userid`）。

---

## 7. 任务拆分

详细任务见 [`docs/task/wecom_sync_fields/`](../task/wecom_sync_fields/task000-实施总览与依赖关系.md)。

| 序号 | 优先级 | 任务 | 产出 | 预估 |
|------|--------|------|------|------|
| T1 | P0 | DTO `biz_mail` / `main_department` + Client 单测 | 解析正确 | 0.5d |
| T2 | P0 | `UserSyncHandler`：统一邮箱、主部门、空值、冲突登记、缺失计数 | 落库正确 | 1～1.5d |
| T6 | P0 | 邮箱冲突表 + resolve API + 弹窗三选一 | 人工可处理 | 1～1.5d |
| T3 | P1 | 同步日志 / `SyncResult` / 面板展示缺失指标 | 可运维 | 0.5d |
| T4 | P1 | 连接测试字段抽样提示（可后置） | 权限前置可发现 | 0.5d |
| T5 | P0 | 单测回归 + 灰度全量同步验收 | 占位升级、冲突可解 | 0.5～1d |

**建议合入顺序**：`T1 → T2 → T6 → T5`（主数据 + 冲突闭环）→ `T3` → `T4`。

合计约 **4～5.5 人日**（含冲突人工处理；不含企微后台权限开通联调时间）。

---

## 8. 验收标准

- [ ] 企微返回 `email` 或 `biz_mail` 时，库内 `user.email` 为真实值，不长期停留 `@wecom.sync.internal`  
- [ ] 企微返回 `mobile` 且应用有权限时，`user.phone` 正确  
- [ ] API 返回空 `mobile` 时，不覆盖本地已有手机号  
- [ ] 仅有 `biz_mail`、无 `email` 的成员，**首次创建**即写入企业邮箱  
- [ ] **存量占位用户**：仅有 `biz_mail` 时，全量同步后**更新路径**也能升级（回归现网最大坑）  
- [ ] 历史占位邮箱用户，全量同步后升级为真实邮箱（权限与数据具备时）  
- [ ] `main_department` 优先于 `department[0]`；主部门映射失败时回退可映射部门  
- [ ] 同用户邮箱与企微不一致时，同步后以企微为准（企微覆盖）  
- [ ] 跨用户邮箱冲突：同步不中断；登记 PENDING；弹窗可选跳过 / 覆盖 / 新建且行为符合 §5.8.3  
- [ ] 覆盖操作有二次确认；占用方邮箱改为占位且可审计  
- [ ] 超长邮箱：单用户 failed，全量不中断，错误信息可读  
- [ ] 同步日志可见缺失手机 / 占位邮箱 / 待处理冲突数量；权限不足有 WARN  
- [ ] 详情页仍脱敏；DB 明文正确  
- [ ] 回归：空列表不误失活；`admin` / `DEV_` 前缀账号不受影响  
- [ ] 相关单测通过（含 biz_mail、占位升级、空 mobile 不覆盖、冲突三选一）  

---

## 9. 风险与人工审核点

| 风险 | 说明 | 处理 |
|------|------|------|
| 邮箱唯一冲突 | 升级到真实邮箱时可能与手工用户冲突 | **弹窗三选一**（跳过/覆盖/新建）；定时同步入队后处理；覆盖需二次确认 |
| 企微覆盖本地 | 同用户手工改邮箱会被下次同步改回 | **已确认**产品接受；可在帮助文案中说明 |
| 企微权限不足 | 代码无法变出手机 / 邮箱 | 运维开通权限 + 连接测试抽样 + 同步 WARN |
| 历史脏数据 | 已有占位邮箱 | 依赖 T2 升级 + 一次全量同步，避免直接生产裸 SQL |
| 邮箱长度 | `user.email` 校验 max 64 | **单用户 failed，禁止截断** |
| 关键逻辑 | 用户主数据写入 | 合入前人工走查 `UserSyncHandler` 与冲突分支 |

> 禁止将 AI 生成的修复 SQL 未经本地验证直接用于生产。优先用同步引擎 upsert 修复。

---

## 10. 验证步骤（提测）

1. **灰度前**：在企微管理后台确认通讯录 Secret 权限（手机号、邮箱）。  
2. **抓包 / 日志**：确认 `user/list` 原始 JSON 是否含 `mobile`、`email`、`biz_mail`（区分权限问题 vs 代码问题）。  
3. **部署 T1+T2**：触发手动全量同步。  
4. **DB 抽查**：

```sql
-- 仍为占位的用户（期望趋近 0，或确认为企微侧无邮箱）
SELECT id, name, email, phone, wecom_userid
FROM `user`
WHERE deleted = 0
  AND wecom_userid IS NOT NULL
  AND email LIKE '%@wecom.sync.internal';

-- 有 wecom_userid 但手机为空（结合权限判断）
SELECT id, name, email, phone, wecom_userid
FROM `user`
WHERE deleted = 0
  AND wecom_userid IS NOT NULL
  AND (phone IS NULL OR phone = '');
```

5. **UI**：组织架构成员详情仍脱敏；系统用户管理可见真实邮箱（若该页不脱敏）。  
6. **回归**：关闭 Secret 权限再同步，确认已有手机不被清空。  

---

## 11. 二期可选

| 项 | 说明 |
|----|------|
| 头像同步 | `avatar` → `user_extend.avatar` |
| 性别 | 若产品需要，扩展 `user` 或扩展表 |
| 增量通讯录回调 | 与全量定时并存，缩短一致延迟 |
| 详情「查看明文」权限 | 有权限角色可见未脱敏联系方式 |
| 邮箱可空改造 | 若长期大量无邮箱成员，评估创建链路与唯一约束方案 |

---

## 12. 参考文档

| 文档 | 说明 |
|------|------|
| [组织架构模块设计摘要](./Metersphere-组织架构模块设计摘要.md) | 模块定位与同步原则 |
| [task006 企微通讯录客户端](../task/community_rebuild/task006-P2-企微通讯录客户端.md) | Client 边界 |
| [task007 组织架构同步引擎](../task/community_rebuild/task007-P2-组织架构同步引擎.md) | 同步流程与空值不覆盖 |
| myTapd：`工单系统与MeterSphere-企微同步对比-2026-07-29.md` | 能力定位对比 |
| myTapd：`组织架构模块设计摘要.md` | 工单侧字段映射与同步语义 |
| myTapd：`WecomSyncService.java` | 员工 upsert / 空值保护 / 缺失日志参考实现 |

---

## 13. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-29 | 首版：基于源码对照 myTapd 与现网失真问题整理实现方案 |
| v1.1 | 2026-07-29 | 源码复核：补更新路径 `biz_mail`、统一邮箱函数、主部门回退、邮箱长度/唯一预检、采样区分权限、下游说明；拆出 `docs/task/wecom_sync_fields` |
| v1.2 | 2026-07-29 | 产品确认：同用户企微覆盖本地；跨用户冲突弹窗三选一（跳过/覆盖/新建）+ 冲突队列表与 API |
