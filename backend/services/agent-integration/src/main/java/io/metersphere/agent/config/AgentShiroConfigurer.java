package io.metersphere.agent.config;

import io.metersphere.agent.security.AgentTokenFilter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class AgentShiroConfigurer {
    @Autowired
    @Qualifier("&shiroFilterFactoryBean")
    private ShiroFilterFactoryBean shiroFilterFactoryBean;
    @Resource
    private AgentTokenFilter agentTokenFilter;

    @PostConstruct
    public void configureAgentFilterChain() {
        shiroFilterFactoryBean.getFilters().put("agentToken", agentTokenFilter);
        Map<String, String> chain = new LinkedHashMap<>(shiroFilterFactoryBean.getFilterChainDefinitionMap());
        String defaultChain = chain.remove("/**");
        chain.put("/api/agent/v1/functional/health", "anon");
        chain.put("/api/agent/v1/**", "agentToken, authc");
        chain.put("/api/agent/token/**", "authc");
        if (defaultChain != null) {
            chain.put("/**", defaultChain);
        }
        shiroFilterFactoryBean.setFilterChainDefinitionMap(chain);
    }
}
