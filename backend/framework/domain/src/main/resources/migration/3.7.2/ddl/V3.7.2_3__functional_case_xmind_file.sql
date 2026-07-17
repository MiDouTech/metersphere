SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS functional_case_xmind_file (
  id             VARCHAR(50)   NOT NULL COMMENT '主键',
  project_id     VARCHAR(50)   NOT NULL COMMENT '项目ID',
  name           VARCHAR(255)  NOT NULL COMMENT '显示名称',
  original_name  VARCHAR(255)  NOT NULL COMMENT '上传原始文件名',
  file_id        VARCHAR(50)   NOT NULL COMMENT 'MinIO 文件标识',
  size           BIGINT        NOT NULL COMMENT '字节大小',
  storage        VARCHAR(50)   NOT NULL DEFAULT 'MINIO' COMMENT '存储类型',
  create_time    BIGINT        NOT NULL,
  update_time    BIGINT        NOT NULL,
  create_user    VARCHAR(50)   NOT NULL,
  update_user    VARCHAR(50)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_project_id (project_id),
  KEY idx_update_time (update_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT='Xmind用例文件库（仅存文件资产，不解析为功能用例）';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
