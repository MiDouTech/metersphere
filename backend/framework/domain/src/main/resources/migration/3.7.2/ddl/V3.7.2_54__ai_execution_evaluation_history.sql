CREATE TABLE ai_execution_evaluation_history
(
    id            VARCHAR(64)   NOT NULL PRIMARY KEY,
    task_id       VARCHAR(64)   NOT NULL,
    project_id    VARCHAR(64)   NOT NULL,
    score         DECIMAL(5,2)  NOT NULL,
    comment       VARCHAR(2000) NULL,
    evaluated_by  VARCHAR(64)   NOT NULL,
    evaluated_at  BIGINT        NOT NULL,
    KEY idx_ai_evaluation_history_task (task_id, evaluated_at),
    KEY idx_ai_evaluation_history_project (project_id, evaluated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Manual execution evaluation history';
