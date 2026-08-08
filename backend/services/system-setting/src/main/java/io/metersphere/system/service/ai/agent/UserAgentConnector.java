package io.metersphere.system.service.ai.agent;

import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import reactor.core.publisher.Flux;

import java.util.Map;

public interface UserAgentConnector {
    boolean supports(String provider, String connectionMode);

    AiUserAgentConnectionDTO connectionStatus(String connectionId, String userId);

    Flux<AgentStreamEvent> stream(UserAgentExecutionRequest request, String userId);

    void sendToolResult(String requestId, String toolCallId, boolean success,
                        Map<String, Object> result, String errorCode);

    void cancel(String requestId, String userId);
}
