ALTER TABLE workflow_migration_batch
  ADD COLUMN update_time BIGINT NULL AFTER create_time;

CREATE TABLE IF NOT EXISTS workflow_migration_item (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL,
  bug_id VARCHAR(64) NOT NULL,
  source_status_id VARCHAR(64) NOT NULL,
  source_workflow_id VARCHAR(64) NULL,
  source_workflow_version INT NULL,
  target_status_id VARCHAR(64) NOT NULL,
  target_workflow_id VARCHAR(64) NOT NULL,
  target_workflow_version INT NOT NULL,
  status VARCHAR(32) NOT NULL COMMENT 'SUCCESS|ROLLED_BACK|ROLLBACK_CONFLICT',
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  UNIQUE KEY uk_workflow_migration_item_bug (batch_id, bug_id),
  KEY idx_workflow_migration_item_status (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='缺陷流程迁移逐项快照';
