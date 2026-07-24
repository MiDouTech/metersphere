-- 默认项目标记 + 用例模块文件夹 + 枢纽映射表
ALTER TABLE project
  ADD COLUMN is_default BIT(1) NOT NULL DEFAULT 0 COMMENT '是否系统默认项目（米多公司默认项目）' AFTER all_resource_pool;

CREATE INDEX idx_project_is_default ON project (is_default);

ALTER TABLE functional_case_module
  ADD COLUMN module_type VARCHAR(20) NOT NULL DEFAULT 'MODULE' COMMENT 'MODULE|FOLDER' AFTER parent_id,
  ADD COLUMN ref_project_id VARCHAR(50) DEFAULT NULL COMMENT '同步业务项目ID（默认项目下文件夹）' AFTER module_type;

CREATE INDEX idx_fcm_ref_project ON functional_case_module (ref_project_id);
CREATE INDEX idx_fcm_module_type ON functional_case_module (module_type);

CREATE TABLE IF NOT EXISTS default_hub_case_map (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  biz_project_id VARCHAR(50) NOT NULL,
  biz_case_id VARCHAR(50) NOT NULL,
  hub_case_id VARCHAR(50) NOT NULL,
  content_hash VARCHAR(64) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  UNIQUE KEY uk_hub_case_biz (biz_case_id),
  KEY idx_hub_case_biz_project (biz_project_id),
  KEY idx_hub_case_hub (hub_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务用例→默认项目枢纽映射';

CREATE TABLE IF NOT EXISTS default_hub_plan_map (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  biz_project_id VARCHAR(50) NOT NULL,
  biz_plan_id VARCHAR(50) NOT NULL,
  hub_plan_id VARCHAR(50) NOT NULL,
  content_hash VARCHAR(64) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  UNIQUE KEY uk_hub_plan_biz (biz_plan_id),
  KEY idx_hub_plan_biz_project (biz_project_id),
  KEY idx_hub_plan_hub (hub_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务计划→默认项目枢纽映射';

CREATE TABLE IF NOT EXISTS default_hub_sync_job (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  job_type VARCHAR(20) NOT NULL COMMENT 'EVENT|CRON|MANUAL',
  scope_project_id VARCHAR(50) DEFAULT NULL COMMENT '空=全量',
  status VARCHAR(20) NOT NULL COMMENT 'PENDING|RUNNING|SUCCESS|FAILED',
  progress INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  error_message VARCHAR(2000) DEFAULT NULL,
  create_user VARCHAR(50) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  finish_time BIGINT DEFAULT NULL,
  KEY idx_hub_sync_status (status),
  KEY idx_hub_sync_create (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='默认项目枢纽同步任务';
