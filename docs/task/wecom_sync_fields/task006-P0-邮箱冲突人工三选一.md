# task006 - P0 邮箱冲突人工三选一

> **阶段**：P0  
> **预估工期**：1～1.5 人日  
> **前置依赖**：[task002](task002-P0-UserSyncHandler字段策略.md)  
> **阻塞任务**：task005  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：§5.8  
> **说明**：改写占用方邮箱属高风险；覆盖路径须人工审核 + 二次确认  
> **状态**：✅ 代码已完成（2026-07-29）；覆盖事务须人工审核；灰度实机待验

---

## 1. 任务目标

跨用户邮箱冲突时，同步**不自动抢邮箱**，登记 PENDING；提供列表与弹窗，人工选择 **跳过 / 覆盖 / 新建** 并落库审计。

---

## 2. 产品语义（已确认）

| 选项 | 行为摘要 |
|------|----------|
| 跳过 | 企微成员不用该邮箱；占用方不变；关闭冲突 |
| 覆盖 | 邮箱给企微成员；占用方改为占位邮箱；**二次确认** |
| 新建 | 仅创建冲突：保留双账号，企微侧继续占位；更新冲突场景禁用「新建」 |

定时同步：只入队。手动同步结束若有 PENDING：自动打开处理面板。

---

## 3. 任务清单

### 3.1 数据模型

- [x] Flyway：`org_sync_email_conflict`（字段见方案 §5.8.2）  
- [x] Domain / Mapper / Example（本期用 `JdbcTemplate` + DTO，等价实现）  
- [x] 唯一幂等：`(organization_id, wecom_userid, conflict_email)` 的 PENDING 不重复插入  

### 3.2 同步侧登记（与 task002 衔接）

- [x] `UserSyncHandler` 冲突时调用 `registerEmailConflict`（可抽 `OrgSyncEmailConflictService`）  
- [x] 创建冲突：用户仍可建出（占位邮箱）；更新冲突：其它字段照常、邮箱不抢  
- [x] `SyncResult.userEmailConflict` = 本轮新增/仍 PENDING 数  

### 3.3 Resolve API

- [x] `GET .../email-conflict/pending?organizationId=`  
- [x] `POST .../email-conflict/resolve`：`{ id, action: SKIP|OVERWRITE|CREATE }`  
- [x] 权限：组织管理员 / 与同步配置同级权限  
- [x] OVERWRITE：事务内改 pending_user.email + occupied_user.email(占位)；占位需保证唯一  
- [x] CREATE：校验冲突类型为创建场景；否则 4xx  
- [x] 操作写操作日志 / 审计字段  

### 3.4 前端弹窗

- [x] 冲突列表：企微成员、期望邮箱、占用方信息  
- [x] 三按钮 + 覆盖二次确认文案（占用方将失去该邮箱登录能力）  
- [x] 手动同步结束 `userEmailConflict > 0` 自动弹出  
- [x] 同步入口「待处理邮箱冲突」红点 / 入口  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| Flyway SQL | 新表 |
| Conflict Service / Controller | 登记与 resolve |
| `UserSyncHandler` / ApplicationService | 登记钩子、结果字段 |
| `SyncPanel` + 冲突弹窗组件 | UI |
| locale | 文案 |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| 同步撞邮箱 | PENDING 记录出现；全量不中断 |
| 跳过 | 双方邮箱不变；状态 RESOLVED/SKIP |
| 覆盖 | 企微用户得真实邮箱；占用方变占位；可登录影响符合文案 |
| 新建（创建冲突） | 企微用户保持占位；占用方不变 |
| 新建（更新冲突） | 接口拒绝 / 按钮禁用 |
| 定时同步后 | 有红点；打开入口可处理 |
| 重复同步同一冲突 | 仍一条 PENDING |

---

## 6. 验收标准

- [x] 冲突入队与三选一行为符合方案 §5.8.3（单测已覆盖 SKIP/OVERWRITE/CREATE）  
- [x] 覆盖有二次确认与审计  
- [x] 不自动合并账号  
- [ ] **人工审核**覆盖事务与占位唯一性（合入前）  
