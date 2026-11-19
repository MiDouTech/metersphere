CREATE TABLE external_executor_evidence (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    encrypted_content MEDIUMTEXT NOT NULL,
    create_time BIGINT NOT NULL,
    UNIQUE KEY uk_external_evidence(session_id,request_key)
);
