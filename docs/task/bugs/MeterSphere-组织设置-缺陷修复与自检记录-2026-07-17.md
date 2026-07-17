# MeterSphere-组织设置-缺陷修复与自检记录-2026-07-17

> **标注**：【AI生成】已按缺陷清单修复并全仓自检；BUG-ORG-003 仍建议人工 Network 确认  
> **关联**：[`MeterSphere-组织设置-缺陷清单-2026-07-17.md`](./MeterSphere-组织设置-缺陷清单-2026-07-17.md)  
> **技能**：[`docs/skills/metersphere-mysql-distinct-orderby-audit/SKILL.md`](../../skills/metersphere-mysql-distinct-orderby-audit/SKILL.md)

## 已修复

| 缺陷 | 改动 |
|------|------|
| BUG-ORG-001 | `ExtUserMapper.xml` → `getUserListByOrgId` SELECT 增加 `u.create_time` |
| BUG-ORG-002 | `ExtSystemProjectMapper.xml` → `getUserAdminList` SELECT 增加 `u.create_time` |
| 自检同类 | `getUserMemberList` / `getUserList`：去掉多余 DISTINCT，GROUP BY 含 `create_time` |
| 自检同类 | `ExtProjectUserRoleMapper.xml` → `getProjectUserList` 增加 `u.create_time` |
| BUG-ORG-003（文案） | `OrgStructureMemberService.detail` 改用 `ServiceUtils.checkResourceExist(..., "成员")` |

## 自检结论

- 高风险模式「`SELECT DISTINCT u.id,u.NAME,u.email` + `ORDER BY u.create_time`」在 backend mapper 中已按上表清零。
- `select distinct u.* ... order by ...`、已含 `create_time` 的查询视为安全。
- BUG-ORG-003：无稳定 3065 堆栈；可能为 404 Toast / 成员不存在。需手工打开组织架构页看 Network。

## 建议回归

1. `/#/setting/organization/log?orgId=*` → 操作人下拉  
2. `/#/setting/organization/project` → 创建项目 → 项目管理员  
3. `/#/setting/organization/org-structure` → 无异常红条；点成员详情文案为「成员不存在」（若不存在时）
