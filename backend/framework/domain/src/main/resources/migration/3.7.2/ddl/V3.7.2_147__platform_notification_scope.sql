-- MSP-30: additive binding only. NULL keeps every existing delivery on its original
-- project configuration and ciphertext AAD. Never copy, rewrite, or replay history.
ALTER TABLE hifox_http_delivery ADD COLUMN config_scope VARCHAR(50) NULL;
CREATE INDEX idx_http_config_scope ON hifox_http_delivery(config_scope,state);
ALTER TABLE project_robot MODIFY COLUMN app_key VARCHAR(255) NULL;

-- Reserved configuration namespace, not a business project. Built-in mention/reply
-- tasks are prepared transactionally by PlatformNotificationInitializer after migration.
-- No project configuration or ordinary/mail rule is copied.
INSERT INTO project_robot(id,project_id,name,platform,webhook,enable,create_user,create_time,update_user,update_time,description)
VALUES ('msp-platform-in-site','__MSP_PLATFORM_NOTIFICATIONS__','robot_in_site','IN_SITE','NONE',1,'admin',UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000,'admin',UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000,'robot_in_site_description'),
       ('msp-platform-mail','__MSP_PLATFORM_NOTIFICATIONS__','robot_mail','MAIL','NONE',0,'admin',UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000,'admin',UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000,'robot_mail_description');
