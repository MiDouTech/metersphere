CREATE TABLE functional_import_effect_outbox (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    effect_type VARCHAR(32) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    KEY idx_import_effect_due (state, next_retry_at, id),
    KEY idx_import_effect_retention (state, update_time, id),
    KEY idx_import_effect_project (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
