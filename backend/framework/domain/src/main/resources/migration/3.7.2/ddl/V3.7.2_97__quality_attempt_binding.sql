CREATE TABLE execution_quality_attempt_policy (
 execution_id VARCHAR(64) NOT NULL PRIMARY KEY,
 task_id VARCHAR(64) NOT NULL,
 policy_id VARCHAR(64) NOT NULL,
 version_no INT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 rules_json MEDIUMTEXT NOT NULL,
 bound_at BIGINT NOT NULL,
 KEY idx_quality_attempt_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
