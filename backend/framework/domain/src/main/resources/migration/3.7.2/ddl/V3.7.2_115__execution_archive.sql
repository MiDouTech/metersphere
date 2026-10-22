CREATE TABLE agent_execution_archive (
 id VARCHAR(50) PRIMARY KEY,
 source_type VARCHAR(16) NOT NULL,
 source_id VARCHAR(50) NOT NULL,
 run_id VARCHAR(50) NOT NULL,
 project_id VARCHAR(50) NOT NULL,
 user_id VARCHAR(50) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 data_payload MEDIUMTEXT NULL,
 data_user_id VARCHAR(50) NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 document_id VARCHAR(50) NULL,
 dataset_id VARCHAR(50) NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt BIGINT NOT NULL,
 create_time BIGINT NOT NULL,
 update_time BIGINT NOT NULL,
 UNIQUE KEY uk_execution_archive(source_type,run_id),
 KEY ix_execution_archive_work(state,next_attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE agent_execution_archive_baseline (id INT PRIMARY KEY,since_time BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
INSERT INTO agent_execution_archive_baseline VALUES (1,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED));
