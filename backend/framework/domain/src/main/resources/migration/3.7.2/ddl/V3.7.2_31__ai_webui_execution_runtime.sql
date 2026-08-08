SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE ai_execution_task
    ADD COLUMN selection_mode VARCHAR(32) NULL COMMENT 'MANUAL/NATURAL_LANGUAGE' AFTER source,
    ADD COLUMN prompt TEXT NULL COMMENT '脱敏后的用户原始提示词' AFTER selection_mode,
    ADD COLUMN resolved_filter MEDIUMTEXT NULL COMMENT '结构化筛选 DSL' AFTER prompt,
    ADD COLUMN case_snapshot_hash VARCHAR(128) NULL COMMENT '最终执行范围摘要' AFTER resolved_filter,
    ADD COLUMN policy_snapshot MEDIUMTEXT NULL COMMENT '执行、自愈、截图与风险策略快照' AFTER case_snapshot_hash,
    ADD COLUMN runner_lease_id VARCHAR(50) NULL COMMENT '当前 Runner 租约 ID' AFTER runner_id,
    ADD COLUMN writeback_status VARCHAR(32) NULL COMMENT '结果回写汇总状态' AFTER unexecuted_count,
    ADD COLUMN artifact_status VARCHAR(32) NULL COMMENT '证据落库汇总状态' AFTER writeback_status,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER update_user,
    ADD INDEX idx_ai_execution_task_runner_lease (runner_lease_id);

ALTER TABLE ai_execution_case
    ADD COLUMN case_version VARCHAR(50) NULL COMMENT '执行时功能用例版本' AFTER test_plan_case_id,
    ADD COLUMN case_snapshot MEDIUMTEXT NULL COMMENT '执行时功能用例不可变快照' AFTER case_version,
    ADD COLUMN heal_count INT NOT NULL DEFAULT 0 COMMENT '自愈尝试次数' AFTER retry_count,
    ADD COLUMN healed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否发生成功自愈' AFTER heal_count,
    ADD COLUMN failure_category VARCHAR(64) NULL COMMENT '失败分类' AFTER error_message,
    ADD COLUMN writeback_status VARCHAR(32) NULL COMMENT '单用例回写状态' AFTER failure_category,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER update_time;

ALTER TABLE ai_execution_event
    ADD COLUMN contract_version VARCHAR(20) NOT NULL DEFAULT 'v1' AFTER id,
    ADD COLUMN runner_event_id VARCHAR(64) NULL COMMENT 'Runner 侧事件幂等 ID' AFTER contract_version,
    ADD COLUMN attempt INT NOT NULL DEFAULT 0 AFTER step_id,
    ADD UNIQUE KEY uk_ai_execution_event_runner_id (task_id, runner_event_id);

CREATE TABLE IF NOT EXISTS ai_execution_step (
    id                  VARCHAR(50) NOT NULL PRIMARY KEY,
    task_id             VARCHAR(50) NOT NULL,
    execution_case_id   VARCHAR(50) NOT NULL,
    case_id             VARCHAR(50) NOT NULL,
    source_step_id      VARCHAR(50),
    pos                 INT NOT NULL DEFAULT 0,
    instruction         TEXT,
    expected            TEXT,
    action_json         MEDIUMTEXT,
    assertion_json      MEDIUMTEXT,
    risk_level          VARCHAR(16) NOT NULL DEFAULT 'LOW',
    retryable           TINYINT(1) NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    actual_result       TEXT,
    error_message       VARCHAR(2048),
    failure_category    VARCHAR(64),
    attempt             INT NOT NULL DEFAULT 0,
    retry_count         INT NOT NULL DEFAULT 0,
    healed              TINYINT(1) NOT NULL DEFAULT 0,
    started_at          BIGINT,
    finished_at         BIGINT,
    create_time         BIGINT,
    update_time         BIGINT,
    version             INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ai_execution_step_case_pos (execution_case_id, pos),
    INDEX idx_ai_execution_step_task_status (task_id, status),
    INDEX idx_ai_execution_step_case (execution_case_id),
    INDEX idx_ai_execution_step_source (source_step_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI Web UI 执行步骤快照与结果';

CREATE TABLE IF NOT EXISTS ai_execution_healing (
    id                   VARCHAR(50) NOT NULL PRIMARY KEY,
    task_id              VARCHAR(50) NOT NULL,
    execution_case_id    VARCHAR(50) NOT NULL,
    execution_step_id    VARCHAR(50) NOT NULL,
    attempt              INT NOT NULL,
    failure_type         VARCHAR(64) NOT NULL,
    original_locator     TEXT,
    candidate_locators   MEDIUMTEXT,
    selected_locator     TEXT,
    reason               VARCHAR(2048),
    confidence           DECIMAL(5,4),
    result               VARCHAR(32) NOT NULL,
    before_artifact_id   VARCHAR(50),
    after_artifact_id    VARCHAR(50),
    duration_ms          BIGINT,
    create_time          BIGINT,
    create_user          VARCHAR(50),
    UNIQUE KEY uk_ai_execution_healing_step_attempt (execution_step_id, attempt),
    INDEX idx_ai_execution_healing_task (task_id),
    INDEX idx_ai_execution_healing_case (execution_case_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI Web UI 失败自愈轨迹';

CREATE TABLE IF NOT EXISTS ai_execution_artifact (
    id                  VARCHAR(50) NOT NULL PRIMARY KEY,
    task_id             VARCHAR(50) NOT NULL,
    execution_case_id   VARCHAR(50),
    case_id             VARCHAR(50),
    step_id             VARCHAR(50),
    purpose             VARCHAR(32) NOT NULL COMMENT 'BEFORE_STEP/AFTER_STEP/FAILURE/HEALING_BEFORE/HEALING_AFTER',
    file_id             VARCHAR(50) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    storage_folder      VARCHAR(1024) NOT NULL,
    content_type        VARCHAR(128) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    sha256              VARCHAR(64) NOT NULL,
    redacted            TINYINT(1) NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    retention_until     BIGINT,
    create_time         BIGINT,
    create_user         VARCHAR(50),
    UNIQUE KEY uk_ai_execution_artifact_task_hash (task_id, sha256, purpose, step_id),
    INDEX idx_ai_execution_artifact_task (task_id),
    INDEX idx_ai_execution_artifact_case (execution_case_id),
    INDEX idx_ai_execution_artifact_step (step_id),
    INDEX idx_ai_execution_artifact_retention (status, retention_until)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI Web UI 执行截图证据';

CREATE TABLE IF NOT EXISTS ai_runner (
    id                  VARCHAR(50) NOT NULL PRIMARY KEY,
    organization_id     VARCHAR(50),
    name                VARCHAR(255) NOT NULL,
    runner_version      VARCHAR(50) NOT NULL,
    contract_version    VARCHAR(20) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    operating_system    VARCHAR(64),
    browser_capabilities TEXT,
    environment_labels  TEXT,
    auth_token_hash     VARCHAR(128) NOT NULL COMMENT 'Runner 长期注册令牌摘要，不存明文',
    max_concurrency     INT NOT NULL DEFAULT 1,
    active_count        INT NOT NULL DEFAULT 0,
    last_heartbeat_time BIGINT,
    create_time         BIGINT,
    update_time         BIGINT,
    create_user         VARCHAR(50),
    update_user         VARCHAR(50),
    INDEX idx_ai_runner_org_status (organization_id, status),
    INDEX idx_ai_runner_heartbeat (last_heartbeat_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI Web UI Browser Runner';

CREATE TABLE IF NOT EXISTS ai_runner_lease (
    id                  VARCHAR(50) NOT NULL PRIMARY KEY,
    task_id             VARCHAR(50) NOT NULL,
    runner_id           VARCHAR(50) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    lease_token_hash    VARCHAR(128) NOT NULL COMMENT '一次性任务令牌摘要，不存明文',
    accepted_time       BIGINT,
    expire_time         BIGINT NOT NULL,
    last_heartbeat_time BIGINT,
    last_event_sequence BIGINT NOT NULL DEFAULT 0,
    create_time         BIGINT,
    update_time         BIGINT,
    version             INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ai_runner_lease_active_task (task_id, status),
    INDEX idx_ai_runner_lease_runner_status (runner_id, status),
    INDEX idx_ai_runner_lease_expire (expire_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI Web UI Runner 任务租约';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
