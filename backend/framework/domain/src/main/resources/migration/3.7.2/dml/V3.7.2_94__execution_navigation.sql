-- Navigation metadata only. Stable resource codes and custom role UI grants are retained.
-- No task, asset, token, version, evidence or role permission data is rewritten.
UPDATE permission_resource SET name = '执行中心', permission_id = 'AI_EXECUTION:READ',
    description = '执行任务入口；策略配置迁入项目执行设置'
WHERE code = 'EXECUTION_QUALITY_MENU';
UPDATE permission_resource SET parent_code = 'EXECUTION_QUALITY_MENU', name = '执行任务', route_name = 'executionTasks'
WHERE code = 'FUNCTIONAL_CASE_AUTOMATION_EXECUTION_TAB';
UPDATE permission_resource SET parent_code = 'EXECUTION_QUALITY_MENU', scope_type = 'PROJECT', name = '执行详情'
WHERE code = 'AGENT_EXECUTION_DETAIL_PAGE';
UPDATE permission_resource SET parent_code = 'EXECUTION_QUALITY_MENU', scope_type = 'PROJECT', name = '执行统计与历史评价'
WHERE code = 'AGENT_EVALUATION_PAGE';
UPDATE permission_resource SET parent_code = NULL, name = '门禁策略（项目执行设置）'
WHERE code = 'EXECUTION_QUALITY_POLICY_PAGE';
UPDATE permission_resource SET name = '执行配置兼容入口', description = '旧地址保留，配置入口迁入设置'
WHERE code = 'AGENT_MENU';
UPDATE permission_resource SET name = '执行器管理' WHERE code = 'AGENT_LIST_PAGE';
UPDATE permission_resource SET name = '运行告警' WHERE code = 'AGENT_CAPABILITY_PAGE';
UPDATE permission_resource SET name = '租约与调度' WHERE code = 'AGENT_QUEUE_PAGE';
UPDATE permission_resource SET name = '个人接入', parent_code = NULL WHERE code = 'AGENT_INTEGRATION_PAGE';
