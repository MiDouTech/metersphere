CREATE TABLE agent_recovery_scan_health (
    scan_name VARCHAR(64) NOT NULL PRIMARY KEY,
    lease_owner VARCHAR(64) NULL,
    lease_until BIGINT NOT NULL DEFAULT 0,
    next_scan_at BIGINT NOT NULL DEFAULT 0,
    last_success_at BIGINT NULL,
    last_failure_at BIGINT NULL,
    consecutive_failures INT NOT NULL DEFAULT 0,
    error_category VARCHAR(32) NULL,
    update_time BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
INSERT INTO agent_recovery_scan_health(scan_name) VALUES ('WRITEBACK');
