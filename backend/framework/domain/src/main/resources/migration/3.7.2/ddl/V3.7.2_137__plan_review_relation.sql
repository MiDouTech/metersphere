-- MSP-12: additive schema only. No historical pairing or member reconciliation is run by Flyway.
CREATE TABLE plan_review_grant (
    project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    enabled BIT(1) NOT NULL DEFAULT 0,
    approved_by VARCHAR(50) COLLATE utf8mb4_bin NULL,
    update_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_relation (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    plan_id VARCHAR(50) NOT NULL,
    review_id VARCHAR(50) NOT NULL,
    generation VARCHAR(50) NOT NULL,
    active_plan_id VARCHAR(50) NULL,
    active_review_id VARCHAR(50) NULL,
    state VARCHAR(32) NOT NULL,
    sync_state VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    applied_version BIGINT NOT NULL DEFAULT 0,
    opinion_revision BIGINT NOT NULL DEFAULT 0,
    approved_source_version BIGINT NULL,
    approved_opinion_revision BIGINT NULL,
    approved_grant_revision BIGINT NULL,
    approved_target_fingerprint VARCHAR(64) NULL,
    approval_pending BIT(1) NOT NULL DEFAULT 0,
    approval_id VARCHAR(50) NULL,
    applied_batches BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    retry_round BIGINT NOT NULL DEFAULT 0,
    next_retry_at BIGINT NULL,
    source_changed_at BIGINT NOT NULL,
    last_success_at BIGINT NULL,
    safe_error VARCHAR(500) NULL,
    fault_round BIGINT NOT NULL DEFAULT 0,
    original_actor VARCHAR(50) NOT NULL,
    ended_reason VARCHAR(64) NULL,
    protocol_version INT NOT NULL DEFAULT 2,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_active_plan (project_id,active_plan_id),
    UNIQUE KEY uk_active_review (project_id,active_review_id),
    UNIQUE KEY uk_generation (generation),
    KEY idx_due (state,sync_state,next_retry_at),
    KEY idx_plan (plan_id),
    KEY idx_review (review_id),
    CONSTRAINT ck_plan_review_active CHECK (
        (state='ACTIVE' AND active_plan_id IS NOT NULL AND active_review_id IS NOT NULL
            AND active_plan_id=plan_id AND active_review_id=review_id)
        OR (state<>'ACTIVE' AND active_plan_id IS NULL AND active_review_id IS NULL)),
    CONSTRAINT ck_plan_review_version CHECK (applied_version>=0 AND applied_version<=source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_source_clock (
    plan_id VARCHAR(50) NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    update_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_source_member (
    plan_id VARCHAR(50) NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    epoch BIGINT NOT NULL,
    present BIT(1) NOT NULL,
    last_version BIGINT NOT NULL,
    PRIMARY KEY (plan_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_target_member (
    review_id VARCHAR(50) NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    epoch BIGINT NOT NULL,
    source_epoch BIGINT NOT NULL,
    present BIT(1) NOT NULL,
    PRIMARY KEY (review_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_member_history (
    review_id VARCHAR(50) NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    epoch BIGINT NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    relation_id VARCHAR(50) NOT NULL,
    generation VARCHAR(50) NOT NULL,
    source_epoch BIGINT NOT NULL,
    case_snapshot LONGBLOB NOT NULL,
    review_snapshot LONGBLOB NOT NULL,
    history_snapshot LONGBLOB NOT NULL,
    exited_at BIGINT NOT NULL,
    exit_reason VARCHAR(64) NOT NULL,
    PRIMARY KEY (review_id,case_id,epoch),
    KEY idx_relation_history (relation_id,exited_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_preview (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    plan_id VARCHAR(50) NOT NULL,
    review_id VARCHAR(50) NOT NULL,
    generation VARCHAR(50) NOT NULL,
    source_version BIGINT NOT NULL,
    opinion_revision BIGINT NOT NULL,
    grant_revision BIGINT NOT NULL,
    plan_revision BIGINT NOT NULL,
    review_revision BIGINT NOT NULL,
    target_fingerprint VARCHAR(64) NOT NULL,
    difference_hash VARCHAR(64) NOT NULL,
    issued_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    consumed_by VARCHAR(50) NULL,
    actor VARCHAR(50) NOT NULL,
    KEY idx_preview_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_operation (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    initial_member_count INT NOT NULL DEFAULT 0,
    request_key VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    request_json LONGTEXT NOT NULL,
    original_actor VARCHAR(50) NOT NULL,
    executing_actor VARCHAR(50) NOT NULL,
    executing_credential_id VARCHAR(50) NULL,
    original_credential_id VARCHAR(50) NULL,
    approver VARCHAR(50) NULL,
    plan_id VARCHAR(50) NULL,
    review_id VARCHAR(50) NULL,
    relation_id VARCHAR(50) NULL,
    generation VARCHAR(50) NULL,
    stage VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    protocol_version INT NOT NULL DEFAULT 2,
    safe_error VARCHAR(500) NULL,
    result_json LONGTEXT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_operation_key (project_id,source_kind,operation_type,request_key),
    KEY idx_actor_operations (project_id,original_actor,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_notice (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    relation_id VARCHAR(50) NOT NULL,
    generation VARCHAR(50) NOT NULL,
    fault_round BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    recipient_id VARCHAR(50) NOT NULL,
    state VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    retry_round BIGINT NOT NULL DEFAULT 0,
    next_retry_at BIGINT NULL,
    detected_at BIGINT NOT NULL,
    persisted_at BIGINT NOT NULL,
    readable_at BIGINT NULL,
    recovered_at BIGINT NULL,
    last_finished_at BIGINT NULL,
    safe_error VARCHAR(500) NULL,
    UNIQUE KEY uk_notice_intent (relation_id,generation,fault_round,reason,type,recipient_id),
    KEY idx_notice_due (state,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_audit (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    relation_id VARCHAR(50) NULL,
    generation VARCHAR(50) NULL,
    operation_id VARCHAR(50) NULL,
    action VARCHAR(64) NOT NULL,
    actor VARCHAR(50) NOT NULL,
    service_actor VARCHAR(50) NULL,
    grant_revision BIGINT NULL,
    source_version BIGINT NULL,
    added_count INT NOT NULL DEFAULT 0,
    removed_count INT NOT NULL DEFAULT 0,
    result VARCHAR(32) NOT NULL,
    safe_reason VARCHAR(500) NULL,
    create_time BIGINT NOT NULL,
    KEY idx_relation_audit (project_id,relation_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
