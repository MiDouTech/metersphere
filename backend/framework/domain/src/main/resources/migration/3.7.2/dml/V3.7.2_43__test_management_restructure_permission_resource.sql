INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
VALUES
('FUNCTIONAL_CASE_PROJECT_TAB', 'FUNCTIONAL_CASE_PROJECT_TAB', 'Functional case project view tab', 'TAB', 'PROJECT', 'FUNCTIONAL_CASE_CASE_TAB', NULL, 'FUNCTIONAL_CASE:READ', b'1', b'0', 518, b'1', 'Functional cases organized by project/module'),
('FUNCTIONAL_CASE_SYSTEM_TAB', 'FUNCTIONAL_CASE_SYSTEM_TAB', 'Functional case system view tab', 'TAB', 'PROJECT', 'FUNCTIONAL_CASE_CASE_TAB', NULL, 'FUNCTIONAL_CASE:READ', b'1', b'0', 519, b'1', 'Functional cases organized by business system/system module'),
('FUNCTIONAL_CASE_WORKSPACE_READ', 'FUNCTIONAL_CASE_WORKSPACE_READ', 'Workspace case read', 'BUTTON', 'PROJECT', 'FUNCTIONAL_CASE_CASE_TAB', NULL, 'FUNCTIONAL_CASE_WORKSPACE:READ', b'1', b'0', 523, b'1', 'Read workspace-level cases without project binding'),
('FUNCTIONAL_CASE_WORKSPACE_ADD', 'FUNCTIONAL_CASE_WORKSPACE_ADD', 'Workspace case add', 'BUTTON', 'PROJECT', 'FUNCTIONAL_CASE_CASE_TAB', NULL, 'FUNCTIONAL_CASE_WORKSPACE:ADD', b'1', b'1', 524, b'1', 'Add workspace-level cases without project binding'),
('FUNCTIONAL_CASE_WORKSPACE_UPDATE', 'FUNCTIONAL_CASE_WORKSPACE_UPDATE', 'Workspace case update', 'BUTTON', 'PROJECT', 'FUNCTIONAL_CASE_CASE_TAB', NULL, 'FUNCTIONAL_CASE_WORKSPACE:UPDATE', b'1', b'1', 525, b'1', 'Update workspace-level cases without project binding'),
('FUNCTIONAL_CASE_SYSTEM_CLASSIFY', 'FUNCTIONAL_CASE_SYSTEM_CLASSIFY', 'Functional case system classify', 'BUTTON', 'PROJECT', 'FUNCTIONAL_CASE_SYSTEM_TAB', NULL, 'FUNCTIONAL_CASE_SYSTEM:CLASSIFY', b'1', b'1', 526, b'1', 'Maintain functional case business system and system module classification'),
('TEST_PLAN_REVIEW_TAB', 'TEST_PLAN_REVIEW_TAB', 'Test plan case review tab', 'TAB', 'PROJECT', 'TEST_PLAN_LIST_PAGE', NULL, 'CASE_REVIEW:READ', b'1', b'0', 330, b'1', 'Case review entry migrated to test plan module'),
('TEST_PLAN_TEST_REPORT_TAB', 'TEST_PLAN_TEST_REPORT_TAB', 'Test plan functional test report tab', 'TAB', 'PROJECT', 'TEST_PLAN_LIST_PAGE', NULL, 'PROJECT_TEST_PLAN_REPORT:READ', b'1', b'0', 331, b'1', 'Functional test report entry migrated to test plan module'),
('PROJECT_SWITCH', 'PROJECT_SWITCH', 'Project enter/switch', 'BUTTON', 'PROJECT', 'PROJECT_DETAIL_PAGE', NULL, 'PROJECT_BASE_INFO:READ', b'1', b'1', 226, b'1', 'Enter or switch project from project list/detail');

UPDATE permission_resource
SET enabled = b'1', visible_default = b'1'
WHERE code IN ('PROJECT_LIST_PAGE', 'PROJECT_DETAIL_PAGE');

UPDATE permission_resource
SET enabled = b'0', visible_default = b'0'
WHERE code IN ('FUNCTIONAL_CASE_REVIEW_TAB', 'FUNCTIONAL_CASE_REPORT_TAB', 'TEST_PLAN_DETAIL_REPORT_TAB');

INSERT IGNORE INTO user_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), ur.id, p.permission_id
FROM user_role ur
JOIN permission_resource p ON p.code IN (
    'FUNCTIONAL_CASE_PROJECT_TAB',
    'FUNCTIONAL_CASE_SYSTEM_TAB',
    'FUNCTIONAL_CASE_WORKSPACE_READ',
    'FUNCTIONAL_CASE_WORKSPACE_ADD',
    'FUNCTIONAL_CASE_WORKSPACE_UPDATE',
    'FUNCTIONAL_CASE_SYSTEM_CLASSIFY',
    'TEST_PLAN_REVIEW_TAB',
    'TEST_PLAN_TEST_REPORT_TAB',
    'PROJECT_SWITCH'
)
WHERE ur.id = 'admin'
  AND p.permission_id IS NOT NULL
  AND p.permission_id <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM user_role_permission urp
      WHERE urp.role_id = ur.id
        AND urp.permission_id = p.permission_id
  );
