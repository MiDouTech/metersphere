-- New system resources: do not promote any project UI/role grants.
INSERT INTO permission_resource
(id,code,name,type,scope_type,parent_code,route_name,permission_id,visible_default,operable_default,sort,enabled,description)
VALUES
('SYSTEM_QUALITY_POLICY_PAGE','SYSTEM_QUALITY_POLICY_PAGE','平台质量门禁','PAGE','SYSTEM',NULL,'SystemQualityPolicy','SYSTEM_QUALITY:READ',b'1',b'0',69,b'1','全平台统一门禁策略'),
('SYSTEM_QUALITY_POLICY_EDIT','SYSTEM_QUALITY_POLICY_EDIT','编辑平台策略','BUTTON','SYSTEM','SYSTEM_QUALITY_POLICY_PAGE',NULL,'SYSTEM_QUALITY:MANAGE',b'1',b'0',70,b'1','创建和校验全局草稿'),
('SYSTEM_QUALITY_POLICY_PUBLISH','SYSTEM_QUALITY_POLICY_PUBLISH','发布平台策略','BUTTON','SYSTEM','SYSTEM_QUALITY_POLICY_PAGE',NULL,'SYSTEM_QUALITY:PUBLISH',b'1',b'0',71,b'1','发布全平台版本');

-- Legacy project write controls are retired; stored grants are not promoted or deleted.
UPDATE permission_resource SET enabled=b'0' WHERE code IN ('EXECUTION_QUALITY_POLICY_EDIT','EXECUTION_QUALITY_POLICY_PUBLISH');
