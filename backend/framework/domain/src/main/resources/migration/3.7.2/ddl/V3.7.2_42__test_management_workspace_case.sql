SET SESSION innodb_lock_wait_timeout = 7200;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND COLUMN_NAME = 'workspace_id') = 0,
    'ALTER TABLE functional_case ADD COLUMN workspace_id VARCHAR(50) NULL COMMENT ''工作空间ID，历史数据回填为项目所属组织'' AFTER id',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND COLUMN_NAME = 'system_id') = 0,
    'ALTER TABLE functional_case ADD COLUMN system_id VARCHAR(50) NULL COMMENT ''所属业务系统ID'' AFTER project_id',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND COLUMN_NAME = 'system_module_id') = 0,
    'ALTER TABLE functional_case ADD COLUMN system_module_id VARCHAR(50) NULL COMMENT ''所属业务系统模块ID'' AFTER system_id',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE functional_case fc
LEFT JOIN project p ON p.id = fc.project_id
SET fc.workspace_id = COALESCE(fc.workspace_id, p.organization_id)
WHERE fc.workspace_id IS NULL
  AND fc.project_id IS NOT NULL
  AND fc.project_id <> '';

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND COLUMN_NAME = 'project_id'
       AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE functional_case MODIFY COLUMN project_id VARCHAR(50) NULL COMMENT ''项目ID，工作空间用例可为空''',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND COLUMN_NAME = 'module_id'
       AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE functional_case MODIFY COLUMN module_id VARCHAR(50) NULL COMMENT ''项目模块ID，无所属项目用例可为空''',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS functional_business_system (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  workspace_id VARCHAR(50) NOT NULL COMMENT '工作空间ID',
  name VARCHAR(255) NOT NULL COMMENT '系统名称',
  code VARCHAR(128) DEFAULT NULL COMMENT '系统编码',
  description VARCHAR(1000) DEFAULT NULL COMMENT '描述',
  enabled BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  create_user VARCHAR(50) NOT NULL COMMENT '创建人',
  update_user VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  create_time BIGINT NOT NULL COMMENT '创建时间',
  update_time BIGINT NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_functional_business_system_workspace_name (workspace_id, name),
  KEY idx_functional_business_system_workspace (workspace_id),
  KEY idx_functional_business_system_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试用例业务系统';

CREATE TABLE IF NOT EXISTS functional_system_module (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  workspace_id VARCHAR(50) NOT NULL COMMENT '工作空间ID',
  system_id VARCHAR(50) NOT NULL COMMENT '业务系统ID',
  name VARCHAR(255) NOT NULL COMMENT '模块名称',
  parent_id VARCHAR(50) NOT NULL DEFAULT 'NONE' COMMENT '父级模块ID',
  pos BIGINT NOT NULL DEFAULT 0 COMMENT '排序',
  create_user VARCHAR(50) NOT NULL COMMENT '创建人',
  update_user VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  create_time BIGINT NOT NULL COMMENT '创建时间',
  update_time BIGINT NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_functional_system_module_system_parent_name (system_id, parent_id, name),
  KEY idx_functional_system_module_workspace (workspace_id),
  KEY idx_functional_system_module_system (system_id),
  KEY idx_functional_system_module_parent (parent_id),
  KEY idx_functional_system_module_pos (pos)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试用例业务系统模块';

CREATE TABLE IF NOT EXISTS functional_case_system_relation (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  case_id VARCHAR(50) NOT NULL COMMENT '用例ID',
  system_id VARCHAR(50) NOT NULL COMMENT '业务系统ID',
  system_module_id VARCHAR(50) DEFAULT NULL COMMENT '业务系统模块ID',
  create_user VARCHAR(50) NOT NULL COMMENT '创建人',
  create_time BIGINT NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_functional_case_system_relation_case_system (case_id, system_id),
  KEY idx_functional_case_system_relation_case (case_id),
  KEY idx_functional_case_system_relation_system (system_id),
  KEY idx_functional_case_system_relation_module (system_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='测试用例与业务系统关系';

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND INDEX_NAME = 'idx_functional_case_workspace') = 0,
    'ALTER TABLE functional_case ADD INDEX idx_functional_case_workspace (workspace_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND INDEX_NAME = 'idx_functional_case_workspace_project') = 0,
    'ALTER TABLE functional_case ADD INDEX idx_functional_case_workspace_project (workspace_id, project_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND INDEX_NAME = 'idx_functional_case_workspace_system') = 0,
    'ALTER TABLE functional_case ADD INDEX idx_functional_case_workspace_system (workspace_id, system_id, system_module_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
