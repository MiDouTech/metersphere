SET SESSION innodb_lock_wait_timeout = 7200;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'public_id') = 0,
    'ALTER TABLE agent_token ADD COLUMN public_id VARCHAR(50) NULL COMMENT ''Token public id for msat_v2 lookup'' AFTER name',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'secret_hash') = 0,
    'ALTER TABLE agent_token ADD COLUMN secret_hash VARCHAR(128) NULL COMMENT ''SHA-256(token secret or raw token)'' AFTER token_hash',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'display_prefix') = 0,
    'ALTER TABLE agent_token ADD COLUMN display_prefix VARCHAR(80) NULL COMMENT ''Safe display prefix'' AFTER secret_hash',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'client_type') = 0,
    'ALTER TABLE agent_token ADD COLUMN client_type VARCHAR(50) NULL COMMENT ''Agent client type'' AFTER scopes',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE agent_token ADD COLUMN status VARCHAR(20) NULL COMMENT ''ACTIVE / DISABLED / REVOKED'' AFTER enable',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'last_used_at') = 0,
    'ALTER TABLE agent_token ADD COLUMN last_used_at BIGINT NULL COMMENT ''Last invocation time'' AFTER status',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'invocation_count') = 0,
    'ALTER TABLE agent_token ADD COLUMN invocation_count BIGINT NULL COMMENT ''Invocation counter'' AFTER last_used_at',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'last_ip') = 0,
    'ALTER TABLE agent_token ADD COLUMN last_ip VARCHAR(128) NULL COMMENT ''Last invocation IP'' AFTER invocation_count',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'revoked_at') = 0,
    'ALTER TABLE agent_token ADD COLUMN revoked_at BIGINT NULL COMMENT ''Revoked time'' AFTER last_ip',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'revoked_by') = 0,
    'ALTER TABLE agent_token ADD COLUMN revoked_by VARCHAR(50) NULL COMMENT ''Revoked by user id'' AFTER revoked_at',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND COLUMN_NAME = 'token_version') = 0,
    'ALTER TABLE agent_token ADD COLUMN token_version INT NULL COMMENT ''Token format version'' AFTER revoked_by',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND INDEX_NAME = 'idx_agent_token_public_id') = 0,
    'ALTER TABLE agent_token ADD INDEX idx_agent_token_public_id (public_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'agent_token'
       AND INDEX_NAME = 'idx_agent_token_user') = 0,
    'ALTER TABLE agent_token ADD INDEX idx_agent_token_user (user_id, create_time)',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
