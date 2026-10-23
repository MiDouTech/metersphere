ALTER TABLE functional_case_comment
    ADD COLUMN deleted BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at BIGINT NULL,
    ADD COLUMN deleted_by VARCHAR(50) NULL,
    ADD KEY idx_case_comment_page (case_id, parent_id, create_time, id);

CREATE TABLE functional_case_comment_audit (
    id VARCHAR(50) PRIMARY KEY,
    comment_id VARCHAR(50) NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    operator_id VARCHAR(50) NOT NULL,
    action VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    before_hash CHAR(64) NOT NULL,
    after_hash CHAR(64) NOT NULL,
    create_time BIGINT NOT NULL,
    UNIQUE KEY uk_comment_audit_event (comment_id, version, action),
    KEY idx_comment_audit_case (case_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
