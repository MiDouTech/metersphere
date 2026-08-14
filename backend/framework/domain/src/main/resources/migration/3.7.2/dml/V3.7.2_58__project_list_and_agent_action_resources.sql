-- 项目列表与 Agent 集成页面的真实按钮资源，确保“可见/可操作”配置有前端落点。
INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
VALUES
('PROJECT_LIST_ENTER_BUTTON', 'PROJECT_LIST_ENTER_BUTTON', '进入项目按钮', 'BUTTON', 'PROJECT', 'PROJECT_LIST_PAGE', NULL, NULL, b'1', b'1', 216, b'1', '切换并进入有权访问的项目'),
('PROJECT_LIST_ADD_MEMBER_BUTTON', 'PROJECT_LIST_ADD_MEMBER_BUTTON', '项目列表添加成员按钮', 'BUTTON', 'PROJECT', 'PROJECT_LIST_PAGE', NULL, 'PROJECT_USER:READ+ADD', b'1', b'1', 217, b'1', '从项目列表进入成员添加入口'),
('PROJECT_LIST_COPY_ID_BUTTON', 'PROJECT_LIST_COPY_ID_BUTTON', '复制项目 ID 按钮', 'BUTTON', 'PROJECT', 'PROJECT_LIST_PAGE', NULL, NULL, b'1', b'1', 218, b'1', '复制项目 ID'),
('AGENT_TOKEN_CREATE_BUTTON', 'AGENT_TOKEN_CREATE_BUTTON', '创建 Agent Token 按钮', 'BUTTON', 'SYSTEM', 'AGENT_INTEGRATION_PAGE', NULL, 'SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT', b'1', b'1', 656, b'1', '创建个人 Agent Token'),
('AGENT_TOKEN_DOWNLOAD_BUTTON', 'AGENT_TOKEN_DOWNLOAD_BUTTON', '下载 Agent 技能包按钮', 'BUTTON', 'SYSTEM', 'AGENT_INTEGRATION_PAGE', NULL, 'SYSTEM_PERSONAL_AI_AGENT:READ', b'1', b'1', 657, b'1', '下载 Agent 接入技能包'),
('AGENT_TOKEN_UPDATE_BUTTON', 'AGENT_TOKEN_UPDATE_BUTTON', '设置 Agent Token 按钮', 'BUTTON', 'SYSTEM', 'AGENT_INTEGRATION_PAGE', NULL, 'SYSTEM_PERSONAL_AI_AGENT:READ+CONNECT', b'1', b'1', 658, b'1', '编辑或启停个人 Agent Token'),
('AGENT_TOKEN_DELETE_BUTTON', 'AGENT_TOKEN_DELETE_BUTTON', '删除 Agent Token 按钮', 'BUTTON', 'SYSTEM', 'AGENT_INTEGRATION_PAGE', NULL, 'SYSTEM_PERSONAL_AI_AGENT:READ+REVOKE', b'1', b'1', 659, b'1', '撤销个人 Agent Token');

-- 兼容已执行过早期脚本的环境：Agent 是全局入口，页面可见性由系统角色管理；业务执行仍与项目 AI_EXECUTION 权限取交集。
UPDATE permission_resource
SET scope_type = 'SYSTEM'
WHERE code IN (
  'AGENT_MENU', 'AGENT_LIST_PAGE', 'AGENT_CAPABILITY_PAGE', 'AGENT_QUEUE_PAGE',
  'AGENT_EVALUATION_PAGE', 'AGENT_INTEGRATION_PAGE', 'AGENT_TOKEN_CREATE_BUTTON',
  'AGENT_TOKEN_DOWNLOAD_BUTTON', 'AGENT_TOKEN_UPDATE_BUTTON', 'AGENT_TOKEN_DELETE_BUTTON'
);

UPDATE permission_resource
SET permission_id = 'SYSTEM_PERSONAL_AI_AGENT:READ'
WHERE code = 'AGENT_INTEGRATION_PAGE';

-- 原列表页不存在新建/归档产品入口，停用遗留占位资源。
UPDATE permission_resource
SET enabled = b'0', visible_default = b'0', operable_default = b'0'
WHERE code IN ('PROJECT_ADD_BUTTON', 'PROJECT_ARCHIVE_BUTTON');

-- 成员角色初始权限等同管理员，升级后新增资源也默认补齐。
INSERT IGNORE INTO user_role_ui_permission (id, role_id, resource_code, visible, operable)
SELECT UUID_SHORT(), 'permission_member', pr.code, b'1', b'1'
FROM permission_resource pr
WHERE pr.code IN (
  'PROJECT_LIST_ENTER_BUTTON', 'PROJECT_LIST_ADD_MEMBER_BUTTON', 'PROJECT_LIST_COPY_ID_BUTTON',
  'AGENT_TOKEN_CREATE_BUTTON', 'AGENT_TOKEN_DOWNLOAD_BUTTON', 'AGENT_TOKEN_UPDATE_BUTTON', 'AGENT_TOKEN_DELETE_BUTTON'
)
AND EXISTS (SELECT 1 FROM user_role WHERE id = 'permission_member');
