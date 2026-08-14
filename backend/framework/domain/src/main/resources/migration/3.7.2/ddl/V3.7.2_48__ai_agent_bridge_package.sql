CREATE TABLE ai_agent_bridge_package
(
    id            VARCHAR(64)  NOT NULL,
    version       VARCHAR(64)  NOT NULL COMMENT 'Bridge semantic version',
    os_type       VARCHAR(32)  NOT NULL COMMENT 'WINDOWS/MACOS/LINUX',
    architecture  VARCHAR(32)  NOT NULL COMMENT 'X64/ARM64',
    file_name     VARCHAR(255) NOT NULL,
    storage       VARCHAR(32)  NOT NULL DEFAULT 'MINIO',
    storage_folder VARCHAR(512) NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'INACTIVE' COMMENT 'ACTIVE/INACTIVE',
    active_key    VARCHAR(96)  NULL COMMENT 'Unique os/architecture slot while active',
    description   VARCHAR(1000) NULL,
    download_count BIGINT      NOT NULL DEFAULT 0,
    create_user   VARCHAR(64)  NOT NULL,
    create_time   BIGINT       NOT NULL,
    update_user   VARCHAR(64)  NULL,
    update_time   BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_bridge_package_version (version, os_type, architecture),
    UNIQUE KEY uk_ai_agent_bridge_package_active (active_key),
    KEY idx_ai_agent_bridge_package_active (os_type, architecture, status, update_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT 'Administrator-published Agent Bridge installation packages';
