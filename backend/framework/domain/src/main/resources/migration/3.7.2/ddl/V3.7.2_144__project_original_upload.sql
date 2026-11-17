-- Temporary chunks have a 24-hour window. Operation identity and completed facts are not expired.
CREATE TABLE IF NOT EXISTS project_original_upload (
    id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    user_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    token_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    request_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
    request_json TEXT NOT NULL,
    state VARCHAR(20) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    version_id VARCHAR(50) COLLATE utf8mb4_bin NULL,
    create_time BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    UNIQUE KEY uk_original_request(project_id,user_id,token_id,request_id),
    KEY idx_original_expiry(state,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS project_original_chunk (
    upload_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    byte_offset BIGINT NOT NULL,
    byte_size INT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    content MEDIUMBLOB NULL,
    PRIMARY KEY(upload_id,byte_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per immutable FileManagement version, not a second file store.
CREATE TABLE IF NOT EXISTS project_file_original (
    version_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL PRIMARY KEY,
    project_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    sha256 CHAR(64) NOT NULL,
    source_label VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    operation_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    actor_id VARCHAR(50) COLLATE utf8mb4_bin NOT NULL,
    stored_at BIGINT NOT NULL,
    KEY idx_original_hash(project_id,sha256,source_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
