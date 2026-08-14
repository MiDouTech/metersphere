-- 权限控制角色收口：将所有旧非管理员用户组成员合并到单一“成员”角色。
-- 本脚本幂等；旧角色与旧关系保留用于观察期回滚，不做物理删除。

CREATE TABLE IF NOT EXISTS permission_role_migration_audit
(
    id                 varchar(64)  NOT NULL COMMENT '主键',
    migration_version  varchar(64)  NOT NULL COMMENT '迁移版本',
    source_role_id     varchar(64)  NOT NULL COMMENT '旧角色 ID',
    source_role_name   varchar(255)          DEFAULT NULL COMMENT '旧角色名称',
    source_member_count bigint      NOT NULL DEFAULT 0 COMMENT '旧角色成员数',
    migrated_user_count bigint      NOT NULL DEFAULT 0 COMMENT '去重后可迁移成员数',
    skipped_admin_count bigint      NOT NULL DEFAULT 0 COMMENT '因管理员身份跳过数',
    target_role_id     varchar(64)  NOT NULL COMMENT '目标成员角色 ID',
    execute_time       bigint       NOT NULL COMMENT '执行时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_role_migration_version_role (migration_version, source_role_id)
) COMMENT '权限角色迁移审计';

CREATE TABLE IF NOT EXISTS permission_role_migration_failure
(
    id                 varchar(64)  NOT NULL COMMENT '主键',
    migration_version  varchar(64)  NOT NULL COMMENT '迁移版本',
    source_role_id     varchar(64)           DEFAULT NULL COMMENT '旧角色 ID',
    user_id            varchar(64)           DEFAULT NULL COMMENT '失败用户 ID',
    failure_stage      varchar(64)  NOT NULL COMMENT '失败阶段',
    failure_reason     varchar(1000) NOT NULL COMMENT '失败原因',
    execute_time       bigint       NOT NULL COMMENT '执行时间',
    PRIMARY KEY (id),
    KEY idx_permission_role_migration_failure_version (migration_version),
    KEY idx_permission_role_migration_failure_user (user_id)
) COMMENT '权限角色迁移失败明细';

CREATE TABLE IF NOT EXISTS permission_member_initialization
(
    role_id          varchar(64) NOT NULL COMMENT '成员角色 ID',
    init_version     varchar(64) NOT NULL COMMENT '初始化版本',
    initialized_time bigint      NOT NULL COMMENT '初始化完成时间',
    PRIMARY KEY (role_id, init_version)
) COMMENT '成员角色权威权限源初始化记录';

-- 先处理历史同名自定义角色，避免创建新角色时触发全局同名约束。
UPDATE user_role
SET name = CONCAT(name, '（已迁移）'), update_time = UNIX_TIMESTAMP() * 1000
WHERE id <> 'permission_member'
  AND scope_id = 'global'
  AND name = '成员';

-- 新权限控制使用独立角色，避免改动旧系统成员默认组的生命周期语义。
INSERT INTO user_role
    (id, name, description, internal, type, create_time, update_time, create_user, scope_id, enabled)
VALUES
    ('permission_member', '成员', '由旧非管理员用户组成员合并迁移；初始权限与管理员一致', b'0', 'SYSTEM',
     UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1')
ON DUPLICATE KEY UPDATE
    id = VALUES(id);

INSERT IGNORE INTO permission_role_migration_audit
    (id, migration_version, source_role_id, source_role_name, source_member_count,
     migrated_user_count, skipped_admin_count, target_role_id, execute_time)
SELECT
    UUID_SHORT(), 'V3.7.2_50', ur.id, ur.name,
    COUNT(DISTINCT urr.user_id),
    COUNT(DISTINCT CASE WHEN admin_rel.user_id IS NULL THEN urr.user_id END),
    COUNT(DISTINCT CASE WHEN admin_rel.user_id IS NOT NULL THEN urr.user_id END),
    'permission_member', UNIX_TIMESTAMP() * 1000
FROM user_role ur
LEFT JOIN user_role_relation urr ON urr.role_id = ur.id
LEFT JOIN (
    SELECT DISTINCT user_id
    FROM user_role_relation
    WHERE role_id IN ('admin', 'org_admin', 'project_admin')
) admin_rel ON admin_rel.user_id = urr.user_id
WHERE ur.id NOT IN ('admin', 'org_admin', 'project_admin', 'permission_member')
  AND ur.scope_id = 'global'
  AND ur.type = 'SYSTEM'
GROUP BY ur.id, ur.name;

-- 非管理员用户只生成一条系统级成员关系；属于多个旧角色时自动去重。
INSERT INTO user_role_relation
    (id, user_id, role_id, source_id, organization_id, create_time, create_user)
SELECT
    UUID_SHORT(), candidates.user_id, 'permission_member', 'system', 'system', UNIX_TIMESTAMP() * 1000, 'admin'
FROM (
    SELECT DISTINCT urr.user_id
    FROM user_role_relation urr
    INNER JOIN user_role source_role ON source_role.id = urr.role_id
    WHERE urr.role_id NOT IN ('admin', 'org_admin', 'project_admin', 'permission_member')
      AND source_role.scope_id = 'global'
      AND source_role.type = 'SYSTEM'
      AND NOT EXISTS (
          SELECT 1 FROM user_role_relation admin_rel
          WHERE admin_rel.user_id = urr.user_id
            AND admin_rel.role_id IN ('admin', 'org_admin', 'project_admin')
      )
) candidates
WHERE NOT EXISTS (
    SELECT 1 FROM user_role_relation member_rel
    WHERE member_rel.user_id = candidates.user_id
      AND member_rel.role_id = 'permission_member'
);

-- 成员初始接口权限与平台全部已登记权限一致；管理员本身通过稳定角色 ID 默认拥有全部权限。
INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'permission_member', permissions.permission_id
FROM (
    SELECT DISTINCT permission_id
    FROM user_role_permission
) permissions
WHERE NOT EXISTS (
    SELECT 1
    FROM user_role_permission member_permission
    WHERE member_permission.role_id = 'permission_member'
      AND member_permission.permission_id = permissions.permission_id
);

-- 成员初始 UI 权限为全部可见；按钮和接口资源同时可操作。
INSERT INTO user_role_ui_permission (id, role_id, resource_code, visible, operable)
SELECT UUID_SHORT(), 'permission_member', pr.code, b'1',
       IF(pr.type IN ('BUTTON', 'API'), b'1', b'0')
FROM permission_resource pr
WHERE pr.enabled = b'1'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_ui_permission up
      WHERE up.role_id = 'permission_member' AND up.resource_code = pr.code
  );

-- 旧非管理员自定义角色停止继续授权；关系保留用于审计和回滚。
UPDATE user_role
SET enabled = b'0',
    description = CONCAT('[已迁移旧用户组] ', COALESCE(description, '')),
    update_time = UNIX_TIMESTAMP() * 1000
WHERE id NOT IN ('admin', 'org_admin', 'project_admin', 'permission_member')
  AND scope_id = 'global'
  AND type = 'SYSTEM'
  AND internal = b'0'
  AND COALESCE(description, '') NOT LIKE '[已迁移旧用户组] %';
