INSERT INTO permission_resource
(id,code,name,type,scope_type,parent_code,route_name,permission_id,visible_default,operable_default,sort,enabled,description)
VALUES ('CASE_ASSET_EXPORT_BUTTON','CASE_ASSET_EXPORT_BUTTON','导出资产用例','BUTTON','ORGANIZATION','TEST_ASSET_CASES_PAGE',NULL,'CASE_ASSET:READ+EXPORT',b'1',b'1',571,b'1','仅导出当前组织有权资产目录的用例')
ON DUPLICATE KEY UPDATE name=VALUES(name),permission_id=VALUES(permission_id),enabled=VALUES(enabled);
