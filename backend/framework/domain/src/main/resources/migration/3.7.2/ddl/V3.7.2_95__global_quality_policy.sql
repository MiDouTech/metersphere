-- Additive migration: legacy project policies and their grants are preserved.
CREATE TABLE execution_quality_policy_global_state (
 scope VARCHAR(16) NOT NULL PRIMARY KEY,
 current_policy_id VARCHAR(64) NULL,
 next_version INT NOT NULL DEFAULT 1,
 row_version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_global_quality_singleton CHECK (scope = 'GLOBAL')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO execution_quality_policy_global_state(scope) VALUES ('GLOBAL');
CREATE TABLE execution_quality_policy_global (
 id VARCHAR(64) NOT NULL PRIMARY KEY,
 version_no INT NOT NULL UNIQUE,
 schema_version VARCHAR(40) NOT NULL,
 status VARCHAR(24) NOT NULL,
 rules_json MEDIUMTEXT NOT NULL,
 content_hash CHAR(64) NOT NULL,
 row_version BIGINT NOT NULL DEFAULT 0,
 created_by VARCHAR(64) NOT NULL,
 created_at BIGINT NOT NULL,
 updated_by VARCHAR(64) NOT NULL,
 updated_at BIGINT NOT NULL,
 published_by VARCHAR(64) NULL,
 published_at BIGINT NULL,
 change_reason VARCHAR(1000) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE execution_quality_policy_global_publication (
 policy_id VARCHAR(64) NOT NULL PRIMARY KEY,
 previous_policy_id VARCHAR(64) NULL,
 previous_hash CHAR(64) NULL,
 published_hash CHAR(64) NOT NULL,
 actor VARCHAR(64) NOT NULL,
 published_at BIGINT NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 trace_id VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE execution_quality_policy_migration_map (
 legacy_policy_id VARCHAR(64) NOT NULL PRIMARY KEY,
 legacy_project_id VARCHAR(64) NOT NULL,
 content_hash CHAR(64) NOT NULL,
 global_policy_id VARCHAR(64) NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'INVENTORIED',
 inventoried_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- Inventory only: never pick a project's policy as the global effective standard.
INSERT INTO execution_quality_policy_migration_map
 (legacy_policy_id,legacy_project_id,content_hash,inventoried_at)
 SELECT id,project_id,content_hash,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)
 FROM execution_quality_policy;
