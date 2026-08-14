-- 补齐本次信息架构改造页面的 UI 权限资源。
-- 使用稳定 code 和 INSERT IGNORE，保证升级重试幂等。
INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
VALUES
('TEST_ASSET_MENU', 'TEST_ASSET_MENU', '测试资产', 'MENU', 'PROJECT', NULL, 'testAsset', 'FUNCTIONAL_CASE:READ', b'1', b'0', 560, b'1', '测试资产模块入口'),
('TEST_ASSET_DOCUMENTS_PAGE', 'TEST_ASSET_DOCUMENTS_PAGE', '业务文档', 'PAGE', 'PROJECT', 'TEST_ASSET_MENU', 'testAssetDocuments', NULL, b'1', b'0', 561, b'1', '测试资产业务文档'),
('TEST_ASSET_VERSIONS_PAGE', 'TEST_ASSET_VERSIONS_PAGE', '资产版本', 'PAGE', 'PROJECT', 'TEST_ASSET_MENU', 'testAssetVersions', NULL, b'1', b'0', 562, b'1', '测试资产版本'),
('TEST_ASSET_RELATIONS_PAGE', 'TEST_ASSET_RELATIONS_PAGE', '关联追溯', 'PAGE', 'PROJECT', 'TEST_ASSET_MENU', 'testAssetRelations', NULL, b'1', b'0', 563, b'1', '测试资产关联追溯'),
('TEST_ASSET_CASES_PAGE', 'TEST_ASSET_CASES_PAGE', '用例资产', 'PAGE', 'PROJECT', 'TEST_ASSET_MENU', 'testAssetCases', 'FUNCTIONAL_CASE:READ', b'1', b'0', 564, b'1', '跨项目只读用例资产'),
('TEST_ASSET_CASE_PROJECT_TAB', 'TEST_ASSET_CASE_PROJECT_TAB', '用例资产项目页签', 'TAB', 'PROJECT', 'TEST_ASSET_CASES_PAGE', 'testAssetCasesProject', 'FUNCTIONAL_CASE:READ', b'1', b'0', 565, b'1', '按项目查看用例资产'),
('TEST_ASSET_CASE_SYSTEM_TAB', 'TEST_ASSET_CASE_SYSTEM_TAB', '用例资产系统页签', 'TAB', 'PROJECT', 'TEST_ASSET_CASES_PAGE', 'testAssetCasesSystem', NULL, b'1', b'0', 566, b'1', '系统级用例资产预留空页'),

('AGENT_MENU', 'AGENT_MENU', 'Agent', 'MENU', 'SYSTEM', NULL, 'agent', NULL, b'1', b'0', 650, b'1', 'Agent 模块入口'),
('AGENT_LIST_PAGE', 'AGENT_LIST_PAGE', 'Agent 列表', 'PAGE', 'SYSTEM', 'AGENT_MENU', 'agentList', 'AI_EXECUTION:READ', b'1', b'0', 651, b'1', 'Agent 列表'),
('AGENT_CAPABILITY_PAGE', 'AGENT_CAPABILITY_PAGE', '能力与授权', 'PAGE', 'SYSTEM', 'AGENT_MENU', 'agentCapability', 'AI_EXECUTION:READ', b'1', b'0', 652, b'1', 'Agent 能力与授权'),
('AGENT_QUEUE_PAGE', 'AGENT_QUEUE_PAGE', '调度队列', 'PAGE', 'SYSTEM', 'AGENT_MENU', 'agentQueue', 'AI_EXECUTION:READ', b'1', b'0', 653, b'1', 'Agent 调度队列'),
('AGENT_EVALUATION_PAGE', 'AGENT_EVALUATION_PAGE', '执行评价', 'PAGE', 'SYSTEM', 'AGENT_MENU', 'agentEvaluation', 'AI_EXECUTION:READ', b'1', b'0', 654, b'1', 'Agent 执行评价'),
('AGENT_INTEGRATION_PAGE', 'AGENT_INTEGRATION_PAGE', 'Agent 集成', 'PAGE', 'SYSTEM', 'AGENT_MENU', 'agentAccess', 'SYSTEM_PERSONAL_AI_AGENT:READ', b'1', b'0', 655, b'1', 'Agent Token、技能包与安装包集成入口');

-- 原项目列表资源指向旧基本信息路由，改为真实列表路由。
UPDATE permission_resource
SET route_name = 'projectManagementProjects',
    name = '项目列表页',
    description = '当前用户可访问项目列表'
WHERE code = 'PROJECT_LIST_PAGE';

-- 成员角色在本次迁移中初始等同管理员，新增页面资源也应首次默认可见。
INSERT INTO user_role_ui_permission (id, role_id, resource_code, visible, operable)
SELECT UUID_SHORT(), 'permission_member', pr.code, b'1',
       IF(pr.type IN ('BUTTON', 'API'), b'1', b'0')
FROM permission_resource pr
WHERE pr.code IN (
    'TEST_ASSET_MENU', 'TEST_ASSET_DOCUMENTS_PAGE', 'TEST_ASSET_VERSIONS_PAGE',
    'TEST_ASSET_RELATIONS_PAGE', 'TEST_ASSET_CASES_PAGE', 'TEST_ASSET_CASE_PROJECT_TAB',
    'TEST_ASSET_CASE_SYSTEM_TAB', 'AGENT_MENU', 'AGENT_LIST_PAGE', 'AGENT_CAPABILITY_PAGE',
    'AGENT_QUEUE_PAGE', 'AGENT_EVALUATION_PAGE', 'AGENT_INTEGRATION_PAGE'
)
AND EXISTS (SELECT 1 FROM user_role WHERE id = 'permission_member')
AND NOT EXISTS (
    SELECT 1 FROM user_role_ui_permission up
    WHERE up.role_id = 'permission_member' AND up.resource_code = pr.code
);
