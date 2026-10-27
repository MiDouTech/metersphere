-- Length expansion only. Do not change legacy case-folding semantics without alias reconciliation.
ALTER TABLE functional_import_job MODIFY COLUMN request_id VARCHAR(128) NOT NULL;
