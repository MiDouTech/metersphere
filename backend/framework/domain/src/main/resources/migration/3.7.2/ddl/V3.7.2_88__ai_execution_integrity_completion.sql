SET SESSION innodb_lock_wait_timeout = 7200;

-- V44 used a two-column schedule key.  Once trigger definitions are versioned,
-- history idempotency must include the frozen trigger version.
ALTER TABLE ai_task_trigger_history
    DROP INDEX uk_ai_trigger_schedule;

-- Each lease owns an immutable, bounded copy of the published dataset.  The
-- copy is cleared by the cleanup job so a later source-file update cannot alter
-- an active execution and released data does not remain recoverable here.
ALTER TABLE ai_test_data_lease
    ADD COLUMN content_snapshot LONGBLOB NULL AFTER namespace,
    ADD COLUMN content_type VARCHAR(255) NULL AFTER content_snapshot,
    ADD COLUMN content_sha256 VARCHAR(128) NULL AFTER content_type,
    ADD COLUMN cleaned_at BIGINT NULL AFTER released_at;

ALTER TABLE ai_execution_checkpoint
    ADD COLUMN request_id VARCHAR(128) NULL AFTER execution_id,
    ADD UNIQUE KEY uk_ai_checkpoint_request (task_id, request_id);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
