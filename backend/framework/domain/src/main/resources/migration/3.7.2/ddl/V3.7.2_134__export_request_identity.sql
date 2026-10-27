-- Preserve historical request identities and binary equality; expanding is additive.
ALTER TABLE export_task MODIFY COLUMN request_key VARCHAR(128) COLLATE utf8mb4_bin NULL;
