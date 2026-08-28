-- 统一权限角色修正：六个内置角色是唯一必备角色，废弃 permission_member 全权限角色。
-- 本脚本幂等，保留 permission_member 元数据用于历史审计，但不再保留有效授权或成员关系。

INSERT INTO user_role
    (id, name, description, internal, type, create_time, update_time, create_user, scope_id, enabled)
VALUES
    ('admin', '系统管理员', '系统作用域内无条件放权', b'1', 'SYSTEM', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1'),
    ('member', '系统成员', '系统默认成员角色', b'1', 'SYSTEM', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1'),
    ('org_admin', '组织管理员', '组织及其项目作用域内无条件放权', b'1', 'ORGANIZATION', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1'),
    ('org_member', '组织成员', '组织默认成员角色', b'1', 'ORGANIZATION', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1'),
    ('project_admin', '项目管理员', '项目作用域内无条件放权', b'1', 'PROJECT', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1'),
    ('project_member', '项目成员', '项目默认成员角色', b'1', 'PROJECT', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global', b'1')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), internal = b'1', type = VALUES(type),
    scope_id = 'global', enabled = b'1', update_time = UNIX_TIMESTAMP() * 1000;

-- 将历史 permission_member 系统关系迁回内置系统成员；同一用户只保留一条有效关系。
INSERT INTO user_role_relation
    (id, user_id, role_id, source_id, organization_id, create_time, create_user)
SELECT UUID_SHORT(), legacy.user_id, 'member', 'system', 'system', UNIX_TIMESTAMP() * 1000, 'admin'
FROM user_role_relation legacy
WHERE legacy.role_id = 'permission_member'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_relation current_member
      WHERE current_member.user_id = legacy.user_id
        AND current_member.role_id = 'member'
        AND current_member.source_id = 'system'
  );

DELETE FROM user_role_relation WHERE role_id = 'permission_member';
DELETE FROM user_role_permission WHERE role_id = 'permission_member';
DELETE FROM user_role_ui_permission WHERE role_id = 'permission_member';

UPDATE user_role
SET enabled = b'0', internal = b'0',
    name = '成员（已废弃）',
    description = '[已迁移旧用户组] 权限与成员已迁移至内置系统成员角色',
    update_time = UNIX_TIMESTAMP() * 1000
WHERE id = 'permission_member';
