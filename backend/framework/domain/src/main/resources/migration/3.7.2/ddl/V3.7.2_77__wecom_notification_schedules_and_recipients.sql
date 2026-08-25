CREATE TABLE IF NOT EXISTS wecom_notification_schedule (
    id VARCHAR(50) NOT NULL,
    rule_id VARCHAR(50) NOT NULL,
    cycle_type VARCHAR(16) NOT NULL,
    weekdays VARCHAR(64) NULL,
    execution_time VARCHAR(5) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled BIT NOT NULL DEFAULT b'1',
    next_fire_time BIGINT NULL,
    last_fire_time BIGINT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    create_user VARCHAR(50) NOT NULL,
    update_user VARCHAR(50) NOT NULL,
    enabled_definition VARCHAR(255) GENERATED ALWAYS AS (
        CASE WHEN enabled = b'1'
             THEN CONCAT(cycle_type, '|', COALESCE(weekdays, ''), '|', execution_time, '|', timezone)
             ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wecom_schedule_enabled_definition (rule_id, enabled_definition),
    KEY idx_wecom_schedule_definition (rule_id, cycle_type, weekdays, execution_time, timezone, enabled),
    KEY idx_wecom_schedule_rule (rule_id, enabled),
    KEY idx_wecom_schedule_next_fire (enabled, next_fire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企业微信通知定时计划';

CREATE TABLE IF NOT EXISTS wecom_recipient_position (
    id VARCHAR(50) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wecom_recipient_position_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企微通知岗位稳定标识';

INSERT IGNORE INTO wecom_recipient_position(id, normalized_name, display_name, create_time, update_time)
SELECT UUID_SHORT(), LOWER(TRIM(position)), MIN(TRIM(position)),
       UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000,
       UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
FROM user
WHERE position IS NOT NULL AND TRIM(position) <> ''
GROUP BY LOWER(TRIM(position));

CREATE TABLE IF NOT EXISTS wecom_notification_schedule_execution (
    id VARCHAR(50) NOT NULL,
    schedule_id VARCHAR(50) NOT NULL,
    rule_id VARCHAR(50) NOT NULL,
    trigger_mode VARCHAR(32) NOT NULL,
    trigger_user_id VARCHAR(50) NULL,
    planned_fire_time BIGINT NOT NULL,
    actual_start_time BIGINT NOT NULL,
    actual_finish_time BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 1,
    max_attempts INT NOT NULL DEFAULT 4,
    next_retry_at BIGINT NULL,
    target_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(1000) NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wecom_schedule_execution (schedule_id, trigger_mode, planned_fire_time),
    KEY idx_wecom_schedule_execution_retry (status, next_retry_at),
    KEY idx_wecom_schedule_execution_rule_time (rule_id, planned_fire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='企微通知计划执行记录';

ALTER TABLE wecom_notification_rule
    ADD COLUMN config_status VARCHAR(32) NOT NULL DEFAULT 'READY' AFTER enabled,
    ADD COLUMN config_warning VARCHAR(500) NULL AFTER config_status;

ALTER TABLE wecom_notification_outbox
    ADD COLUMN trigger_mode VARCHAR(32) NULL AFTER event_id,
    ADD COLUMN trigger_user_id VARCHAR(50) NULL AFTER trigger_mode,
    ADD COLUMN schedule_id VARCHAR(50) NULL AFTER trigger_user_id,
    ADD COLUMN recipient_user_id VARCHAR(50) NULL AFTER schedule_id,
    ADD COLUMN configured_recipient_spec LONGTEXT NULL AFTER recipient_user_id,
    ADD COLUMN resolved_user_ids LONGTEXT NULL AFTER configured_recipient_spec,
    ADD COLUMN mentioned_user_ids LONGTEXT NULL AFTER resolved_user_ids,
    ADD COLUMN partial_failure_count INT NOT NULL DEFAULT 0 AFTER mentioned_user_ids,
    ADD COLUMN partial_failure_detail LONGTEXT NULL AFTER partial_failure_count;

UPDATE wecom_notification_rule
SET recipient_spec = JSON_REMOVE(CAST(recipient_spec AS JSON), '$.userGroupIds', '$.projectRoleIds'),
    enabled = b'0',
    config_status = 'NEEDS_ATTENTION',
    config_warning = '原用户组配置已清除，请重新选择岗位或角色后启用',
    update_time = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
WHERE JSON_VALID(recipient_spec)
  AND (JSON_LENGTH(JSON_EXTRACT(recipient_spec, '$.userGroupIds')) > 0
       OR JSON_LENGTH(JSON_EXTRACT(recipient_spec, '$.projectRoleIds')) > 0);

UPDATE wecom_notification_timer t
JOIN wecom_notification_rule r ON r.id = t.rule_id
SET t.status = 'CANCELLED', t.lease_until = NULL, t.update_time = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000
WHERE r.config_status = 'NEEDS_ATTENTION'
  AND t.status IN ('WAITING', 'PROCESSING');
