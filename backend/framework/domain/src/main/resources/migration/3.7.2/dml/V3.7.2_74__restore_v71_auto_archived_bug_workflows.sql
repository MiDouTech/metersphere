-- V3.7.2.71 installed a built-in fallback and archived an unusable active flow.
-- Workflow lifecycle is now explicitly manual. Restore only rows that can be
-- proven to have been archived by V71: V71 stamped them with the exact same
-- millisecond used as the built-in flow's published_time.
UPDATE workflow_definition flow
JOIN workflow_definition builtin_flow
  ON builtin_flow.id = _utf8mb4'builtin_bug_workflow_v1' COLLATE utf8mb4_general_ci
 AND builtin_flow.code = _utf8mb4'BUILTIN_BUG_WORKFLOW' COLLATE utf8mb4_general_ci
SET flow.lifecycle = 'PUBLISHED',
    flow.default_flow = b'0',
    flow.active_for_new = b'0',
    flow.update_time = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
WHERE flow.id <> builtin_flow.id
  AND flow.scene = 'BUG'
  AND flow.scope_type = 'SYSTEM'
  AND flow.scope_id = 'system'
  AND flow.lifecycle = 'ARCHIVED'
  AND flow.update_time = builtin_flow.published_time;
