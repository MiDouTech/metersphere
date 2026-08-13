-- AI generated cases must pass explicit human review before publication.
ALTER TABLE functional_case_ai_draft
    ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW'
        COMMENT 'PENDING_REVIEW/APPROVED/CHANGES_REQUESTED/REJECTED' AFTER draft_status,
    ADD COLUMN review_comment VARCHAR(2000) NULL COMMENT 'Latest review comment' AFTER review_status,
    ADD COLUMN reviewed_by VARCHAR(50) NULL AFTER review_comment,
    ADD COLUMN reviewed_at BIGINT NULL AFTER reviewed_by,
    ADD COLUMN publish_mode VARCHAR(16) NOT NULL DEFAULT 'CREATE'
        COMMENT 'CREATE/UPDATE/DEPRECATE' AFTER reviewed_at,
    ADD COLUMN target_case_id VARCHAR(50) NULL COMMENT 'Target formal case for UPDATE/DEPRECATE' AFTER publish_mode,
    ADD COLUMN baseline_snapshot MEDIUMTEXT NULL COMMENT 'Immutable target baseline used for diff review' AFTER target_case_id,
    ADD COLUMN content_hash VARCHAR(128) NULL COMMENT 'Reviewed draft content hash' AFTER baseline_snapshot,
    ADD COLUMN reviewed_content_hash VARCHAR(128) NULL COMMENT 'Hash approved by reviewer' AFTER content_hash,
    ADD INDEX idx_fc_ai_draft_review (project_id, review_status, draft_status),
    ADD INDEX idx_fc_ai_draft_target (project_id, target_case_id);

CREATE TABLE functional_case_ai_review_history
(
    id                  VARCHAR(50)   NOT NULL,
    draft_id            VARCHAR(50)   NOT NULL,
    project_id          VARCHAR(50)   NOT NULL,
    action              VARCHAR(32)   NOT NULL COMMENT 'SUBMIT/APPROVE/REQUEST_CHANGES/REJECT/PUBLISH',
    comment             VARCHAR(2000) NULL,
    content_hash        VARCHAR(128)  NULL,
    reviewer            VARCHAR(50)   NOT NULL,
    create_time         BIGINT        NOT NULL,
    PRIMARY KEY (id),
    KEY idx_fc_ai_review_history_draft (draft_id, create_time),
    KEY idx_fc_ai_review_history_project (project_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'AI generated case review and publication audit';
