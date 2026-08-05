INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, p.permission_id
FROM user_role r
JOIN (
    SELECT 'AI_EXECUTION:READ' AS permission_id UNION ALL
    SELECT 'AI_EXECUTION:RUN' UNION ALL
    SELECT 'AI_EXECUTION:CANCEL' UNION ALL
    SELECT 'AI_EXECUTION:LOGIN' UNION ALL
    SELECT 'AI_EXECUTION:ADMIN'
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
    SELECT 'AI_EXECUTION:READ' AS permission_id UNION ALL
    SELECT 'AI_EXECUTION:RUN' UNION ALL
    SELECT 'AI_EXECUTION:CANCEL' UNION ALL
    SELECT 'AI_EXECUTION:LOGIN'
) p
WHERE r.id = 'project_member'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_permission urp
      WHERE urp.role_id = r.id AND urp.permission_id = p.permission_id
  );
