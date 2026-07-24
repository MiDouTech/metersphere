-- 从枢纽导入的副本审计字段（不参与回写同步）
ALTER TABLE functional_case
  ADD COLUMN imported_from_hub_case_id VARCHAR(50) DEFAULT NULL COMMENT '导入源枢纽用例ID' AFTER latest;

CREATE INDEX idx_fc_imported_hub ON functional_case (imported_from_hub_case_id);

ALTER TABLE test_plan
  ADD COLUMN imported_from_hub_plan_id VARCHAR(50) DEFAULT NULL COMMENT '导入源枢纽计划ID' AFTER pos;

CREATE INDEX idx_tp_imported_hub ON test_plan (imported_from_hub_plan_id);
