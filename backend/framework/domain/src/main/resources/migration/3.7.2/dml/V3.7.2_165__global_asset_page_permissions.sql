UPDATE permission_resource SET scope_type='ORGANIZATION',permission_id=NULL WHERE code='TEST_ASSET_MENU';
UPDATE permission_resource SET scope_type='ORGANIZATION',permission_id='TEST_DATA_ASSET:READ' WHERE code='TEST_ASSET_DATASETS_PAGE';
UPDATE permission_resource SET scope_type='ORGANIZATION',permission_id='BUSINESS_ASSET:READ' WHERE code='TEST_ASSET_DOCUMENTS_PAGE';
UPDATE permission_resource SET scope_type='ORGANIZATION',permission_id='SHARED_CONFIGURATION:READ' WHERE code='TEST_ASSET_SHARED_CONFIGURATION_PAGE';
UPDATE permission_resource SET scope_type='ORGANIZATION',permission_id=NULL WHERE code='TEST_ASSET_VERSIONS_PAGE';
-- Source indexes and project relation permissions deliberately retain their existing mappings.

-- Version visibility is an any-source-READ projection in PermissionUiService, not MANAGE.
