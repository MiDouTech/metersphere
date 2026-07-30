SET SESSION innodb_lock_wait_timeout = 7200;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND COLUMN_NAME = 'last_execute_time') = 0,
    'ALTER TABLE functional_case ADD COLUMN last_execute_time BIGINT NULL COMMENT ''最后执行时间，仅执行状态变更时更新'' AFTER last_execute_user',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    (SELECT COUNT(1)
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'functional_case'
       AND INDEX_NAME = 'idx_functional_case_last_execute_time') = 0,
    'ALTER TABLE functional_case ADD INDEX idx_functional_case_last_execute_time (last_execute_time)',
    'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET SESSION innodb_lock_wait_timeout = DEFAULT;
