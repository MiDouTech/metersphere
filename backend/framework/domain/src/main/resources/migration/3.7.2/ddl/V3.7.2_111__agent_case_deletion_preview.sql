CREATE TABLE agent_case_deletion_preview (
    id VARCHAR(50) PRIMARY KEY,
    token_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    targets_json MEDIUMTEXT NOT NULL,
    tasks_json TEXT NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    decision VARCHAR(24),
    result_json MEDIUMTEXT,
    create_time BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    decision_time BIGINT,
    KEY idx_case_deletion_owner (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
