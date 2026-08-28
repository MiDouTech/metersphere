SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE ai_execution_task
    ADD COLUMN environment_profile_id VARCHAR(64) NULL AFTER execution_parameter_snapshot,
    ADD COLUMN environment_profile_version INT NULL AFTER environment_profile_id,
    ADD COLUMN credential_reference_id VARCHAR(64) NULL AFTER environment_profile_version,
    ADD COLUMN model_profile_id VARCHAR(64) NULL AFTER credential_reference_id,
    ADD COLUMN prompt_template_version_id VARCHAR(64) NULL AFTER model_profile_id,
    ADD COLUMN preflight_id VARCHAR(64) NULL AFTER prompt_template_version_id,
    ADD COLUMN execution_contract MEDIUMTEXT NULL AFTER preflight_id,
    ADD COLUMN execution_contract_hash VARCHAR(128) NULL AFTER execution_contract,
    ADD COLUMN blocked_reason VARCHAR(64) NULL AFTER execution_contract_hash,
    ADD COLUMN blocked_detail VARCHAR(1000) NULL AFTER blocked_reason,
    ADD COLUMN original_scope_count INT NOT NULL DEFAULT 0 AFTER blocked_detail,
    ADD COLUMN expanded_scope_count INT NOT NULL DEFAULT 0 AFTER original_scope_count,
    ADD COLUMN scope_expansion_rate DECIMAL(6,4) NOT NULL DEFAULT 0 AFTER expanded_scope_count,
    ADD INDEX idx_ai_execution_task_preflight (preflight_id),
    ADD INDEX idx_ai_execution_task_environment (environment_profile_id, status),
    ADD INDEX idx_ai_execution_task_model (model_profile_id, status);

ALTER TABLE ai_task_trigger
    ADD COLUMN trigger_version INT NOT NULL DEFAULT 1 AFTER version,
    ADD COLUMN model_profile_id VARCHAR(64) NULL AFTER task_template,
    ADD COLUMN prompt_template_id VARCHAR(64) NULL AFTER model_profile_id,
    ADD COLUMN environment_profile_id VARCHAR(64) NULL AFTER prompt_template_id,
    ADD COLUMN credential_reference_id VARCHAR(64) NULL AFTER environment_profile_id,
    ADD COLUMN runner_type VARCHAR(32) NULL AFTER credential_reference_id,
    ADD COLUMN required_capabilities TEXT NULL AFTER runner_type,
    ADD COLUMN policy_json MEDIUMTEXT NULL AFTER required_capabilities,
    ADD COLUMN evidence_policy_json TEXT NULL AFTER policy_json,
    ADD COLUMN notification_policy_json TEXT NULL AFTER evidence_policy_json,
    ADD COLUMN responsible_user_ids TEXT NULL AFTER notification_policy_json,
    ADD INDEX idx_ai_task_trigger_profiles (environment_profile_id, model_profile_id);

ALTER TABLE ai_task_trigger_history
    ADD COLUMN trigger_version INT NOT NULL DEFAULT 1 AFTER trigger_id,
    ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 AFTER scheduled_at,
    ADD COLUMN idempotency_key VARCHAR(192) NULL AFTER attempt_no,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER idempotency_key,
    ADD COLUMN blocked_reason VARCHAR(64) NULL AFTER status,
    ADD UNIQUE KEY uk_ai_trigger_scheduled_version (trigger_id, scheduled_at, trigger_version),
    ADD INDEX idx_ai_trigger_history_trace (trace_id);

ALTER TABLE ai_execution_human_request
    ADD COLUMN resolution_version INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN resolved_reason VARCHAR(1000) NULL AFTER response,
    ADD COLUMN checkpoint_id VARCHAR(64) NULL AFTER resolved_reason,
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER checkpoint_id,
    ADD INDEX idx_ai_human_request_task_status (task_id, status),
    ADD INDEX idx_ai_human_request_checkpoint (checkpoint_id);

ALTER TABLE ai_execution_artifact
    ADD COLUMN redaction_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER upload_status,
    ADD COLUMN retention_until BIGINT NULL AFTER redaction_status,
    ADD INDEX idx_ai_artifact_retention (retention_until, status);

CREATE TABLE ai_environment_execution_profile
(
    id                              VARCHAR(64)   NOT NULL,
    organization_id                 VARCHAR(64)   NOT NULL,
    project_id                      VARCHAR(64)   NOT NULL,
    environment_id                  VARCHAR(64)   NOT NULL,
    name                            VARCHAR(255)  NOT NULL,
    base_url                        VARCHAR(2048) NOT NULL,
    allowed_origins                 TEXT          NOT NULL,
    network_zone                    VARCHAR(64)   NULL,
    environment_type                VARCHAR(32)   NOT NULL,
    login_profile_id                VARCHAR(64)   NULL,
    default_credential_reference_id VARCHAR(64)   NULL,
    runner_type                     VARCHAR(32)   NOT NULL,
    required_capabilities           TEXT          NULL,
    production_allowed              BIT(1)        NOT NULL DEFAULT b'0',
    enabled                         BIT(1)        NOT NULL DEFAULT b'1',
    version                         INT           NOT NULL DEFAULT 0,
    create_user                     VARCHAR(64)   NULL,
    update_user                     VARCHAR(64)   NULL,
    create_time                     BIGINT        NOT NULL,
    update_time                     BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_environment_profile_name (project_id, name),
    KEY idx_ai_environment_profile_environment (project_id, environment_id, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_credential_reference
(
    id                  VARCHAR(64)   NOT NULL,
    organization_id     VARCHAR(64)   NOT NULL,
    project_id          VARCHAR(64)   NOT NULL,
    environment_id      VARCHAR(64)   NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    credential_type     VARCHAR(32)   NOT NULL,
    business_role       VARCHAR(64)   NOT NULL,
    provider_type       VARCHAR(32)   NOT NULL,
    secret_ref          VARCHAR(1024) NOT NULL,
    secret_version      VARCHAR(128)  NULL,
    username_hint       VARCHAR(255)  NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'UNVERIFIED',
    expires_at          BIGINT        NULL,
    last_verified_at    BIGINT        NULL,
    last_verify_status  VARCHAR(32)   NULL,
    last_verify_message VARCHAR(1000) NULL,
    enabled             BIT(1)        NOT NULL DEFAULT b'1',
    version             INT           NOT NULL DEFAULT 0,
    create_user         VARCHAR(64)   NULL,
    update_user         VARCHAR(64)   NULL,
    create_time         BIGINT        NOT NULL,
    update_time         BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_credential_reference_name (project_id, environment_id, name),
    KEY idx_ai_credential_reference_status (project_id, environment_id, enabled, status),
    KEY idx_ai_credential_reference_expiry (expires_at, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_login_profile
(
    id                     VARCHAR(64)   NOT NULL,
    organization_id        VARCHAR(64)   NOT NULL,
    project_id             VARCHAR(64)   NOT NULL,
    environment_profile_id VARCHAR(64)   NOT NULL,
    name                   VARCHAR(255)  NOT NULL,
    login_type             VARCHAR(32)   NOT NULL,
    login_url              VARCHAR(2048) NOT NULL,
    username_locator       TEXT          NULL,
    password_locator       TEXT          NULL,
    submit_locator         TEXT          NULL,
    success_assertion      TEXT          NOT NULL,
    session_validation     TEXT          NULL,
    mfa_policy             VARCHAR(32)   NOT NULL DEFAULT 'BLOCK',
    timeout_ms             INT           NOT NULL DEFAULT 30000,
    version                INT           NOT NULL DEFAULT 0,
    enabled                BIT(1)        NOT NULL DEFAULT b'1',
    create_user            VARCHAR(64)   NULL,
    update_user            VARCHAR(64)   NULL,
    create_time            BIGINT        NOT NULL,
    update_time            BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_login_profile_name (environment_profile_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_execution_preflight
(
    id                         VARCHAR(64)   NOT NULL,
    task_id                    VARCHAR(64)   NULL,
    project_id                 VARCHAR(64)   NOT NULL,
    trace_id                   VARCHAR(64)   NOT NULL,
    status                     VARCHAR(32)   NOT NULL,
    checks_json                MEDIUMTEXT    NOT NULL,
    scope_hash                 VARCHAR(128)  NOT NULL,
    asset_snapshot_hash        VARCHAR(128)  NULL,
    environment_profile_version INT          NULL,
    credential_secret_version  VARCHAR(128)  NULL,
    model_profile_version       INT           NULL,
    runner_capability_hash      VARCHAR(128)  NULL,
    blocked_reason              VARCHAR(64)   NULL,
    blocked_detail              VARCHAR(1000) NULL,
    started_at                  BIGINT        NOT NULL,
    finished_at                 BIGINT        NULL,
    expires_at                  BIGINT        NOT NULL,
    create_time                 BIGINT        NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_preflight_project_status (project_id, status, create_time),
    KEY idx_ai_preflight_task (task_id),
    KEY idx_ai_preflight_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_model_profile
(
    id                         VARCHAR(64)   NOT NULL,
    organization_id            VARCHAR(64)   NOT NULL,
    project_id                 VARCHAR(64)   NOT NULL,
    name                       VARCHAR(255)  NOT NULL,
    gateway_app_caller         VARCHAR(255)  NOT NULL,
    gateway_service_key_ref    VARCHAR(1024) NOT NULL,
    logical_model_public_id    VARCHAR(255)  NOT NULL,
    prompt_policy_id           VARCHAR(64)   NOT NULL,
    required_capabilities      TEXT          NULL,
    request_timeout_ms         INT           NOT NULL DEFAULT 120000,
    max_output_tokens          INT           NOT NULL DEFAULT 8192,
    max_cost_amount            DECIMAL(18,6) NULL,
    currency                   VARCHAR(16)   NOT NULL DEFAULT 'CNY',
    enabled                    BIT(1)        NOT NULL DEFAULT b'1',
    version                    INT           NOT NULL DEFAULT 0,
    last_verified_at           BIGINT        NULL,
    last_verify_status         VARCHAR(32)   NULL,
    create_user                VARCHAR(64)   NULL,
    update_user                VARCHAR(64)   NULL,
    create_time                BIGINT        NOT NULL,
    update_time                BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_profile_name (project_id, name),
    KEY idx_ai_model_profile_enabled (organization_id, project_id, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_model_invocation
(
    id                         VARCHAR(64)    NOT NULL,
    task_id                    VARCHAR(64)    NOT NULL,
    execution_id               VARCHAR(64)    NULL,
    trace_id                   VARCHAR(64)    NOT NULL,
    gateway_request_id         VARCHAR(128)   NULL,
    model_profile_id           VARCHAR(64)    NOT NULL,
    logical_model_public_id    VARCHAR(255)   NOT NULL,
    resolved_offering_snapshot TEXT           NULL,
    prompt_version_id          VARCHAR(64)    NOT NULL,
    request_hash               VARCHAR(128)   NOT NULL,
    status                     VARCHAR(32)    NOT NULL,
    finish_reason              VARCHAR(64)    NULL,
    input_tokens               BIGINT         NOT NULL DEFAULT 0,
    output_tokens              BIGINT         NOT NULL DEFAULT 0,
    reasoning_tokens           BIGINT         NOT NULL DEFAULT 0,
    cached_tokens              BIGINT         NOT NULL DEFAULT 0,
    cost_amount                DECIMAL(18,8)  NULL,
    currency                   VARCHAR(16)    NULL,
    retry_count                INT            NOT NULL DEFAULT 0,
    ttft_ms                    BIGINT         NULL,
    duration_ms                BIGINT         NULL,
    error_code                 VARCHAR(64)    NULL,
    error_message              VARCHAR(1000)  NULL,
    create_time                BIGINT         NOT NULL,
    finish_time                BIGINT         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_model_invocation_gateway (gateway_request_id),
    KEY idx_ai_model_invocation_task (task_id, create_time),
    KEY idx_ai_model_invocation_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_prompt_template_version
(
    id                    VARCHAR(64)  NOT NULL,
    prompt_template_id    VARCHAR(64)  NOT NULL,
    organization_id       VARCHAR(64)  NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    version_no            INT          NOT NULL,
    system_template       MEDIUMTEXT   NOT NULL,
    business_template     MEDIUMTEXT   NOT NULL,
    variable_schema       TEXT         NOT NULL,
    output_schema_version VARCHAR(32)  NOT NULL,
    content_hash          VARCHAR(128) NOT NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_by          VARCHAR(64)  NULL,
    published_at          BIGINT       NULL,
    create_user           VARCHAR(64)  NULL,
    create_time           BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_prompt_template_version (prompt_template_id, version_no),
    KEY idx_ai_prompt_template_status (organization_id, prompt_template_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_human_request_recipient
(
    id              VARCHAR(64) NOT NULL,
    request_id      VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    notify_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    notified_at     BIGINT      NULL,
    response_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    responded_at    BIGINT      NULL,
    create_time     BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_human_recipient (request_id, user_id),
    KEY idx_ai_human_recipient_user (user_id, response_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_execution_checkpoint
(
    id                VARCHAR(64)  NOT NULL,
    task_id           VARCHAR(64)  NOT NULL,
    execution_id      VARCHAR(64)  NOT NULL,
    checkpoint_version INT         NOT NULL,
    state_snapshot    MEDIUMTEXT   NOT NULL,
    state_hash        VARCHAR(128) NOT NULL,
    reason            VARCHAR(255) NOT NULL,
    resume_token_hash VARCHAR(128) NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at        BIGINT       NOT NULL,
    resumed_at        BIGINT       NULL,
    resumed_by        VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_checkpoint_version (task_id, checkpoint_version),
    KEY idx_ai_checkpoint_status (task_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_page_object
(
    id              VARCHAR(64)  NOT NULL,
    organization_id VARCHAR(64)  NOT NULL,
    project_id      VARCHAR(64)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    route_pattern   VARCHAR(1024) NULL,
    allowed_origins TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    version         INT          NOT NULL DEFAULT 0,
    create_user     VARCHAR(64)  NULL,
    update_user     VARCHAR(64)  NULL,
    create_time     BIGINT       NOT NULL,
    update_time     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_page_object_name (project_id, name),
    KEY idx_ai_page_object_status (project_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_page_element
(
    id                VARCHAR(64)  NOT NULL,
    page_object_id    VARCHAR(64)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    strategy          VARCHAR(32)  NOT NULL,
    selector_value    VARCHAR(2048) NOT NULL,
    fallback_locators TEXT         NULL,
    sensitive         BIT(1)       NOT NULL DEFAULT b'0',
    risk_level        VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    version           INT          NOT NULL DEFAULT 0,
    create_user       VARCHAR(64)  NULL,
    update_user       VARCHAR(64)  NULL,
    create_time       BIGINT       NOT NULL,
    update_time       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_page_element_name (page_object_id, name),
    KEY idx_ai_page_element_page (page_object_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_test_data_lease
(
    id               VARCHAR(64)  NOT NULL,
    task_id          VARCHAR(64)  NOT NULL,
    execution_id     VARCHAR(64)  NOT NULL,
    project_id       VARCHAR(64)  NOT NULL,
    dataset_id       VARCHAR(64)  NOT NULL,
    data_key         VARCHAR(255) NOT NULL,
    namespace        VARCHAR(255) NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    lease_token_hash VARCHAR(128) NOT NULL,
    expires_at       BIGINT       NOT NULL,
    released_at      BIGINT       NULL,
    version          INT          NOT NULL DEFAULT 0,
    create_time      BIGINT       NOT NULL,
    update_time      BIGINT       NOT NULL,
    active_data_key  VARCHAR(768) GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN CONCAT(project_id, ':', dataset_id, ':', data_key) ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_test_data_active (active_data_key),
    KEY idx_ai_test_data_expiry (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_test_data_cleanup
(
    id             VARCHAR(64)   NOT NULL,
    lease_id       VARCHAR(64)   NOT NULL,
    cleanup_type   VARCHAR(32)   NOT NULL,
    status         VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    attempt_count  INT           NOT NULL DEFAULT 0,
    next_retry_at  BIGINT        NULL,
    error_code     VARCHAR(64)   NULL,
    error_message  VARCHAR(1000) NULL,
    created_at     BIGINT        NOT NULL,
    finished_at    BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_ai_test_cleanup_due (status, next_retry_at),
    KEY idx_ai_test_cleanup_lease (lease_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_step_approval
(
    id             VARCHAR(64)  NOT NULL,
    task_id        VARCHAR(64)  NOT NULL,
    execution_id   VARCHAR(64)  NOT NULL,
    step_id        VARCHAR(64)  NOT NULL,
    action_hash    VARCHAR(128) NOT NULL,
    environment_id VARCHAR(64)  NOT NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    expires_at     BIGINT       NOT NULL,
    consumed_at    BIGINT       NULL,
    approved_by    VARCHAR(64)  NULL,
    created_at     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_step_approval_action (task_id, step_id, action_hash, environment_id),
    KEY idx_ai_step_approval_status (task_id, status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
