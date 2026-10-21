-- Additive: legacy exports retain their existing state and nullable metadata.
ALTER TABLE export_task
    ADD COLUMN request_key VARCHAR(64) COLLATE utf8mb4_bin NULL,
    ADD COLUMN request_hash VARCHAR(64) NULL,
    ADD COLUMN request_payload MEDIUMTEXT NULL,
    ADD COLUMN content_snapshot MEDIUMTEXT NULL,
    ADD COLUMN snapshot_ids MEDIUMTEXT NULL,
    ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN result_count INT NOT NULL DEFAULT 0,
    ADD COLUMN safe_error_code VARCHAR(64) NULL,
    ADD COLUMN worker_id VARCHAR(64) NULL,
    ADD COLUMN lease_until BIGINT NULL,
    ADD COLUMN expires_at BIGINT NULL,
    ADD COLUMN finished_at BIGINT NULL,
    ADD COLUMN cleanup_done BIT(1) NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_export_request (create_user, project_id, type, request_key),
    ADD KEY idx_export_recovery (type, state, lease_until),
    ADD KEY idx_export_owner_history (create_user, project_id, create_time);

-- A shared admission lock; no in-memory per-replica counters for the global queue budget.
CREATE TABLE export_task_capacity (type VARCHAR(50) PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO export_task_capacity(type) VALUES ('CASE');
