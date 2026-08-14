INSERT IGNORE INTO user_role_permission (id, role_id, permission_id)
VALUES
    (UUID_SHORT(), 'admin', 'SYSTEM_CONFIG_WECOM_BOT:READ'),
    (UUID_SHORT(), 'admin', 'SYSTEM_CONFIG_WECOM_BOT:UPDATE'),
    (UUID_SHORT(), 'admin', 'SYSTEM_NOTIFICATION_RULE:READ'),
    (UUID_SHORT(), 'admin', 'SYSTEM_NOTIFICATION_RULE:CREATE'),
    (UUID_SHORT(), 'admin', 'SYSTEM_NOTIFICATION_RULE:UPDATE'),
    (UUID_SHORT(), 'admin', 'SYSTEM_NOTIFICATION_RULE:DELETE'),
    (UUID_SHORT(), 'admin', 'SYSTEM_NOTIFICATION_LOG:READ'),
    (UUID_SHORT(), 'admin', 'SYSTEM_NOTIFICATION_LOG:RETRY');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('SYSTEM_CONFIG_WECOM_BOT_TAB', 'SYSTEM_CONFIG_WECOM_BOT_TAB', '企微智能机器人', 'TAB', 'SYSTEM',
 'SYSTEM_CONFIG_PAGE', NULL, 'SYSTEM_CONFIG_WECOM_BOT:READ', b'1', b'0', 724, b'1', '企微智能机器人配置与通知管理'),
('SYSTEM_CONFIG_WECOM_BOT_UPDATE_BUTTON', 'SYSTEM_CONFIG_WECOM_BOT_UPDATE_BUTTON', '配置企微智能机器人', 'BUTTON', 'SYSTEM',
 'SYSTEM_CONFIG_WECOM_BOT_TAB', NULL, 'SYSTEM_CONFIG_WECOM_BOT:UPDATE', b'1', b'1', 1, b'1', '保存、启停和测试机器人'),
('SYSTEM_NOTIFICATION_RULE_PAGE', 'SYSTEM_NOTIFICATION_RULE_PAGE', '企微通知规则', 'PAGE', 'SYSTEM',
 'SYSTEM_CONFIG_WECOM_BOT_TAB', NULL, 'SYSTEM_NOTIFICATION_RULE:READ', b'1', b'0', 2, b'1', '管理企微通知规则'),
('SYSTEM_NOTIFICATION_LOG_PAGE', 'SYSTEM_NOTIFICATION_LOG_PAGE', '企微通知日志', 'PAGE', 'SYSTEM',
 'SYSTEM_CONFIG_WECOM_BOT_TAB', NULL, 'SYSTEM_NOTIFICATION_LOG:READ', b'1', b'0', 3, b'1', '查看企微通知发送日志')
ON DUPLICATE KEY UPDATE
 name=VALUES(name), permission_id=VALUES(permission_id), sort=VALUES(sort), enabled=b'1', description=VALUES(description);

INSERT INTO user_role_ui_permission (id, role_id, resource_code, visible, operable)
SELECT UUID_SHORT(), 'admin', pr.code, b'1', IF(pr.type = 'BUTTON', b'1', b'0')
FROM permission_resource pr
WHERE pr.code IN ('SYSTEM_CONFIG_WECOM_BOT_TAB', 'SYSTEM_CONFIG_WECOM_BOT_UPDATE_BUTTON',
                  'SYSTEM_NOTIFICATION_RULE_PAGE', 'SYSTEM_NOTIFICATION_LOG_PAGE')
  AND EXISTS (SELECT 1 FROM user_role WHERE id = 'admin')
  AND NOT EXISTS (
      SELECT 1 FROM user_role_ui_permission urp
      WHERE urp.role_id = 'admin' AND urp.resource_code = pr.code
  );
