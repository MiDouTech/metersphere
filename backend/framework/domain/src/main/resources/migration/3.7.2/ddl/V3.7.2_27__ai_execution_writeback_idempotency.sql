SET SESSION innodb_lock_wait_timeout = 7200;

-- 单条结果回写幂等去重：同一任务下相同幂等键只允许成功落一次正式回写
CREATE TABLE IF NOT EXISTS ai_execution_writeback_idempotency (
    id                 VARCHAR(50)  NOT NULL PRIMARY KEY,
    task_id            VARCHAR(50)  NOT NULL COMMENT 'AI 执行任务 ID',
    case_id            VARCHAR(50)  NOT NULL COMMENT '功能用例 ID',
    idempotency_key    VARCHAR(128) NOT NULL COMMENT '回写幂等键',
    project_id         VARCHAR(50)  NOT NULL,
    last_exec_result   VARCHAR(32),
    create_user        VARCHAR(50),
    create_time        BIGINT       NOT NULL,
    UNIQUE KEY uk_ai_execution_writeback_idem (task_id, case_id, idempotency_key),
    INDEX idx_ai_execution_writeback_task (task_id),
    INDEX idx_ai_execution_writeback_case (case_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 自动化执行结果回写幂等记录';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
