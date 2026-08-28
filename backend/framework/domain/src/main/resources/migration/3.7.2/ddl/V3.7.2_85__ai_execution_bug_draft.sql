CREATE TABLE ai_execution_bug_draft
(
    id                VARCHAR(64)  NOT NULL,
    task_id           VARCHAR(64)  NOT NULL,
    execution_case_id VARCHAR(64)  NOT NULL,
    case_id           VARCHAR(64)  NOT NULL,
    bug_id            VARCHAR(64)  NULL,
    status            VARCHAR(32)  NOT NULL,
    evidence_count    INT          NOT NULL DEFAULT 0,
    error_code        VARCHAR(128) NULL,
    create_time       BIGINT       NOT NULL,
    update_time       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_execution_bug_draft_case (task_id, execution_case_id),
    KEY idx_ai_execution_bug_draft_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
