-- Provide a usable global bug workflow for installations that do not have one yet.
-- Fixed identifiers make the migration idempotent and keep later workflow copies stable.
SET @default_bug_flow_id = 'builtin_bug_workflow_v1';
SET @now_ms = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000;

-- Fail this migration immediately when the prerequisite V3.7.2.60 or V3.7.2.68 schema is missing.
SELECT workflow_id, workflow_version, expected_resolve_time FROM bug LIMIT 0;
SELECT version, lifecycle, published_default_scene FROM workflow_definition LIMIT 0;

-- An unusable published workflow blocks both publishing and bug creation. Archive it before installing the fallback.
UPDATE workflow_definition flow
LEFT JOIN (
    SELECT flow_id, SUM(CASE WHEN initial_status = b'1' AND enabled = b'1' THEN 1 ELSE 0 END) initial_count
    FROM status_item
    GROUP BY flow_id
) status_summary ON status_summary.flow_id = flow.id
SET flow.lifecycle = 'ARCHIVED', flow.default_flow = b'0', flow.update_time = @now_ms
WHERE flow.scene = 'BUG' AND flow.scope_type = 'SYSTEM' AND flow.scope_id = 'system'
  AND flow.lifecycle = 'PUBLISHED' AND flow.default_flow = b'1' AND flow.enabled = b'1'
  AND COALESCE(status_summary.initial_count, 0) <> 1;

INSERT INTO workflow_definition
    (id, code, name, scene, scope_type, scope_id, default_flow, enabled, description, version,
     lifecycle, published_time, published_by, source_flow_id, create_time, update_time)
SELECT @default_bug_flow_id, 'BUILTIN_BUG_WORKFLOW', '默认缺陷流程', 'BUG', 'SYSTEM', 'system', b'1', b'1',
       '系统内置流程，可复制为新版本后按实际需求调整状态、流转和职位授权。', 1,
       'PUBLISHED', @now_ms, 'system', NULL, @now_ms, @now_ms
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_definition
    WHERE scene = 'BUG' AND scope_type = 'SYSTEM' AND scope_id = 'system'
      AND lifecycle = 'PUBLISHED' AND default_flow = b'1' AND enabled = b'1'
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), update_time = VALUES(update_time);

INSERT IGNORE INTO status_item
    (id, flow_id, status_code, name, scene, remark, internal, scope_type, ref_id, scope_id, pos,
     initial_status, terminal_status, enabled)
SELECT status_id, @default_bug_flow_id, status_code, status_name, 'BUG', remark, b'1', 'SYSTEM', NULL, 'system', pos,
       initial_status, terminal_status, b'1'
FROM (
    SELECT 'builtin_bug_status_created' status_id, 'CREATED' status_code, '创建' status_name,
           '缺陷已创建，等待处理' remark, 1000 pos, b'1' initial_status, b'0' terminal_status
    UNION ALL SELECT 'builtin_bug_status_processing', 'PROCESSING', '处理中', '缺陷正在处理中', 2000, b'0', b'0'
    UNION ALL SELECT 'builtin_bug_status_resolved', 'RESOLVED', '已解决', '缺陷已解决，等待确认', 3000, b'0', b'0'
    UNION ALL SELECT 'builtin_bug_status_not_bug', 'NOT_A_BUG', '非问题', '确认不是缺陷', 4000, b'0', b'1'
    UNION ALL SELECT 'builtin_bug_status_closed', 'CLOSED', '已关闭', '缺陷已确认关闭', 5000, b'0', b'1'
    UNION ALL SELECT 'builtin_bug_status_suspended', 'SUSPENDED', '挂起', '缺陷暂时挂起', 6000, b'0', b'0'
    UNION ALL SELECT 'builtin_bug_status_reopened', 'REOPENED', '重新打开', '缺陷被重新打开', 7000, b'0', b'0'
) defaults
WHERE EXISTS (SELECT 1 FROM workflow_definition WHERE id = @default_bug_flow_id);

-- Repair the built-in flow if a previous interrupted deployment left its initial-state flags incomplete.
UPDATE status_item
SET initial_status = CASE WHEN id = 'builtin_bug_status_created' THEN b'1' ELSE b'0' END
WHERE flow_id = @default_bug_flow_id;

INSERT IGNORE INTO status_flow (id, flow_id, from_id, to_id, enabled)
SELECT transition_id, @default_bug_flow_id, from_id, to_id, b'1'
FROM (
    SELECT 'builtin_bug_flow_created_processing' transition_id,
           'builtin_bug_status_created' from_id, 'builtin_bug_status_processing' to_id
    UNION ALL SELECT 'builtin_bug_flow_created_not_bug', 'builtin_bug_status_created', 'builtin_bug_status_not_bug'
    UNION ALL SELECT 'builtin_bug_flow_created_suspended', 'builtin_bug_status_created', 'builtin_bug_status_suspended'
    UNION ALL SELECT 'builtin_bug_flow_processing_resolved', 'builtin_bug_status_processing', 'builtin_bug_status_resolved'
    UNION ALL SELECT 'builtin_bug_flow_processing_not_bug', 'builtin_bug_status_processing', 'builtin_bug_status_not_bug'
    UNION ALL SELECT 'builtin_bug_flow_processing_suspended', 'builtin_bug_status_processing', 'builtin_bug_status_suspended'
    UNION ALL SELECT 'builtin_bug_flow_resolved_closed', 'builtin_bug_status_resolved', 'builtin_bug_status_closed'
    UNION ALL SELECT 'builtin_bug_flow_resolved_reopened', 'builtin_bug_status_resolved', 'builtin_bug_status_reopened'
    UNION ALL SELECT 'builtin_bug_flow_not_bug_reopened', 'builtin_bug_status_not_bug', 'builtin_bug_status_reopened'
    UNION ALL SELECT 'builtin_bug_flow_closed_reopened', 'builtin_bug_status_closed', 'builtin_bug_status_reopened'
    UNION ALL SELECT 'builtin_bug_flow_suspended_reopened', 'builtin_bug_status_suspended', 'builtin_bug_status_reopened'
    UNION ALL SELECT 'builtin_bug_flow_reopened_processing', 'builtin_bug_status_reopened', 'builtin_bug_status_processing'
) defaults
WHERE EXISTS (SELECT 1 FROM workflow_definition WHERE id = @default_bug_flow_id);

INSERT IGNORE INTO workflow_role
    (id, flow_id, code, name, role_type, role_id, field_key, enabled, create_time, update_time)
SELECT role_id, @default_bug_flow_id, role_code, role_name, role_type, NULL, match_rule, b'1', @now_ms, @now_ms
FROM (
    SELECT 'builtin_bug_role_handler' role_id, 'CURRENT_HANDLER' role_code, '当前处理人' role_name,
           'FIELD_USER' role_type, 'handle_user' match_rule
    UNION ALL SELECT 'builtin_bug_role_creator', 'CREATOR', '创建者', 'FIELD_USER', 'create_user'
    UNION ALL SELECT 'builtin_bug_role_management', 'POSITION_MANAGEMENT', '管理岗', 'POSITION', '总经理|经理|主管|负责人|组长'
    UNION ALL SELECT 'builtin_bug_role_development', 'POSITION_DEVELOPMENT', '研发岗', 'POSITION', '开发|研发|工程师'
    UNION ALL SELECT 'builtin_bug_role_testing', 'POSITION_TESTING', '测试岗', 'POSITION', '测试|质量|QA'
    UNION ALL SELECT 'builtin_bug_role_product', 'POSITION_PRODUCT', '产品业务岗', 'POSITION', '产品|业务|运营'
    UNION ALL SELECT 'builtin_bug_role_other', 'POSITION_OTHER', '其他企微职位', 'POSITION', '*'
) defaults
WHERE EXISTS (SELECT 1 FROM workflow_definition WHERE id = @default_bug_flow_id);

-- Every built-in role can see every transition. Operability follows the default responsibility matrix.
INSERT IGNORE INTO status_flow_role_permission
    (id, flow_id, status_flow_id, workflow_role_id, visible, operable, enabled, create_time, update_time)
SELECT CONCAT('builtin_perm_', SUBSTRING(MD5(CONCAT(t.id, ':', r.id)), 1, 32)),
       @default_bug_flow_id, t.id, r.id, b'1',
       CASE
           WHEN r.code = 'POSITION_MANAGEMENT' THEN b'1'
           WHEN r.code IN ('POSITION_DEVELOPMENT', 'CURRENT_HANDLER') AND t.id IN (
               'builtin_bug_flow_created_processing', 'builtin_bug_flow_processing_resolved',
               'builtin_bug_flow_processing_suspended', 'builtin_bug_flow_reopened_processing') THEN b'1'
           WHEN r.code IN ('POSITION_TESTING', 'CREATOR') AND t.id IN (
               'builtin_bug_flow_created_not_bug', 'builtin_bug_flow_processing_not_bug',
               'builtin_bug_flow_resolved_closed', 'builtin_bug_flow_resolved_reopened',
               'builtin_bug_flow_not_bug_reopened', 'builtin_bug_flow_closed_reopened',
               'builtin_bug_flow_suspended_reopened') THEN b'1'
           WHEN r.code = 'POSITION_PRODUCT' AND t.id IN (
               'builtin_bug_flow_created_not_bug', 'builtin_bug_flow_created_suspended',
               'builtin_bug_flow_processing_not_bug', 'builtin_bug_flow_processing_suspended',
               'builtin_bug_flow_resolved_reopened', 'builtin_bug_flow_not_bug_reopened',
               'builtin_bug_flow_closed_reopened', 'builtin_bug_flow_suspended_reopened') THEN b'1'
           ELSE b'0'
       END,
       b'1', @now_ms, @now_ms
FROM status_flow t
JOIN workflow_role r ON r.flow_id = @default_bug_flow_id
WHERE t.flow_id = @default_bug_flow_id;

-- Promote a repaired built-in definition when there is still no usable published default.
UPDATE workflow_definition flow
SET flow.lifecycle = 'PUBLISHED', flow.default_flow = b'1', flow.enabled = b'1',
    flow.published_time = COALESCE(flow.published_time, @now_ms),
    flow.published_by = COALESCE(flow.published_by, 'system'), flow.update_time = @now_ms
WHERE flow.id = @default_bug_flow_id
  AND NOT EXISTS (
      SELECT 1 FROM (
          SELECT id FROM workflow_definition
          WHERE scene = 'BUG' AND scope_type = 'SYSTEM' AND scope_id = 'system'
            AND lifecycle = 'PUBLISHED' AND default_flow = b'1' AND enabled = b'1'
      ) published
      WHERE published.id <> @default_bug_flow_id
  );
