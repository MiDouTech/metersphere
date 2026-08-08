SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS ai_case_conversation (
    id                    VARCHAR(50)  NOT NULL COMMENT 'Conversation ID',
    project_id            VARCHAR(50)  NOT NULL COMMENT 'Project ID',
    organization_id       VARCHAR(50)  NOT NULL COMMENT 'Organization ID',
    user_id               VARCHAR(50)  NOT NULL COMMENT 'Conversation owner',
    title                 VARCHAR(255) NOT NULL COMMENT 'Conversation title',
    model_source_id       VARCHAR(50)  NOT NULL COMMENT 'Model selected for subsequent messages',
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED/DELETED',
    system_prompt_version VARCHAR(64)  NOT NULL DEFAULT 'case-agent-v1' COMMENT 'System prompt version',
    last_message_time     BIGINT       DEFAULT NULL,
    create_time           BIGINT       NOT NULL,
    update_time           BIGINT       NOT NULL,
    deleted               TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_case_conversation_owner_time (project_id, user_id, update_time),
    KEY idx_ai_case_conversation_owner_status (project_id, user_id, status, deleted),
    KEY idx_ai_case_conversation_model (model_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Functional case generation Agent conversations';

CREATE TABLE IF NOT EXISTS ai_case_message (
    id                 VARCHAR(50) NOT NULL,
    conversation_id    VARCHAR(50) NOT NULL,
    project_id         VARCHAR(50) NOT NULL,
    user_id            VARCHAR(50) NOT NULL,
    role               VARCHAR(16) NOT NULL COMMENT 'SYSTEM/USER/ASSISTANT/TOOL',
    content            LONGTEXT    DEFAULT NULL,
    status             VARCHAR(32) NOT NULL COMMENT 'STREAMING/COMPLETED/FAILED/CANCELED',
    model_source_id    VARCHAR(50) DEFAULT NULL COMMENT 'Actual model used by this message',
    request_id         VARCHAR(64) DEFAULT NULL,
    tool_name          VARCHAR(128) DEFAULT NULL,
    tool_call_id       VARCHAR(128) DEFAULT NULL,
    tool_arguments     LONGTEXT DEFAULT NULL COMMENT 'Sanitized tool arguments',
    tool_result        LONGTEXT DEFAULT NULL COMMENT 'Sanitized tool result',
    input_tokens       BIGINT NOT NULL DEFAULT 0,
    output_tokens      BIGINT NOT NULL DEFAULT 0,
    token_estimated    TINYINT(1) NOT NULL DEFAULT 0,
    error_code         VARCHAR(64) DEFAULT NULL,
    create_time        BIGINT NOT NULL,
    update_time        BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_case_message_conversation_time (conversation_id, create_time, id),
    KEY idx_ai_case_message_owner_request (project_id, user_id, request_id),
    KEY idx_ai_case_message_tool_call (conversation_id, tool_call_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Functional case generation Agent messages';

CREATE TABLE IF NOT EXISTS ai_case_execution (
    id                         VARCHAR(50) NOT NULL,
    request_id                 VARCHAR(64) NOT NULL,
    conversation_id            VARCHAR(50) NOT NULL,
    project_id                 VARCHAR(50) NOT NULL,
    user_id                    VARCHAR(50) NOT NULL,
    user_message_id            VARCHAR(50) DEFAULT NULL,
    assistant_message_id       VARCHAR(50) DEFAULT NULL,
    execution_type             VARCHAR(32) NOT NULL DEFAULT 'CHAT',
    status                     VARCHAR(32) NOT NULL COMMENT 'CREATED/RUNNING/WAITING_CONFIRMATION/COMPLETED/FAILED/CANCELED',
    requested_model_source_id  VARCHAR(50) NOT NULL,
    actual_model_source_id     VARCHAR(50) DEFAULT NULL,
    cancel_requested           TINYINT(1) NOT NULL DEFAULT 0,
    retry_of_request_id        VARCHAR(64) DEFAULT NULL,
    input_tokens               BIGINT NOT NULL DEFAULT 0,
    output_tokens              BIGINT NOT NULL DEFAULT 0,
    token_estimated            TINYINT(1) NOT NULL DEFAULT 0,
    error_code                 VARCHAR(64) DEFAULT NULL,
    error_message              VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized error message',
    start_time                 BIGINT DEFAULT NULL,
    first_token_time           BIGINT DEFAULT NULL,
    finish_time                BIGINT DEFAULT NULL,
    duration_ms                BIGINT DEFAULT NULL,
    event_sequence             BIGINT NOT NULL DEFAULT 0 COMMENT 'Last persisted SSE event sequence',
    create_time                BIGINT NOT NULL,
    update_time                BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_case_execution_request (request_id),
    KEY idx_ai_case_execution_conversation_status (conversation_id, status),
    KEY idx_ai_case_execution_project_status (project_id, status),
    KEY idx_ai_case_execution_owner_time (project_id, user_id, create_time),
    KEY idx_ai_case_execution_retry (retry_of_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Functional case generation Agent executions';

CREATE TABLE IF NOT EXISTS ai_case_execution_event (
    id            VARCHAR(50) NOT NULL,
    request_id    VARCHAR(64) NOT NULL,
    sequence_no   BIGINT      NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    payload       LONGTEXT    DEFAULT NULL COMMENT 'Sanitized event payload',
    create_time   BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_case_event_request_sequence (request_id, sequence_no),
    KEY idx_ai_case_event_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Recoverable Agent execution events';

CREATE TABLE IF NOT EXISTS ai_case_tool_call (
    id                    VARCHAR(50)  NOT NULL,
    request_id            VARCHAR(64)  NOT NULL,
    conversation_id       VARCHAR(50)  NOT NULL,
    project_id            VARCHAR(50)  NOT NULL,
    user_id               VARCHAR(50)  NOT NULL,
    tool_call_id          VARCHAR(128) NOT NULL,
    tool_name             VARCHAR(128) NOT NULL,
    arguments_hash        VARCHAR(64)  NOT NULL,
    arguments_json        LONGTEXT DEFAULT NULL COMMENT 'Sanitized tool arguments',
    result_json           LONGTEXT DEFAULT NULL COMMENT 'Sanitized tool result',
    status                VARCHAR(32) NOT NULL COMMENT 'CREATED/WAITING_CONFIRMATION/RUNNING/SUCCEEDED/FAILED/CANCELED',
    confirmation_required TINYINT(1) NOT NULL DEFAULT 0,
    confirmed_user        VARCHAR(50) DEFAULT NULL,
    confirmed_time        BIGINT DEFAULT NULL,
    error_code            VARCHAR(64) DEFAULT NULL,
    create_time           BIGINT NOT NULL,
    update_time           BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_case_tool_call (conversation_id, tool_call_id),
    KEY idx_ai_case_tool_request (request_id),
    KEY idx_ai_case_tool_owner_status (project_id, user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Idempotent and confirmable Agent tool calls';

ALTER TABLE functional_case_ai_draft
    ADD COLUMN conversation_id VARCHAR(50) DEFAULT NULL COMMENT 'Agent conversation ID' AFTER generation_id,
    ADD COLUMN request_id VARCHAR(64) DEFAULT NULL COMMENT 'Agent request ID' AFTER conversation_id,
    ADD KEY idx_fc_ai_draft_conversation (conversation_id),
    ADD KEY idx_fc_ai_draft_request (request_id);

ALTER TABLE ai_provider_usage
    ADD COLUMN conversation_id VARCHAR(50) DEFAULT NULL AFTER user_id,
    ADD COLUMN request_id VARCHAR(64) DEFAULT NULL AFTER conversation_id,
    ADD COLUMN token_estimated TINYINT(1) NOT NULL DEFAULT 0 AFTER total_tokens,
    ADD KEY idx_ai_usage_conversation_time (conversation_id, create_time),
    ADD KEY idx_ai_usage_request (request_id);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
