CREATE TABLE plan_case_sync_job (
 id VARCHAR(50) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL,
 user_id VARCHAR(50) NOT NULL,
 request_id VARCHAR(128) NOT NULL,
 state VARCHAR(24) NOT NULL,
 lease_token VARCHAR(50) NULL,
 lease_until BIGINT NULL,
 create_time BIGINT NOT NULL,
 update_time BIGINT NOT NULL,
 UNIQUE KEY uq_plan_sync_request(project_id,user_id,request_id),
 KEY idx_plan_sync_due(state,lease_until,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE plan_case_sync_item (
 job_id VARCHAR(50) NOT NULL,
 plan_id VARCHAR(50) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 updated_count INT NOT NULL DEFAULT 0,
 defect_added_count INT NOT NULL DEFAULT 0,
 error_code VARCHAR(64) NULL,
 trace_id VARCHAR(50) NULL,
 PRIMARY KEY(job_id,plan_id),
 KEY idx_plan_sync_item_state(job_id,state,plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
