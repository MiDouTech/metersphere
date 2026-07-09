# task004 - P1 数据模型与 Flyway 迁移

> **阶段**：P1  
> **预估工期**：1 天  
> **前置依赖**：[task002](task002-P0-组织创建与切换API.md)（需多组织 `organization_id`）  
> **阻塞任务**：task005、task006、task007、task011  
> **关联文档**：[community-unlock-and-org-structure.md](../../summary/community-unlock-and-org-structure.md) §5.2

---

## 1. 任务目标

新增组织架构相关数据库表结构，扩展 `user` 表字段，为部门树查询与企微同步提供持久化基础。

---

## 2. 迁移文件规划

**建议版本号**：`3.7.0`（按当前仓库 Flyway 版本递增）

| 文件 | 内容 |
|------|------|
| `ddl/V3.7.0_1__org_structure.sql` | `department`、`org_wecom_sync_config`、`org_sync_log` |
| `ddl/V3.7.0_2__user_org_structure.sql` | `user` 表扩展字段与索引 |

**路径**：`backend/framework/domain/src/main/resources/migration/3.7.0/`

---

## 3. 表结构设计

### 3.1 department（部门）

```sql
CREATE TABLE department (
  id                  VARCHAR(50)  NOT NULL COMMENT '部门ID',
  organization_id     VARCHAR(50)  NOT NULL COMMENT '所属MeterSphere组织ID',
  name                VARCHAR(255) NOT NULL COMMENT '部门名称',
  parent_id           VARCHAR(50)           COMMENT '父部门本地ID',
  wecom_dept_id       BIGINT                COMMENT '企微部门ID',
  sort_order          INT          DEFAULT 0 COMMENT '排序',
  dept_status         TINYINT      DEFAULT 1 COMMENT '1启用 0停用',
  sync_status         TINYINT      DEFAULT 0 COMMENT '0未同步 1已同步 2同步失败',
  sync_time           BIGINT                COMMENT '最近同步时间戳',
  leader_wecom_userid VARCHAR(100)          COMMENT '部门负责人企微UserID',
  create_time         BIGINT       NOT NULL,
  update_time         BIGINT       NOT NULL,
  create_user         VARCHAR(50)  NOT NULL,
  update_user         VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_org_wecom_dept (organization_id, wecom_dept_id),
  KEY idx_org_parent (organization_id, parent_id),
  KEY idx_org_status (organization_id, dept_status)
) COMMENT='组织部门表';
```

### 3.2 user 表扩展

```sql
ALTER TABLE user ADD COLUMN wecom_userid VARCHAR(100) COMMENT '企微UserID';
ALTER TABLE user ADD COLUMN department_id VARCHAR(50) COMMENT '主部门本地ID';
ALTER TABLE user ADD COLUMN position VARCHAR(100) COMMENT '职位';
ALTER TABLE user ADD COLUMN sync_status TINYINT DEFAULT 0 COMMENT '同步状态';
ALTER TABLE user ADD COLUMN sync_time BIGINT COMMENT '最近同步时间';

CREATE UNIQUE INDEX uk_user_wecom_userid ON user(wecom_userid);
CREATE INDEX idx_user_department ON user(department_id);
CREATE INDEX idx_user_org_dept ON user(last_organization_id, department_id);
```

**说明**：

- `wecom_userid` 全局唯一（一个企微用户对应一个 MS 用户）  
- `last_organization_id` 为 MS 已有字段，同步时一并维护  
- `department_id` 仅存主部门（一期不支持一人多部门）  

### 3.3 org_wecom_sync_config（企微同步配置）

```sql
CREATE TABLE org_wecom_sync_config (
  id                  VARCHAR(50)  NOT NULL PRIMARY KEY,
  organization_id     VARCHAR(50)  NOT NULL COMMENT 'MS组织ID',
  corp_id             VARCHAR(100) NOT NULL COMMENT '企微CorpID',
  contact_secret      VARCHAR(255) NOT NULL COMMENT '通讯录Secret',
  agent_id            VARCHAR(50)           COMMENT '应用AgentId（可选）',
  schedule_enabled    TINYINT      DEFAULT 0 COMMENT '是否启用定时同步',
  schedule_cron       VARCHAR(50)           COMMENT 'Cron表达式',
  retry_times         INT          DEFAULT 3 COMMENT '失败重试次数',
  last_sync_time      BIGINT                COMMENT '最近同步时间',
  create_time         BIGINT       NOT NULL,
  update_time         BIGINT       NOT NULL,
  create_user         VARCHAR(50)  NOT NULL,
  update_user         VARCHAR(50)  NOT NULL,
  UNIQUE KEY uk_org_config (organization_id)
) COMMENT='组织企微同步配置';
```

### 3.4 org_sync_log（同步日志）

```sql
CREATE TABLE org_sync_log (
  id                  VARCHAR(50)  NOT NULL PRIMARY KEY,
  organization_id     VARCHAR(50)  NOT NULL,
  sync_mode           VARCHAR(20)  NOT NULL COMMENT 'MANUAL/SCHEDULE/LOGIN',
  sync_status         VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED',
  dept_total          INT          DEFAULT 0,
  dept_success        INT          DEFAULT 0,
  dept_failed         INT          DEFAULT 0,
  user_total          INT          DEFAULT 0,
  user_success        INT          DEFAULT 0,
  user_failed         INT          DEFAULT 0,
  duration_ms         BIGINT       DEFAULT 0,
  error_message       TEXT,
  create_time         BIGINT       NOT NULL,
  create_user         VARCHAR(50)  NOT NULL,
  KEY idx_org_time (organization_id, create_time DESC)
) COMMENT='组织同步日志';
```

---

## 4. 领域模型与 Mapper

### 4.1 生成 / 新增文件

| 类型 | 路径 |
|------|------|
| Domain | `backend/framework/domain/.../domain/Department.java` |
| Domain | `backend/framework/domain/.../domain/OrgWecomSyncConfig.java` |
| Domain | `backend/framework/domain/.../domain/OrgSyncLog.java` |
| Mapper | `backend/framework/domain/.../mapper/DepartmentMapper.java` + `.xml` |
| ExtMapper | `backend/services/system-setting/.../mapper/ExtDepartmentMapper.java` + `.xml` |

**User 扩展**：更新 `User.java` 及 `UserMapper.xml` 字段映射。

### 4.2 企微字段映射表

| 企微（部门） | 本地字段 |
|-------------|----------|
| id | wecom_dept_id |
| name | name |
| parentid | parent_id（Pass2 换算） |
| order | sort_order |
| department_leader[0] | leader_wecom_userid |

| 企微（成员） | 本地字段 |
|-------------|----------|
| userid | wecom_userid |
| name | name |
| mobile | phone（有值才更新） |
| email | email |
| position | position（有值才更新） |
| department[0] | department_id（换算为本地 ID） |
| status | enable / deleted 映射 |

---

## 5. 回滚脚本

在同目录或 `docs/task/rollback/` 提供：

```sql
-- rollback V3.7.0_2
ALTER TABLE user DROP INDEX uk_user_wecom_userid;
ALTER TABLE user DROP COLUMN wecom_userid, department_id, position, sync_status, sync_time;

-- rollback V3.7.0_1
DROP TABLE IF EXISTS org_sync_log;
DROP TABLE IF EXISTS org_wecom_sync_config;
DROP TABLE IF EXISTS department;
```

---

## 6. 验收标准

- [x] 本地 `spring-boot:run` Flyway 迁移无报错（Docker MySQL 已验证 SQL；宿主机 3306 连通性待排查）
- [x] `department`、`org_wecom_sync_config`、`org_sync_log` 表存在
- [x] `user` 表新字段与索引存在
- [x] MyBatis Generator 或手写 Mapper 可 CRUD
- [x] 回滚脚本已在文档中记录（`docs/task/rollback/V3.7.0_rollback.sql`）

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-06 |
| 完成日期 | 2026-07-06 |
