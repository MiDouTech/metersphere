package io.metersphere.system.config;

import io.metersphere.system.service.ai.agent.AiUserAgentService;
import io.metersphere.system.service.ai.agent.bridge.AgentBridgeHandshakeInterceptor;
import io.metersphere.system.service.ai.agent.bridge.AgentBridgeSessionRegistry;
import io.metersphere.system.service.ai.agent.bridge.AgentBridgeWebSocketHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "ms.ai.user-agent", name = "enabled", havingValue = "true")
public class AiAgentBridgeWebSocketConfig implements WebSocketConfigurer {
    private final AgentBridgeSessionRegistry sessionRegistry;
    private final AiUserAgentService userAgentService;

    public AiAgentBridgeWebSocketConfig(AgentBridgeSessionRegistry sessionRegistry,
                                        AiUserAgentService userAgentService) {
        this.sessionRegistry = sessionRegistry;
        this.userAgentService = userAgentService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AgentBridgeWebSocketHandler(sessionRegistry, userAgentService),
                        "/ai/agent-bridge/ws")
                .addInterceptors(new AgentBridgeHandshakeInterceptor(userAgentService));
    }
}
