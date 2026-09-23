-- Permissions are explicitly assigned through project role management.
-- No write/publish grant to existing roles, token owners or execution agents.
INSERT IGNORE INTO permission_resource
(id,code,name,type,scope_type,parent_code,route_name,permission_id,visible_default,operable_default,sort,enabled,description)
VALUES
('EXECUTION_QUALITY_MENU','EXECUTION_QUALITY_MENU','执行质量','MENU','PROJECT',NULL,'ExecutionQuality','QUALITY:READ',b'1',b'0',65,b'1','项目质量策略'),
('EXECUTION_QUALITY_POLICY_PAGE','EXECUTION_QUALITY_POLICY_PAGE','门禁策略','PAGE','PROJECT','EXECUTION_QUALITY_MENU','ExecutionQualityPolicy','QUALITY:READ',b'1',b'0',66,b'1','门禁策略草稿及不可变版本'),
('EXECUTION_QUALITY_POLICY_EDIT','EXECUTION_QUALITY_POLICY_EDIT','编辑草稿','BUTTON','PROJECT','EXECUTION_QUALITY_POLICY_PAGE',NULL,'QUALITY_POLICY:MANAGE',b'1',b'0',67,b'1','编辑和校验质量策略'),
('EXECUTION_QUALITY_POLICY_PUBLISH','EXECUTION_QUALITY_POLICY_PUBLISH','发布版本','BUTTON','PROJECT','EXECUTION_QUALITY_POLICY_PAGE',NULL,'QUALITY_POLICY:PUBLISH',b'1',b'0',68,b'1','发布质量策略版本');
