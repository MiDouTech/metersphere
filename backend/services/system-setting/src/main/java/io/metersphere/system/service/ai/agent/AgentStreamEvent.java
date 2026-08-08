package io.metersphere.system.service.ai.agent;

import java.util.Map;

public record AgentStreamEvent(String type, String requestId, long sequence, Map<String, Object> payload) {
}
