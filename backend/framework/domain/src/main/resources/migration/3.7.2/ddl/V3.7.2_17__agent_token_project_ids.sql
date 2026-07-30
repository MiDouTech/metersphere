SET SESSION innodb_lock_wait_timeout = 7200;

-- Agent Token 可访问项目白名单（JSON 数组）；NULL/空 = 全部项目
ALTER TABLE agent_token
    ADD COLUMN project_ids TEXT NULL COMMENT '可访问项目ID JSON数组，空=全部项目' AFTER project_id;

UPDATE agent_token
SET project_ids = CONCAT('[\"', project_id, '\"]')
WHERE project_id IS NOT NULL
  AND project_id <> ''
  AND (project_ids IS NULL OR project_ids = '');

SET SESSION innodb_lock_wait_timeout = DEFAULT;
