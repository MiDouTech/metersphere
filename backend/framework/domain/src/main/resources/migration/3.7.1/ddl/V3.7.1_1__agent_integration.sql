SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS agent_token (
    id           VARCHAR(50)  NOT NULL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL COMMENT 'Token 名称',
    token_prefix VARCHAR(10)  NOT NULL COMMENT '前缀 msat',
    token_hash   VARCHAR(128) NOT NULL COMMENT 'SHA-256(token)',
    user_id      VARCHAR(50)  NOT NULL COMMENT '关联用户',
    project_id   VARCHAR(50)           COMMENT '默认项目 ID',
    scopes       VARCHAR(255)          COMMENT '权限范围',
    expire_time  BIGINT                COMMENT '过期时间戳，NULL=永不过期',
    enable       TINYINT(1)   DEFAULT 1,
    create_time  BIGINT,
    create_user  VARCHAR(50)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Agent API Token';

CREATE INDEX idx_agent_token_hash ON agent_token (token_hash);

CREATE TABLE IF NOT EXISTS agent_exec_log (
    id                VARCHAR(50) NOT NULL PRIMARY KEY,
    case_id           VARCHAR(50) NOT NULL,
    test_plan_id      VARCHAR(50),
    test_plan_case_id VARCHAR(50),
    last_exec_result  VARCHAR(20) NOT NULL,
    executed_by       VARCHAR(100) COMMENT 'Agent 标识',
    steps_snapshot    LONGTEXT COMMENT '步骤执行快照 JSON',
    content           LONGTEXT COMMENT '执行备注',
    create_time       BIGINT,
    create_user       VARCHAR(50)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Agent 执行审计日志';

CREATE INDEX idx_agent_exec_log_case ON agent_exec_log (case_id, create_time DESC);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
