CREATE TABLE agent_case_evidence (
 id VARCHAR(64) NOT NULL PRIMARY KEY,
 token_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(64) NOT NULL,
 project_id VARCHAR(64) NOT NULL,
 case_id VARCHAR(64) NOT NULL,
 content_hash CHAR(64) NOT NULL,
 step_num INT NOT NULL,
 purpose VARCHAR(32) NOT NULL,
 file_name VARCHAR(255) NOT NULL,
 content_type VARCHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 payload MEDIUMBLOB NOT NULL,
 submission_id VARCHAR(64) NULL,
 create_time BIGINT NOT NULL,
 expires_at BIGINT NOT NULL,
 KEY idx_case_evidence_cleanup (submission_id, expires_at),
 KEY idx_case_evidence_owner (token_id, case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE agent_case_submission (
 id VARCHAR(64) NOT NULL PRIMARY KEY,
 token_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(64) NOT NULL,
 project_id VARCHAR(64) NOT NULL,
 case_id VARCHAR(64) NOT NULL,
 content_hash CHAR(64) NOT NULL,
 policy_hash CHAR(64) NOT NULL,
 facts MEDIUMTEXT NOT NULL,
 result VARCHAR(32) NOT NULL,
 create_time BIGINT NOT NULL,
 KEY idx_case_submission (case_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
