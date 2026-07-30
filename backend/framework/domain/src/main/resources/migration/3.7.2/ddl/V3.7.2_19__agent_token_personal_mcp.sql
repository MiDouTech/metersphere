SET SESSION innodb_lock_wait_timeout = 7200;

DROP PROCEDURE IF EXISTS add_agent_token_column_if_missing;
DROP PROCEDURE IF EXISTS add_agent_token_index_if_missing;

DELIMITER //

CREATE PROCEDURE add_agent_token_column_if_missing(
    IN column_name VARCHAR(64),
    IN alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_token'
          AND COLUMN_NAME = column_name
    ) THEN
        SET @ddl = alter_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE add_agent_token_index_if_missing(
    IN index_name VARCHAR(64),
    IN alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'agent_token'
          AND INDEX_NAME = index_name
    ) THEN
        SET @ddl = alter_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL add_agent_token_column_if_missing(
    'public_id',
    'ALTER TABLE agent_token ADD COLUMN public_id VARCHAR(50) NULL COMMENT ''Token public id for msat_v2 lookup'' AFTER name'
);
CALL add_agent_token_column_if_missing(
    'secret_hash',
    'ALTER TABLE agent_token ADD COLUMN secret_hash VARCHAR(128) NULL COMMENT ''SHA-256(token secret or raw token)'' AFTER token_hash'
);
CALL add_agent_token_column_if_missing(
    'display_prefix',
    'ALTER TABLE agent_token ADD COLUMN display_prefix VARCHAR(80) NULL COMMENT ''Safe display prefix'' AFTER secret_hash'
);
CALL add_agent_token_column_if_missing(
    'client_type',
    'ALTER TABLE agent_token ADD COLUMN client_type VARCHAR(50) NULL COMMENT ''Agent client type'' AFTER scopes'
);
CALL add_agent_token_column_if_missing(
    'status',
    'ALTER TABLE agent_token ADD COLUMN status VARCHAR(20) NULL COMMENT ''ACTIVE / DISABLED / REVOKED'' AFTER enable'
);
CALL add_agent_token_column_if_missing(
    'last_used_at',
    'ALTER TABLE agent_token ADD COLUMN last_used_at BIGINT NULL COMMENT ''Last invocation time'' AFTER status'
);
CALL add_agent_token_column_if_missing(
    'invocation_count',
    'ALTER TABLE agent_token ADD COLUMN invocation_count BIGINT NULL COMMENT ''Invocation counter'' AFTER last_used_at'
);
CALL add_agent_token_column_if_missing(
    'last_ip',
    'ALTER TABLE agent_token ADD COLUMN last_ip VARCHAR(128) NULL COMMENT ''Last invocation IP'' AFTER invocation_count'
);
CALL add_agent_token_column_if_missing(
    'revoked_at',
    'ALTER TABLE agent_token ADD COLUMN revoked_at BIGINT NULL COMMENT ''Revoked time'' AFTER last_ip'
);
CALL add_agent_token_column_if_missing(
    'revoked_by',
    'ALTER TABLE agent_token ADD COLUMN revoked_by VARCHAR(50) NULL COMMENT ''Revoked by user id'' AFTER revoked_at'
);
CALL add_agent_token_column_if_missing(
    'token_version',
    'ALTER TABLE agent_token ADD COLUMN token_version INT NULL COMMENT ''Token format version'' AFTER revoked_by'
);

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

CALL add_agent_token_index_if_missing(
    'idx_agent_token_public_id',
    'ALTER TABLE agent_token ADD INDEX idx_agent_token_public_id (public_id)'
);
CALL add_agent_token_index_if_missing(
    'idx_agent_token_user',
    'ALTER TABLE agent_token ADD INDEX idx_agent_token_user (user_id, create_time)'
);

DROP PROCEDURE IF EXISTS add_agent_token_column_if_missing;
DROP PROCEDURE IF EXISTS add_agent_token_index_if_missing;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
