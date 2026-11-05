CREATE TABLE plan_review_takeover (
 id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL, operation_id VARCHAR(50) NOT NULL,
 operation_revision BIGINT NOT NULL, old_actor VARCHAR(50) NOT NULL,
 new_actor VARCHAR(50) NOT NULL, requested_by VARCHAR(50) NOT NULL,
 generation VARCHAR(50) NULL, approved_by VARCHAR(50) NULL,
 issued_at BIGINT NOT NULL, expires_at BIGINT NOT NULL,
 consumed_at BIGINT NULL, request_key VARCHAR(128) NULL,
 result_revision BIGINT NULL,
 credential_to_web BIT(1) NOT NULL DEFAULT 0,
 KEY idx_takeover_operation(project_id,operation_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE plan_review_operation_command (
 operation_id VARCHAR(50) NOT NULL, request_key VARCHAR(128) NOT NULL,
 actor VARCHAR(50) NOT NULL, command VARCHAR(32) NOT NULL,
 expected_revision BIGINT NOT NULL, result_json LONGTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 PRIMARY KEY(operation_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
