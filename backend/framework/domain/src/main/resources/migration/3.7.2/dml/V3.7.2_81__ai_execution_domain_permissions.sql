-- Dedicated AI execution permissions. PLATFORM_AUTOMATION_MANAGE is deliberately
-- not granted to interactive project roles or Personal Agent tokens.
INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
VALUES ('AGENT_BUSINESS_FLOW_PAGE','AGENT_BUSINESS_FLOW_PAGE','Business flow','PAGE','SYSTEM','AGENT_MENU','AgentBusinessFlow','AI_EXECUTION:READ',b'1',b'0',662,b'1','Governed versioned business flow');

INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('AGENT_EXECUTION_DETAIL_PAGE','AGENT_EXECUTION_DETAIL_PAGE','AI execution detail','PAGE','SYSTEM','AGENT_MENU','AgentExecutionDetail','AI_EXECUTION:READ',b'0',b'0',661,b'1','AI execution frozen scope, runner, evidence and human collaboration detail');

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, p.permission_id
FROM user_role r
JOIN (
    SELECT 'AI_MODEL:READ' AS permission_id UNION ALL
    SELECT 'AI_EXECUTION:CONFIRM' UNION ALL
    SELECT 'AI_MODEL:MANAGE' UNION ALL SELECT 'AI_MODEL:VERIFY' UNION ALL
    SELECT 'AI_TRIGGER:READ' UNION ALL SELECT 'AI_TRIGGER:MANAGE' UNION ALL
    SELECT 'AI_RUNNER:READ' UNION ALL SELECT 'AI_RUNNER:MANAGE' UNION ALL
    SELECT 'AI_EVIDENCE:READ' UNION ALL
    SELECT 'AI_CREDENTIAL:READ_METADATA' UNION ALL
    SELECT 'AI_CREDENTIAL:MANAGE' UNION ALL SELECT 'AI_CREDENTIAL:VERIFY'
) p
WHERE r.id = 'project_admin'
  AND NOT EXISTS (SELECT 1 FROM user_role_permission urp
                   WHERE urp.role_id=r.id AND urp.permission_id=p.permission_id);

INSERT INTO user_role_permission (id, role_id, permission_id)
SELECT UUID_SHORT(), r.id, p.permission_id
FROM user_role r
JOIN (
    SELECT 'AI_MODEL:READ' AS permission_id UNION ALL
    SELECT 'AI_TRIGGER:READ' UNION ALL SELECT 'AI_RUNNER:READ' UNION ALL
    SELECT 'AI_EVIDENCE:READ' UNION ALL SELECT 'AI_CREDENTIAL:READ_METADATA'
) p
WHERE r.id = 'project_member'
  AND NOT EXISTS (SELECT 1 FROM user_role_permission urp
                   WHERE urp.role_id=r.id AND urp.permission_id=p.permission_id);

INSERT IGNORE INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id,
 visible_default, operable_default, sort, enabled, description)
VALUES
('AGENT_ENVIRONMENT_PROFILE_PAGE', 'AGENT_ENVIRONMENT_PROFILE_PAGE', '环境执行配置', 'PAGE',
 'SYSTEM', 'AGENT_MENU', 'AgentEnvironmentProfile', 'AI_EXECUTION:READ',
 b'1', b'0', 655, b'1', 'AI 执行环境配置'),
('AGENT_CREDENTIAL_REFERENCE_PAGE', 'AGENT_CREDENTIAL_REFERENCE_PAGE', '凭据引用', 'PAGE',
 'SYSTEM', 'AGENT_MENU', 'AgentCredentialReference', 'AI_CREDENTIAL:READ_METADATA',
 b'1', b'0', 656, b'1', 'AI 执行凭据引用元数据管理'),
('AGENT_MODEL_PROFILE_PAGE','AGENT_MODEL_PROFILE_PAGE','MAP Gateway 模型配置','PAGE','SYSTEM','AGENT_MENU','AgentModelProfile','AI_MODEL:READ',b'1',b'0',657,b'1','Gateway 逻辑模型配置'),
('AGENT_PROMPT_TEMPLATE_PAGE','AGENT_PROMPT_TEMPLATE_PAGE','Prompt 模板','PAGE','SYSTEM','AGENT_MENU','AgentPromptTemplate','AI_MODEL:READ',b'1',b'0',658,b'1','Prompt 不可变版本和发布'),
('AGENT_LOGIN_PROFILE_PAGE','AGENT_LOGIN_PROFILE_PAGE','自动登录配置','PAGE','SYSTEM','AGENT_MENU','AgentLoginProfile','AI_EXECUTION:READ',b'1',b'0',659,b'1','自动登录定位器和会话断言'),
('AGENT_PAGE_OBJECT_PAGE','AGENT_PAGE_OBJECT_PAGE','Page Object','PAGE','SYSTEM','AGENT_MENU','AgentPageObject','AI_EXECUTION:READ',b'1',b'0',660,b'1','受治理的页面元素定位器');
