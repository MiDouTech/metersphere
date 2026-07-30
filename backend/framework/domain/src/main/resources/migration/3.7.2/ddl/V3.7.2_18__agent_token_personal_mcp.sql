SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE agent_token
    ADD COLUMN public_id VARCHAR(50) NULL COMMENT 'Token public id for msat_v2 lookup' AFTER name,
    ADD COLUMN secret_hash VARCHAR(128) NULL COMMENT 'SHA-256(token secret or raw token)' AFTER token_hash,
    ADD COLUMN display_prefix VARCHAR(80) NULL COMMENT 'Safe display prefix' AFTER secret_hash,
    ADD COLUMN client_type VARCHAR(50) NULL COMMENT 'Agent client type' AFTER scopes,
    ADD COLUMN status VARCHAR(20) NULL COMMENT 'ACTIVE / DISABLED / REVOKED' AFTER enable,
    ADD COLUMN last_used_at BIGINT NULL COMMENT 'Last invocation time' AFTER status,
    ADD COLUMN invocation_count BIGINT NULL COMMENT 'Invocation counter' AFTER last_used_at,
    ADD COLUMN last_ip VARCHAR(128) NULL COMMENT 'Last invocation IP' AFTER invocation_count,
    ADD COLUMN revoked_at BIGINT NULL COMMENT 'Revoked time' AFTER last_ip,
    ADD COLUMN revoked_by VARCHAR(50) NULL COMMENT 'Revoked by user id' AFTER revoked_at,
    ADD COLUMN token_version INT NULL COMMENT 'Token format version' AFTER revoked_by;

UPDATE agent_token
SET public_id = id
WHERE public_id IS NULL OR public_id = '';

UPDATE agent_token
SET secret_hash = token_hash
WHERE secret_hash IS NULL OR secret_hash = '';

UPDATE agent_token
SET display_prefix = token_prefix
WHERE display_prefix IS NULL OR display_prefix = '';

UPDATE agent_token
SET client_type = 'GENERIC',
    status = CASE WHEN enable = 1 THEN 'ACTIVE' ELSE 'DISABLED' END,
    invocation_count = 0,
    token_version = 1
WHERE token_version IS NULL;

CREATE INDEX idx_agent_token_public_id ON agent_token (public_id);
CREATE INDEX idx_agent_token_user ON agent_token (user_id, create_time);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
