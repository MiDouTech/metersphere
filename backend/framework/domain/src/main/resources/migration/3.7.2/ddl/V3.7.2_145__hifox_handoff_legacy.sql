-- MSP-15. Additive and disabled by default. Never delete business keys on rollback.
-- MSP-15 v1.5: immutable Webhook events; old workers must not consume protocol_version=2.
CREATE TABLE IF NOT EXISTS hifox_handoff_webhook (
 event_id VARCHAR(50) NOT NULL PRIMARY KEY, request_id VARCHAR(50) NOT NULL,
 business_key CHAR(64) NOT NULL, project_id VARCHAR(50) NOT NULL, sequence_no BIGINT NOT NULL,
 recovery_id VARCHAR(50) NULL, predecessor_event_id VARCHAR(50) NULL,
 payload_hash CHAR(64) NOT NULL, envelope_json LONGTEXT NOT NULL,
 state VARCHAR(24) NOT NULL, operation_id VARCHAR(100) NULL,
 reception_json LONGTEXT NULL, creation_json LONGTEXT NULL, terminal_json LONGTEXT NULL,
 create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 UNIQUE KEY uk_handoff_webhook_request(request_id),
 UNIQUE KEY uk_handoff_webhook_sequence(business_key,sequence_no),
 UNIQUE KEY uk_handoff_recovery(recovery_id),
 KEY idx_handoff_webhook_project(project_id,business_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_config (
 project_id VARCHAR(50) NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0, event_sequence BIGINT NOT NULL DEFAULT 0,
 enabled BIT(1) NOT NULL DEFAULT 0, external_grant BIT(1) NOT NULL DEFAULT 0,
 source_grant BIT(1) NOT NULL DEFAULT 0, source_actor VARCHAR(50) NULL, cutover BIGINT NULL,
 config_json LONGTEXT NOT NULL, update_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_settings (
 project_id VARCHAR(50) NOT NULL, plan_id VARCHAR(50) NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL, settings_json LONGTEXT NOT NULL, update_time BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_fact (
 business_key CHAR(64) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL, plan_id VARCHAR(50) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, event_sequence BIGINT NOT NULL,
 eligible BIT(1) NOT NULL, reason VARCHAR(64) NOT NULL,
 passed_at BIGINT NULL, timing_trusted BIT(1) NOT NULL DEFAULT 0, snapshot_hash CHAR(64) NOT NULL,
 snapshot_json LONGTEXT NOT NULL, update_time BIGINT NOT NULL,
 UNIQUE KEY uk_handoff_source(project_id,plan_id), KEY idx_handoff_fact(project_id,eligible)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_event (
 project_id VARCHAR(50) NOT NULL, event_sequence BIGINT NOT NULL,
 business_key CHAR(64) NOT NULL, revision BIGINT NOT NULL,
 eligible BIT(1) NOT NULL, reason VARCHAR(64) NOT NULL, business_time BIGINT NULL,
 snapshot_hash CHAR(64) NOT NULL, snapshot_json LONGTEXT NOT NULL, captured_at BIGINT NOT NULL,
 PRIMARY KEY(project_id,event_sequence), KEY idx_handoff_event(business_key,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_enable_interval (
 id VARCHAR(50) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL,
 active_project_id VARCHAR(50) NULL, start_sequence BIGINT NOT NULL, end_sequence BIGINT NULL,
 config_revision BIGINT NOT NULL, baseline_hash CHAR(64) NOT NULL, baseline_json LONGTEXT NOT NULL,
 create_time BIGINT NOT NULL, UNIQUE KEY uk_handoff_active_interval(active_project_id),
 KEY idx_handoff_intervals(project_id,start_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_delivery (
 business_key CHAR(64) NOT NULL PRIMARY KEY,
 project_id VARCHAR(50) NOT NULL, plan_id VARCHAR(50) NOT NULL,
 plan_name VARCHAR(255) NOT NULL, plan_number VARCHAR(50) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, fact_revision BIGINT NOT NULL,
 candidate_kind VARCHAR(24) NOT NULL, source_changed BIT(1) NOT NULL DEFAULT 0,
 consistency_conflict BIT(1) NOT NULL DEFAULT 0, conflict_json LONGTEXT NULL,
 target_json LONGTEXT NOT NULL, snapshot_json LONGTEXT NOT NULL,
 task_json LONGTEXT NOT NULL, attempt_json LONGTEXT NOT NULL,
 task_id VARCHAR(100) NULL, target_identifier VARCHAR(100) NULL, target_url VARCHAR(1000) NULL,
 state VARCHAR(24) NOT NULL, next_at BIGINT NOT NULL DEFAULT 0,
 protocol_version INT NOT NULL DEFAULT 2, create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 KEY idx_handoff_due(state,next_at), KEY idx_handoff_project(project_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_notice (
 notice_key CHAR(64) NOT NULL PRIMARY KEY, business_key CHAR(64) NOT NULL,
 project_id VARCHAR(50) NOT NULL, channel VARCHAR(16) NOT NULL,
 event_kind VARCHAR(64) NOT NULL, recipient VARCHAR(50) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, content_json LONGTEXT NOT NULL,
 attempt_json LONGTEXT NOT NULL, state VARCHAR(24) NOT NULL,
 next_at BIGINT NOT NULL DEFAULT 0, update_time BIGINT NOT NULL,
 UNIQUE KEY uk_handoff_recipient(business_key,channel,event_kind,recipient),
 KEY idx_handoff_notice_due(channel,state,next_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_preview (
 id VARCHAR(50) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL,
 business_key CHAR(64) NULL, actor VARCHAR(50) NOT NULL,
 action VARCHAR(32) NOT NULL, revision_hash CHAR(64) NOT NULL,
 payload_json LONGTEXT NOT NULL, expires_at BIGINT NOT NULL,
 consumed_at BIGINT NULL, result_json LONGTEXT NULL,
 KEY idx_handoff_preview(project_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_audit (
 id VARCHAR(50) NOT NULL PRIMARY KEY, project_id VARCHAR(50) NOT NULL,
 business_key CHAR(64) NULL, actor VARCHAR(50) NOT NULL,
 action VARCHAR(64) NOT NULL, revision BIGINT NOT NULL, create_time BIGINT NOT NULL,
 KEY idx_handoff_audit(project_id,business_key,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS hifox_handoff_attempt_history (
 request_id VARCHAR(50) NOT NULL PRIMARY KEY, business_key CHAR(64) NOT NULL,
 notice_key CHAR(64) NULL, project_id VARCHAR(50) NOT NULL, channel VARCHAR(16) NOT NULL,
 content_hash CHAR(64) NOT NULL, state VARCHAR(24) NOT NULL,
 receipt VARCHAR(200) NULL, evidence_ref VARCHAR(200) NULL, proof_json LONGTEXT NULL, notice_receipt_json LONGTEXT NULL,
 destination_json LONGTEXT NOT NULL, request_json LONGTEXT NOT NULL, accepted_at BIGINT NULL, create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 KEY idx_handoff_attempt_history(project_id,business_key,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
