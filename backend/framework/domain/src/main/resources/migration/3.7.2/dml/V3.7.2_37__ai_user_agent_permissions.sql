-- Personal Agent permissions are attached to all built-in roles that already have personal API key access.
INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), role_id, permission_id
FROM (
    SELECT DISTINCT urp.role_id, p.permission_id
    FROM user_role_permission urp
    JOIN (
        SELECT 'SYSTEM_PERSONAL_AI_AGENT:READ' AS permission_id UNION ALL
        SELECT 'SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT' UNION ALL
        SELECT 'SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE'
    ) p
    WHERE urp.permission_id = 'SYSTEM_PERSONAL_API_KEY:READ'
) source
WHERE NOT EXISTS (
    SELECT 1 FROM user_role_permission existing
    WHERE existing.role_id = source.role_id AND existing.permission_id = source.permission_id
);
