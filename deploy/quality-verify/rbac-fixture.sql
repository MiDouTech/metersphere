-- ONLY for msp-quality-verify / metersphere_quality. Never apply to shared data.
USE metersphere_quality;
INSERT IGNORE INTO user_role(id,name,internal,type,create_time,update_time,create_user,scope_id,enabled)
VALUES ('quality-reader','Quality reader',0,'PROJECT',1,1,'admin','100001100001',1),
       ('quality-editor','Quality editor',0,'PROJECT',1,1,'admin','100001100001',1),
       ('quality-publisher','Quality publisher',0,'PROJECT',1,1,'admin','100001100001',1);
-- Accounts must first be created through /system/user/add (the E2E setup does this).
UPDATE user SET last_organization_id='100001',last_project_id='100001100001'
WHERE email IN ('quality-reader-api@quality.invalid','quality-editor-api@quality.invalid','quality-publisher-api@quality.invalid');
INSERT IGNORE INTO user_role_relation(id,user_id,role_id,source_id,organization_id,create_time,create_user)
SELECT CONCAT(r.id,'-api-project'),u.id,r.id,'100001100001','100001',1,'admin' FROM user_role r JOIN user u ON u.email=CONCAT(r.id,'-api@quality.invalid') WHERE r.id IN ('quality-reader','quality-editor','quality-publisher');
INSERT IGNORE INTO user_role_relation(id,user_id,role_id,source_id,organization_id,create_time,create_user)
SELECT CONCAT(r.id,'-api-member'),u.id,'project_member','100001100001','100001',1,'admin' FROM user_role r JOIN user u ON u.email=CONCAT(r.id,'-api@quality.invalid') WHERE r.id IN ('quality-reader','quality-editor','quality-publisher');
INSERT IGNORE INTO user_role_relation(id,user_id,role_id,source_id,organization_id,create_time,create_user)
SELECT CONCAT(r.id,'-api-org'),u.id,'org_member','100001','100001',1,'admin' FROM user_role r JOIN user u ON u.email=CONCAT(r.id,'-api@quality.invalid') WHERE r.id IN ('quality-reader','quality-editor','quality-publisher');
INSERT IGNORE INTO user_role_permission(id,role_id,permission_id)
VALUES ('quality-reader-read','quality-reader','QUALITY:READ'),
       ('quality-editor-read','quality-editor','QUALITY:READ'),
       ('quality-publisher-read','quality-publisher','QUALITY:READ'),
       ('quality-editor-manage','quality-editor','QUALITY_POLICY:MANAGE'),
       ('quality-publisher-publish','quality-publisher','QUALITY_POLICY:PUBLISH');
