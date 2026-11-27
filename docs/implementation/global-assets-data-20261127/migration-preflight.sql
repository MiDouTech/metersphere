-- Read-only checks BEFORE migration 162 on an unupgraded database.
-- First result must be empty. Do not guess an organization for an orphan.
SELECT 'business_asset' AS asset_type,a.id,a.project_id
FROM business_asset a LEFT JOIN project p ON p.id=a.project_id
WHERE p.id IS NULL OR p.organization_id IS NULL
UNION ALL
SELECT 'shared_configuration',c.id,c.project_id
FROM shared_configuration c LEFT JOIN project p ON p.id=c.project_id
WHERE p.id IS NULL OR p.organization_id IS NULL;

-- Historical duplicates are retained, not auto-merged. Review these results.
SELECT p.organization_id,a.identity_key,COUNT(*) AS retained_duplicates
FROM business_asset a JOIN project p ON p.id=a.project_id
GROUP BY p.organization_id,a.identity_key HAVING COUNT(*)>1;
SELECT p.organization_id,c.system_key,c.environment_key,c.name,COUNT(*) AS retained_duplicates
FROM shared_configuration c JOIN project p ON p.id=c.project_id
GROUP BY p.organization_id,c.system_key,c.environment_key,c.name HAVING COUNT(*)>1;

-- Version ownership must remain intact.
SELECT v.id AS orphan_business_version FROM business_asset_version v
LEFT JOIN business_asset a ON a.id=v.asset_id WHERE a.id IS NULL;
SELECT v.configuration_id AS orphan_configuration_version FROM shared_configuration_version v
LEFT JOIN shared_configuration c ON c.id=v.configuration_id WHERE c.id IS NULL;
