UPDATE permission_resource SET name='业务资产',description='业务理解、操作分支及版本治理' WHERE code='TEST_ASSET_DOCUMENTS_PAGE';
INSERT IGNORE INTO permission_resource
(id,code,name,type,scope_type,parent_code,route_name,permission_id,visible_default,operable_default,sort,enabled,description)
VALUES ('TEST_ASSET_SHARED_CONFIGURATION_PAGE','TEST_ASSET_SHARED_CONFIGURATION_PAGE','公共配置','PAGE','PROJECT','TEST_ASSET_MENU','testAssetSharedConfiguration',NULL,b'1',b'0',564,b'1','按系统和环境管理普通配置、敏感配置及配置文档');
