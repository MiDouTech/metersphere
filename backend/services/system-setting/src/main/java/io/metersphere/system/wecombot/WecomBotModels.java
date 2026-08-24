package io.metersphere.system.wecombot;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class WecomBotModels {
    private WecomBotModels() {
    }

    public record ConfigView(String id, String name, String botId, boolean secretConfigured, boolean enabled,
                             String status, Long lastConnectedAt, Long lastHeartbeatAt, String lastErrorCode,
                             String lastErrorMessage) {
    }

    public record ConfigRequest(@NotBlank String name, @NotBlank String botId, String secret, String secretRef) {
    }

    public record StatusView(String status, boolean ready, Long lastConnectedAt, Long lastHeartbeatAt,
                             String lastErrorCode, String lastErrorMessage) {
    }

    public record RenameRequest(@NotBlank String name) {
    }

    public record TestMessageRequest(String chatId, String userId, @NotBlank String content) {
    }

    public record RuleRequest(@NotBlank String name, @NotBlank String scopeType, String scopeId,
                              @NotBlank String notificationType, @NotBlank String triggerType,
                              Map<String, Object> triggerConfig, String cron, @NotBlank String timezone,
                              @NotBlank String template, Map<String, Object> recipientSpec,
                              @NotBlank String deliveryMode, Map<String, Object> stopConfig,
                              Long startAt, Long endAt, List<ScheduleRequest> schedules) {
    }

    public record ScheduleRequest(String id, @NotBlank String cycleType, List<Integer> weekdays,
                                  @NotBlank String executionTime, @NotBlank String timezone,
                                  Boolean enabled) {
    }

    public record TemplateVariable(String key, String name, String description, String example) {
    }

    public record PreviewRequest(Map<String, Object> variables) {
    }

    public record CallbackEvent(@NotBlank String eventId, String nonce, String botId, String status,
                                String errorCode, String errorMessage, Long occurredAt, String chatId,
                                String chatType, String fromUserid, String displayName, String outboxId,
                                String requestId, Boolean success, Boolean retryable, Map<String, Object> metadata) {
    }

    public record BridgeSendRequest(String requestId, String outboxId, String targetType, String targetId, String messageType,
                                    Map<String, Object> payload) {
    }

    public record BridgeResult(boolean success, boolean retryable, String errorCode, String errorMessage) {
    }

    public record PageResult<T>(List<T> list, long total) {
    }
}
