package io.metersphere.system.wecombot;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.SessionUtils;
import jakarta.validation.Valid;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.Logical;
import org.springframework.web.bind.annotation.*;
import io.metersphere.system.log.annotation.Log;
import io.metersphere.system.log.constants.OperationLogType;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wecom-bot")
public class WecomBotController {
    private final WecomBotService service;

    public WecomBotController(WecomBotService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_READ)
    public WecomBotModels.ConfigView config() { return service.getConfig(); }

    @PostMapping("/config")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.config(#request)", msClass = WecomBotAuditLogService.class)
    public WecomBotModels.ConfigView save(@Valid @RequestBody WecomBotModels.ConfigRequest request) {
        return service.saveConfig(request, SessionUtils.getUserId());
    }

    @PostMapping("/config/test-connection")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Test WeCom Bot connection')", msClass = WecomBotAuditLogService.class)
    public WecomBotModels.StatusView testConnection() { return service.testConnection(); }

    @PostMapping("/config/enable")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Enable WeCom Bot')", msClass = WecomBotAuditLogService.class)
    public WecomBotModels.StatusView enable() { return service.enable(true, SessionUtils.getUserId()); }

    @PostMapping("/config/disable")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Disable WeCom Bot')", msClass = WecomBotAuditLogService.class)
    public WecomBotModels.StatusView disable() { return service.enable(false, SessionUtils.getUserId()); }

    @GetMapping("/status")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_READ)
    public WecomBotModels.StatusView status() { return service.status(); }

    @GetMapping("/chats")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_READ)
    public List<Map<String, Object>> chats() { return service.chats(); }

    @GetMapping("/recipient-options/users")
    @RequiresPermissions(value = {PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ,
            PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE}, logical = Logical.OR)
    public List<Map<String, Object>> userOptions(@RequestParam(required = false) String projectId) { return service.userOptions(projectId); }

    @GetMapping("/recipient-options/roles")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public List<Map<String, Object>> roleOptions(@RequestParam(required = false) String projectId) { return service.roleOptions(projectId); }

    @GetMapping("/recipient-options/positions")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public List<Map<String, Object>> positionOptions(@RequestParam(required = false) String projectId) { return service.positionOptions(projectId); }

    @PostMapping("/recipient-options/preview")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public Map<String, Object> recipientPreview(@Valid @RequestBody WecomBotModels.RuleRequest request) {
        return service.recipientPreview(request);
    }

    @GetMapping("/template-variables/{notificationType}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public List<WecomBotModels.TemplateVariable> templateVariables(@PathVariable String notificationType) {
        return service.templateVariables(notificationType);
    }

    @GetMapping("/recipient-options/bug-terminal-statuses")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public List<Map<String, Object>> bugTerminalStatuses() { return service.bugTerminalStatuses(); }

    @PostMapping("/chats/{id}/rename")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Rename discovered WeCom group')", msClass = WecomBotAuditLogService.class)
    public void rename(@PathVariable String id, @Valid @RequestBody WecomBotModels.RenameRequest request) { service.renameChat(id, request.name()); }

    @PostMapping("/chats/{id}/enable")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Enable discovered WeCom group')", msClass = WecomBotAuditLogService.class)
    public void enableChat(@PathVariable String id) { service.activateChat(id, true); }

    @PostMapping("/chats/{id}/disable")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Disable discovered WeCom group')", msClass = WecomBotAuditLogService.class)
    public void disableChat(@PathVariable String id) { service.activateChat(id, false); }

    @PostMapping("/messages/test-user")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.action('Send WeCom user test message')", msClass = WecomBotAuditLogService.class)
    public String testUser(@Valid @RequestBody WecomBotModels.TestMessageRequest request) { return service.enqueueTest(request, false, SessionUtils.getUserId()); }

    @PostMapping("/messages/test-group")
    @RequiresPermissions(PermissionConstants.SYSTEM_CONFIG_WECOM_BOT_UPDATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.action('Send WeCom group test message')", msClass = WecomBotAuditLogService.class)
    public String testGroup(@Valid @RequestBody WecomBotModels.TestMessageRequest request) { return service.enqueueTest(request, true, SessionUtils.getUserId()); }

    @GetMapping("/notification-rules")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public List<Map<String, Object>> rules() { return service.rules(); }

    @PostMapping("/notification-rules")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_CREATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.rule(#request)", msClass = WecomBotAuditLogService.class)
    public String createRule(@Valid @RequestBody WecomBotModels.RuleRequest request) { return service.createRule(request, SessionUtils.getUserId()); }

    @PutMapping("/notification-rules/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.rule(#id,#request)", msClass = WecomBotAuditLogService.class)
    public void updateRule(@PathVariable String id, @Valid @RequestBody WecomBotModels.RuleRequest request) { service.updateRule(id, request, SessionUtils.getUserId()); }

    @DeleteMapping("/notification-rules/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_DELETE)
    @Log(type = OperationLogType.DELETE, expression = "#msClass.ruleAction(#id,'Delete WeCom notification rule')", msClass = WecomBotAuditLogService.class)
    public void deleteRule(@PathVariable String id) { service.deleteRule(id); }

    @PostMapping("/notification-rules/{id}/enable")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.ruleAction(#id,'Enable WeCom notification rule')", msClass = WecomBotAuditLogService.class)
    public void enableRule(@PathVariable String id) { service.enableRule(id, true, SessionUtils.getUserId()); }

    @PostMapping("/notification-rules/{id}/disable")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.ruleAction(#id,'Disable WeCom notification rule')", msClass = WecomBotAuditLogService.class)
    public void disableRule(@PathVariable String id) { service.enableRule(id, false, SessionUtils.getUserId()); }

    @PostMapping("/notification-rules/{id}/preview")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public String preview(@PathVariable String id, @RequestBody(required = false) WecomBotModels.PreviewRequest request) {
        return service.preview(id, request == null ? Map.of() : request.variables());
    }

    @PostMapping("/notification-rules/{id}/run-once")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.ruleAction(#id,'Run WeCom notification rule once')", msClass = WecomBotAuditLogService.class)
    public List<String> runOnce(@PathVariable String id) { return service.runOnce(id, SessionUtils.getUserId(), null); }

    @GetMapping("/notification-rules/{id}/schedules")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_READ)
    public List<Map<String, Object>> schedules(@PathVariable String id) { return service.schedules(id); }

    @GetMapping("/notification-rules/{id}/schedule-executions")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_LOG_READ)
    public List<Map<String, Object>> scheduleExecutions(@PathVariable String id) { return service.scheduleExecutions(id); }

    @PostMapping("/notification-rules/{id}/schedules")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.newSchedule(#id,#request)", msClass = WecomBotAuditLogService.class)
    public String createSchedule(@PathVariable String id, @Valid @RequestBody WecomBotModels.ScheduleRequest request) {
        return service.createSchedule(id, request, SessionUtils.getUserId());
    }

    @PutMapping("/notification-schedules/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.schedule(#id,#request)", msClass = WecomBotAuditLogService.class)
    public void updateSchedule(@PathVariable String id, @Valid @RequestBody WecomBotModels.ScheduleRequest request) {
        service.updateSchedule(id, request, SessionUtils.getUserId());
    }

    @DeleteMapping("/notification-schedules/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_DELETE)
    @Log(type = OperationLogType.DELETE, expression = "#msClass.scheduleAction(#id,'Delete WeCom notification schedule')", msClass = WecomBotAuditLogService.class)
    public void deleteSchedule(@PathVariable String id) { service.deleteSchedule(id); }

    @PostMapping("/notification-schedules/{id}/{enabled:enable|disable}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.scheduleAction(#id,'Toggle WeCom notification schedule')", msClass = WecomBotAuditLogService.class)
    public void toggleSchedule(@PathVariable String id, @PathVariable String enabled) {
        service.enableSchedule(id, "enable".equals(enabled), SessionUtils.getUserId());
    }

    @PostMapping("/notification-schedules/{id}/run-once")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_RULE_UPDATE)
    @Log(type = OperationLogType.ADD, expression = "#msClass.scheduleAction(#id,'Run WeCom notification schedule once')", msClass = WecomBotAuditLogService.class)
    public List<String> runSchedule(@PathVariable String id) {
        return service.runScheduleOnce(id, SessionUtils.getUserId());
    }

    @GetMapping("/messages/logs")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_LOG_READ)
    public WecomBotModels.PageResult<Map<String, Object>> logs(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String eventType,
                                                               @RequestParam(required = false) String targetType,
                                                               @RequestParam(required = false) String ruleId,
                                                               @RequestParam(required = false) Long startAt,
                                                               @RequestParam(required = false) Long endAt) {
        return service.logs(page, pageSize, status, eventType, targetType, ruleId, startAt, endAt);
    }

    @GetMapping("/messages/{id}")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_LOG_READ)
    public Map<String, Object> log(@PathVariable String id) { return service.log(id); }

    @GetMapping("/metrics")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_LOG_READ)
    public Map<String, Object> metrics() { return service.metrics(); }

    @PostMapping("/messages/{id}/retry")
    @RequiresPermissions(PermissionConstants.SYSTEM_NOTIFICATION_LOG_RETRY)
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.action('Retry WeCom notification')", msClass = WecomBotAuditLogService.class)
    public void retry(@PathVariable String id) { service.retry(id); }
}
