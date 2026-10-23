CREATE TABLE functional_import_job (
 id VARCHAR(50) PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL,
 user_id VARCHAR(50) NOT NULL,
 token_id VARCHAR(50) NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 request_json MEDIUMTEXT NOT NULL,
 template_hash VARCHAR(64) NOT NULL,
 source_hash VARCHAR(64) NOT NULL,
 source_name VARCHAR(255) NOT NULL,
 source_bytes LONGBLOB NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PRECHECKED',
 create_time BIGINT NOT NULL,
 update_time BIGINT NOT NULL,
 expires_at BIGINT NOT NULL,
 UNIQUE KEY uk_import_request(user_id,token_id,request_id),
 KEY ix_import_work(state,update_time),
 KEY ix_import_owner(user_id,project_id,create_time)
);
CREATE TABLE functional_import_item (
 job_id VARCHAR(50) NOT NULL,
 position INT NOT NULL,
 action VARCHAR(16) NOT NULL,
 payload MEDIUMTEXT NULL,
 target_id VARCHAR(50) NULL,
 expected_version BIGINT NULL,
 state VARCHAR(24) NOT NULL,
 message VARCHAR(1000) NULL,
 PRIMARY KEY(job_id,position),
 CONSTRAINT fk_import_job FOREIGN KEY(job_id) REFERENCES functional_import_job(id) ON DELETE CASCADE
);
