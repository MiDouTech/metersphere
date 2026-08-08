package io.metersphere.system.config;

import io.metersphere.system.service.ai.agent.bridge.AgentBridgeSessionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(prefix = "ms.ai.user-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAgentBridgeRedisBusConfig {
    @Bean
    public RedisMessageListenerContainer aiAgentBridgeRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, AgentBridgeSessionRegistry registry) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(registry, new ChannelTopic(registry.nodeChannel()));
        return container;
    }
}
