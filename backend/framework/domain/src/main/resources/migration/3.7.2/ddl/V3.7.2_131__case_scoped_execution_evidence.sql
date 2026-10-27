ALTER TABLE agent_case_evidence
 ADD COLUMN evidence_scope VARCHAR(16) NOT NULL DEFAULT 'STEP',
 ADD COLUMN definition_hash_version VARCHAR(32) NOT NULL DEFAULT 'legacy-dto-v1',
 ADD COLUMN definition_snapshot MEDIUMTEXT NULL,
 MODIFY COLUMN step_num INT NULL,
 MODIFY COLUMN purpose VARCHAR(32) NULL;
ALTER TABLE agent_case_submission
 ADD COLUMN definition_hash_version VARCHAR(32) NOT NULL DEFAULT 'legacy-dto-v1',
 ADD COLUMN facts_schema_version VARCHAR(32) NOT NULL DEFAULT 'direct-execution.v1';
CREATE TABLE agent_case_current_execution (
 case_id VARCHAR(64) NOT NULL PRIMARY KEY,
 submission_id VARCHAR(64) NOT NULL,
 execute_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
