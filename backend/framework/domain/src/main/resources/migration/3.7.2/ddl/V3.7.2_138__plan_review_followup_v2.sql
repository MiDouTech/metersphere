-- Separate ownership: old workers cannot select new protocol jobs by ignoring a version predicate.
-- Existing v1 rows are left untouched; adoption requires the explicit migration/fencing procedure.
CREATE TABLE plan_review_import_followup (
 job_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 user_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 operation_id VARCHAR(50) COLLATE utf8mb4_bin NULL,
 policy VARCHAR(32) NOT NULL, request_json MEDIUMTEXT NOT NULL,
 group_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 plan_id VARCHAR(50) COLLATE utf8mb4_bin NULL,
 review_id VARCHAR(50) COLLATE utf8mb4_bin NULL,
 relation_id VARCHAR(50) COLLATE utf8mb4_bin NULL,
 generation VARCHAR(50) COLLATE utf8mb4_bin NULL,
 protocol_version INT NOT NULL DEFAULT 2,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING', error_message VARCHAR(1000) NULL,
 create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 source_kind VARCHAR(16) NOT NULL DEFAULT 'FILE',
 KEY idx_followup_state(state,update_time,job_id),
 KEY idx_followup_relation(relation_id,generation,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE plan_review_import_followup_item (
 job_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 case_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 plan_done BIT(1) NOT NULL DEFAULT 0, review_done BIT(1) NOT NULL DEFAULT 0,
 PRIMARY KEY(job_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
