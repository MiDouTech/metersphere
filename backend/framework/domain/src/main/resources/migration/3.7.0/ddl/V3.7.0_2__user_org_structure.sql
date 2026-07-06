-- set innodb lock wait timeout
SET SESSION innodb_lock_wait_timeout = 7200;

ALTER TABLE `user` ADD COLUMN wecom_userid VARCHAR(100) COMMENT '企微UserID';
ALTER TABLE `user` ADD COLUMN department_id VARCHAR(50) COMMENT '主部门本地ID';
ALTER TABLE `user` ADD COLUMN `position` VARCHAR(100) COMMENT '职位';
ALTER TABLE `user` ADD COLUMN sync_status TINYINT DEFAULT 0 COMMENT '同步状态';
ALTER TABLE `user` ADD COLUMN sync_time BIGINT COMMENT '最近同步时间';

CREATE UNIQUE INDEX uk_user_wecom_userid ON `user`(wecom_userid);
CREATE INDEX idx_user_department ON `user`(department_id);
CREATE INDEX idx_user_org_dept ON `user`(last_organization_id, department_id);

-- set innodb lock wait timeout to default
SET SESSION innodb_lock_wait_timeout = DEFAULT;
