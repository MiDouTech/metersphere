package io.metersphere.system.service.ai.agent;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import io.metersphere.system.service.ai.agent.bridge.AgentBridgeSessionRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service
public class BridgeUserAgentConnector implements UserAgentConnector {
    private final AiUserAgentService userAgentService;
    private final AgentBridgeSessionRegistry sessionRegistry;
    private final JdbcTemplate jdbcTemplate;

    public BridgeUserAgentConnector(AiUserAgentService userAgentService,
                                    AgentBridgeSessionRegistry sessionRegistry,
                                    JdbcTemplate jdbcTemplate) {
        this.userAgentService = userAgentService;
        this.sessionRegistry = sessionRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean supports(String provider, String connectionMode) {
        return StringUtils.equals(connectionMode, "LOCAL_BRIDGE")
                && StringUtils.equalsAnyIgnoreCase(provider, "WORKBUDDY", "CODEX", "CURSOR");
    }

    @Override
    public AiUserAgentConnectionDTO connectionStatus(String connectionId, String userId) {
        return userAgentService.requireAvailable(connectionId, userId);
    }

    @Override
    public Flux<AgentStreamEvent> stream(UserAgentExecutionRequest request, String userId) {
        AiUserAgentConnectionDTO connection = userAgentService.requireAvailable(request.connectionId(), userId);
        if (!StringUtils.equals(connection.getDeviceId(), request.deviceId())
                || !supports(connection.getProvider(), connection.getConnectionMode())) {
            throw new MSException("Agent 连接与设备或连接方式不匹配");
        }
        return sessionRegistry.start(request);
    }

    @Override
    public void sendToolResult(String requestId, String toolCallId, boolean success,
                               Map<String, Object> result, String errorCode) {
        sessionRegistry.toolResult(requestId, toolCallId, success, result, errorCode);
    }

    @Override
    public void cancel(String requestId, String userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM ai_case_execution
                WHERE request_id=? AND user_id=? AND resource_type='USER_AGENT'
                """, Integer.class, requestId, userId);
        if (count == null || count == 0) {
            throw new MSException("Agent 执行不存在或无权限");
        }
        sessionRegistry.cancel(requestId);
    }
}
