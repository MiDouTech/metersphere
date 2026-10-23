CREATE TABLE agent_attachment_upload_intent (
    id VARCHAR(50) PRIMARY KEY,
    token_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    expected_size BIGINT NOT NULL,
    expected_sha256 CHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PREPARED',
    result_json TEXT,
    expires_at BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    UNIQUE KEY uk_attachment_intent_request (token_id, request_id),
    KEY idx_attachment_intent_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
