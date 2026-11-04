CREATE TABLE plan_review_lifecycle_preview (
 id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 object_side VARCHAR(16) NOT NULL, object_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 actor VARCHAR(50) COLLATE utf8mb4_bin NOT NULL, action VARCHAR(32) NOT NULL,
 generation VARCHAR(50) COLLATE utf8mb4_bin NULL,
 object_revision BIGINT NOT NULL, object_state VARCHAR(32) NOT NULL,
 source_execution_revision VARCHAR(64) NULL,
 source_version BIGINT NOT NULL, target_fingerprint VARCHAR(64) NOT NULL,
 issued_at BIGINT NOT NULL, expires_at BIGINT NOT NULL,
 consumed_at BIGINT NULL,
 KEY idx_lifecycle_preview(object_side,object_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE plan_review_frozen_snapshot (
 id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 object_side VARCHAR(16) NOT NULL, object_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
 relation_id VARCHAR(50) COLLATE utf8mb4_bin NULL,
 generation VARCHAR(50) COLLATE utf8mb4_bin NULL,
 object_state VARCHAR(32) NOT NULL, source_version BIGINT NOT NULL, applied_version BIGINT NOT NULL,
 target_fingerprint VARCHAR(64) NOT NULL, snapshot LONGBLOB NOT NULL,
 actor VARCHAR(50) COLLATE utf8mb4_bin NOT NULL, create_time BIGINT NOT NULL,
 KEY idx_frozen_object(object_side,object_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
