SET SESSION innodb_lock_wait_timeout = 7200;

CREATE TABLE ai_execution_audit
(
    id          VARCHAR(64)  NOT NULL,
    actor_type  VARCHAR(32)  NOT NULL,
    actor_id    VARCHAR(128) NOT NULL,
    action      VARCHAR(128) NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(64)  NOT NULL,
    before_json MEDIUMTEXT   NULL,
    after_json  MEDIUMTEXT   NULL,
    trace_id    VARCHAR(64)  NOT NULL,
    create_time BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_execution_audit_target (target_type,target_id,create_time),
    KEY idx_ai_execution_audit_trace (trace_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE ai_execution_metric
(
    id          VARCHAR(64)  NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    metric_value DECIMAL(24,6) NOT NULL,
    tags_json   TEXT         NOT NULL,
    trace_id    VARCHAR(64)  NULL,
    create_time BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_execution_metric_name (metric_name,create_time),
    KEY idx_ai_execution_metric_trace (trace_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
