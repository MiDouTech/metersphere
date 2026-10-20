package io.metersphere.agent.config;

import io.metersphere.agent.security.AgentTokenFilter;
import io.metersphere.sdk.util.ShiroFilterChainExtender;
import jakarta.annotation.Resource;
import jakarta.servlet.Filter;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@Component
public class AgentShiroFilterChainExtender implements ShiroFilterChainExtender {
    @Resource
    private AgentTokenFilter agentTokenFilter;

    @Bean
    public FilterRegistrationBean<AgentTokenFilter> agentTokenServletRegistration(AgentTokenFilter filter) {
        // Shiro owns this filter's route selection and lifecycle. Spring Boot would
        // otherwise also register the Filter bean on /*, counting one search twice
        // and rejecting it against the 300 ms minimum interval.
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    public void extend(Map<String, Filter> filters, Map<String, String> chain) {
        filters.put("agentToken", agentTokenFilter);
        chain.put("/ai/execution/triggers/*/webhook", "anon");
        chain.put("/api/ai/execution/triggers/*/webhook", "anon");
        // Gateway aliases share the same credential boundary.
        chain.put("/agent/v1/functional/health", "anon");
        chain.put("/api/agent/v1/functional/health", "anon");
        chain.put("/agent/v1/**", "agentToken, authc");
        chain.put("/api/agent/v1/**", "agentToken, authc");
        chain.put("/agent/mcp/**", "agentToken, authc");
        chain.put("/api/agent/mcp/**", "agentToken, authc");
        chain.put("/personal/agent-tokens/**", "agentToken, authc");
        chain.put("/api/personal/agent-tokens/**", "agentToken, authc");
        chain.put("/personal/agent-projects/**", "agentToken, authc");
        chain.put("/api/personal/agent-projects/**", "agentToken, authc");
        chain.put("/personal/agent-package/**", "agentToken, authc");
        chain.put("/api/personal/agent-package/**", "agentToken, authc");
        chain.put("/admin/agent-tokens/**", "agentToken, authc");
        chain.put("/api/admin/agent-tokens/**", "agentToken, authc");
        chain.put("/admin/agent-policy/**", "agentToken, authc");
        chain.put("/api/admin/agent-policy/**", "agentToken, authc");
        chain.put("/admin/agent-audit/**", "agentToken, authc");
        chain.put("/api/admin/agent-audit/**", "agentToken, authc");
        // Runner endpoints authenticate an msrt_/msrl_ token inside AgentRunnerService.
        chain.put("/internal/ai-runner/v1/**", "anon");
        chain.put("/api/internal/ai-runner/v1/**", "anon");
        chain.put("/mcp", "agentToken, authc");
        chain.put("/api/mcp", "agentToken, authc");
    }
}
