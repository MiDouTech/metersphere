-- 命名默认组织/项目；标记默认项目；种子枢纽角色（初始不含 SYSTEM_*）

UPDATE organization SET name = '米多公司' WHERE num = 100001 AND (name IS NULL OR name <> '米多公司');

UPDATE project
SET name = '米多公司默认项目',
    is_default = 1
WHERE id = '100001100001';

-- 仅当仍无默认项目时，将组织下最早启用的未删项目标为默认（幂等兜底）
-- 注意：MySQL 禁止 UPDATE project 同时在 WHERE 子查询直接读 project（Error 1093），须包一层派生表
UPDATE project p
JOIN (
  SELECT MIN(id) AS id FROM project
  WHERE organization_id = '100001' AND deleted = 0 AND enable = 1
) t ON p.id = t.id
SET p.is_default = 1,
    p.name = IF(p.id = '100001100001', '米多公司默认项目', p.name)
WHERE NOT EXISTS (
  SELECT 1 FROM (
    SELECT id FROM project WHERE is_default = 1 AND deleted = 0 LIMIT 1
  ) already_default
);

-- 组织侧：默认项目成员自动补授的组织设置角色（不含 SYSTEM_*）
INSERT INTO user_role (id, name, description, internal, type, create_time, update_time, create_user, scope_id)
SELECT 'default_hub_org_setting', '默认项目-组织设置', '加入默认项目时自动授予的组织设置权限（初始不含系统模块；管理员可后续调整）', 1, 'ORGANIZATION',
       UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_role WHERE id = 'default_hub_org_setting');

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'default_hub_org_setting', p.permission_id
FROM (
  SELECT 'ORGANIZATION_USER_ROLE:READ' AS permission_id UNION ALL
  SELECT 'ORGANIZATION_USER_ROLE:READ+ADD' UNION ALL
  SELECT 'ORGANIZATION_USER_ROLE:READ+UPDATE' UNION ALL
  SELECT 'ORGANIZATION_USER_ROLE:READ+DELETE' UNION ALL
  SELECT 'ORGANIZATION_MEMBER:READ' UNION ALL
  SELECT 'ORGANIZATION_MEMBER:READ+ADD' UNION ALL
  SELECT 'ORGANIZATION_MEMBER:READ+INVITE' UNION ALL
  SELECT 'ORGANIZATION_MEMBER:READ+UPDATE' UNION ALL
  SELECT 'ORGANIZATION_MEMBER:READ+DELETE' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+ADD' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+UPDATE' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+DELETE' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+RECOVER' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+ADD_MEMBER' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+UPDATE_MEMBER' UNION ALL
  SELECT 'ORGANIZATION_PROJECT:READ+DELETE_MEMBER' UNION ALL
  SELECT 'ORGANIZATION_TEMPLATE:READ' UNION ALL
  SELECT 'ORGANIZATION_TEMPLATE:READ+ADD' UNION ALL
  SELECT 'ORGANIZATION_TEMPLATE:READ+UPDATE' UNION ALL
  SELECT 'ORGANIZATION_TEMPLATE:READ+DELETE' UNION ALL
  SELECT 'ORGANIZATION_TEMPLATE:READ+ENABLE' UNION ALL
  SELECT 'ORGANIZATION_LOG:READ'
) p
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_permission urp
  WHERE urp.role_id = 'default_hub_org_setting' AND urp.permission_id = p.permission_id
);

-- 项目侧：默认项目专用成员组（复用 project_admin 权限点，排除 SYSTEM_* 由角色类型保证）
INSERT INTO user_role (id, name, description, internal, type, create_time, update_time, create_user, scope_id)
SELECT 'default_hub_project_member', '默认项目成员', '默认项目业务权限全集（初始不含系统模块）', 1, 'PROJECT',
       UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'global'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM user_role WHERE id = 'default_hub_project_member');

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'default_hub_project_member', urp.permission_id
FROM user_role_permission urp
WHERE urp.role_id = 'project_admin'
  AND urp.permission_id NOT LIKE 'SYSTEM_%'
  AND NOT EXISTS (
    SELECT 1 FROM user_role_permission x
    WHERE x.role_id = 'default_hub_project_member' AND x.permission_id = urp.permission_id
  );
