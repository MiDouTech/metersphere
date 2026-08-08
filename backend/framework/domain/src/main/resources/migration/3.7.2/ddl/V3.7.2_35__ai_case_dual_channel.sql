SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE ai_case_conversation
    MODIFY COLUMN model_source_id VARCHAR(50) DEFAULT NULL COMMENT 'Legacy/current model API source',
    ADD COLUMN resource_type VARCHAR(32) NOT NULL DEFAULT 'MODEL_API' AFTER title,
    ADD COLUMN resource_id VARCHAR(50) DEFAULT NULL AFTER resource_type,
    ADD COLUMN agent_connection_id VARCHAR(50) DEFAULT NULL AFTER resource_id,
    ADD KEY idx_ai_case_conversation_resource (resource_type, resource_id),
    ADD KEY idx_ai_case_conversation_agent (agent_connection_id);

UPDATE ai_case_conversation
SET resource_type = 'MODEL_API', resource_id = model_source_id
WHERE resource_id IS NULL AND model_source_id IS NOT NULL;

ALTER TABLE ai_case_message
    ADD COLUMN resource_type VARCHAR(32) NOT NULL DEFAULT 'MODEL_API' AFTER status,
    ADD COLUMN resource_id VARCHAR(50) DEFAULT NULL AFTER resource_type,
    ADD COLUMN agent_connection_id VARCHAR(50) DEFAULT NULL AFTER resource_id,
    ADD KEY idx_ai_case_message_resource (resource_type, resource_id);

UPDATE ai_case_message
SET resource_type = 'MODEL_API', resource_id = model_source_id
WHERE resource_id IS NULL AND model_source_id IS NOT NULL;

ALTER TABLE ai_case_execution
    MODIFY COLUMN requested_model_source_id VARCHAR(50) DEFAULT NULL,
    ADD COLUMN resource_type VARCHAR(32) NOT NULL DEFAULT 'MODEL_API' AFTER execution_type,
    ADD COLUMN requested_resource_id VARCHAR(50) DEFAULT NULL AFTER resource_type,
    ADD COLUMN actual_resource_id VARCHAR(50) DEFAULT NULL AFTER requested_resource_id,
    ADD COLUMN agent_connection_id VARCHAR(50) DEFAULT NULL AFTER actual_resource_id,
    ADD COLUMN agent_device_id VARCHAR(50) DEFAULT NULL AFTER agent_connection_id,
    ADD KEY idx_ai_case_execution_resource (resource_type, requested_resource_id),
    ADD KEY idx_ai_case_execution_agent (agent_connection_id, status);

UPDATE ai_case_execution
SET resource_type = 'MODEL_API',
    requested_resource_id = requested_model_source_id,
    actual_resource_id = actual_model_source_id
WHERE requested_resource_id IS NULL AND requested_model_source_id IS NOT NULL;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
