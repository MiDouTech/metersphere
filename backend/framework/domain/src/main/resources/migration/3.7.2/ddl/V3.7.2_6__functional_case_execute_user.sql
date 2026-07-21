ALTER TABLE functional_case
  ADD COLUMN execute_user VARCHAR(50) DEFAULT NULL COMMENT '执行人用户ID';
CREATE INDEX idx_functional_case_execute_user ON functional_case(execute_user);
