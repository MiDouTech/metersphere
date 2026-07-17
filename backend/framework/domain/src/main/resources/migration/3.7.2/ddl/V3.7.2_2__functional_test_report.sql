SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE IF NOT EXISTS functional_test_report (
  id              VARCHAR(50)  NOT NULL COMMENT '主键',
  project_id      VARCHAR(50)  NOT NULL COMMENT '项目ID',
  name            VARCHAR(255) NOT NULL COMMENT '报告名称',
  plan_id         VARCHAR(50)           DEFAULT NULL COMMENT '关联测试计划ID，可空',
  content         LONGTEXT     NOT NULL COMMENT '报告正文 JSON 分节',
  stats_snapshot  LONGTEXT              DEFAULT NULL COMMENT '统计快照 JSON',
  create_time     BIGINT       NOT NULL,
  update_time     BIGINT       NOT NULL,
  create_user     VARCHAR(50)  NOT NULL,
  update_user     VARCHAR(50)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_project_id (project_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT='功能测试报告';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
