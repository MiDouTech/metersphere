---
name: metersphere-mysql-distinct-orderby-audit
description: >-
  Audit and fix MySQL error 3065 (DISTINCT/GROUP BY + ORDER BY column not in
  SELECT) in MeterSphere MyBatis XML, especially org-setting user dropdowns.
  Use when fixing SQL 500 with ONLY_FULL_GROUP_BY, DISTINCT + ORDER BY
  create_time, org log operator list, project admin list, or similar mapper bugs.
---

# MeterSphere：DISTINCT / GROUP BY + ORDER BY 自检与修复

## 何时使用

- 缺陷清单出现 MySQL **3065**：`ORDER BY` 列不在 `SELECT DISTINCT` / `GROUP BY` 列表中
- 组织设置 / 用户下拉接口 HTTP **500**，堆栈指向 `Ext*Mapper.xml`
- 需要对全仓做「同类 SQL」排查，避免只修一处

触发示例：组织日志操作人、创建项目选管理员、`getUserListByOrgId`、`getUserAdminList`。

## 根因模型（必须说清）

在 MySQL `ONLY_FULL_GROUP_BY` / 严格 DISTINCT 模式下：

```text
SELECT DISTINCT col_a, col_b ... ORDER BY col_c
→ 非法（若 col_c 不在 SELECT 列表）

SELECT ... GROUP BY col_a ORDER BY col_c
→ 非法（若 col_c 不在 GROUP BY / SELECT 且非功能依赖）
```

MeterSphere 常见坏味道：

```sql
SELECT DISTINCT u.id, u.NAME, u.email
...
ORDER BY u.create_time DESC
```

```sql
SELECT module_id, count(id), project_id, project.name
...
GROUP BY module_id   -- project_id / name 未进 GROUP BY → MySQL 1055
```

```sql
SELECT u.id, u.name, u.email, ue.avatar
...
GROUP BY urr.user_id   -- 非聚合列未进 GROUP BY → MySQL 1055
```

对照正确写法（同文件常已有）：

```sql
SELECT DISTINCT u.id, u.NAME, u.email, u.create_time
...
ORDER BY u.create_time DESC
```

```sql
GROUP BY module_id, project_id, project.name
-- 或 GROUP BY u.id, u.name, u.email, ue.avatar
```

## 标准排查流程

### 1) 从缺陷 / 报错定位 Mapper

1. 读缺陷清单或前端红条中的 `The error may exist in ...Mapper.xml`
2. 记下接口路径（如 `/organization/log/user/list/{orgId}`）
3. 从 Controller → Service → `Ext*Mapper` 方法 id 精确定位 `<select id="...">`

### 2) 判定是否同类根因

对目标 SQL 同时满足则列为 **P1 必修**：

- [ ] 含 `SELECT DISTINCT` 或 `GROUP BY`
- [ ] 含 `ORDER BY` 某列（常见 `u.create_time` / `*.update_time`）
- [ ] 该排序列 **未** 出现在 SELECT（DISTINCT）或 GROUP BY 列表
- [ ] SELECT 不是 `u.*` / `表.*`（`*` 通常已含排序列，风险低）

### 3) 全仓同类扫描（必做）

在仓库 `backend/**/*.xml` 执行等价检索：

```text
模式 A：order by u.create_time / ORDER BY u.create_time
模式 B：select distinct + 仅 id/name/email 列
模式 C：group by u.id 后跟 order by u.create_time
```

推荐命令（任选）：

```bash
rg -n --glob "*.xml" "order by u\\.create_time" backend
rg -n --glob "*.xml" -U "(?is)select\\s+distinct[^;]{0,400}?order\\s+by\\s+u\\.create_time" backend
rg -n --glob "*.xml" "group by u\\.id" -A3 backend
rg -n --glob "*.xml" "GROUP BY module_id|GROUP BY moduleId" -B8 backend
rg -n --glob "*.xml" "GROUP BY urr\\.user_id" -B10 backend
```

**同时扫社区版缺失接口**（No static resource）：

```bash
rg -n "setting/get/platform|/display/info|authsource/list" frontend/src/api
# 对照 backend 是否存在同路径 @RestController；社区版常因 revert xpack stub 而 404
```

对每个命中点打开前后文，按上一节清单勾选；**不要**只修缺陷单上的两处。
**也不要**只扫 `ORDER BY create_time`——`GROUP BY` 缺列（1055）与「No static resource」是同批回归必查项。

### 4) 修复策略（优先统一）

| 场景 | 推荐修法 |
|------|----------|
| `DISTINCT` + `ORDER BY create_time` | SELECT 增加 `u.create_time`（对齐 `selectUserList`） |
| `GROUP BY u.id` + `ORDER BY create_time` | SELECT 增加 `u.create_time`，`GROUP BY` 补齐 `u.id, u.NAME, u.email, u.create_time`；去掉多余 `DISTINCT` |
| 仅需去重不需按创建时间排 | 改为 `ORDER BY u.id` / `u.name`（已选列） |
| `select distinct u.*` | 一般已安全，无需改 |

**禁止**：为通过编译删掉 `ORDER BY` 却不评估产品排序预期；禁止改生产 `sql_mode` 掩盖问题。

### 5) 关联产品回归

修完后至少回归：

1. 组织 → 日志 → 「操作人」下拉
2. 组织 → 项目 → 「创建项目」→「项目管理员」下拉
3. 项目侧同类用户下拉（若修了 `ExtProjectUserRoleMapper.getProjectUserList`）
4. 确认不再弹出 MyBatis `Error querying database` / 3065

### 6) 组织架构「not found」类（P2 待确认）并行路径

若巡检捕获文案含 `not found`，**不要**默认当成 DISTINCT SQL：

1. DevTools Network 找 4xx/5xx 的 `/api/**`
2. 区分：HTTP 404 前端 `api.errMsg404`、业务码 `100404` + i18n `http_result_not_found`
3. 成员详情缺失应使用 `ServiceUtils.checkResourceExist(resource, "成员")`，展示「成员不存在」
4. 无稳定接口证据则保持「待确认」，写入观察项，勿硬改无关 SQL

## 已知历史命中点（组织设置 2026-07-17）

| ID | Mapper | 方法 | 状态 |
|----|--------|------|------|
| BUG-ORG-001 | `ExtUserMapper.xml` | `getUserListByOrgId` | 加 `u.create_time` |
| BUG-ORG-002 | `ExtSystemProjectMapper.xml` | `getUserAdminList` | 加 `u.create_time` |
| 自检 | `ExtSystemProjectMapper.xml` | `getUserMemberList` / `getUserList` | GROUP BY 补齐 create_time |
| 自检 | `ExtProjectUserRoleMapper.xml` | `getProjectUserList` | 加 `u.create_time` |
| BUG-ORG-003 | 组织架构页 | `not found` 文案 | 待 Network 确认；详情用 checkResourceExist |

## 已知历史命中点（2026-07-17 第二轮：评审/计划模块计数 + 扫码）

| ID | 位置 | 问题 | 状态 |
|----|------|------|------|
| BUG-SQL-1055-R1 | `ExtCaseReviewFunctionalCaseMapper.countModuleIdByRequest` | GROUP BY 仅 module_id | GROUP BY 补 project_id, name |
| BUG-SQL-1055-R2 | `ExtTestPlanFunctionalCaseMapper` / ApiCase / ApiScenario `countModuleIdByRequest` | 同上 | 同上 |
| BUG-SQL-1055-R3 | `ExtUserMapper.getUserByKeyword` / `getUserByPermission` | GROUP BY 仅 urr.user_id | GROUP BY 补全选中列 |
| BUG-SQL-3065-R4 | `ExtProjectMapper.getProjectByOrgId` | DISTINCT 缺 create_time | SELECT 加 create_time |
| BUG-STUB-QR | `PlatformSettingController` 等 | revert 后缺失 → No static resource | 从 fdd5227bac 恢复社区 stub |

**注意**：源码已修但灰度未重新部署时，截图仍会显示旧 SQL（无 create_time）。自检必须对照 **运行中容器** 的 Mapper 或接口响应，不能只看本地 git。

## 输出要求

完成一次排查后，向用户交付：

1. **已修复清单**（文件 + select id + 改法）
2. **自检命中但已修 / 判定安全** 的列表
3. **仍待确认** 项（含复现与证据缺口）
4. 建议回归入口 URL / 操作路径

## 反模式

- 只改缺陷单两处、不做全仓 `order by u.create_time` 扫描
- 把前端 Toast「not found」直接当成 SQL 3065
- 提交含真实密钥的本地配置或巡检报告 JSON
