-- Only for the isolated msp-export-20261021 acceptance database.
UPDATE system_parameter SET param_value='true' WHERE param_key='ui.login.admin.enabled';
SELECT param_key,param_value FROM system_parameter WHERE param_key='ui.login.admin.enabled';
SELECT version,success FROM metersphere_version WHERE version='3.7.2.104';
