SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS ai_execution_task (
    id                 VARCHAR(50)  NOT NULL PRIMARY KEY,
    organization_id    VARCHAR(50)           COMMENT '组织 ID',
    project_id         VARCHAR(50)  NOT NULL COMMENT '项目 ID',
    test_plan_id       VARCHAR(50)           COMMENT '测试计划 ID；计划外任务为空',
    source             VARCHAR(32)  NOT NULL COMMENT '来源：MCP/CASE_LIST/WORKBENCH/API',
    status             VARCHAR(32)  NOT NULL COMMENT 'CREATED/WAITING_CONFIRMATION/PREPARING_BROWSER/WAITING_LOGIN/RUNNING/WRITING_BACK/SUCCESS/PARTIAL_SUCCESS/FAILED/CANCELED',
    runner_id          VARCHAR(50)           COMMENT 'Runner ID',
    provider_id        VARCHAR(50)           COMMENT 'AI Provider ID',
    environment_id     VARCHAR(50)           COMMENT '环境 ID',
    target_url         VARCHAR(1024)         COMMENT '目标访问地址',
    browser_type       VARCHAR(50)           COMMENT '浏览器类型',
    login_mode         VARCHAR(50)           COMMENT '登录方式',
    idempotency_key    VARCHAR(128)          COMMENT '创建幂等键',
    confirm_required   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否需要确认',
    confirmation_reason VARCHAR(512)         COMMENT '确认原因',
    total_count        INT         NOT NULL DEFAULT 0,
    success_count      INT         NOT NULL DEFAULT 0,
    failed_count       INT         NOT NULL DEFAULT 0,
    blocked_count      INT         NOT NULL DEFAULT 0,
    skipped_count      INT         NOT NULL DEFAULT 0,
    unexecuted_count   INT         NOT NULL DEFAULT 0,
    executed_by        VARCHAR(100)          COMMENT 'Agent 标识',
    create_time        BIGINT,
    update_time        BIGINT,
    create_user        VARCHAR(50),
    update_user        VARCHAR(50),
    UNIQUE KEY uk_ai_execution_task_idempotency (project_id, create_user, idempotency_key),
    INDEX idx_ai_execution_task_project_status (project_id, status),
    INDEX idx_ai_execution_task_plan (test_plan_id),
    INDEX idx_ai_execution_task_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 自动化执行任务';

CREATE TABLE IF NOT EXISTS ai_execution_case (
    id                  VARCHAR(50) NOT NULL PRIMARY KEY,
    task_id             VARCHAR(50) NOT NULL,
    project_id          VARCHAR(50) NOT NULL,
    case_id             VARCHAR(50) NOT NULL,
    test_plan_id        VARCHAR(50),
    test_plan_case_id   VARCHAR(50),
    status              VARCHAR(32) NOT NULL COMMENT 'CREATED/RUNNING/WRITING_BACK/SUCCESS/FAILED/BLOCKED/SKIPPED/CANCELED',
    result              VARCHAR(32),
    pos                 INT         NOT NULL DEFAULT 0,
    retry_count         INT         NOT NULL DEFAULT 0,
    error_message       VARCHAR(1024),
    last_event_sequence BIGINT      NOT NULL DEFAULT 0,
    create_time         BIGINT,
    update_time         BIGINT,
    UNIQUE KEY uk_ai_execution_case_task_case (task_id, case_id),
    INDEX idx_ai_execution_case_task_status (task_id, status),
    INDEX idx_ai_execution_case_case (case_id),
    INDEX idx_ai_execution_case_plan_case (test_plan_case_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 自动化执行任务用例快照';

CREATE TABLE IF NOT EXISTS ai_execution_event (
    id                 VARCHAR(50) NOT NULL PRIMARY KEY,
    task_id            VARCHAR(50) NOT NULL,
    case_id            VARCHAR(50),
    step_id            VARCHAR(50),
    sequence           BIGINT      NOT NULL,
    event_time         BIGINT      NOT NULL,
    level              VARCHAR(16) NOT NULL,
    event_type         VARCHAR(64) NOT NULL,
    message            VARCHAR(2048),
    artifact_ids       TEXT,
    sanitized_metadata MEDIUMTEXT,
    create_user        VARCHAR(50),
    UNIQUE KEY uk_ai_execution_event_task_seq (task_id, sequence),
    INDEX idx_ai_execution_event_task_time (task_id, event_time),
    INDEX idx_ai_execution_event_case (case_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 自动化执行结构化事件日志';

CREATE TABLE IF NOT EXISTS ai_runner_session (
    runner_id         VARCHAR(50) NOT NULL PRIMARY KEY,
    user_id           VARCHAR(50) NOT NULL,
    domain            VARCHAR(255),
    status            VARCHAR(32) NOT NULL,
    lease_expire_time BIGINT,
    create_time       BIGINT,
    update_time       BIGINT,
    INDEX idx_ai_runner_session_user_domain (user_id, domain),
    INDEX idx_ai_runner_session_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 自动化执行 Runner 会话租约';

CREATE TABLE IF NOT EXISTS ai_credential_reference (
    id             VARCHAR(50) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(50),
    project_id     VARCHAR(50) NOT NULL,
    environment_id VARCHAR(50),
    domain         VARCHAR(255) NOT NULL,
    secret_ref     VARCHAR(255) NOT NULL COMMENT '密钥管理系统引用，不存明文',
    policy         TEXT,
    enable         TINYINT(1) NOT NULL DEFAULT 1,
    create_time    BIGINT,
    update_time    BIGINT,
    create_user    VARCHAR(50),
    update_user    VARCHAR(50),
    INDEX idx_ai_credential_reference_project_domain (project_id, domain),
    INDEX idx_ai_credential_reference_env (environment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 自动化执行凭据引用';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
