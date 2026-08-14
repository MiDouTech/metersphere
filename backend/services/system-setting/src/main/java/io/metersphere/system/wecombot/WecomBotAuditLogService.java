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

    public LogDTO rule(WecomBotModels.RuleRequest request) {
        LogDTO log = log(OperationLogType.UPDATE, "变更企微通知规则：" + request.name());
        log.setModifiedValue(JSON.toJSONBytes(Map.of(
                "name", request.name(), "scopeType", request.scopeType(), "notificationType", request.notificationType(),
                "triggerType", request.triggerType(), "recipientCount", request.recipientSpec() == null ? 0 : request.recipientSpec().size())));
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
