# task002 - P0 UserSyncHandler 字段策略

> **阶段**：P0  
> **预估工期**：1～1.5 人日  
> **前置依赖**：[task001](task001-P0-DTO与Client解析.md)  
> **阻塞任务**：task003、task005  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：§5.2～5.4、§5.8、§5.10  
> **说明**：用户主数据写入，合入前须人工走查  
> **状态**：✅ 代码已完成（2026-07-29）；合入前请人工审核 Handler 升级/冲突分支

---

## 1. 任务目标

在 `UserSyncHandler` 内一次性补齐：真实邮箱解析与占位升级、主部门解析、空值不覆盖、**邮箱冲突登记（不自动抢）**、超长处理、缺失计数与采样日志。

冲突弹窗三选一见 [task006](task006-P0-邮箱冲突人工三选一.md)。

---

## 2. 现状分析（关键坑）

| 路径 | 现状 | 问题 |
|------|------|------|
| 创建 | `resolveEmail()`：无 email 则写占位 | 未读 `biz_mail` |
| 更新 | 仅 `wecomUser.getEmail()` 非空才改 | **仅有 biz_mail 的存量占位永远升不了级** |
| 主部门 | `department.getFirst()` | 忽略 `main_department` |
| 冲突 | 依赖底层异常 | 改为登记 PENDING，交 task006 人工处理 |
| 统计 | 仅 created/updated/failed | 无缺失手机/占位统计 |

---

## 3. 任务清单

### 3.1 邮箱（创建与更新统一）

- [x] 实现 `resolveSyncEmail`（email → biz_mail → null）  
- [x] 实现 `resolveCreateEmail`（无真实值才占位）  
- [x] 实现 `isPlaceholderEmail`  
- [x] 实现 `applyEmailOnUpdate`（见方案矩阵；API 空不覆盖；有真实值则写入/升级）  
- [x] 长度 > 64：该用户 failed，**禁止截断**  
- [x] 唯一冲突：`registerEmailConflict`，**不**自动改占用方；创建用占位建用户；更新保留原邮箱；`userEmailConflict++`  
- [x] 同用户真实邮箱与企微不一致：**企微覆盖**（无冲突占用方时）

### 3.2 手机 / 职位

- [x] 新建：`phone = trimToNull(mobile)`（允许 null）  
- [x] 更新：仅 `latestPhone != null` 时更新（保持现网）  
- [x] `position` 同空值不覆盖  

### 3.3 主部门

- [x] 优先 `main_department`  
- [x] 否则 `department[0]`  
- [x] 映射失败时回退部门列表中第一个可映射项  
- [x] 皆失败 → `department_id = null`（不抛错中断）  

### 3.4 可观测（Handler 内先落地计数）

- [x] 统计：`userMissingMobile` / `userMissingEmail` / `userPlaceholderEmail`（及可选 conflict）  
- [x] 同步结束应用日志：`企微成员 N 人，手机号缺失 M 人，邮箱缺失/占位 P 人`；`M>0` → WARN  
- [x]（可选）前 N=20 条字段非空比例采样一行日志（方案 §5.10）  
- [x] 计数写入 `SyncPartResult`（本任务可先扩包内 DTO；面板展示见 task003）  

### 3.5 保护与回归

- [x] `admin` / `DEV_` 保护逻辑不变  
- [x] 空成员列表跳过失活不变  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `.../department/UserSyncHandler.java` | 主逻辑 |
| `.../department/OrgSyncConstants.java` | 可选 `isPlaceholder` 辅助 |
| `.../department/SyncPartResult.java` | 可选统计字段 |
| `ExtUserMapper` / XML（若需） | 按 email 查占用用户 |

---

## 5. 测试用例（单测清单，实现见 task005）

| 场景 | 预期 |
|------|------|
| 仅 biz_mail 新建 | email=企业邮箱，非占位 |
| 仅 biz_mail 更新占位用户 | 升级为真实邮箱 |
| API email/mobile 皆空更新 | 本地真实 email/phone 不变 |
| 有 mobile 更新 | phone 更新 |
| main_department 与 department[0] 不同 | 挂主部门对应本地 id |
| 邮箱冲突 | 登记 PENDING，其它字段仍同步；不 failed 整用户（除非建用户本身失败） |
| 同用户邮箱 A→企微 B | 直接覆盖为 B |
| 超长邮箱 | failed++，不截断写入 |

---

## 6. 验收标准

- [x] 创建/更新邮箱行为与方案 §5.2 矩阵一致  
- [x] 存量占位 + 仅 biz_mail 可升级（现网最大坑关闭）  
- [x] 空值不覆盖；主部门优先正确  
- [x] 冲突入队不中断全量；同用户企微覆盖正确  
- [x] 日志可见缺失摘要与冲突计数  
- [ ] **人工审核** `UserSyncHandler` 升级与登记分支（合入前）  

---

## 7. 风险

合入前须人工走查升级与冲突登记分支，避免误覆盖生产邮箱。
