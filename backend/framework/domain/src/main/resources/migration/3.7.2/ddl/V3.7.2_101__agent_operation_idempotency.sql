-- Preserve legacy keys and responses; unknown outcomes are never executed automatically.
ALTER TABLE agent_idempotency_record
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN hash_version VARCHAR(32) NOT NULL DEFAULT 'legacy-v0',
    ADD COLUMN completed_time BIGINT NULL;
UPDATE agent_idempotency_record SET status='SUCCEEDED',completed_time=create_time
WHERE response_json IS NOT NULL AND LENGTH(TRIM(response_json))>0;

-- Historical runtime responses can contain lease credentials or expiring URLs.
-- Preserve request identity, but remove the response and prevent replay.
UPDATE agent_idempotency_record SET response_json=NULL, status='EXPIRED'
WHERE tool_name IN ('metersphere.task.claim','metersphere.task.lease.heartbeat',
'metersphere.task.release','metersphere.execution.events.batch','metersphere.execution.step.submit',
'metersphere.execution.complete','metersphere.execution.fail','metersphere.execution.checkpoint.create',
'metersphere.human_request.create','metersphere.artifact.prepare','metersphere.artifact.commit');
