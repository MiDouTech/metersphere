ALTER TABLE case_review ADD COLUMN test_plan_id VARCHAR(50) NULL COMMENT '关联测试计划ID';
CREATE INDEX idx_case_review_test_plan ON case_review (project_id, test_plan_id);
