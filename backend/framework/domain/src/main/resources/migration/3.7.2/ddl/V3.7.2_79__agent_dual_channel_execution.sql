SET SESSION innodb_lock_wait_timeout = 7200;

-- Explicit task provenance and execution channel. Legacy routing columns remain during
-- the compatibility window, but new code must only route by these two columns.
ALTER TABLE ai_execution_task
    ADD COLUMN task_origin VARCHAR(32) NULL COMMENT 'PLATFORM_SCHEDULED/PLATFORM_MANUAL/PERSONAL_MCP' AFTER source,
    ADD COLUMN executor_channel VARCHAR(32) NULL COMMENT 'MODEL_API_RUNNER/EXTERNAL_MCP_AGENT' AFTER task_origin,
    ADD COLUMN current_execution_id VARCHAR(64) NULL COMMENT 'Current execution attempt id' AFTER runner_lease_id,
    ADD COLUMN plan_schema_version VARCHAR(32) NULL COMMENT 'Frozen execution plan schema version' AFTER provider_id,
    ADD COLUMN model_snapshot MEDIUMTEXT NULL COMMENT 'Frozen provider/model configuration without secrets' AFTER plan_schema_version,
    ADD COLUMN prompt_template_snapshot MEDIUMTEXT NULL COMMENT 'Frozen prompt template and version' AFTER model_snapshot,
    ADD COLUMN execution_parameter_snapshot MEDIUMTEXT NULL COMMENT 'Frozen browser/environment/runtime parameters' AFTER prompt_template_snapshot,
    ADD COLUMN trace_id VARCHAR(64) NULL COMMENT 'Task correlation id' AFTER execution_parameter_snapshot;

-- Historical platform PUSH tasks are deliberately rerouted to the controlled Runner
-- for future execution. Their old execution_mode/dispatch_mode values remain for audit.
UPDATE ai_execution_task
SET task_origin = CASE
        WHEN source LIKE 'TRIGGER:%' THEN 'PLATFORM_SCHEDULED'
        WHEN (source = 'AGENT_API' OR source = 'MCP')
             AND execution_mode = 'AGENT' AND dispatch_mode = 'PULL' THEN 'PERSONAL_MCP'
        ELSE 'PLATFORM_MANUAL'
    END,
    executor_channel = CASE
        WHEN (source = 'AGENT_API' OR source = 'MCP')
             AND execution_mode = 'AGENT' AND dispatch_mode = 'PULL' THEN 'EXTERNAL_MCP_AGENT'
        ELSE 'MODEL_API_RUNNER'
    END
WHERE task_origin IS NULL OR executor_channel IS NULL;

ALTER TABLE ai_execution_task
    MODIFY COLUMN task_origin VARCHAR(32) NOT NULL COMMENT 'PLATFORM_SCHEDULED/PLATFORM_MANUAL/PERSONAL_MCP',
    MODIFY COLUMN executor_channel VARCHAR(32) NOT NULL COMMENT 'MODEL_API_RUNNER/EXTERNAL_MCP_AGENT',
    ADD INDEX idx_ai_execution_task_channel (organization_id, executor_channel, status, attempt_count, create_time),
    ADD INDEX idx_ai_execution_task_origin (project_id, task_origin, create_time),
    ADD INDEX idx_ai_execution_task_execution (current_execution_id),
    ADD INDEX idx_ai_execution_task_trace (trace_id);

CREATE TABLE ai_execution_attempt
(
    id               VARCHAR(64)  NOT NULL,
    task_id          VARCHAR(50)  NOT NULL,
    attempt_no       INT          NOT NULL,
    executor_channel VARCHAR(32)  NOT NULL,
    executor_type    VARCHAR(32)  NOT NULL COMMENT 'RUNNER/MCP_AGENT',
    executor_id      VARCHAR(128) NULL,
    lease_id         VARCHAR(50)  NULL,
    status           VARCHAR(32)  NOT NULL COMMENT 'CLAIMED/RUNNING/SUCCEEDED/FAILED/CANCELED/EXPIRED/RELEASED',
    trace_id         VARCHAR(64)  NOT NULL,
    error_code       VARCHAR(64)  NULL,
    error_message    VARCHAR(1000) NULL,
    start_time       BIGINT       NOT NULL,
    finish_time      BIGINT       NULL,
    create_time      BIGINT       NOT NULL,
    update_time      BIGINT       NOT NULL,
    version          INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_execution_attempt_no (task_id, attempt_no),
    KEY idx_ai_execution_attempt_task_status (task_id, status),
    KEY idx_ai_execution_attempt_lease (lease_id),
    KEY idx_ai_execution_attempt_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci
  COMMENT 'One immutable execution attempt for an AI execution task';

ALTER TABLE ai_runner_lease
    DROP INDEX uk_ai_runner_lease_active_task,
    MODIFY COLUMN runner_id VARCHAR(50) NULL,
    ADD COLUMN execution_id VARCHAR(64) NULL AFTER task_id,
    ADD COLUMN executor_channel VARCHAR(32) NULL AFTER execution_id,
    ADD COLUMN lease_owner_type VARCHAR(32) NULL COMMENT 'RUNNER/MCP_TOKEN' AFTER executor_id,
    ADD COLUMN lease_owner_id VARCHAR(128) NULL AFTER lease_owner_type,
    ADD COLUMN released_reason VARCHAR(1000) NULL AFTER last_event_sequence,
    ADD COLUMN closed_at BIGINT NULL AFTER released_reason,
    ADD COLUMN active_task_key VARCHAR(50)
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN task_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_ai_runner_lease_active_task (active_task_key),
    ADD INDEX idx_ai_runner_lease_execution (execution_id),
    ADD INDEX idx_ai_runner_lease_owner (lease_owner_type, lease_owner_id, status);

UPDATE ai_runner_lease
SET executor_channel = CASE WHEN executor_type = 'AGENT' THEN 'EXTERNAL_MCP_AGENT' ELSE 'MODEL_API_RUNNER' END,
    lease_owner_type = CASE WHEN executor_type = 'AGENT' THEN 'MCP_TOKEN' ELSE 'RUNNER' END,
    lease_owner_id = COALESCE(executor_id, runner_id)
WHERE executor_channel IS NULL;

ALTER TABLE ai_runner_lease
    MODIFY COLUMN executor_channel VARCHAR(32) NOT NULL,
    MODIFY COLUMN lease_owner_type VARCHAR(32) NOT NULL,
    MODIFY COLUMN lease_owner_id VARCHAR(128) NOT NULL;

ALTER TABLE ai_execution_event
    ADD COLUMN execution_id VARCHAR(64) NULL AFTER task_id,
    ADD COLUMN lease_id VARCHAR(50) NULL AFTER execution_id,
    ADD COLUMN actor_type VARCHAR(32) NULL AFTER event_type,
    ADD COLUMN actor_id VARCHAR(128) NULL AFTER actor_type,
    ADD COLUMN tool_name VARCHAR(128) NULL AFTER actor_id,
    ADD COLUMN request_id VARCHAR(128) NULL AFTER tool_name,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER request_id,
    ADD COLUMN payload MEDIUMTEXT NULL AFTER sanitized_metadata,
    ADD INDEX idx_ai_execution_event_execution (execution_id, sequence),
    ADD INDEX idx_ai_execution_event_trace (trace_id),
    ADD INDEX idx_ai_execution_event_request (request_id);

ALTER TABLE ai_execution_artifact
    MODIFY COLUMN file_id VARCHAR(50) NULL,
    MODIFY COLUMN file_name VARCHAR(255) NULL,
    MODIFY COLUMN storage_folder VARCHAR(1024) NULL,
    MODIFY COLUMN content_type VARCHAR(128) NULL,
    MODIFY COLUMN size_bytes BIGINT NULL,
    MODIFY COLUMN sha256 VARCHAR(64) NULL,
    ADD COLUMN execution_id VARCHAR(64) NULL AFTER task_id,
    ADD COLUMN lease_id VARCHAR(50) NULL AFTER execution_id,
    ADD COLUMN upload_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' AFTER status,
    ADD COLUMN expected_size BIGINT NULL AFTER upload_status,
    ADD COLUMN expected_sha256 VARCHAR(64) NULL AFTER expected_size,
    ADD COLUMN expected_content_type VARCHAR(128) NULL AFTER expected_sha256,
    ADD COLUMN upload_token_hash VARCHAR(128) NULL AFTER expected_content_type,
    ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER upload_token_hash,
    ADD COLUMN prepared_at BIGINT NULL AFTER idempotency_key,
    ADD COLUMN committed_at BIGINT NULL AFTER prepared_at,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER committed_at,
    ADD INDEX idx_ai_execution_artifact_execution (execution_id),
    ADD UNIQUE KEY uk_ai_execution_artifact_prepare (task_id, idempotency_key);

CREATE TABLE ai_execution_step_result
(
    id               VARCHAR(64)   NOT NULL,
    task_id          VARCHAR(50)   NOT NULL,
    execution_id     VARCHAR(64)   NOT NULL,
    lease_id         VARCHAR(50)   NOT NULL,
    step_id          VARCHAR(50)   NOT NULL,
    attempt          INT           NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    input_snapshot   MEDIUMTEXT    NULL,
    output_summary   MEDIUMTEXT    NULL,
    assertion_result MEDIUMTEXT    NULL,
    error_code       VARCHAR(64)   NULL,
    error_message    VARCHAR(1000) NULL,
    artifact_ids     TEXT          NULL,
    request_id       VARCHAR(128)  NOT NULL,
    trace_id         VARCHAR(64)   NOT NULL,
    started_at       BIGINT        NULL,
    finished_at      BIGINT        NULL,
    create_time      BIGINT        NOT NULL,
    create_user      VARCHAR(128)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_execution_step_result_request (execution_id, step_id, request_id),
    KEY idx_ai_execution_step_result_task (task_id, execution_id),
    KEY idx_ai_execution_step_result_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci
  COMMENT 'Per-attempt immutable execution step results';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
