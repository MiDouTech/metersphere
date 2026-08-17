-- This migration intentionally uses conditional DDL. MySQL commits ALTER TABLE statements
-- implicitly, so a failed migration must be safe to run again after Flyway removes its failed row.

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_definition' AND column_name = 'active_for_new'),
    'SELECT 1',
    'ALTER TABLE workflow_definition ADD COLUMN active_for_new BIT NOT NULL DEFAULT b''0'' COMMENT ''Accepts newly created bugs'' AFTER default_flow'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_definition' AND column_name = 'active_bug_scene'),
    'SELECT 1',
    'ALTER TABLE workflow_definition ADD COLUMN active_bug_scene VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN lifecycle = ''PUBLISHED'' AND active_for_new = b''1'' THEN scene ELSE NULL END) STORED'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE workflow_definition
SET active_for_new = CASE WHEN lifecycle = 'PUBLISHED' AND default_flow = b'1' THEN b'1' ELSE b'0' END;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'workflow_definition'
              AND index_name = 'uk_workflow_single_active_bug_scene'),
    'SELECT 1',
    'ALTER TABLE workflow_definition ADD UNIQUE KEY uk_workflow_single_active_bug_scene (active_bug_scene)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'workflow_definition'
              AND index_name = 'uk_workflow_single_published_default'),
    'ALTER TABLE workflow_definition DROP INDEX uk_workflow_single_published_default',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_definition'
              AND column_name = 'published_default_scene'),
    'ALTER TABLE workflow_definition DROP COLUMN published_default_scene',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_role' AND column_name = 'source_type'),
    'SELECT 1',
    'ALTER TABLE workflow_role ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL or WECOM_POSITION'' AFTER field_key'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_role' AND column_name = 'source_key'),
    'SELECT 1',
    'ALTER TABLE workflow_role ADD COLUMN source_key VARCHAR(255) NULL COMMENT ''Stable external source key'' AFTER source_type'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_role' AND column_name = 'match_mode'),
    'SELECT 1',
    'ALTER TABLE workflow_role ADD COLUMN match_mode VARCHAR(16) NOT NULL DEFAULT ''CONTAINS'' COMMENT ''CONTAINS or EXACT'' AFTER source_key'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'workflow_role'
              AND index_name = 'uk_workflow_role_source'),
    'SELECT 1',
    'ALTER TABLE workflow_role ADD UNIQUE KEY uk_workflow_role_source (flow_id, source_type, source_key)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS case_asset_source_relation (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    asset_case_id VARCHAR(50) NOT NULL,
    source_project_id VARCHAR(50) NOT NULL,
    source_case_id VARCHAR(50) NOT NULL,
    source_update_time BIGINT NULL,
    create_user VARCHAR(50) DEFAULT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_case_asset_source_project_case (source_project_id, source_case_id),
    KEY idx_case_asset_source_asset (asset_case_id),
    KEY idx_case_asset_source_project (source_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Source relation between historical project cases and asset cases';

-- A copied case may retain the same ref_id in several projects. Source identity is
-- therefore project + case reference, never the reference alone.
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'case_asset_source_relation'
              AND index_name = 'uk_case_asset_source_case'),
    'ALTER TABLE case_asset_source_relation DROP INDEX uk_case_asset_source_case',
    'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'case_asset_source_relation'
              AND index_name = 'uk_case_asset_source_project_case'),
    'SELECT 1',
    'ALTER TABLE case_asset_source_relation ADD UNIQUE KEY uk_case_asset_source_project_case (source_project_id, source_case_id)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS workflow_position_sync_log (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    flow_id VARCHAR(64) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    disabled_count INT NOT NULL DEFAULT 0,
    detail_json LONGTEXT NULL,
    create_user VARCHAR(50) DEFAULT NULL,
    create_time BIGINT NOT NULL,
    KEY idx_workflow_position_sync_flow_time (flow_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='WeCom workflow position synchronization result';

SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'workflow_position_sync_log' AND column_name = 'detail_json'),
    'SELECT 1',
    'ALTER TABLE workflow_position_sync_log ADD COLUMN detail_json LONGTEXT NULL AFTER disabled_count'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS case_asset_history_sync_job (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    case_created_count INT NOT NULL DEFAULT 0,
    case_updated_count INT NOT NULL DEFAULT 0,
    case_skipped_count INT NOT NULL DEFAULT 0,
    create_user VARCHAR(50) DEFAULT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    finish_time BIGINT NULL,
    KEY idx_case_asset_history_sync_org_time (organization_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Historical case asset synchronization job';

CREATE TABLE IF NOT EXISTS case_asset_history_sync_item (
    id VARCHAR(50) NOT NULL PRIMARY KEY,
    job_id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    case_created_count INT NOT NULL DEFAULT 0,
    case_updated_count INT NOT NULL DEFAULT 0,
    case_skipped_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(2000) NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    UNIQUE KEY uk_case_asset_history_sync_job_project (job_id, project_id),
    KEY idx_case_asset_history_sync_item_status (job_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Historical case asset synchronization item';
