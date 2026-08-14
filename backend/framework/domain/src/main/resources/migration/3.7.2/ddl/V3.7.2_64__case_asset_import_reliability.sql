ALTER TABLE default_hub_sync_job
  ADD COLUMN idempotency_key VARCHAR(100) DEFAULT NULL COMMENT '客户端幂等键' AFTER scope_project_id;

CREATE UNIQUE INDEX uk_hub_sync_import_idempotency
  ON default_hub_sync_job (job_type, scope_project_id, idempotency_key);

CREATE TABLE IF NOT EXISTS case_asset_import_result (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  job_id VARCHAR(50) NOT NULL,
  source_case_id VARCHAR(50) NOT NULL,
  target_case_id VARCHAR(50) DEFAULT NULL,
  target_project_id VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'SUCCESS|SKIPPED|FAILED',
  action VARCHAR(20) DEFAULT NULL COMMENT 'CREATED|OVERWRITTEN|SKIPPED',
  error_message VARCHAR(2000) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  UNIQUE KEY uk_case_asset_import_result (job_id, source_case_id),
  KEY idx_case_asset_import_result_project (target_project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用例资产导入逐项结果';
