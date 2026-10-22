CREATE TABLE agent_case_search_snapshot (
    id VARCHAR(50) PRIMARY KEY,
    token_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    total INT NOT NULL,
    create_time BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    KEY idx_search_snapshot_owner (token_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE agent_case_search_snapshot_item (
    snapshot_id VARCHAR(50) NOT NULL,
    position INT NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    PRIMARY KEY (snapshot_id, position),
    UNIQUE KEY uk_search_snapshot_case (snapshot_id, case_id),
    CONSTRAINT fk_case_search_snapshot FOREIGN KEY (snapshot_id) REFERENCES agent_case_search_snapshot(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
