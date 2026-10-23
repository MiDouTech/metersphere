-- Credential scope identity is independent from a published execution asset.
-- Keep all legacy IDs (including credentials and frozen task snapshots) unchanged.
ALTER TABLE ai_environment_execution_profile
    ADD COLUMN environment_asset_id VARCHAR(64) NULL;

-- Map only real same-project environments. Missing legacy assets stay unbound.
UPDATE ai_environment_execution_profile p
JOIN environment e ON BINARY e.id = BINARY p.environment_id
    AND BINARY e.project_id = BINARY p.project_id
SET p.environment_asset_id = e.id;
