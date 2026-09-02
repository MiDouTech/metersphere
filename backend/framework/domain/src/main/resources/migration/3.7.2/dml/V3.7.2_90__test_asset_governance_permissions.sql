INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, p.permission_id
FROM user_role r
JOIN (
    SELECT 'TEST_ASSET_CATEGORY:MANAGE' permission_id UNION ALL
    SELECT 'TEST_ASSET_CATEGORY:ASSIGN' UNION ALL
    SELECT 'TEST_ASSET_SOURCE:GOVERN'
) p
WHERE r.id IN ('admin', 'org_admin', 'project_admin', 'default_hub_org_setting')
  AND NOT EXISTS (SELECT 1 FROM user_role_permission urp
                  WHERE urp.role_id = r.id AND urp.permission_id = p.permission_id);

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, 'TEST_ASSET_CATEGORY:ASSIGN'
FROM user_role r
WHERE r.id IN ('project_member', 'default_hub_project_member')
  AND NOT EXISTS (SELECT 1 FROM user_role_permission urp
                  WHERE urp.role_id = r.id AND urp.permission_id = 'TEST_ASSET_CATEGORY:ASSIGN');
