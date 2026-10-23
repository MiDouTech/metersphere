ALTER TABLE export_task ADD COLUMN source_task_id VARCHAR(50) NULL COMMENT 'Original export task for an explicit retry';
CREATE INDEX idx_export_task_source ON export_task(source_task_id);
