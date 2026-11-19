CREATE TABLE ai_execution_knowledge_usage (
    step_result_id VARCHAR(64) NOT NULL,
    knowledge_id VARCHAR(64) NOT NULL,
    knowledge_version BIGINT NOT NULL,
    PRIMARY KEY (step_result_id, knowledge_id, knowledge_version),
    KEY idx_ai_execution_knowledge_usage_lookup (knowledge_id, knowledge_version),
    CONSTRAINT fk_ai_execution_knowledge_usage_result FOREIGN KEY (step_result_id)
        REFERENCES ai_execution_step_result (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
