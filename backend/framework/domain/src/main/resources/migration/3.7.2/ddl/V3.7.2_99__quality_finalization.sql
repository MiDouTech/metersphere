CREATE TABLE ai_execution_case_sync (
    execution_id VARCHAR(64) NOT NULL,
    execution_case_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    facts_json LONGTEXT NOT NULL,
    facts_hash VARCHAR(64) NOT NULL,
    business_status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(128),
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY(execution_id,execution_case_id),
    KEY idx_case_sync_task(task_id,execution_id),
    KEY idx_case_sync_retry(status,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
