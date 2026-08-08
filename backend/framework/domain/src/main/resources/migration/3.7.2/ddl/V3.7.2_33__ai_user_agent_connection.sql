SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS ai_agent_device (
    id                       VARCHAR(50)  NOT NULL,
    user_id                  VARCHAR(50)  NOT NULL,
    device_name              VARCHAR(255) NOT NULL,
    public_key               LONGTEXT     NOT NULL COMMENT 'Device signing public key; private key never leaves Bridge',
    certificate_fingerprint  VARCHAR(128) NOT NULL,
    status                   VARCHAR(32)  NOT NULL COMMENT 'PAIRING/ONLINE/OFFLINE/REVOKED',
    bridge_version           VARCHAR(64)  DEFAULT NULL,
    protocol_version         VARCHAR(32)  DEFAULT NULL,
    os_type                  VARCHAR(32)  DEFAULT NULL,
    last_heartbeat_time      BIGINT       DEFAULT NULL,
    access_token_hash        VARCHAR(64)  DEFAULT NULL COMMENT 'Hash of short-lived Bridge access token',
    access_token_expires_at  BIGINT       DEFAULT NULL,
    revoked_time             BIGINT       DEFAULT NULL,
    version                  BIGINT       NOT NULL DEFAULT 0,
    create_time              BIGINT       NOT NULL,
    update_time              BIGINT       NOT NULL,
    deleted                  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_device_fingerprint (certificate_fingerprint),
    KEY idx_ai_agent_device_owner_status (user_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='User-owned outbound Agent Bridge devices';

CREATE TABLE IF NOT EXISTS ai_user_agent_connection (
    id                    VARCHAR(50)  NOT NULL,
    user_id               VARCHAR(50)  NOT NULL,
    provider              VARCHAR(32)  NOT NULL COMMENT 'WORKBUDDY/CODEX/CURSOR',
    connection_mode       VARCHAR(32)  NOT NULL COMMENT 'LOCAL_BRIDGE/OAUTH/API_KEY/REMOTE_GATEWAY',
    display_name          VARCHAR(255) NOT NULL,
    external_account_id   VARCHAR(255) DEFAULT NULL COMMENT 'Masked/non-secret external account identity',
    credential_reference  VARCHAR(512) DEFAULT NULL COMMENT 'Opaque local/secret-store reference; never a third-party credential',
    status                VARCHAR(32)  NOT NULL COMMENT 'PENDING/CONNECTED/OFFLINE/AUTH_EXPIRED/DISABLED/REVOKED',
    capabilities          LONGTEXT     DEFAULT NULL COMMENT 'Sanitized JSON capability declaration',
    device_id             VARCHAR(50)  DEFAULT NULL,
    expires_at            BIGINT       DEFAULT NULL,
    last_health_time      BIGINT       DEFAULT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    create_time           BIGINT       NOT NULL,
    update_time           BIGINT       NOT NULL,
    deleted               TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_user_agent_owner_status (user_id, status, deleted),
    KEY idx_ai_user_agent_device (device_id, status),
    KEY idx_ai_user_agent_provider (provider, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='User-owned AI Agent connections without third-party credentials';

CREATE TABLE IF NOT EXISTS ai_agent_session_binding (
    conversation_id     VARCHAR(50)  NOT NULL,
    connection_id       VARCHAR(50)  NOT NULL,
    external_session_id VARCHAR(255) DEFAULT NULL,
    provider            VARCHAR(32)  NOT NULL,
    device_id           VARCHAR(50)  NOT NULL,
    last_sequence       BIGINT       NOT NULL DEFAULT 0,
    create_time         BIGINT       NOT NULL,
    update_time         BIGINT       NOT NULL,
    PRIMARY KEY (conversation_id, connection_id),
    KEY idx_ai_agent_session_connection (connection_id, update_time),
    KEY idx_ai_agent_session_device (device_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='MeterSphere conversation to external Agent session binding';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
