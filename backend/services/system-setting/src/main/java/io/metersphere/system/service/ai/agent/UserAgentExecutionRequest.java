package io.metersphere.system.service.ai.agent;

import java.util.List;
import java.util.Map;

public record UserAgentExecutionRequest(String requestId, String conversationId, String projectId,
                                        String connectionId, String deviceId, String provider,
                                        String systemPrompt, String prompt, String externalSessionId,
                                        List<String> allowedTools, Map<String, Object> limits) {
}
