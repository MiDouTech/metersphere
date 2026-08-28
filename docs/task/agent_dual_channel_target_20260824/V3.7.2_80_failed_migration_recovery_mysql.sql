-- Recovery for the confirmed MySQL 8 failure of V3.7.2.80 at the
-- ai_execution_artifact ALTER TABLE statement (duplicate retention_until).
--
-- Preconditions:
--   1. Stop the failing application container.
--   2. Back up the metersphere database.
--   3. Deploy an image containing the corrected V3.7.2.80 migration before
--      starting the application again.
--
-- This script reverses only the four ALTER TABLE statements committed before
-- the failure, removes the failed Flyway history row, and leaves all data that
-- predates V3.7.2.80 intact. It intentionally never drops retention_until,
-- which belongs to V3.7.2.31.

DELIMITER //

DROP PROCEDURE IF EXISTS ms_v80_drop_index_if_exists//
CREATE PROCEDURE ms_v80_drop_index_if_exists(IN p_table_name VARCHAR(64), IN p_index_name VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ms_v80_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
                                 '` DROP INDEX `', REPLACE(p_index_name, '`', '``'), '`');
        PREPARE ms_v80_stmt FROM @ms_v80_ddl;
        EXECUTE ms_v80_stmt;
        DEALLOCATE PREPARE ms_v80_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS ms_v80_drop_column_if_exists//
CREATE PROCEDURE ms_v80_drop_column_if_exists(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ms_v80_ddl = CONCAT('ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
                                 '` DROP COLUMN `', REPLACE(p_column_name, '`', '``'), '`');
        PREPARE ms_v80_stmt FROM @ms_v80_ddl;
        EXECUTE ms_v80_stmt;
        DEALLOCATE PREPARE ms_v80_stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS ms_v80_recover_failed_migration//
CREATE PROCEDURE ms_v80_recover_failed_migration()
BEGIN
    DECLARE v_failed_rows INT DEFAULT 0;
    DECLARE v_unexpected_tables INT DEFAULT 0;

    SELECT COUNT(*) INTO v_failed_rows
    FROM flyway_schema_history
    WHERE version = '3.7.2.80' AND success = 0;

    IF v_failed_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Recovery stopped: expected exactly one failed Flyway V3.7.2.80 row';
    END IF;

    SELECT COUNT(*) INTO v_unexpected_tables
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'ai_environment_execution_profile', 'ai_login_profile', 'ai_execution_preflight',
          'ai_model_profile', 'ai_model_invocation', 'ai_prompt_template_version',
          'ai_human_request_recipient', 'ai_execution_checkpoint', 'ai_page_object',
          'ai_page_element', 'ai_test_data_lease', 'ai_test_data_cleanup', 'ai_step_approval'
      );

    IF v_unexpected_tables <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Recovery stopped: V3.7.2.80 advanced beyond the confirmed failure point';
    END IF;

    CALL ms_v80_drop_index_if_exists('ai_execution_task', 'idx_ai_execution_task_preflight');
    CALL ms_v80_drop_index_if_exists('ai_execution_task', 'idx_ai_execution_task_environment');
    CALL ms_v80_drop_index_if_exists('ai_execution_task', 'idx_ai_execution_task_model');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'scope_expansion_rate');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'expanded_scope_count');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'original_scope_count');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'blocked_detail');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'blocked_reason');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'execution_contract_hash');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'execution_contract');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'preflight_id');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'prompt_template_version_id');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'model_profile_id');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'credential_reference_id');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'environment_profile_version');
    CALL ms_v80_drop_column_if_exists('ai_execution_task', 'environment_profile_id');

    CALL ms_v80_drop_index_if_exists('ai_task_trigger', 'idx_ai_task_trigger_profiles');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'responsible_user_ids');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'notification_policy_json');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'evidence_policy_json');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'policy_json');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'required_capabilities');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'runner_type');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'credential_reference_id');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'environment_profile_id');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'prompt_template_id');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'model_profile_id');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger', 'trigger_version');

    CALL ms_v80_drop_index_if_exists('ai_task_trigger_history', 'uk_ai_trigger_scheduled_version');
    CALL ms_v80_drop_index_if_exists('ai_task_trigger_history', 'idx_ai_trigger_history_trace');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger_history', 'blocked_reason');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger_history', 'trace_id');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger_history', 'idempotency_key');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger_history', 'attempt_no');
    CALL ms_v80_drop_column_if_exists('ai_task_trigger_history', 'trigger_version');

    CALL ms_v80_drop_index_if_exists('ai_execution_human_request', 'idx_ai_human_request_task_status');
    CALL ms_v80_drop_index_if_exists('ai_execution_human_request', 'idx_ai_human_request_checkpoint');
    CALL ms_v80_drop_column_if_exists('ai_execution_human_request', 'trace_id');
    CALL ms_v80_drop_column_if_exists('ai_execution_human_request', 'checkpoint_id');
    CALL ms_v80_drop_column_if_exists('ai_execution_human_request', 'resolved_reason');
    CALL ms_v80_drop_column_if_exists('ai_execution_human_request', 'resolution_version');

    -- MySQL 8 applies a failed ALTER TABLE atomically. These calls also make
    -- the recovery safe if a compatible server retained the first sub-action.
    CALL ms_v80_drop_index_if_exists('ai_execution_artifact', 'idx_ai_artifact_retention');
    CALL ms_v80_drop_column_if_exists('ai_execution_artifact', 'redaction_status');

    DELETE FROM flyway_schema_history
    WHERE version = '3.7.2.80' AND success = 0;
END//

CALL ms_v80_recover_failed_migration()//

DROP PROCEDURE ms_v80_recover_failed_migration//
DROP PROCEDURE ms_v80_drop_column_if_exists//
DROP PROCEDURE ms_v80_drop_index_if_exists//

DELIMITER ;

SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
