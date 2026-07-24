-- 管理员账密登录入口 /login/admin：默认关闭（false）
INSERT INTO system_parameter (param_key, param_value, type)
SELECT 'ui.login.admin.enabled', 'false', 'text'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM system_parameter WHERE param_key = 'ui.login.admin.enabled'
);
