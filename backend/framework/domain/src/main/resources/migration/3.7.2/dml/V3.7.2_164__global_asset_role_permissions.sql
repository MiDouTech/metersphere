-- Global asset grants apply only to existing organization and system administrative roles.
-- Existing explicit custom-role grants remain unchanged. Project roles are never promoted.
INSERT INTO user_role_permission(id,role_id,permission_id)
SELECT UUID(),r.id,p.permission_id FROM user_role r JOIN (
SELECT 'BUSINESS_ASSET:READ' permission_id UNION ALL SELECT 'BUSINESS_ASSET:WRITE'
UNION ALL SELECT 'SHARED_CONFIGURATION:READ' UNION ALL SELECT 'SHARED_CONFIGURATION:WRITE'
UNION ALL SELECT 'SHARED_CONFIGURATION:RESOLVE' UNION ALL SELECT 'TEST_ASSET_VERSION:MANAGE'
UNION ALL SELECT 'TEST_DATA_ASSET:READ' UNION ALL SELECT 'TEST_DATA_ASSET:READ+UPDATE'
) p WHERE ((r.type='SYSTEM' AND r.id='admin') OR (r.type='ORGANIZATION' AND r.id IN ('org_admin','default_hub_org_setting')))
AND NOT EXISTS(SELECT 1 FROM user_role_permission x WHERE x.role_id=r.id AND x.permission_id=p.permission_id);
