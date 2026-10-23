CREATE TABLE functional_case_comment_notice (
    id CHAR(64) PRIMARY KEY,
    comment_id VARCHAR(50) NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    receiver_id VARCHAR(50) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    payload LONGTEXT NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    error_code VARCHAR(64),
    KEY idx_comment_notice_pending (state, next_attempt),
    KEY idx_comment_notice_case (case_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
