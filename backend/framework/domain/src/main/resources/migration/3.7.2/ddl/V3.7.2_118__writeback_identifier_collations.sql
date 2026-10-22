-- Match the existing platform's task/attempt/project identifiers without rebuilding unrelated tables.
-- Keep exact-byte comparisons in recovery SQL: a database collation must not redefine opaque ID equality.
-- Preflight the changed primary-key comparison semantics before the ALTER; never discard collisions.
CREATE TEMPORARY TABLE recovery_identifier_preflight_118 (
    execution_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    execution_case_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY(execution_id,execution_case_id)
);
INSERT INTO recovery_identifier_preflight_118
    SELECT execution_id,execution_case_id FROM ai_execution_case_sync;
ALTER TABLE ai_execution_case_sync
    MODIFY execution_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    MODIFY execution_case_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    MODIFY task_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    MODIFY project_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL;
DROP TEMPORARY TABLE recovery_identifier_preflight_118;
