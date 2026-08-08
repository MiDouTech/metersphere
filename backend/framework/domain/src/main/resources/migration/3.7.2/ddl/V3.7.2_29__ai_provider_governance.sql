SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS ai_project_governance (
    project_id             VARCHAR(50) NOT NULL,
    allowed_model_ids      LONGTEXT DEFAULT NULL COMMENT 'JSON model source id allowlist; null/[] means all authorized models',
    fallback_model_id      VARCHAR(50) DEFAULT NULL COMMENT 'Project default model used after transient provider failures',
    max_concurrent_tasks   INT NOT NULL DEFAULT 3,
    monthly_token_quota    BIGINT NOT NULL DEFAULT 1000000,
    project_file_quota     BIGINT NOT NULL DEFAULT 1073741824,
    session_file_limit     INT NOT NULL DEFAULT 20,
    single_file_limit      BIGINT NOT NULL DEFAULT 52428800,
    update_user            VARCHAR(50) NOT NULL,
    update_time            BIGINT NOT NULL,
    PRIMARY KEY (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Project AI governance limits';

CREATE TABLE IF NOT EXISTS ai_provider_usage (
    id              VARCHAR(50) NOT NULL,
    project_id      VARCHAR(50) NOT NULL,
    user_id         VARCHAR(50) NOT NULL,
    model_source_id VARCHAR(50) NOT NULL,
    provider_name   VARCHAR(255) DEFAULT NULL,
    request_type    VARCHAR(32) NOT NULL,
    input_tokens    BIGINT NOT NULL DEFAULT 0,
    output_tokens   BIGINT NOT NULL DEFAULT 0,
    total_tokens    BIGINT NOT NULL DEFAULT 0,
    success         TINYINT(1) NOT NULL DEFAULT 0,
    duration_ms     BIGINT NOT NULL DEFAULT 0,
    error_code      VARCHAR(64) DEFAULT NULL,
    create_time     BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_usage_project_time (project_id, create_time),
    KEY idx_ai_usage_user_time (user_id, create_time),
    KEY idx_ai_usage_model_time (model_source_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI provider metered usage';

CREATE TABLE IF NOT EXISTS ai_oauth_connection (
    id                    VARCHAR(50) NOT NULL,
    provider_id           VARCHAR(100) NOT NULL,
    organization_id       VARCHAR(50) DEFAULT NULL,
    project_id            VARCHAR(50) DEFAULT NULL,
    user_id               VARCHAR(50) NOT NULL,
    authorization_uri     VARCHAR(512) NOT NULL,
    token_uri             VARCHAR(512) NOT NULL,
    revoke_uri            VARCHAR(512) DEFAULT NULL,
    client_id             VARCHAR(255) NOT NULL,
    client_secret_cipher  LONGTEXT NOT NULL,
    scopes                LONGTEXT DEFAULT NULL,
    access_token_cipher   LONGTEXT DEFAULT NULL,
    refresh_token_cipher  LONGTEXT DEFAULT NULL,
    token_type            VARCHAR(32) DEFAULT NULL,
    expires_at            BIGINT DEFAULT NULL,
    status                VARCHAR(32) NOT NULL,
    state_hash            VARCHAR(64) DEFAULT NULL,
    state_expires_at      BIGINT DEFAULT NULL,
    redirect_uri          VARCHAR(512) DEFAULT NULL,
    code_verifier_cipher  LONGTEXT DEFAULT NULL,
    create_time           BIGINT NOT NULL,
    update_time           BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_oauth_owner (provider_id, organization_id, project_id, user_id),
    UNIQUE KEY uk_ai_oauth_state (state_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Encrypted OAuth provider connections';

CREATE TABLE IF NOT EXISTS ai_agent_gateway (
    id                 VARCHAR(50) NOT NULL,
    name               VARCHAR(255) NOT NULL,
    protocol           VARCHAR(32) NOT NULL,
    base_url           VARCHAR(512) NOT NULL,
    auth_type          VARCHAR(32) NOT NULL DEFAULT 'BEARER',
    auth_cipher        LONGTEXT DEFAULT NULL,
    organization_id    VARCHAR(50) DEFAULT NULL,
    project_id         VARCHAR(50) DEFAULT NULL,
    owner_user_id      VARCHAR(50) DEFAULT NULL,
    enabled            TINYINT(1) NOT NULL DEFAULT 1,
    capabilities       LONGTEXT DEFAULT NULL,
    create_user        VARCHAR(50) NOT NULL,
    create_time        BIGINT NOT NULL,
    update_user        VARCHAR(50) NOT NULL,
    update_time        BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_gateway_scope (organization_id, project_id, owner_user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Enterprise Agent gateway connections';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
