-- Retain historical case-insensitive identities, new writers explicitly use binary-v2.
ALTER TABLE agent_idempotency_record
 ADD COLUMN identity_version VARCHAR(32) NOT NULL DEFAULT 'legacy-ci-v1',
 MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE agent_attachment_upload_intent
 ADD COLUMN identity_version VARCHAR(32) NOT NULL DEFAULT 'legacy-ci-v1',
 MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE functional_import_job
 ADD COLUMN identity_version VARCHAR(32) NOT NULL DEFAULT 'legacy-ci-v1',
 MODIFY COLUMN request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
