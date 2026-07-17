SET SESSION innodb_lock_wait_timeout = 7200;

-- 为已有组织/项目缺陷模板补齐「缺陷类型」字段 bug_type（镜像 bug_degree 初始化方式）
-- 【注意】须人工审查后再上生产

-- 1) 组织级：每个 ORGANIZATION 范围的 bug_default 模板补字段（若尚无）
INSERT INTO custom_field(id, name, scene, `type`, remark, internal, scope_type, create_time, update_time, create_user, scope_id)
SELECT UUID_SHORT(), 'bug_type', 'BUG', 'SELECT', '', 1, cf.scope_type, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', cf.scope_id
FROM (
  SELECT DISTINCT scope_id, scope_type
  FROM custom_field
  WHERE scene = 'BUG' AND name = 'bug_degree' AND scope_type = 'ORGANIZATION'
) cf
WHERE NOT EXISTS (
  SELECT 1 FROM custom_field x
  WHERE x.scene = 'BUG' AND x.name = 'bug_type' AND x.scope_id = cf.scope_id AND x.scope_type = 'ORGANIZATION'
);

INSERT INTO custom_field_option (field_id, value, `text`, internal, pos)
SELECT cf.id, opt.value, opt.text, 1, opt.pos
FROM custom_field cf
JOIN (
  SELECT 'functional' AS value, '功能' AS text, 1 AS pos UNION ALL
  SELECT 'performance', '性能', 2 UNION ALL
  SELECT 'compatibility', '兼容性', 3 UNION ALL
  SELECT 'security', '安全', 4 UNION ALL
  SELECT 'other', '其他', 5
) opt
WHERE cf.scene = 'BUG' AND cf.name = 'bug_type' AND cf.scope_type = 'ORGANIZATION'
  AND NOT EXISTS (
    SELECT 1 FROM custom_field_option o WHERE o.field_id = cf.id AND o.value = opt.value
  );

INSERT INTO template_custom_field(id, field_id, template_id, required, pos, system_field, api_field_id, default_value)
SELECT UUID_SHORT(), cf.id, t.id, 0, 1, 0, NULL, NULL
FROM custom_field cf
JOIN template t ON t.scope_id = cf.scope_id AND t.scene = 'BUG' AND t.name = 'bug_default' AND t.scope_type = cf.scope_type
WHERE cf.scene = 'BUG' AND cf.name = 'bug_type' AND cf.scope_type = 'ORGANIZATION'
  AND NOT EXISTS (
    SELECT 1 FROM template_custom_field tcf WHERE tcf.field_id = cf.id AND tcf.template_id = t.id
  );

-- 2) 项目级
INSERT INTO custom_field(id, name, scene, `type`, remark, internal, scope_type, create_time, update_time, create_user, scope_id, ref_id)
SELECT UUID_SHORT(), 'bug_type', 'BUG', 'SELECT', '', 1, 'PROJECT', UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', cf.scope_id,
       (SELECT id FROM custom_field orgf WHERE orgf.name = 'bug_type' AND orgf.scope_type = 'ORGANIZATION' AND orgf.scope_id = (
          SELECT organization_id FROM project p WHERE p.id = cf.scope_id LIMIT 1
       ) LIMIT 1)
FROM (
  SELECT DISTINCT scope_id
  FROM custom_field
  WHERE scene = 'BUG' AND name = 'bug_degree' AND scope_type = 'PROJECT'
) cf
WHERE NOT EXISTS (
  SELECT 1 FROM custom_field x
  WHERE x.scene = 'BUG' AND x.name = 'bug_type' AND x.scope_id = cf.scope_id AND x.scope_type = 'PROJECT'
);

INSERT INTO custom_field_option (field_id, value, `text`, internal, pos)
SELECT cf.id, opt.value, opt.text, 1, opt.pos
FROM custom_field cf
JOIN (
  SELECT 'functional' AS value, '功能' AS text, 1 AS pos UNION ALL
  SELECT 'performance', '性能', 2 UNION ALL
  SELECT 'compatibility', '兼容性', 3 UNION ALL
  SELECT 'security', '安全', 4 UNION ALL
  SELECT 'other', '其他', 5
) opt
WHERE cf.scene = 'BUG' AND cf.name = 'bug_type' AND cf.scope_type = 'PROJECT'
  AND NOT EXISTS (
    SELECT 1 FROM custom_field_option o WHERE o.field_id = cf.id AND o.value = opt.value
  );

INSERT INTO template_custom_field(id, field_id, template_id, required, pos, system_field, api_field_id, default_value)
SELECT UUID_SHORT(), cf.id, t.id, 0, 1, 0, NULL, NULL
FROM custom_field cf
JOIN template t ON t.scope_id = cf.scope_id AND t.scene = 'BUG' AND t.name = 'bug_default' AND t.scope_type = 'PROJECT'
WHERE cf.scene = 'BUG' AND cf.name = 'bug_type' AND cf.scope_type = 'PROJECT'
  AND NOT EXISTS (
    SELECT 1 FROM template_custom_field tcf WHERE tcf.field_id = cf.id AND tcf.template_id = t.id
  );

SET SESSION innodb_lock_wait_timeout = DEFAULT;
