-- Extend only: historical facts are not reinterpreted or backfilled.
ALTER TABLE ai_execution_step_result
    ADD COLUMN payload_hash VARCHAR(64) NULL COMMENT 'Canonical payload SHA-256; NULL for legacy facts',
    ADD COLUMN facts_version VARCHAR(32) NULL COMMENT 'Canonical facts protocol version';

CREATE TABLE ai_quality_evaluation (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    execution_id VARCHAR(64),
    step_id VARCHAR(64),
    stage VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    rule_code VARCHAR(128) NOT NULL,
    request_id VARCHAR(64),
    trace_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    contract_hash VARCHAR(64),
    policy_hash VARCHAR(64),
    payload_hash VARCHAR(64),
    KEY idx_quality_task_time(task_id,created_at),
    KEY idx_quality_project_time(project_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
