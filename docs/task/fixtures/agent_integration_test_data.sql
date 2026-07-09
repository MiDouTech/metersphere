-- Agent 集成测试 Fixture
-- 明文 Token: msat_demo_token_for_local_testing_01
-- 用法：将 REPLACE_PROJECT_ID 替换为实际项目 ID（默认项目可用 100001100001）

INSERT INTO agent_token (
    id, name, token_prefix, token_hash, user_id, project_id, scopes, enable, create_time, create_user
) VALUES (
    'agent-token-demo-001',
    'Local Dev Agent',
    'msat',
    'a59b6414c0face6b83f9dcf931bf1c9591bf7b9466dc141da02cb91567fd1a58',
    'admin',
    'REPLACE_PROJECT_ID',
    'FUNCTIONAL_ALL',
    1,
    UNIX_TIMESTAMP() * 1000,
    'admin'
) ON DUPLICATE KEY UPDATE
    token_hash = VALUES(token_hash),
    scopes = VALUES(scopes),
    enable = VALUES(enable);

-- 只读 Token（用于验证 submit 403）
INSERT INTO agent_token (
    id, name, token_prefix, token_hash, user_id, project_id, scopes, enable, create_time, create_user
) VALUES (
    'agent-token-demo-read',
    'Local Dev Agent ReadOnly',
    'msat',
    '3cede22562f199a4ea28a2916dffc8064c4f8506097d738d5a62e830e234b496',
    'admin',
    'REPLACE_PROJECT_ID',
    'FUNCTIONAL_READ',
    1,
    UNIX_TIMESTAMP() * 1000,
    'admin'
) ON DUPLICATE KEY UPDATE
    scopes = VALUES(scopes),
    enable = VALUES(enable);

-- 模块别名示例（CW -> 财务模块，需替换 module_id）
-- INSERT INTO agent_module_alias (id, project_id, alias, module_id, create_time, create_user)
-- VALUES ('alias-cw-001', 'REPLACE_PROJECT_ID', 'CW', 'REPLACE_FINANCE_MODULE_ID', UNIX_TIMESTAMP() * 1000, 'admin');
