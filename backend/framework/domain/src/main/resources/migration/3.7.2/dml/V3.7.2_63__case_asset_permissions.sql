-- Independent case-asset permissions. Existing functional-case grants are copied once for compatibility;
-- administrators can subsequently manage the asset permissions independently.
INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), source.role_id, mapping.target_permission
FROM user_role_permission source
JOIN (
    SELECT 'FUNCTIONAL_CASE:READ' source_permission, 'CASE_ASSET:READ' target_permission
    UNION ALL SELECT 'FUNCTIONAL_CASE:READ+ADD', 'CASE_ASSET:READ+ADD'
    UNION ALL SELECT 'FUNCTIONAL_CASE:READ+UPDATE', 'CASE_ASSET:READ+UPDATE'
    UNION ALL SELECT 'FUNCTIONAL_CASE:READ+DELETE', 'CASE_ASSET:READ+DELETE'
    UNION ALL SELECT 'FUNCTIONAL_CASE:READ+IMPORT', 'CASE_ASSET:READ+IMPORT'
) mapping ON mapping.source_permission = source.permission_id
WHERE NOT EXISTS (
    SELECT 1 FROM user_role_permission existing
    WHERE existing.role_id = source.role_id AND existing.permission_id = mapping.target_permission
);

UPDATE permission_resource
SET permission_id = 'CASE_ASSET:READ', scope_type = 'ORGANIZATION', description = '组织级用例资产库'
WHERE code IN ('TEST_ASSET_CASES_PAGE', 'TEST_ASSET_CASE_PROJECT_TAB', 'TEST_ASSET_CASE_SYSTEM_TAB');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('CASE_ASSET_ADD_BUTTON', 'CASE_ASSET_ADD_BUTTON', '新建资产用例/目录', 'BUTTON', 'ORGANIZATION',
 'TEST_ASSET_CASES_PAGE', NULL, 'CASE_ASSET:READ+ADD', b'1', b'1', 567, b'1', '新建资产目录和资产用例'),
('CASE_ASSET_UPDATE_BUTTON', 'CASE_ASSET_UPDATE_BUTTON', '编辑资产用例/目录', 'BUTTON', 'ORGANIZATION',
 'TEST_ASSET_CASES_PAGE', NULL, 'CASE_ASSET:READ+UPDATE', b'1', b'1', 568, b'1', '编辑资产目录和资产用例'),
('CASE_ASSET_DELETE_BUTTON', 'CASE_ASSET_DELETE_BUTTON', '删除资产用例/目录', 'BUTTON', 'ORGANIZATION',
 'TEST_ASSET_CASES_PAGE', NULL, 'CASE_ASSET:READ+DELETE', b'1', b'1', 569, b'1', '软删除资产用例或空目录'),
('CASE_ASSET_IMPORT_BUTTON', 'CASE_ASSET_IMPORT_BUTTON', '导入资产用例', 'BUTTON', 'ORGANIZATION',
 'TEST_ASSET_CASES_PAGE', NULL, 'CASE_ASSET:READ+IMPORT', b'1', b'1', 570, b'1', '导入资产用例及补建目录')
ON DUPLICATE KEY UPDATE
 name = VALUES(name), permission_id = VALUES(permission_id), enabled = VALUES(enabled), description = VALUES(description);
