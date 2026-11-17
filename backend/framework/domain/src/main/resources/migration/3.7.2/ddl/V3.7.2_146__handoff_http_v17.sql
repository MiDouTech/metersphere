-- MSP-15 v1.7 additive protocol boundary. Stop all legacy workers before enabling the new worker.
-- No legacy facts/delivery/receipts are overwritten, copied or automatically resent.
CREATE TABLE hifox_http_config (
 project_id VARCHAR(50) NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0, event_sequence BIGINT NOT NULL DEFAULT 0,
 enabled BIT(1) NOT NULL DEFAULT 0, external_grant BIT(1) NOT NULL DEFAULT 0,
 url_secret LONGTEXT NULL, token_secret LONGTEXT NULL,
 auth_mode VARCHAR(16) NOT NULL DEFAULT 'NONE', url_revision BIGINT NOT NULL DEFAULT 0,
 credential_revision BIGINT NOT NULL DEFAULT 0, cutover BIGINT NOT NULL DEFAULT 0,
 update_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE hifox_http_fact (
 business_key CHAR(64) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL, plan_id VARCHAR(50) NOT NULL,
 revision BIGINT NOT NULL, event_sequence BIGINT NOT NULL, eligible BIT(1) NOT NULL,
 passed_at BIGINT NULL, time_revision CHAR(64) NULL, interval_known BIT(1) NOT NULL DEFAULT 0,
 automatic BIT(1) NOT NULL DEFAULT 0, snapshot_hash CHAR(64) NOT NULL, snapshot_json LONGTEXT NOT NULL,
 UNIQUE KEY uk_http_fact(project_id,plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE hifox_http_delivery (
 business_key CHAR(64) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL, plan_id VARCHAR(50) NOT NULL,
 event_id VARCHAR(50) NOT NULL, operation_id VARCHAR(50) NULL, revision BIGINT NOT NULL DEFAULT 0,
 state VARCHAR(32) NOT NULL, reason VARCHAR(64) NULL, candidate_kind VARCHAR(16) NOT NULL,
 snapshot_json LONGTEXT NOT NULL, fact_revision BIGINT NOT NULL,
 time_revision CHAR(64) NOT NULL, payload_json LONGTEXT NULL, payload_hash CHAR(64) NULL,
 url_revision BIGINT NOT NULL DEFAULT 0, attempts_used INT NOT NULL DEFAULT 0,
 next_at BIGINT NOT NULL DEFAULT 0, active_attempt VARCHAR(50) NULL,
 http_status INT NULL, had_unknown BIT(1) NOT NULL DEFAULT 0, source_changed BIT(1) NOT NULL DEFAULT 0,
 create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 UNIQUE KEY uk_http_plan(project_id,plan_id), UNIQUE KEY uk_http_event(event_id),
 KEY idx_http_due(state,next_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE hifox_http_attempt (
 attempt_id VARCHAR(50) NOT NULL PRIMARY KEY, business_key CHAR(64) NOT NULL,
 sequence_no INT NOT NULL, started_at BIGINT NOT NULL, ended_at BIGINT NULL,
 http_status INT NULL, error_code VARCHAR(64) NULL, result_unknown BIT(1) NOT NULL DEFAULT 0,
 credential_revision BIGINT NOT NULL, UNIQUE KEY uk_http_attempt(business_key,sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
-- Permission names remain stable. Old notice permissions no longer authorize outbound operations.

-- The immutable source snapshot set at W is an ordering proof, including during capture replay.
CREATE TABLE hifox_http_interval (
 id VARCHAR(50) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL,
 active_project_id VARCHAR(50) NULL, start_sequence BIGINT NOT NULL,
 baseline_json LONGTEXT NOT NULL, end_json LONGTEXT NULL,
 UNIQUE KEY uk_http_active_interval(active_project_id), KEY idx_http_interval_project(project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE hifox_http_legacy_migration (
 legacy_key CHAR(64) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL,
 business_key CHAR(64) NOT NULL, original_state VARCHAR(24) NOT NULL,
 actor VARCHAR(50) NOT NULL, confirmation_id VARCHAR(50) NOT NULL, create_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
