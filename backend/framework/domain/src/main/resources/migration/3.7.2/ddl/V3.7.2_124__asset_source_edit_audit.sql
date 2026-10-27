CREATE TABLE case_asset_source_edit_audit (
 id VARCHAR(50) NOT NULL PRIMARY KEY, asset_case_id VARCHAR(50) NOT NULL,
 organization_id VARCHAR(50) NOT NULL, operator_id VARCHAR(50) NOT NULL,
 edit_mode VARCHAR(20) NOT NULL, source_snapshot MEDIUMTEXT NOT NULL,
 create_time BIGINT NOT NULL,
 KEY idx_asset_source_edit(asset_case_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
