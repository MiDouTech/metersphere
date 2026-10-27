CREATE TABLE functional_import_followup_group (
 id VARCHAR(50) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL,
 plan_id VARCHAR(50) NOT NULL, review_id VARCHAR(50) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 1, create_user VARCHAR(50) NOT NULL, create_time BIGINT NOT NULL,
 KEY idx_followup_group_project(project_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE functional_import_followup (
 job_id VARCHAR(50) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL, user_id VARCHAR(50) NOT NULL,
 policy VARCHAR(32) NOT NULL, request_json MEDIUMTEXT NOT NULL, group_id VARCHAR(50) NOT NULL,
 plan_id VARCHAR(50) NULL, review_id VARCHAR(50) NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING', error_message VARCHAR(1000) NULL,
 create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 KEY idx_followup_state(state,update_time,job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE functional_import_followup_item (
 job_id VARCHAR(50) NOT NULL, case_id VARCHAR(50) NOT NULL,
 plan_done BIT(1) NOT NULL DEFAULT b'0', review_done BIT(1) NOT NULL DEFAULT b'0',
 PRIMARY KEY(job_id,case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
