-- Old mirror hashes omitted execution state. Mark relations stale; bounded compensation
-- refreshes existing assets without changing their identity or overwriting sources.
ALTER TABLE case_asset_source_relation ADD KEY idx_asset_projection_stale (source_update_time,id);
UPDATE case_asset_source_relation SET source_update_time=0;
