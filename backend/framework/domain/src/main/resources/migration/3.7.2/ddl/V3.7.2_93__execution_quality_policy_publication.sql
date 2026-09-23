-- Append-only publication facts. Existing releases are not backfilled with inferred history.
CREATE TABLE execution_quality_policy_publication (
    policy_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    previous_policy_id VARCHAR(64) NULL,
    previous_hash CHAR(64) NULL,
    published_hash CHAR(64) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    published_at BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    PRIMARY KEY (policy_id),
    KEY idx_quality_publication_project (project_id, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
