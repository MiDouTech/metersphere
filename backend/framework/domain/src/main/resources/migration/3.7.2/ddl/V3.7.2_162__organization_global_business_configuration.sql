ALTER TABLE shared_configuration ADD COLUMN organization_id VARCHAR(64) NULL;
UPDATE shared_configuration c JOIN project p ON p.id=c.project_id SET c.organization_id=p.organization_id;
CREATE INDEX idx_shared_configuration_organization ON shared_configuration(organization_id,system_key,environment_key);

ALTER TABLE business_asset ADD COLUMN organization_id VARCHAR(64) NULL;
UPDATE business_asset a JOIN project p ON p.id=a.project_id SET a.organization_id=p.organization_id;
CREATE INDEX idx_business_asset_organization ON business_asset(organization_id,identity_key);
-- Original source identity is retained for legacy ciphertext AAD and provenance.
-- Organization row locks serialize identities. Historical duplicate names remain accessible.
ALTER TABLE shared_configuration DROP INDEX uk_shared_configuration;
ALTER TABLE business_asset DROP INDEX uk_business_asset_identity;
-- Fail safely for orphan source rows instead of silently hiding unassigned assets.
ALTER TABLE shared_configuration MODIFY organization_id VARCHAR(64) NOT NULL;
ALTER TABLE business_asset MODIFY organization_id VARCHAR(64) NOT NULL;
