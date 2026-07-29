# task005 - P0 单测与灰度全量验收

> **阶段**：P0  
> **预估工期**：0.5～1 人日  
> **前置依赖**：[task001](task001-P0-DTO与Client解析.md)、[task002](task002-P0-UserSyncHandler字段策略.md)、[task006](task006-P0-邮箱冲突人工三选一.md)  
> **阻塞任务**：无  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：§8、§10  
> **状态**：✅ 单测已完成（2026-07-29，25 cases 全绿）；⏳ 灰度全量验收待环境实机

---

## 1. 任务目标

用单测锁住字段策略，并在灰度环境通过一次全量同步验证历史占位邮箱升级与手机落库；区分权限问题与代码问题。

---

## 2. 单测任务清单

### 2.1 `UserSyncHandlerTest`

- [x] 仅 `biz_mail` 新建 → 非占位邮箱  
- [x] 占位用户 + 仅 `biz_mail` 更新 → 升级  
- [x] 本地已有真实 email，API email/biz_mail 皆空 → 不覆盖  
- [x] 本地已有 phone，API mobile 空 → 不覆盖  
- [x] API 有 mobile → 更新 phone  
- [x] `main_department` 优先于 `department[0]`  
- [x] 主部门映射失败时回退可映射部门  
- [x] 邮箱冲突 → 登记 PENDING，不中断；不自动改占用方  
- [x] 同用户邮箱被企微覆盖  
- [x] resolve：SKIP / OVERWRITE / CREATE 行为单测（`OrgSyncEmailConflictServiceTest`）  
- [x] 超长邮箱 → failed，未写入截断值  
- [x] 空成员列表 → 不失活  
- [x] 受保护账号不被失活  

### 2.2 `WecomContactClientTest`

- [x] 依赖 task001 fixture 全绿  

### 2.3（可选）编排单测

- [x] `WecomOrgSyncServiceTest`：用户 Part 统计字段透传（若 task003 已合）  

---

## 3. 灰度验收步骤

1. **权限**：企微后台确认通讯录 Secret 具备手机号、邮箱（及企业邮箱）权限；可信 IP 正确。  
2. **采样**：同步日志或 DEBUG 确认原始字段非空比例（方案 §5.10）。  
3. **部署**：合入 task001+002+006（建议含本任务单测）。  
4. **手动全量同步**一次；若有冲突弹窗，分别验证跳过/覆盖（灰度慎用覆盖）/新建。  
5. **DB 抽查**（主验收口径）：

```sql
-- 仍为占位（期望趋近 0，或确认为企微侧无邮箱）
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

6. **UI**：组织成员详情仍脱敏；系统用户管理可见真实邮箱（若该页不脱敏）。  
7. **负向**：临时关闭手机权限再同步，确认已有 phone 不被清空。  
8. **禁止**：未经本地验证的批量 UPDATE SQL 直接上生产。  

---

## 4. 验收标准

- [x] 相关单测全部通过（UserSyncHandlerTest + OrgSyncEmailConflictServiceTest + WecomOrgSyncServiceTest + WecomContactClientTest = 25）  
- [ ] 灰度全量后：有权限场景下真实邮箱/手机符合方案 §8  
- [ ] 占位升级路径（存量 + 仅 biz_mail）验证通过  
- [ ] 空值不覆盖负向验证通过  
- [ ] 验收记录可附同步 log id 与抽查 SQL 结果摘要（脱敏）  

---

## 5. 交付物

| 项 | 说明 |
|----|------|
| 单测改动 | `UserSyncHandlerTest` / `OrgSyncEmailConflictServiceTest` / `WecomOrgSyncServiceTest` |
| 验收纪要 | 可选追加 `docs/task/wecom_sync_fields/` 下短记录，或贴到 MR 描述 |
