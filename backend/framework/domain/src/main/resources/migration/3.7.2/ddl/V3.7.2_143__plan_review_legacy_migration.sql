-- No legacy rows are moved by Flyway. Every change is bound to a reviewed snapshot.
CREATE TABLE plan_review_legacy_change (
 id VARCHAR(50) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL,
 request_key VARCHAR(128) NOT NULL,
 requested_by VARCHAR(50) NOT NULL,
 manifest_hash VARCHAR(64) NOT NULL,
 selection_json MEDIUMTEXT NOT NULL,
 snapshot_json LONGBLOB NOT NULL,
 snapshot_hash VARCHAR(64) NOT NULL,
 state VARCHAR(32) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 expires_at BIGINT NOT NULL,
 backup_at BIGINT NULL,
 rehearsal_id VARCHAR(50) NULL,
 approved_by VARCHAR(50) NULL,
 approved_at BIGINT NULL,
 stop_evidence_hash VARCHAR(64) NULL,
 compatible_release_sha VARCHAR(40) NULL,
 result_json MEDIUMTEXT NULL,
 after_hash VARCHAR(64) NULL,
 rollback_preview_hash VARCHAR(64) NULL,
 rollback_data_hash VARCHAR(64) NULL,
 rollback_expires_at BIGINT NULL,
 rollback_approved_by VARCHAR(50) NULL,
 rollback_approved_at BIGINT NULL,
 rollback_mode VARCHAR(32) NULL,
 create_time BIGINT NOT NULL,
 update_time BIGINT NOT NULL,
 UNIQUE KEY uk_legacy_change_request(project_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_legacy_group_map (
 project_id VARCHAR(50) NOT NULL,
 legacy_group_id VARCHAR(50) NOT NULL,
 change_id VARCHAR(50) NOT NULL,
 action VARCHAR(32) NOT NULL,
 original_json MEDIUMTEXT NOT NULL,
 plan_id VARCHAR(50) NULL,
 review_id VARCHAR(50) NULL,
 relation_id VARCHAR(50) NULL,
 generation VARCHAR(50) NULL,
 created_relation BIT(1) NOT NULL DEFAULT 0,
 state VARCHAR(32) NOT NULL,
 PRIMARY KEY(project_id,legacy_group_id,change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_legacy_job_map (
 project_id VARCHAR(50) NOT NULL,
 job_id VARCHAR(50) NOT NULL,
 change_id VARCHAR(50) NOT NULL,
 action VARCHAR(32) NOT NULL,
 original_state VARCHAR(32) NOT NULL,
 original_watermark_json MEDIUMTEXT NOT NULL,
 operation_id VARCHAR(50) NULL,
 reserved_plan_id VARCHAR(50) NULL,
 reserved_review_id VARCHAR(50) NULL,
 relation_id VARCHAR(50) NULL,
 generation VARCHAR(50) NULL,
 created_relation BIT(1) NOT NULL DEFAULT 0,
 state VARCHAR(32) NOT NULL,
 PRIMARY KEY(project_id,job_id,change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE plan_review_legacy_rehearsal (
 id VARCHAR(50) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL,
 change_id VARCHAR(50) NOT NULL,
 snapshot_hash VARCHAR(64) NOT NULL,
 runner VARCHAR(100) NOT NULL,
 state VARCHAR(32) NOT NULL,
 evidence_json MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 KEY idx_legacy_rehearsal(change_id,snapshot_hash,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
