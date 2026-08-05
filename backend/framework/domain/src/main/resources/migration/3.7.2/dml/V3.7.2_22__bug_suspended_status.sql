-- Add default bug status: suspended.
-- Scope:
-- 1. Existing organizations with BUG status configuration.
-- 2. Existing projects, linked to their organization's suspended status through ref_id.
-- 3. Default transitions for organization and project status flows.

INSERT INTO status_item(id, name, scene, remark, internal, scope_type, ref_id, scope_id, pos)
SELECT UUID_SHORT(), 'bug_suspended', 'BUG', NULL, 1, 'ORGANIZATION', NULL, org_status.scope_id, COALESCE(MAX(scope_status.pos) + 1, 0)
FROM (
    SELECT DISTINCT scope_id
    FROM status_item
    WHERE scene = 'BUG'
      AND scope_type = 'ORGANIZATION'
) org_status
LEFT JOIN status_item scope_status
       ON scope_status.scope_id = org_status.scope_id
      AND scope_status.scene = 'BUG'
WHERE NOT EXISTS (
    SELECT 1
    FROM status_item exists_status
    WHERE exists_status.scope_id = org_status.scope_id
      AND exists_status.scene = 'BUG'
      AND exists_status.name = 'bug_suspended'
)
GROUP BY org_status.scope_id;

INSERT INTO status_item(id, name, scene, remark, internal, scope_type, ref_id, scope_id, pos)
SELECT UUID_SHORT(), 'bug_suspended', 'BUG', NULL, 1, 'PROJECT', org_suspended.id, project.id, COALESCE(MAX(scope_status.pos) + 1, 0)
FROM project
JOIN status_item org_suspended
  ON org_suspended.scope_id = project.organization_id
 AND org_suspended.scope_type = 'ORGANIZATION'
 AND org_suspended.scene = 'BUG'
 AND org_suspended.name = 'bug_suspended'
LEFT JOIN status_item scope_status
       ON scope_status.scope_id = project.id
      AND scope_status.scene = 'BUG'
WHERE NOT EXISTS (
    SELECT 1
    FROM status_item exists_status
    WHERE exists_status.scope_id = project.id
      AND exists_status.scene = 'BUG'
      AND exists_status.name = 'bug_suspended'
)
GROUP BY project.id, org_suspended.id;

INSERT INTO status_flow(id, from_id, to_id)
SELECT UUID_SHORT(), from_status.id, to_status.id
FROM (
    SELECT 'bug_new' AS from_name, 'bug_suspended' AS to_name
    UNION ALL
    SELECT 'bug_in_process', 'bug_suspended'
    UNION ALL
    SELECT 'bug_suspended', 'bug_in_process'
    UNION ALL
    SELECT 'bug_suspended', 'bug_rejected'
    UNION ALL
    SELECT 'bug_suspended', 'bug_closed'
) default_flow
JOIN status_item from_status
  ON from_status.name = default_flow.from_name
 AND from_status.scene = 'BUG'
JOIN status_item to_status
  ON to_status.name = default_flow.to_name
 AND to_status.scene = 'BUG'
 AND to_status.scope_id = from_status.scope_id
 AND to_status.scope_type = from_status.scope_type
WHERE NOT EXISTS (
    SELECT 1
    FROM status_flow exists_flow
    WHERE exists_flow.from_id = from_status.id
      AND exists_flow.to_id = to_status.id
);
