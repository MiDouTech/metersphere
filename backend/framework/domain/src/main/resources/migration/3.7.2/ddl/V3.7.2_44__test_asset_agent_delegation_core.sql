-- Test asset management and Agent task delegation core model.
-- Runtime status and business verdict are intentionally stored separately.

UPDATE ai_execution_task
SET source = 'LEGACY'
WHERE source IS NULL OR source = '';

ALTER TABLE ai_execution_task
    MODIFY COLUMN source VARCHAR(96) NOT NULL COMMENT 'Task source, including TRIGGER:{id}',
    ADD COLUMN name VARCHAR(255) NULL COMMENT 'Human-readable task name' AFTER test_plan_id,
    ADD COLUMN objective TEXT NULL COMMENT 'Execution objective and completion expectation' AFTER name,
    ADD COLUMN verdict VARCHAR(32) NULL COMMENT 'Business verdict: PASSED/PRODUCT_FAILED/ENV_FAILED/DATA_FAILED/AGENT_FAILED/BLOCKED/INCONCLUSIVE' AFTER status,
    ADD COLUMN verdict_reason VARCHAR(1000) NULL COMMENT 'Structured verdict summary' AFTER verdict,
    ADD COLUMN dispatch_mode VARCHAR(16) NOT NULL DEFAULT 'PULL' COMMENT 'PUSH gateway dispatch or PULL Agent claim' AFTER execution_mode,
    ADD COLUMN required_capabilities TEXT NULL COMMENT 'JSON array of required executor capabilities' AFTER agent_gateway_id,
    ADD COLUMN context_snapshot MEDIUMTEXT NULL COMMENT 'Immutable execution context package without plaintext secrets' AFTER policy_snapshot,
    ADD COLUMN context_snapshot_hash VARCHAR(128) NULL COMMENT 'SHA-256 of context_snapshot' AFTER context_snapshot,
    ADD COLUMN approval_policy TEXT NULL COMMENT 'JSON approval and high-risk action policy' AFTER context_snapshot_hash,
    ADD COLUMN timeout_at BIGINT NULL COMMENT 'Task execution timeout epoch millis' AFTER confirmation_reason,
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 3 COMMENT 'Maximum lease attempts' AFTER timeout_at,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT 'Leased attempt count' AFTER max_attempts,
    ADD COLUMN finished_at BIGINT NULL COMMENT 'Terminal completion time epoch millis' AFTER attempt_count,
    ADD INDEX idx_ai_execution_task_verdict (project_id, verdict),
    ADD INDEX idx_ai_execution_task_dispatch (organization_id, execution_mode, status, attempt_count, create_time);

UPDATE ai_execution_task
SET dispatch_mode = CASE WHEN execution_mode = 'AGENT' THEN 'PUSH' ELSE 'PULL' END;

ALTER TABLE ai_runner_lease
    ADD COLUMN executor_type VARCHAR(32) NOT NULL DEFAULT 'RUNNER' COMMENT 'RUNNER or AGENT' AFTER runner_id,
    ADD COLUMN executor_id VARCHAR(128) NULL COMMENT 'Stable runner/Agent identity' AFTER executor_type,
    ADD COLUMN attempt INT NOT NULL DEFAULT 1 COMMENT 'Task attempt number' AFTER executor_id,
    ADD INDEX idx_ai_runner_lease_executor (executor_type, executor_id, status, expire_time);

ALTER TABLE ai_execution_case
    ADD COLUMN asset_version_id VARCHAR(64) NULL COMMENT 'Immutable test_asset_version id' AFTER case_version,
    ADD INDEX idx_ai_execution_case_asset_version (asset_version_id);

CREATE TABLE test_asset_version
(
    id               VARCHAR(64)  NOT NULL COMMENT 'Version id',
    project_id       VARCHAR(64)  NOT NULL COMMENT 'Project id',
    asset_type       VARCHAR(32)  NOT NULL COMMENT 'CASE/DOCUMENT/PLAN/ENVIRONMENT/DATASET',
    asset_id         VARCHAR(64)  NOT NULL COMMENT 'Business asset id',
    version_no       INT          NOT NULL COMMENT 'Monotonic content version',
    source_version   VARCHAR(128) NULL COMMENT 'Original source version or release reference',
    content_hash     VARCHAR(128) NOT NULL COMMENT 'SHA-256 content hash',
    content_snapshot MEDIUMTEXT   NOT NULL COMMENT 'Immutable content snapshot',
    status           VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED/DEPRECATED',
    created_by       VARCHAR(64)  NULL,
    created_at       BIGINT       NOT NULL,
    published_by     VARCHAR(64)  NULL,
    published_at     BIGINT       NULL,
    deprecated_by    VARCHAR(64)  NULL,
    deprecated_at    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_asset_version (project_id, asset_type, asset_id, version_no),
    KEY idx_test_asset_version_latest (project_id, asset_type, asset_id, status, version_no),
    KEY idx_test_asset_version_hash (project_id, content_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Immutable test asset content versions';

CREATE TABLE test_asset_relation
(
    id                    VARCHAR(64)  NOT NULL,
    project_id            VARCHAR(64)  NOT NULL,
    relation_type         VARCHAR(32)  NOT NULL COMMENT 'DERIVED_FROM/COVERS/USES/EXECUTES/PRODUCES/REPLACES',
    source_asset_type     VARCHAR(32)  NOT NULL,
    source_asset_id       VARCHAR(64)  NOT NULL,
    source_version_id     VARCHAR(64)  NOT NULL DEFAULT '',
    target_asset_type     VARCHAR(32)  NOT NULL,
    target_asset_id       VARCHAR(64)  NOT NULL,
    target_version_id     VARCHAR(64)  NOT NULL DEFAULT '',
    metadata              TEXT         NULL,
    created_by            VARCHAR(64)  NULL,
    created_at            BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_asset_relation (project_id, relation_type, source_asset_type, source_asset_id,
                                       source_version_id, target_asset_type, target_asset_id, target_version_id),
    KEY idx_test_asset_relation_source (project_id, source_asset_type, source_asset_id),
    KEY idx_test_asset_relation_target (project_id, target_asset_type, target_asset_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Traceable relationships between test assets';

CREATE TABLE ai_execution_human_request
(
    id             VARCHAR(64)   NOT NULL,
    request_key    VARCHAR(128)  NULL COMMENT 'Agent supplied idempotency key',
    task_id        VARCHAR(64)   NOT NULL,
    project_id     VARCHAR(64)   NOT NULL,
    request_type   VARCHAR(32)   NOT NULL COMMENT 'APPROVAL/INPUT/LOGIN/MANUAL_STEP/REVIEW',
    title          VARCHAR(255)  NOT NULL,
    content        TEXT          NULL,
    risk_level     VARCHAR(16)   NULL,
    status         VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/ANSWERED/CANCELED/EXPIRED',
    requested_by   VARCHAR(128)  NULL,
    assigned_to    VARCHAR(64)   NULL,
    response       TEXT          NULL,
    responded_by   VARCHAR(64)   NULL,
    responded_at   BIGINT        NULL,
    expires_at     BIGINT        NULL,
    created_at     BIGINT        NOT NULL,
    updated_at     BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_human_request_key (task_id, request_key),
    KEY idx_ai_human_request_task (task_id, status),
    KEY idx_ai_human_request_assignee (project_id, assigned_to, status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Human-in-the-loop requests raised by executions';

CREATE TABLE ai_task_trigger
(
    id                   VARCHAR(64)   NOT NULL,
    org_id               VARCHAR(64)   NOT NULL,
    project_id           VARCHAR(64)   NOT NULL,
    name                 VARCHAR(255)  NOT NULL,
    trigger_type         VARCHAR(32)   NOT NULL COMMENT 'CRON/EVENT/MANUAL',
    cron_expression      VARCHAR(128)  NULL,
    timezone             VARCHAR(64)   NULL,
    event_type           VARCHAR(64)   NULL,
    event_filter         TEXT          NULL COMMENT 'JSON exact-match event filter',
    webhook_secret_cipher VARCHAR(512) NULL COMMENT 'Encrypted webhook HMAC secret',
    concurrency_policy   VARCHAR(16)   NOT NULL DEFAULT 'FORBID' COMMENT 'FORBID/ALLOW',
    missed_policy        VARCHAR(16)   NOT NULL DEFAULT 'FIRE_ONCE' COMMENT 'SKIP/FIRE_ONCE',
    task_template        MEDIUMTEXT    NOT NULL COMMENT 'Immutable task create request template',
    enabled              BIT(1)        NOT NULL DEFAULT b'1',
    next_fire_at         BIGINT        NULL,
    last_fire_at         BIGINT        NULL,
    last_fire_status     VARCHAR(32)   NULL,
    last_error           VARCHAR(1000) NULL,
    created_by           VARCHAR(64)   NULL,
    created_at           BIGINT        NOT NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_at           BIGINT        NOT NULL,
    version              INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_task_trigger_due (enabled, trigger_type, next_fire_at),
    KEY idx_ai_task_trigger_project (project_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Scheduled and event-based execution task triggers';

CREATE TABLE ai_task_trigger_history
(
    id          VARCHAR(64)   NOT NULL,
    trigger_id  VARCHAR(64)   NOT NULL,
    task_id     VARCHAR(64)   NULL,
    event_id    VARCHAR(128)  NULL,
    scheduled_at BIGINT       NULL,
    fire_time   BIGINT        NOT NULL,
    status      VARCHAR(32)   NOT NULL COMMENT 'CREATED/SKIPPED/FAILED',
    message     VARCHAR(1000) NULL,
    created_at  BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_trigger_event (trigger_id, event_id),
    UNIQUE KEY uk_ai_trigger_schedule (trigger_id, scheduled_at),
    KEY idx_ai_task_trigger_history (trigger_id, fire_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Task trigger execution history';

CREATE TABLE ai_execution_evaluation
(
    id                    VARCHAR(64)   NOT NULL,
    task_id               VARCHAR(64)   NOT NULL,
    project_id            VARCHAR(64)   NOT NULL,
    executor_type         VARCHAR(32)   NULL,
    executor_id           VARCHAR(128)  NULL,
    operational_status    VARCHAR(32)   NOT NULL,
    business_verdict      VARCHAR(32)   NULL,
    completion_rate       DECIMAL(8,4)  NOT NULL DEFAULT 0,
    evidence_rate         DECIMAL(8,4)  NOT NULL DEFAULT 0,
    healing_count         INT           NOT NULL DEFAULT 0,
    retry_count           INT           NOT NULL DEFAULT 0,
    duration_ms           BIGINT        NULL,
    manual_score          DECIMAL(5,2)  NULL,
    manual_comment        VARCHAR(2000) NULL,
    evaluated_by          VARCHAR(64)   NULL,
    evaluated_at          BIGINT        NULL,
    created_at            BIGINT        NOT NULL,
    updated_at            BIGINT        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_execution_evaluation_task (task_id),
    KEY idx_ai_execution_evaluation_executor (project_id, executor_type, executor_id, created_at),
    KEY idx_ai_execution_evaluation_verdict (project_id, business_verdict, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Execution and Agent quality evaluation';
