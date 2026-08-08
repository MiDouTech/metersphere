SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS ai_agent_bridge_pairing (
    id                    VARCHAR(50)  NOT NULL,
    user_id               VARCHAR(50)  NOT NULL,
    provider              VARCHAR(32)  DEFAULT NULL,
    expected_device_name  VARCHAR(255) DEFAULT NULL,
    code_hash             VARCHAR(64)  NOT NULL COMMENT 'SHA-256 hash; plaintext code is returned once only',
    status                VARCHAR(32)  NOT NULL COMMENT 'PENDING/CONSUMED/EXPIRED/REVOKED',
    expires_at            BIGINT       NOT NULL,
    consumed_at           BIGINT       DEFAULT NULL,
    device_id             VARCHAR(50)  DEFAULT NULL,
    create_time           BIGINT       NOT NULL,
    update_time           BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_pairing_code_hash (code_hash),
    KEY idx_ai_agent_pairing_owner_status (user_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Single-use Agent Bridge pairing challenges';

CREATE TABLE IF NOT EXISTS ai_agent_device_challenge (
    id            VARCHAR(50)  NOT NULL,
    device_id     VARCHAR(50)  NOT NULL,
    nonce_hash    VARCHAR(64)  NOT NULL,
    expires_at    BIGINT       NOT NULL,
    consumed_at   BIGINT       DEFAULT NULL,
    create_time   BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_device_nonce (device_id, nonce_hash),
    KEY idx_ai_agent_challenge_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Single-use Bridge device signature challenges';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
