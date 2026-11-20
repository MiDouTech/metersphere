CREATE TABLE external_executor_session (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    executor_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    token_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    heartbeat_at BIGINT NOT NULL,
    create_time BIGINT NOT NULL,
    UNIQUE KEY uk_external_session (project_id, token_id, request_key)
);
CREATE TABLE external_executor_context (
    session_id VARCHAR(64) NOT NULL,
    asset_id VARCHAR(64) NOT NULL,
    version_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (session_id, asset_id)
);
CREATE TABLE external_executor_event (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    error_category VARCHAR(64) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    summary TEXT NOT NULL,
    evidence_id VARCHAR(64) NULL,
    create_time BIGINT NOT NULL,
    UNIQUE KEY uk_external_event (session_id, request_key),
    KEY idx_external_event_history(session_id,create_time,id)
);
CREATE TABLE external_executor_command (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    instruction TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    response_category VARCHAR(64) NULL,
    create_time BIGINT NOT NULL,
    responded_at BIGINT NULL,
    KEY idx_external_command_pending(session_id,status,create_time,id)
);
