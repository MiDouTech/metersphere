CREATE TABLE IF NOT EXISTS case_asset_catalog (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  organization_id VARCHAR(50) NOT NULL,
  name VARCHAR(255) NOT NULL,
  normalized_name VARCHAR(255) NOT NULL,
  hub_module_id VARCHAR(50) NOT NULL,
  source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL|PROJECT|MIGRATION',
  manually_renamed BIT(1) NOT NULL DEFAULT b'0',
  deleted BIT(1) NOT NULL DEFAULT b'0',
  active_normalized_name VARCHAR(255) GENERATED ALWAYS AS
    (CASE WHEN deleted = b'0' THEN normalized_name ELSE NULL END) STORED,
  create_user VARCHAR(50) DEFAULT NULL,
  update_user VARCHAR(50) DEFAULT NULL,
  delete_user VARCHAR(50) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  delete_time BIGINT DEFAULT NULL,
  UNIQUE KEY uk_case_asset_catalog_org_name (organization_id, active_normalized_name),
  UNIQUE KEY uk_case_asset_catalog_module (hub_module_id),
  KEY idx_case_asset_catalog_org (organization_id, deleted, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例资产目录';

CREATE TABLE IF NOT EXISTS case_asset_catalog_project_rel (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  catalog_id VARCHAR(50) NOT NULL,
  project_id VARCHAR(50) NOT NULL,
  relation_type VARCHAR(20) NOT NULL DEFAULT 'NAME_MATCH' COMMENT 'NAME_MATCH|MANUAL',
  create_user VARCHAR(50) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  UNIQUE KEY uk_case_asset_catalog_project (project_id),
  KEY idx_case_asset_rel_catalog (catalog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例资产目录与业务项目关系';

CREATE TABLE IF NOT EXISTS case_asset_lineage (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  source_case_id VARCHAR(50) NOT NULL,
  target_case_id VARCHAR(50) NOT NULL,
  target_project_id VARCHAR(50) NOT NULL,
  import_batch_id VARCHAR(50) DEFAULT NULL,
  conflict_strategy VARCHAR(20) DEFAULT NULL,
  create_user VARCHAR(50) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  UNIQUE KEY uk_case_asset_lineage_target (target_case_id),
  KEY idx_case_asset_lineage_source (source_case_id),
  KEY idx_case_asset_lineage_project (target_project_id),
  KEY idx_case_asset_lineage_batch (import_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资产源用例至项目副本血缘';

CREATE TABLE IF NOT EXISTS case_asset_import_job (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  catalog_id VARCHAR(50) DEFAULT NULL,
  target_project_id VARCHAR(50) DEFAULT NULL,
  file_name VARCHAR(500) DEFAULT NULL,
  conflict_strategy VARCHAR(20) DEFAULT NULL,
  status VARCHAR(20) NOT NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  error_detail MEDIUMTEXT DEFAULT NULL,
  create_user VARCHAR(50) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  KEY idx_case_asset_import_catalog (catalog_id, create_time),
  KEY idx_case_asset_import_project (target_project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例资产导入任务';

INSERT IGNORE INTO case_asset_lineage
  (id, source_case_id, target_case_id, target_project_id, import_batch_id, conflict_strategy, create_user, create_time, update_time)
SELECT UUID_SHORT(), m.hub_case_id, m.biz_case_id, m.biz_project_id, NULL, 'HISTORY', 'system', m.create_time, m.update_time
FROM default_hub_case_map m;

