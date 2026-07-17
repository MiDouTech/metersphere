# MeterSphere 组织设置 UI 巡检缺陷清单

> **文档类型**：缺陷汇总 / 测试输出  
> **文档状态**：【AI生成】待人工审核确认  
> **关联策略**：[MeterSphere-全系统-测试策略-2026-07-04.md](../destination/MeterSphere-全系统-测试策略-2026-07-04.md)  
> **测试日期**：2026-07-17  
> **版本**：v1.0  
> **测试环境**：`https://msp.ebcone.net`，组织 `orgId=100001`  
> **测试方式**：Edge CDP（`127.0.0.1:9222`）+ Playwright 快速 UI 巡检；手工登录后遍历组织设置可视入口  
> **证据目录**：`output/playwright/org-setting-20260717/`  
> **原始报告**：`output/playwright/org-setting-20260717/org-setting-audit-report.json`

---

## 0. 文档说明

本文档汇总 **2026-07-17** 对「系统设置 → 组织」模块快速 UI 巡检发现的异常弹框 / 报错问题。

**缺陷分级**（同测试策略 §3）：

| 级别 | 定义 |
|------|------|
| **P0-阻断** | 核心流程不可用、数据丢失、安全越权 |
| **P1-严重** | 主要功能异常但有绕行 |
| **P2-一般** | 次要功能、边界场景、测试/文档/环境问题 |
| **P3-轻微** | UI 样式、文案、体验、文档笔误 |

**状态说明**：`待修复` / `已修复（回归）` / `环境阻塞` / `待确认`

---

## 1. 测试执行概况

| 入口 | URL | 结果 |
|------|-----|------|
| 成员 | `/#/setting/organization/member?orgId=100001` | 可达；首轮曾连带命中日志用户列表 500 |
| 组织架构 | `/#/setting/organization/org-structure?orgId=100001` | 可达；出现 `not found` 弹层（见 BUG-ORG-003） |
| 用户组 | `/#/setting/organization/usergroup?orgId=100001` | 可达，未见同类错误 |
| 项目 | `/#/setting/organization/project?orgId=100001` | 可达；「创建项目」触发 SQL 500（见 BUG-ORG-002） |
| 服务集成 | `/#/setting/organization/serviceIntegration?orgId=100001` | 可达，未见同类错误 |
| 模板 | `/#/setting/organization/template?orgId=100001` | 可达，未见同类错误 |
| 任务中心 | `/#/setting/organization/taskCenter?orgId=100001` | 可达；切换了系统即时/后台任务等 Tab |
| 日志 | `/#/setting/organization/log?orgId=100001` | 可达；操作人列表 SQL 500（见 BUG-ORG-001） |

已跳过删除 / 退出等危险操作。登录证据：`wait-manual-login.png`、`logged-in.png`。

---

## 2. 缺陷统计

| 级别 | 待修复 | 待确认 | 已修复（代码） | 合计 |
|------|--------|--------|----------------|------|
| P0 | 0 | 0 | 0 | 0 |
| P1 | 0 | 0 | 2 | 2 |
| P2 | 0 | 1 | 0 | 1 |
| P3 | 0 | 0 | 0 | 0 |
| **合计** | **0** | **1** | **2** | **3** |

> BUG-ORG-001 / BUG-ORG-002 已于 2026-07-17 代码修复（含全仓同类 SQL 自检）；详见 [`MeterSphere-组织设置-缺陷修复与自检记录-2026-07-17.md`](./MeterSphere-组织设置-缺陷修复与自检记录-2026-07-17.md)。

---

## 3. 待修复缺陷

### BUG-ORG-001 【P1-严重】组织日志「操作人」下拉 SQL 500（DISTINCT + ORDER BY）

- **模块**：系统设置 / 组织 / 日志
- **类型**：后端 SQL / 接口
- **状态**：已修复（代码，待环境回归）
- **修复说明**：`getUserListByOrgId` SELECT 增加 `u.create_time`，与 `selectUserList` 对齐。
- **接口**：`GET /api/organization/log/user/list/{organizationId}`
- **关联代码**：
  - `backend/services/system-setting/src/main/java/io/metersphere/system/controller/OrganizationLogController.java`（`/user/list/{organizationId}`）
  - `backend/services/system-setting/src/main/java/io/metersphere/system/mapper/ExtUserMapper.xml` → `getUserListByOrgId`
- **复现步骤**：
  1. 登录后进入 `https://msp.ebcone.net/#/setting/organization/log?orgId=100001`
  2. 进入页面，或点击筛选区「操作人」
- **期望结果**：操作人下拉正常返回组织内用户列表，无错误弹框
- **实际结果**：
  - 顶部红条弹出 MyBatis 数据库异常：`### Error querying database`
  - MySQL 报错码 **3065**：`ORDER BY` 列 `u.create_time` 不在 `SELECT DISTINCT` 列表中
  - HTTP **500**
- **错误摘要**（截图可见）：

```text
Expression #1 of ORDER BY clause is not in SELECT list,
references column 'metersphere.u.create_time' which is not in SELECT list;
this is incompatible with DISTINCT
The error may exist in io/metersphere/system/mapper/ExtUserMapper.xml
SQL: SELECT DISTINCT u.id, u.NAME, u.email FROM `user` u
     LEFT JOIN user_role_relation urr ON u.id = urr.user_id
     WHERE urr.source_id = ? and u.deleted = false
     order by u.create_time desc limit 1000
```

- **根因分析**：`getUserListByOrgId` 使用 `SELECT DISTINCT u.id, u.NAME, u.email`，却 `ORDER BY u.create_time`。在 MySQL `ONLY_FULL_GROUP_BY` / DISTINCT 严格模式下非法。同文件 `selectUserList` 已将 `u.create_time` 放入 SELECT，可作为对照修复方式。
- **建议修复**（二选一或组合）：
  1. SELECT 中增加 `u.create_time`（与 `selectUserList` 对齐）
  2. 改为 `ORDER BY u.id` / `u.name` 等已选列
  3. 去掉不必要的 `DISTINCT`，或用子查询 / `GROUP BY u.id` 后再排序
- **影响范围**：组织操作日志筛选「操作人」；页面主列表可能仍能展示，但筛选能力受损，且错误信息直接暴露给前端用户
- **证据截图**：`output/playwright/org-setting-20260717/defect-log-sql-error.png`

---

### BUG-ORG-002 【P1-严重】创建项目时「项目管理员」下拉 SQL 500（DISTINCT + ORDER BY）

- **模块**：系统设置 / 组织 / 项目
- **类型**：后端 SQL / 接口
- **状态**：已修复（代码，待环境回归）
- **修复说明**：`getUserAdminList` SELECT 增加 `u.create_time`；并自检修复同文件 `getUserMemberList` / `getUserList` 及项目侧 `getProjectUserList`。
- **接口**：`GET /api/organization/project/user-admin-list/{organizationId}`
- **关联代码**：
  - `backend/services/system-setting/src/main/java/io/metersphere/system/controller/OrganizationProjectController.java`（`/user-admin-list/{organizationId}`）
  - `backend/services/system-setting/src/main/java/io/metersphere/system/mapper/ExtSystemProjectMapper.xml` → `getUserAdminList`
- **复现步骤**：
  1. 进入 `https://msp.ebcone.net/#/setting/organization/project?orgId=100001`
  2. 点击「创建项目」
- **期望结果**：弹窗打开后，「项目管理员」下拉可正常加载候选用户
- **实际结果**：
  - 创建项目弹窗打开的同时，顶部红条弹出同类 `### Error querying database`
  - Mapper 指向 `ExtSystemProjectMapper.xml`，错误码 **3065**
  - HTTP **500**；「项目管理员」无法正常选人，创建流程受阻
- **错误摘要**：

```text
Expression #1 of ORDER BY clause is not in SELECT list,
references column 'metersphere.u.create_time' which is not in SELECT list;
this is incompatible with DISTINCT
The error may exist in io/metersphere/system/mapper/ExtSystemProjectMapper.xml
SQL: select distinct u.id, u.NAME, u.email from `user` u
     left join user_role_relation urr on urr.user_id = u.id
     where u.deleted = 0 and urr.source_id = ?
     order by u.create_time desc limit 1000
```

- **根因分析**：与 BUG-ORG-001 同类。`getUserAdminList`（约 L118–L130）`select distinct u.id, u.NAME, u.email ... order by u.create_time desc`。同文件 `getUserMemberList` 亦存在 `group by u.id` + `order by u.create_time`，建议一并排查。
- **建议修复**：与 BUG-ORG-001 相同策略；优先统一修复所有「用户下拉」相关 SQL。
- **影响范围**：组织侧创建项目 / 选择项目管理员；核心管理操作受阻
- **证据截图**：`output/playwright/org-setting-20260717/defect-project-create-sql-error.png`

---

### BUG-ORG-003 【P2-一般】【待确认】组织架构页出现 `not found` 提示

- **模块**：系统设置 / 组织 / 组织架构
- **类型**：前端展示 / 接口（待定位）
- **状态**：待确认
- **页面**：`/#/setting/organization/org-structure?orgId=100001`
- **复现步骤**：
  1. 登录后进入组织设置 →「组织架构」
  2. 观察页面弹层 / 可见错误文案
- **期望结果**：组织架构树与成员列表正常加载，无异常弹框
- **实际结果**：巡检在「进入:组织架构」上下文两次捕获到弹层/页面文本 `not found`（报告 `org-setting-audit-report.json` → `issues`）
- **说明**：
  - 本轮自动化未稳定抓到独立接口 500 与完整堆栈，根因待手工复测确认（可能为部门树/同步配置/某子资源 404 文案透出）
  - 文件名 `defect-org-structure-not-found.png` 内容疑似与创建项目 SQL 错误截图混用，**不以该文件作为本缺陷唯一证据**；以 JSON 报告记录为准
  - 2026-07-17 代码侧：成员详情缺失改为 `ServiceUtils.checkResourceExist(..., "成员")`，业务 404 文案为「成员不存在」；若仍见英文 not found，优先查 HTTP 404 / 前端 `api.errMsg404`
- **建议**：
  1. 手工进入组织架构页，打开 DevTools Network，记录返回 4xx/5xx 的 `/api/**` 请求
  2. 确认是否企微同步 / 部门接口未部署或路径错误
  3. 参考技能：`docs/skills/metersphere-mysql-distinct-orderby-audit/SKILL.md` §6- **证据**：`output/playwright/org-setting-20260717/org-setting-audit-report.json`（`context: 进入:组织架构`）

---

## 4. 观察项（非本轮主缺陷，供回归关注）

| 编号 | 说明 | 优先级 | 备注 |
|------|------|--------|------|
| OBS-ORG-001 | 未登录或会话失效访问组织页时，短时间叠出多条「用户认证失败」+「登出成功」Toast | P3 | 登录页证据见 `01-enter-成员.png` / 早期 auth 截图；属体验问题 |
| OBS-ORG-002 | 登录页仍请求 `GET /api/display/info`、`/api/setting/get/platform/param`、`/api/authentication/get-list` 并出现 500 | P2 | 与历史工作台 `display/info` No static 问题同类，建议发版前回归 |

---

## 5. 修复优先级建议

1. **优先合修 BUG-ORG-001 + BUG-ORG-002**（同一 SQL 模式，改 mapper 即可，影响日志筛选与创建项目）
2. **手工确认 BUG-ORG-003**（组织架构 `not found`），补齐接口证据后再定级
3. 发版前回归：组织设置 8 个顶栏入口 + 创建项目弹窗 + 日志操作人筛选

---

## 6. 附录：证据文件索引

| 文件 | 用途 |
|------|------|
| `wait-manual-login.png` | 等待手工登录 |
| `logged-in.png` | 登录成功进入系统 |
| `defect-log-sql-error.png` | BUG-ORG-001 证据 |
| `defect-project-create-sql-error.png` | BUG-ORG-002 证据 |
| `org-setting-audit-report.json` | 完整巡检原始数据（含组织架构 not found 记录） |

---

*生成说明：本文档由 Cursor Agent 根据 2026-07-17 组织设置 UI 快速巡检结果整理，需测试/研发人工审核确认后再归档为正式缺陷基线。*
