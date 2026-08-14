-- Align server-driven resources with: documents, cases, data, environments, versions, relations.
-- Fixed positions make the migration idempotent and preserve existing resource identifiers.
UPDATE permission_resource SET sort = 561 WHERE code = 'TEST_ASSET_DOCUMENTS_PAGE';
UPDATE permission_resource SET sort = 562 WHERE code = 'TEST_ASSET_CASES_PAGE';
UPDATE permission_resource SET sort = 1 WHERE code = 'TEST_ASSET_CASE_PROJECT_TAB';
UPDATE permission_resource SET sort = 2 WHERE code = 'TEST_ASSET_CASE_SYSTEM_TAB';
UPDATE permission_resource SET sort = 565 WHERE code = 'TEST_ASSET_VERSIONS_PAGE';
UPDATE permission_resource SET sort = 566 WHERE code = 'TEST_ASSET_RELATIONS_PAGE';
