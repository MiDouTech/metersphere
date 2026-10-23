CREATE TABLE agent_upload_storage_guard (
 file_id VARCHAR(50) PRIMARY KEY,
 file_name VARCHAR(255) NOT NULL,
 create_time BIGINT NOT NULL,
 KEY ix_upload_guard_time(create_time)
);
