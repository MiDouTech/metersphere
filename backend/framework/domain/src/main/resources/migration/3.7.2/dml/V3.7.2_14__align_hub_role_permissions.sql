-- 枢纽角色权限与「系统设置-系统-用户组」内置角色对齐：
-- default_hub_org_setting ← org_admin（排除 SYSTEM_*）
-- default_hub_project_member ← project_admin（排除 SYSTEM_*）

-- 组织侧
DELETE FROM user_role_permission WHERE role_id = 'default_hub_org_setting';

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'default_hub_org_setting', urp.permission_id
FROM user_role_permission urp
WHERE urp.role_id = 'org_admin'
  AND urp.permission_id NOT LIKE 'SYSTEM_%'
  AND EXISTS (SELECT 1 FROM user_role r WHERE r.id = 'default_hub_org_setting');

-- 项目侧（幂等重刷，避免历史种子与当前 project_admin 表漂移）
DELETE FROM user_role_permission WHERE role_id = 'default_hub_project_member';

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'default_hub_project_member', urp.permission_id
FROM user_role_permission urp
WHERE urp.role_id = 'project_admin'
  AND urp.permission_id NOT LIKE 'SYSTEM_%'
  AND EXISTS (SELECT 1 FROM user_role r WHERE r.id = 'default_hub_project_member');
