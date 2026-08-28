CREATE TABLE ai_execution_alert
(
    id              VARCHAR(64)  NOT NULL,
    fingerprint     VARCHAR(255) NOT NULL,
    organization_id VARCHAR(64)  NOT NULL,
    project_id      VARCHAR(64)  NULL,
    task_id         VARCHAR(64)  NULL,
    alert_type      VARCHAR(64)  NOT NULL,
    severity        VARCHAR(16)  NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    trace_id        VARCHAR(64)  NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    acknowledged_by VARCHAR(64)  NULL,
    acknowledged_at BIGINT       NULL,
    create_time     BIGINT       NOT NULL,
    update_time     BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_execution_alert_fingerprint (fingerprint),
    KEY idx_ai_execution_alert_status (status, severity, create_time),
    KEY idx_ai_execution_alert_project (project_id, create_time),
    KEY idx_ai_execution_alert_organization (organization_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
