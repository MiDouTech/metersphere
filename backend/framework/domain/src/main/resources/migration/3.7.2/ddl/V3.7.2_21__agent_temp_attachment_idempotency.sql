SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS agent_temp_attachment (
    id           VARCHAR(50)  NOT NULL PRIMARY KEY,
    token_id     VARCHAR(50)  NOT NULL COMMENT 'Agent Token ID',
    user_id      VARCHAR(50)  NOT NULL COMMENT '上传用户 ID',
    project_id   VARCHAR(50)  NOT NULL COMMENT '项目 ID',
    file_id      VARCHAR(50)  NOT NULL COMMENT '临时文件 ID',
    file_name    VARCHAR(255),
    content_type VARCHAR(128),
    size         BIGINT       DEFAULT 0,
    purpose      VARCHAR(32)  NOT NULL COMMENT 'CASE_DETAIL/CASE_COMMENT/BUG_DETAIL/BUG_COMMENT/EXECUTION',
    step_num     INT,
    linked       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已关联到业务资源',
    expires_at   BIGINT       NOT NULL COMMENT '过期时间毫秒',
    create_time  BIGINT,
    INDEX idx_agent_temp_attachment_expires (expires_at),
    INDEX idx_agent_temp_attachment_project (project_id),
    INDEX idx_agent_temp_attachment_token (token_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Agent 通用临时附件';

CREATE TABLE IF NOT EXISTS agent_idempotency_record (
    id            VARCHAR(50)  NOT NULL PRIMARY KEY,
    token_id      VARCHAR(50)  NOT NULL,
    tool_name     VARCHAR(128) NOT NULL,
    request_id    VARCHAR(128) NOT NULL,
    request_hash  VARCHAR(64)  NOT NULL COMMENT '参数摘要，用于冲突检测',
    response_json MEDIUMTEXT,
    create_time   BIGINT,
    UNIQUE KEY uk_agent_idempotency_token_tool_req (token_id, tool_name, request_id),
    INDEX idx_agent_idempotency_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Agent MCP 写操作幂等记录';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
