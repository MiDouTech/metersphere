ALTER TABLE functional_case
    ADD COLUMN last_execute_user VARCHAR(50) DEFAULT NULL COMMENT '最后执行人用户ID',
    ADD INDEX idx_functional_case_last_execute_user (last_execute_user);
