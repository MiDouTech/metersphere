-- 资源编辑锁 + 滚动快照（自动保存 / Undo ≤2 步）
CREATE TABLE IF NOT EXISTS resource_edit_lock (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  resource_type VARCHAR(50) NOT NULL COMMENT 'FUNCTIONAL_CASE|BUG|TEST_PLAN_DOCUMENT|...',
  resource_id VARCHAR(50) NOT NULL,
  project_id VARCHAR(50) NOT NULL,
  holder_user_id VARCHAR(50) NOT NULL,
  holder_user_name VARCHAR(255) DEFAULT NULL,
  expire_time BIGINT NOT NULL COMMENT '过期时间戳毫秒',
  create_time BIGINT NOT NULL,
  update_time BIGINT NOT NULL,
  UNIQUE KEY uk_resource_edit_lock (resource_type, resource_id),
  KEY idx_rel_expire (expire_time),
  KEY idx_rel_holder (holder_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源编辑锁';

CREATE TABLE IF NOT EXISTS resource_edit_snapshot (
  id VARCHAR(50) NOT NULL PRIMARY KEY,
  resource_type VARCHAR(50) NOT NULL,
  resource_id VARCHAR(50) NOT NULL,
  project_id VARCHAR(50) NOT NULL,
  seq BIGINT NOT NULL COMMENT '单调序号',
  payload MEDIUMTEXT NOT NULL COMMENT '整单 JSON 快照',
  content_hash VARCHAR(64) DEFAULT NULL,
  create_user VARCHAR(50) DEFAULT NULL,
  create_time BIGINT NOT NULL,
  UNIQUE KEY uk_res_snap_seq (resource_type, resource_id, seq),
  KEY idx_res_snap_resource (resource_type, resource_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源编辑滚动快照';

CREATE TABLE IF NOT EXISTS resource_edit_pointer (
  resource_type VARCHAR(50) NOT NULL,
  resource_id VARCHAR(50) NOT NULL,
  active_seq BIGINT NOT NULL DEFAULT 0 COMMENT '当前 Undo 指针对应快照 seq',
  update_time BIGINT NOT NULL,
  PRIMARY KEY (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源编辑 Undo 指针';
