-- Align server-driven resources with: documents, cases, data, environments, versions, relations.
-- Fixed positions make the migration idempotent and preserve existing resource identifiers.
UPDATE user_role_permission_resource SET pos = 561 WHERE code = 'TEST_ASSET_DOCUMENTS_PAGE';
UPDATE user_role_permission_resource SET pos = 562 WHERE code = 'TEST_ASSET_CASES_PAGE';
UPDATE user_role_permission_resource SET pos = 1 WHERE code = 'TEST_ASSET_CASE_PROJECT_TAB';
UPDATE user_role_permission_resource SET pos = 2 WHERE code = 'TEST_ASSET_CASE_SYSTEM_TAB';
UPDATE user_role_permission_resource SET pos = 565 WHERE code = 'TEST_ASSET_VERSIONS_PAGE';
UPDATE user_role_permission_resource SET pos = 566 WHERE code = 'TEST_ASSET_RELATIONS_PAGE';
