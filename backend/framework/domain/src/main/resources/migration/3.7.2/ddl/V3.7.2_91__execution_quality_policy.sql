-- Additive policy preparation only. Does not enable enforcement on existing tasks.
CREATE TABLE execution_quality_policy_project (
    project_id VARCHAR(64) NOT NULL,
    next_version INT NOT NULL DEFAULT 1,
    current_policy_id VARCHAR(64) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE execution_quality_policy (
    id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    version_no INT NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    status VARCHAR(24) NOT NULL,
    rules_json MEDIUMTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at BIGINT NOT NULL,
    published_by VARCHAR(64) NULL,
    published_at BIGINT NULL,
    change_reason VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_policy_version (project_id, version_no),
    KEY idx_quality_policy_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
