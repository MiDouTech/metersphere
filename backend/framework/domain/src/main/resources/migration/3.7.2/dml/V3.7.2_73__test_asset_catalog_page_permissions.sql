INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('TEST_ASSET_COMMON_STEPS_PAGE','TEST_ASSET_COMMON_STEPS_PAGE','公共步骤','PAGE','PROJECT','TEST_ASSET_MENU',
 'testAssetCommonSteps','PROJECT_API_SCENARIO:READ',b'1',b'0',566,b'1','测试资产公共步骤'),
('TEST_ASSET_APIS_PAGE','TEST_ASSET_APIS_PAGE','接口资产','PAGE','PROJECT','TEST_ASSET_MENU',
 'testAssetApis','PROJECT_API_DEFINITION:READ',b'1',b'0',567,b'1','测试资产接口定义'),
('TEST_ASSET_EVIDENCE_PAGE','TEST_ASSET_EVIDENCE_PAGE','执行证据','PAGE','PROJECT','TEST_ASSET_MENU',
 'testAssetEvidence','AI_EXECUTION:READ',b'1',b'0',568,b'1','测试资产执行证据'),
('TEST_ASSET_BUGS_PAGE','TEST_ASSET_BUGS_PAGE','缺陷资产','PAGE','PROJECT','TEST_ASSET_MENU',
 'testAssetBugs','PROJECT_BUG:READ',b'1',b'0',569,b'1','测试资产缺陷')
ON DUPLICATE KEY UPDATE
parent_code='TEST_ASSET_MENU', enabled=b'1';

INSERT INTO user_role_ui_permission (id, role_id, resource_code, visible, operable)
SELECT UUID_SHORT(), 'permission_member', pr.code, b'1', b'0'
FROM permission_resource pr
WHERE pr.code IN ('TEST_ASSET_COMMON_STEPS_PAGE','TEST_ASSET_APIS_PAGE','TEST_ASSET_EVIDENCE_PAGE','TEST_ASSET_BUGS_PAGE')
  AND EXISTS (SELECT 1 FROM user_role WHERE id='permission_member')
  AND NOT EXISTS (SELECT 1 FROM user_role_ui_permission up
                  WHERE up.role_id='permission_member' AND up.resource_code=pr.code);
