ALTER TABLE case_asset_catalog
    ADD COLUMN pinned BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN pin_time BIGINT NULL,
    ADD INDEX idx_case_asset_org_pin (organization_id, deleted, pinned, pin_time, id);
