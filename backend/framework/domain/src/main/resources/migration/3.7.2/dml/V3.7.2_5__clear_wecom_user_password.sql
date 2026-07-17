SET SESSION innodb_lock_wait_timeout = 7200;

-- 清理企微同步用户的可预测默认密码；admin 保留运维登录能力。
-- 【注意】须人工审查后再上生产。
UPDATE `user`
SET password = '',
    update_time = UNIX_TIMESTAMP() * 1000,
    update_user = 'admin'
WHERE deleted = 0
  AND id <> 'admin'
  AND wecom_userid IS NOT NULL
  AND wecom_userid <> '';

SET SESSION innodb_lock_wait_timeout = DEFAULT;
