ALTER TABLE functional_import_job
    ADD COLUMN committed_at BIGINT NULL,
    ADD COLUMN stopped_from VARCHAR(32) NULL;

-- A recorded explicit follow-up decision is stronger evidence than state or a
-- successful item: the old retry bug could queue/write without a user commit.
UPDATE functional_import_job j
SET j.committed_at=j.update_time
WHERE j.state<>'PRECHECKED'
  AND EXISTS (SELECT 1 FROM functional_import_followup f WHERE f.job_id=j.id AND f.source_kind='FILE'
              AND f.policy IN ('IMPORT_ONLY','CREATE_NEW_PAIR','APPEND_EXISTING'));

-- Pause ambiguous active legacy jobs. Keep all successful items and require a
-- new explicit commit before any remaining business writes can resume.
UPDATE functional_import_job
SET stopped_from=state,state='STOPPED'
WHERE committed_at IS NULL AND state IN ('QUEUED','RUNNING');
