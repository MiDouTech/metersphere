SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE ai_case_executability_config
(
    id                       VARCHAR(64)  NOT NULL,
    organization_id          VARCHAR(64)  NOT NULL,
    project_id               VARCHAR(64)  NOT NULL,
    case_id                  VARCHAR(64)  NOT NULL,
    environment_profile_id   VARCHAR(64)  NOT NULL,
    credential_role          VARCHAR(64)  NULL,
    page_object_ids          TEXT         NOT NULL,
    dataset_ids              TEXT         NOT NULL,
    business_flow_id         VARCHAR(64)  NULL,
    risk_level               VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    automation_readiness     VARCHAR(16)  NOT NULL DEFAULT 'NOT_READY',
    missing_items            TEXT         NOT NULL,
    last_checked_at          BIGINT       NULL,
    checker_version          VARCHAR(32)  NOT NULL DEFAULT 'v1',
    version                  INT          NOT NULL DEFAULT 0,
    create_user              VARCHAR(64)  NULL,
    update_user              VARCHAR(64)  NULL,
    create_time              BIGINT       NOT NULL,
    update_time              BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_case_executability (project_id, case_id, environment_profile_id),
    KEY idx_ai_case_readiness (project_id, environment_profile_id, automation_readiness, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
