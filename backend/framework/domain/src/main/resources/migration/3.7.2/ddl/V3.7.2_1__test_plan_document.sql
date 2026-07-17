SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS test_plan_document (
  id            VARCHAR(50)  NOT NULL COMMENT '主键',
  test_plan_id  VARCHAR(50)  NOT NULL COMMENT '测试计划ID',
  project_id    VARCHAR(50)  NOT NULL COMMENT '项目ID',
  content       LONGTEXT     NOT NULL COMMENT '文档内容',
  content_type  VARCHAR(20)  NOT NULL DEFAULT 'RICH_TEXT' COMMENT 'RICH_TEXT/MARKDOWN',
  create_time   BIGINT       NOT NULL,
  update_time   BIGINT       NOT NULL,
  create_user   VARCHAR(50)  NOT NULL,
  update_user   VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_test_plan (test_plan_id),
  KEY idx_project (project_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT='测试计划文档';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
