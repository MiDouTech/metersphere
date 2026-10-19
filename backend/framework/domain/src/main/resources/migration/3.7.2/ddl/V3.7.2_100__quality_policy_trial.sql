CREATE TABLE ai_quality_policy_trial (
    id VARCHAR(64) PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL,
    policy_hash VARCHAR(64) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    summary_json LONGTEXT NOT NULL,
    sample_ids LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    KEY idx_quality_trial_policy(policy_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
