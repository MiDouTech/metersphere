SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE ai_project_governance
    ADD COLUMN allowed_resource_types LONGTEXT DEFAULT NULL COMMENT 'JSON allowlist; defaults to MODEL_API only',
    ADD COLUMN allowed_agent_providers LONGTEXT DEFAULT NULL COMMENT 'JSON Agent provider allowlist',
    ADD COLUMN allow_personal_agent TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN allow_local_agent_tools TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN max_agent_concurrent_tasks INT NOT NULL DEFAULT 1,
    ADD COLUMN max_agent_execution_minutes INT NOT NULL DEFAULT 15,
    ADD COLUMN daily_agent_execution_limit INT NOT NULL DEFAULT 50;

CREATE TABLE IF NOT EXISTS ai_agent_usage (
    id                  VARCHAR(50) NOT NULL,
    project_id          VARCHAR(50) NOT NULL,
    user_id             VARCHAR(50) NOT NULL,
    conversation_id     VARCHAR(50) DEFAULT NULL,
    request_id          VARCHAR(64) NOT NULL,
    connection_id       VARCHAR(50) NOT NULL,
    device_id           VARCHAR(50) DEFAULT NULL,
    provider            VARCHAR(32) NOT NULL,
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    input_tokens        BIGINT NOT NULL DEFAULT 0,
    output_tokens       BIGINT NOT NULL DEFAULT 0,
    usage_estimated     TINYINT(1) NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL,
    error_code          VARCHAR(64) DEFAULT NULL,
    create_time         BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_usage_request (request_id),
    KEY idx_ai_agent_usage_project_time (project_id, create_time),
    KEY idx_ai_agent_usage_user_time (user_id, create_time),
    KEY idx_ai_agent_usage_connection_time (connection_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='User Agent execution usage, separate from platform API cost';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
