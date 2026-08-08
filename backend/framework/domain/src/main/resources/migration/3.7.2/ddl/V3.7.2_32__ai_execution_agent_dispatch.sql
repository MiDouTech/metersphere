SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE ai_execution_task
    ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'RUNNER' COMMENT 'RUNNER/AGENT' AFTER status,
    ADD COLUMN agent_type VARCHAR(32) NULL COMMENT 'WORKBUDDY/CURSOR/CODEX' AFTER execution_mode,
    ADD COLUMN agent_gateway_id VARCHAR(50) NULL COMMENT 'Agent Gateway ID' AFTER agent_type,
    ADD INDEX idx_ai_execution_task_agent (execution_mode, agent_type, status);

ALTER TABLE ai_agent_gateway
    ADD COLUMN agent_type VARCHAR(32) NULL COMMENT 'WORKBUDDY/CURSOR/CODEX' AFTER name,
    ADD INDEX idx_ai_agent_gateway_type (agent_type, enabled);

SET SESSION innodb_lock_wait_timeout = DEFAULT;
