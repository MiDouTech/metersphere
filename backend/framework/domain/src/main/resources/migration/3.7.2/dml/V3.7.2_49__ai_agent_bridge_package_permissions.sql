INSERT IGNORE INTO user_role_permission (id, role_id, permission_id)
VALUES
    (UUID_SHORT(), 'admin', 'SYSTEM_AGENT_PACKAGE:READ'),
    (UUID_SHORT(), 'admin', 'SYSTEM_AGENT_PACKAGE:READ+ADD'),
    (UUID_SHORT(), 'admin', 'SYSTEM_AGENT_PACKAGE:READ+UPDATE'),
    (UUID_SHORT(), 'admin', 'SYSTEM_AGENT_PACKAGE:READ+DELETE');

INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('SYSTEM_AGENT_PACKAGE_UPLOAD_BUTTON', 'SYSTEM_AGENT_PACKAGE_UPLOAD_BUTTON', '上传 Agent 安装包', 'BUTTON', 'SYSTEM',
 NULL, 'settingSystemAgentIntegration', 'SYSTEM_AGENT_PACKAGE:READ+ADD', b'1', b'1', 726, b'1', '上传内部 Agent 安装包'),
('SYSTEM_AGENT_PACKAGE_ENABLE_BUTTON', 'SYSTEM_AGENT_PACKAGE_ENABLE_BUTTON', '启停 Agent 安装包', 'BUTTON', 'SYSTEM',
 NULL, 'settingSystemAgentIntegration', 'SYSTEM_AGENT_PACKAGE:READ+UPDATE', b'1', b'1', 727, b'1', '启用或停用 Agent 安装包'),
('SYSTEM_AGENT_PACKAGE_DELETE_BUTTON', 'SYSTEM_AGENT_PACKAGE_DELETE_BUTTON', '删除 Agent 安装包', 'BUTTON', 'SYSTEM',
 NULL, 'settingSystemAgentIntegration', 'SYSTEM_AGENT_PACKAGE:READ+DELETE', b'1', b'1', 728, b'1', '删除未启用的 Agent 安装包');
