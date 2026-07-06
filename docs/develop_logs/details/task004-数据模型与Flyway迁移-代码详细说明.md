# task004 — 数据模型与 Flyway 迁移 · 代码详细说明

> **关联摘要**：[2026-07-06-task004-数据模型与Flyway迁移-开发摘要.md](../2026-07-06-task004-数据模型与Flyway迁移-开发摘要.md)

---

## 1. Flyway 迁移

### V3.7.0_1 — 组织表结构

路径：`backend/framework/domain/src/main/resources/migration/3.7.0/ddl/V3.7.0_1__org_structure.sql`

- `department`：组织内部门树，`(organization_id, wecom_dept_id)` 唯一
- `org_wecom_sync_config`：每组织一条企微同步配置
- `org_sync_log`：同步执行日志，含部门/用户成功失败计数

遵循仓库惯例：`SET SESSION innodb_lock_wait_timeout`、`CREATE TABLE IF NOT EXISTS`、InnoDB utf8mb4。

### V3.7.0_2 — user 扩展

路径：`backend/framework/domain/src/main/resources/migration/3.7.0/ddl/V3.7.0_2__user_org_structure.sql`

| 字段 | 用途 |
|------|------|
| `wecom_userid` | 企微 UserID，全局唯一索引 |
| `department_id` | 主部门本地 ID |
| `position` | 职位（保留字，SQL 使用反引号） |
| `sync_status` / `sync_time` | 同步状态与时间 |

索引：`uk_user_wecom_userid`、`idx_user_department`、`idx_user_org_dept(last_organization_id, department_id)`。

---

## 2. Domain 实体

均位于 `backend/framework/domain/src/main/java/io/metersphere/system/domain/`：

| 类 | 说明 |
|----|------|
| `Department` | 14 字段，含 `Column` 枚举供 batchInsertSelective |
| `OrgWecomSyncConfig` | 企微 corpId / contactSecret / cron 等 |
| `OrgSyncLog` | 同步结果统计 + `errorMessage`(TEXT) |

TINYINT 字段映射为 Java `Integer`，与 MyBatis `jdbcType=TINYINT` 一致。

---

## 3. Mapper 层

### 3.1 标准 CRUD Mapper

`DepartmentMapper`、`OrgWecomSyncConfigMapper`、`OrgSyncLogMapper` 及对应 `*Example`、`*Mapper.xml` 由 `scripts/gen_mybatis_task004.py` 按 MyBatis Generator 风格生成，支持：

- `selectByExample` / `insertSelective` / `updateByPrimaryKeySelective`
- `batchInsert` / `batchInsertSelective`

### 3.2 User 扩展

- `User.java`：新增 5 个字段及 `Column` 枚举项
- `UserMapper.xml`：ResultMap、Base_Column_List、insert/update/batchInsert 全量更新
- `UserExample.java`：追加 5 组 Criteria 方法

### 3.3 ExtDepartmentMapper

路径：`backend/services/system-setting/src/main/java/io/metersphere/system/mapper/`

| 方法 | SQL 行为 |
|------|----------|
| `listByOrganizationId` | 按 `sort_order, create_time` 排序返回 flat list |
| `selectByWecomDeptId` | 企微 ID → 本地部门 |
| `countEnabledByOrganizationId` | `dept_status = 1` 计数 |

供 task005 `DepartmentQueryService.getTree()` 使用。

---

## 4. 回滚

`docs/task/rollback/V3.7.0_rollback.sql`：

1. 先回滚 user 索引与列（V3.7.0_2）
2. 再 DROP 三张新表（V3.7.0_1）

---

## 5. 企微字段映射（供 task007 参考）

已在 task004 规格 §4.2 定义；持久化字段本次全部就绪，换算逻辑在同步引擎 Pass2 实现。
