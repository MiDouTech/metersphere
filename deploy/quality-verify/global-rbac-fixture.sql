-- Disposable msp-quality-verify database only; never deploy this fixture.
USE metersphere_quality;
INSERT IGNORE INTO user_role(id,name,internal,type,create_time,update_time,create_user,scope_id,enabled)
VALUES ('quality-global-reader','Global reader',0,'SYSTEM',1,1,'admin','system',1),
 ('quality-global-editor','Global editor',0,'SYSTEM',1,1,'admin','system',1),
 ('quality-global-publisher','Global publisher',0,'SYSTEM',1,1,'admin','system',1);
INSERT IGNORE INTO user_role_permission(id,role_id,permission_id) VALUES
 ('qg-reader-read','quality-global-reader','SYSTEM_QUALITY:READ'),
 ('qg-editor-read','quality-global-editor','SYSTEM_QUALITY:READ'),
 ('qg-publisher-read','quality-global-publisher','SYSTEM_QUALITY:READ'),
 ('qg-editor-manage','quality-global-editor','SYSTEM_QUALITY:MANAGE'),
 ('qg-publisher-publish','quality-global-publisher','SYSTEM_QUALITY:PUBLISH');
INSERT IGNORE INTO user_role_relation(id,user_id,role_id,source_id,organization_id,create_time,create_user)
SELECT CONCAT(r.id,'-binding'),u.id,r.id,'system',NULL,1,'admin' FROM user_role r
 JOIN user u ON u.email=CONCAT(REPLACE(r.id,'quality-global-','quality-'),'-api@quality.invalid')
 WHERE r.id IN ('quality-global-reader','quality-global-editor','quality-global-publisher');
UPDATE user SET last_organization_id='',last_project_id='' WHERE email='quality-pure-admin-api@quality.invalid';

INSERT IGNORE INTO user_role_relation(id,user_id,role_id,source_id,organization_id,create_time,create_user)
SELECT 'quality-pure-admin-binding',id,'admin','system',NULL,1,'admin' FROM user
WHERE email='quality-pure-admin-api@quality.invalid';
