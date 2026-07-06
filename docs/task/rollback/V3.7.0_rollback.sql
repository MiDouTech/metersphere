-- rollback V3.7.0_2
ALTER TABLE `user` DROP INDEX uk_user_wecom_userid;
ALTER TABLE `user` DROP INDEX idx_user_department;
ALTER TABLE `user` DROP INDEX idx_user_org_dept;
ALTER TABLE `user` DROP COLUMN wecom_userid, DROP COLUMN department_id, DROP COLUMN `position`, DROP COLUMN sync_status, DROP COLUMN sync_time;

-- rollback V3.7.0_1
DROP TABLE IF EXISTS org_sync_log;
DROP TABLE IF EXISTS org_wecom_sync_config;
DROP TABLE IF EXISTS department;
