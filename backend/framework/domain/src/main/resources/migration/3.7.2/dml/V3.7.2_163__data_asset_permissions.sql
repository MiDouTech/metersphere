INSERT INTO user_role_permission(id,role_id,permission_id)
SELECT UUID_SHORT(),r.id,p.permission_id FROM user_role r JOIN (
SELECT 'TEST_DATA_ASSET:READ' permission_id UNION ALL SELECT 'TEST_DATA_ASSET:READ+UPDATE'
) p WHERE r.type IN ('SYSTEM','ORGANIZATION') AND r.id IN ('admin','org_admin','default_hub_org_setting')
AND NOT EXISTS(SELECT 1 FROM user_role_permission x WHERE x.role_id=r.id AND x.permission_id=p.permission_id);
