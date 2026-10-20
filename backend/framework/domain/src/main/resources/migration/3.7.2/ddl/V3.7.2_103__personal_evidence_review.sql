ALTER TABLE agent_case_submission
 ADD COLUMN gate_status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_STRUCTURE_ONLY',
 ADD COLUMN review_code VARCHAR(64) NULL,
 ADD COLUMN reviewer_version VARCHAR(128) NULL;

-- Historical passes are preserved, never upgraded to content-verified passes.
CREATE TABLE agent_case_submission_evidence (
 submission_id VARCHAR(64) NOT NULL,
 evidence_id VARCHAR(64) NOT NULL,
 retain_until BIGINT NOT NULL,
 PRIMARY KEY (submission_id, evidence_id),
 KEY idx_submission_evidence_retention (evidence_id, retain_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
