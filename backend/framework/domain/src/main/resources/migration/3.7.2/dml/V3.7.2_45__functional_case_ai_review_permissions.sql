-- Review is deliberately not granted to ordinary project members. Project
-- administrators can review; publishing still requires FUNCTIONAL_CASE_AI:SAVE.
INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), 'project_admin', 'FUNCTIONAL_CASE_AI:REVIEW'
WHERE NOT EXISTS (
    SELECT 1 FROM user_role_permission
    WHERE role_id = 'project_admin' AND permission_id = 'FUNCTIONAL_CASE_AI:REVIEW'
);

INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('FUNCTIONAL_CASE_AI_REVIEW_BUTTON', 'FUNCTIONAL_CASE_AI_REVIEW_BUTTON', 'AI 用例审核', 'BUTTON', 'PROJECT',
 'FUNCTIONAL_CASE_AI_GENERATE_TAB', NULL, 'FUNCTIONAL_CASE_AI:REVIEW', b'1', b'0', 523, b'1', '审核 AI 生成用例草稿'),
('FUNCTIONAL_CASE_AI_PUBLISH_BUTTON', 'FUNCTIONAL_CASE_AI_PUBLISH_BUTTON', '发布 AI 用例', 'BUTTON', 'PROJECT',
 'FUNCTIONAL_CASE_AI_GENERATE_TAB', NULL, 'FUNCTIONAL_CASE_AI:SAVE', b'1', b'0', 524, b'1', '发布已审核的 AI 用例');
