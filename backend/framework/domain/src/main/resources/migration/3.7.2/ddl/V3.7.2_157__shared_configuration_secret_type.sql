-- Empty secret_type preserves the legacy opaque SECRET string contract.
ALTER TABLE shared_configuration_version ADD COLUMN secret_type VARCHAR(24) NOT NULL DEFAULT '';
ALTER TABLE shared_configuration_audit ADD COLUMN reason VARCHAR(500) NOT NULL DEFAULT '';
