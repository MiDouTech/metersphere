-- 将权限资源目录与当前真实前端入口对齐：有真实路由的资源使用 route_name 自动治理，
-- 已被产品方案取消或从未存在独立入口的占位资源停用，避免出现“可配置但不生效”。
UPDATE permission_resource SET type = 'PAGE', route_name = 'workstationIndexWait'
WHERE code = 'WORKBENCH_TODO_TAB';
UPDATE permission_resource SET type = 'PAGE', route_name = 'workstationIndexFollowed'
WHERE code = 'WORKBENCH_FOLLOWED_TAB';
UPDATE permission_resource SET type = 'PAGE', route_name = 'workstationIndexCreated', name = '我创建的页面'
WHERE code = 'WORKBENCH_RECENT_TAB';
UPDATE permission_resource SET type = 'PAGE', route_name = 'projectManagementPermissionMenuManagement'
WHERE code = 'PROJECT_DETAIL_APP_TAB';
UPDATE permission_resource SET type = 'PAGE', route_name = 'settingSystemUser'
WHERE code = 'SYSTEM_USER_MANAGEMENT_TAB';
UPDATE permission_resource SET type = 'PAGE', route_name = 'settingSystemOrgStructure'
WHERE code = 'SYSTEM_ORG_STRUCTURE_TAB';
UPDATE permission_resource SET type = 'PAGE', route_name = 'settingSystemPluginManagement'
WHERE code = 'SYSTEM_CONFIG_PLUGIN_TAB';

UPDATE permission_resource SET parent_code = 'BUG_MANAGEMENT_PAGE'
WHERE code IN ('BUG_CREATE_BUTTON', 'BUG_BATCH_DELETE_BUTTON');
UPDATE permission_resource SET parent_code = 'BUG_DETAIL_BASE_INFO_TAB'
WHERE code = 'BUG_DETAIL_COMMENT_BUTTON';

INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
VALUES
('PERMISSION_FLOW_DELETE_BUTTON', 'PERMISSION_FLOW_DELETE_BUTTON', '删除流程按钮', 'BUTTON', 'SYSTEM', 'PERMISSION_FLOW_CONTROL_TAB', NULL, 'SYSTEM_PERMISSION_CONTROL:READ+DELETE', b'1', b'0', 742, b'1', '删除非默认流程'),
('PERMISSION_FLOW_ROLE_UPDATE_BUTTON', 'PERMISSION_FLOW_ROLE_UPDATE_BUTTON', '编辑流程角色按钮', 'BUTTON', 'SYSTEM', 'PERMISSION_FLOW_CONTROL_TAB', NULL, 'SYSTEM_PERMISSION_CONTROL:READ+UPDATE', b'1', b'0', 743, b'1', '编辑流程角色'),
('PERMISSION_FLOW_ROLE_DELETE_BUTTON', 'PERMISSION_FLOW_ROLE_DELETE_BUTTON', '删除流程角色按钮', 'BUTTON', 'SYSTEM', 'PERMISSION_FLOW_CONTROL_TAB', NULL, 'SYSTEM_PERMISSION_CONTROL:READ+DELETE', b'1', b'0', 744, b'1', '删除流程角色');

INSERT IGNORE INTO user_role_ui_permission (id, role_id, resource_code, visible, operable)
SELECT UUID_SHORT(), 'permission_member', pr.code, b'1', b'1'
FROM permission_resource pr
WHERE pr.code IN ('PERMISSION_FLOW_DELETE_BUTTON', 'PERMISSION_FLOW_ROLE_UPDATE_BUTTON', 'PERMISSION_FLOW_ROLE_DELETE_BUTTON')
  AND EXISTS (SELECT 1 FROM user_role WHERE id = 'permission_member');

UPDATE permission_resource
SET enabled = b'0', visible_default = b'0', operable_default = b'0'
WHERE code IN (
  'WORKBENCH_OVERVIEW_TAB', 'PROJECT_LIST_ALL_TAB', 'PROJECT_LIST_MY_TAB',
  'TEST_PLAN_MY_TAB', 'TEST_PLAN_ARCHIVED_TAB', 'BUG_LIST_TABLE_TAB', 'BUG_DETAIL_COMMENT_TAB',
  'FUNCTIONAL_CASE_REVIEW_TAB', 'FUNCTIONAL_CASE_REPORT_TAB',
  'API_DETAIL_PAGE', 'API_DETAIL_BASE_INFO_TAB', 'API_DETAIL_REQUEST_PARAM_TAB',
  'API_DETAIL_POST_PROCESSOR_TAB', 'API_DETAIL_ASSERTION_TAB',
  'SYSTEM_POSITION_MANAGEMENT_TAB', 'PERMISSION_COMPAT_TAB',
  'FUNCTIONAL_CASE_PROJECT_TAB', 'FUNCTIONAL_CASE_SYSTEM_TAB',
  'FUNCTIONAL_CASE_WORKSPACE_READ', 'FUNCTIONAL_CASE_WORKSPACE_ADD',
  'FUNCTIONAL_CASE_WORKSPACE_UPDATE', 'FUNCTIONAL_CASE_SYSTEM_CLASSIFY'
);
