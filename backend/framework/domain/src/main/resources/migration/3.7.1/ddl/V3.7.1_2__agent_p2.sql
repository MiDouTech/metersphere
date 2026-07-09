SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS agent_module_alias (
    id           VARCHAR(50)  NOT NULL PRIMARY KEY,
    project_id   VARCHAR(50)  NOT NULL,
    alias        VARCHAR(50)  NOT NULL COMMENT '别名，如 CW',
    module_id    VARCHAR(50)  NOT NULL COMMENT '目标模块 ID',
    create_time  BIGINT,
    create_user  VARCHAR(50),
    UNIQUE KEY uk_project_alias (project_id, alias)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Agent 模块别名';

CREATE TABLE IF NOT EXISTS agent_exec_attachment (
    id              VARCHAR(50) NOT NULL PRIMARY KEY,
    exec_history_id VARCHAR(50) COMMENT '计划内执行历史 ID',
    exec_log_id     VARCHAR(50) COMMENT '计划外审计日志 ID',
    file_id         VARCHAR(50) NOT NULL COMMENT '平台临时文件 ID',
    file_name       VARCHAR(255),
    step_num        INT,
    create_time     BIGINT,
    create_user     VARCHAR(50)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'Agent 执行证据附件';

CREATE INDEX idx_agent_exec_attachment_history ON agent_exec_attachment (exec_history_id);
CREATE INDEX idx_agent_exec_attachment_log ON agent_exec_attachment (exec_log_id);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
