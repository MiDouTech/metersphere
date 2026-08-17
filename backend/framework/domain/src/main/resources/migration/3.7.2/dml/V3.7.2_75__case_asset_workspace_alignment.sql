-- Case assets are physically stored in the protected hub project but belong to
-- the organization owning their catalog. Align existing rows, including cases
-- placed in descendant modules created by Excel/XMind imports.
WITH RECURSIVE asset_module (organization_id, module_id) AS (
    SELECT organization_id, hub_module_id
    FROM case_asset_catalog
    WHERE deleted = b'0'
    UNION DISTINCT
    SELECT parent.organization_id, child.id
    FROM functional_case_module child
    JOIN asset_module parent ON child.parent_id = parent.module_id
    WHERE child.deleted = b'0'
)
UPDATE functional_case fc
JOIN asset_module asset ON asset.module_id = fc.module_id
SET fc.workspace_id = asset.organization_id
WHERE fc.deleted = b'0'
  AND (fc.workspace_id IS NULL OR fc.workspace_id <> asset.organization_id);
