package io.metersphere.agent.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentOpenApiConfig {
    @Bean
    public GroupedOpenApi agentApi() {
        return GroupedOpenApi.builder()
                .group("agent")
                .pathsToMatch(
                        "/agent/v1/**",
                        "/api/agent/v1/**",
                        "/agent/token/**",
                        "/api/agent/token/**")
                .build();
    }
}
