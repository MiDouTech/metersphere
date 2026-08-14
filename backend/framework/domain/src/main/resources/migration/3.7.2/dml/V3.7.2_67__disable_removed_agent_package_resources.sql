UPDATE permission_resource
SET enabled=b'0'
WHERE code IN ('SYSTEM_AGENT_PACKAGE_UPLOAD_BUTTON','SYSTEM_AGENT_PACKAGE_ENABLE_BUTTON','SYSTEM_AGENT_PACKAGE_DELETE_BUTTON');

INSERT INTO user_role_permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, pos, enabled, description)
VALUES
('TEST_ASSET_DATASETS_PAGE','TEST_ASSET_DATASETS_PAGE','测试数据','PAGE','PROJECT','TEST_ASSET_MENU',
 'testAssetDatasets','PROJECT_FILE_MANAGEMENT:READ',b'1',b'0',563,b'1','测试资产测试数据'),
('TEST_ASSET_ENVIRONMENTS_PAGE','TEST_ASSET_ENVIRONMENTS_PAGE','测试环境','PAGE','PROJECT','TEST_ASSET_MENU',
 'testAssetEnvironments','PROJECT_ENVIRONMENT:READ',b'1',b'0',564,b'1','测试资产测试环境')
ON DUPLICATE KEY UPDATE pos=VALUES(pos), route_name=VALUES(route_name), permission_id=VALUES(permission_id), enabled=b'1';
