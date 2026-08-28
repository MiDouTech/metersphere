SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE ai_execution_preflight
    ADD COLUMN actor_id VARCHAR(64) NOT NULL DEFAULT 'SYSTEM_MIGRATION' AFTER project_id,
    ADD COLUMN task_origin VARCHAR(32) NOT NULL DEFAULT 'PERSONAL_MCP' AFTER actor_id,
    ADD COLUMN request_hash VARCHAR(128) NOT NULL DEFAULT '' AFTER task_origin,
    ADD COLUMN request_json MEDIUMTEXT NULL AFTER request_hash,
    ADD COLUMN resolved_scope_json MEDIUMTEXT NULL AFTER checks_json,
    ADD COLUMN snapshot_json MEDIUMTEXT NULL AFTER resolved_scope_json,
    ADD COLUMN original_scope_count INT NOT NULL DEFAULT 0 AFTER snapshot_json,
    ADD COLUMN expanded_scope_count INT NOT NULL DEFAULT 0 AFTER original_scope_count,
    ADD COLUMN scope_expansion_rate DECIMAL(6,4) NOT NULL DEFAULT 0 AFTER expanded_scope_count,
    ADD INDEX idx_ai_preflight_actor (project_id, actor_id, expires_at);

-- V80 and V82 are applied in one release, but DDL is auto-committing in MySQL.
-- Explicitly backfill the two MEDIUMTEXT columns so interrupted/online upgrades
-- remain restart-safe before the application starts reading frozen requests.
UPDATE ai_execution_preflight
SET request_json = COALESCE(request_json, '{}'),
    resolved_scope_json = COALESCE(resolved_scope_json, '[]');

ALTER TABLE ai_execution_preflight
    MODIFY COLUMN request_json MEDIUMTEXT NOT NULL,
    MODIFY COLUMN resolved_scope_json MEDIUMTEXT NOT NULL;

ALTER TABLE ai_model_profile
    ADD COLUMN gateway_prompt_policy_id VARCHAR(128) NULL AFTER prompt_policy_id,
    ADD COLUMN gateway_capability_snapshot TEXT NULL AFTER required_capabilities,
    ADD COLUMN last_verify_message VARCHAR(1000) NULL AFTER last_verify_status;

ALTER TABLE ai_runner
    ADD COLUMN runner_type VARCHAR(32) NOT NULL DEFAULT 'BROWSER' AFTER contract_version,
    ADD COLUMN network_zone VARCHAR(64) NULL AFTER runner_type,
    ADD COLUMN browser_types TEXT NULL AFTER browser_capabilities,
    ADD COLUMN health_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' AFTER status,
    ADD INDEX idx_ai_runner_match (organization_id, runner_type, network_zone, status);

CREATE TABLE ai_business_flow
(
    id              VARCHAR(64)  NOT NULL,
    organization_id VARCHAR(64)  NOT NULL,
    project_id      VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    nodes_json      MEDIUMTEXT   NOT NULL,
    edges_json      MEDIUMTEXT   NOT NULL,
    entry_node_id   VARCHAR(64)  NOT NULL,
    exit_conditions TEXT         NOT NULL,
    allowed_actions TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    version         INT          NOT NULL DEFAULT 0,
    create_user     VARCHAR(64)  NULL,
    update_user     VARCHAR(64)  NULL,
    create_time     BIGINT       NOT NULL,
    update_time     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_business_flow_name (project_id,name),
    KEY idx_ai_business_flow_status (project_id,status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
