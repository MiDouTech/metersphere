-- AI 生成用例独立权限：默认授予项目管理员全部能力，项目成员授予读写/生成/上传/保存（不含 CONFIG）
INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, p.permission_id
FROM user_role r
JOIN (
    SELECT 'FUNCTIONAL_CASE_AI:READ' AS permission_id UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:GENERATE' UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:UPLOAD' UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:SAVE' UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:CONFIG'
) p
WHERE r.id = 'project_admin'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_permission urp
      WHERE urp.role_id = r.id AND urp.permission_id = p.permission_id
  );

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, p.permission_id
FROM user_role r
JOIN (
    SELECT 'FUNCTIONAL_CASE_AI:READ' AS permission_id UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:GENERATE' UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:UPLOAD' UNION ALL
    SELECT 'FUNCTIONAL_CASE_AI:SAVE'
) p
WHERE r.id = 'project_member'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_permission urp
      WHERE urp.role_id = r.id AND urp.permission_id = p.permission_id
  );
