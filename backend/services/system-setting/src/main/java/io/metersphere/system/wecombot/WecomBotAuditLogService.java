package io.metersphere.system.wecombot;

import io.metersphere.sdk.constants.OperationLogConstants;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.log.dto.LogDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WecomBotAuditLogService {
    private final WecomBotService service;

    public WecomBotAuditLogService(WecomBotService service) {
        this.service = service;
    }

    public LogDTO config(WecomBotModels.ConfigRequest request) {
        LogDTO log = log(OperationLogType.UPDATE, "更新企微智能机器人配置");
        WecomBotModels.ConfigView current = service.getConfig();
        log.setOriginalValue(JSON.toJSONBytes(current));
        log.setModifiedValue(JSON.toJSONBytes(Map.of(
                "name", request.name(), "botIdMasked", mask(request.botId()),
                "secretConfigured", current.secretConfigured() || StringUtils.isNotBlank(request.secret())
                        || StringUtils.isNotBlank(request.secretRef()))));
        return log;
    }

    public LogDTO action(String action) {
        return log(OperationLogType.UPDATE, action);
    }

    public LogDTO ruleAction(String id, String action) {
        LogDTO log = log(OperationLogType.UPDATE, action);
        log.setOriginalValue(JSON.toJSONBytes(service.rule(id)));
        return log;
    }

    public LogDTO scheduleAction(String id, String action) {
        LogDTO log = log(OperationLogType.UPDATE, action);
        log.setOriginalValue(JSON.toJSONBytes(service.schedule(id)));
        return log;
    }

    public LogDTO rule(WecomBotModels.RuleRequest request) {
        LogDTO log = log(OperationLogType.UPDATE, "变更企微通知规则：" + request.name());
        log.setModifiedValue(JSON.toJSONBytes(Map.of(
                "name", request.name(), "scopeType", request.scopeType(), "notificationType", request.notificationType(),
                "triggerType", request.triggerType(), "recipientCount", request.recipientSpec() == null ? 0 : request.recipientSpec().size())));
        return log;
    }

    public LogDTO rule(String id, WecomBotModels.RuleRequest request) {
        LogDTO log = rule(request);
        log.setOriginalValue(JSON.toJSONBytes(service.rule(id)));
        log.setModifiedValue(JSON.toJSONBytes(request));
        return log;
    }

    public LogDTO schedule(String id, WecomBotModels.ScheduleRequest request) {
        LogDTO log = log(OperationLogType.UPDATE, "变更企微通知计划");
        log.setOriginalValue(JSON.toJSONBytes(service.schedule(id)));
        log.setModifiedValue(JSON.toJSONBytes(request));
        return log;
    }

    public LogDTO newSchedule(String ruleId, WecomBotModels.ScheduleRequest request) {
        LogDTO log = log(OperationLogType.ADD, "新增企微通知计划");
        log.setSourceId(ruleId);
        log.setModifiedValue(JSON.toJSONBytes(request));
        return log;
    }

    private LogDTO log(OperationLogType type, String content) {
        return new LogDTO(OperationLogConstants.SYSTEM, OperationLogConstants.SYSTEM, null, null,
                type.name(), OperationLogModule.SETTING_SYSTEM_WECOM_BOT, content);
    }

    private String mask(String value) {
        if (value == null || value.length() < 6) return "***";
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
