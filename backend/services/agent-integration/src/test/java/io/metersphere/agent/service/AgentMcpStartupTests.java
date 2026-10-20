package io.metersphere.agent.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentMcpStartupTests {
    @Test
    void bootMustNotRegisterTheShiroAgentFilterOnAllServletRequests() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            var filter = new io.metersphere.agent.security.AgentTokenFilter(null, null, null);
            context.registerBean("agentTokenFilter", io.metersphere.agent.security.AgentTokenFilter.class, () -> filter);
            context.register(io.metersphere.agent.config.AgentShiroFilterChainExtender.class);
            context.refresh();
            var servlet = mock(jakarta.servlet.ServletContext.class);
            for (var initializer : new org.springframework.boot.web.servlet.ServletContextInitializerBeans(context.getBeanFactory())) {
                initializer.onStartup(servlet);
            }
            org.mockito.Mockito.verify(servlet, org.mockito.Mockito.never()).addFilter(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(jakarta.servlet.Filter.class));
            var filters = new java.util.HashMap<String, jakarta.servlet.Filter>();
            var chains = new java.util.HashMap<String, String>();
            context.getBean(io.metersphere.agent.config.AgentShiroFilterChainExtender.class).extend(filters, chains);
            assertSame(filter, filters.get("agentToken"));
            org.junit.jupiter.api.Assertions.assertEquals("agentToken, authc", chains.get("/api/agent/v1/**"));
        }
    }

    @Test
    void mapperResourcesMustResolveSharedFragmentsBeforeFirstQuery() throws Exception {
        var factory = new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(mock(javax.sql.DataSource.class));
        factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath*:io/metersphere/**/mapper/*.xml"));
        var configuration = factory.getObject().getConfiguration();
        org.junit.jupiter.api.Assertions.assertTrue(configuration.getSqlFragments()
                .containsKey("io.metersphere.system.mapper.BaseMapper.filterInWrapper"));
        // Force resolution of deferred includes, as the first startup query does.
        org.junit.jupiter.api.Assertions.assertFalse(configuration.getMappedStatementNames().isEmpty());
    }

    @Test
    void maintenanceMustResolveAgentProjectServiceWhenProjectServiceAlsoExists() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var agentProjectService = mock(AgentProjectService.class);
            context.registerBean("projectService", Object.class, Object::new);
            context.registerBean("bugService", Object.class, Object::new);
            context.getBeanFactory().registerSingleton("agentProjectService", agentProjectService);
            for (var field : AgentMcpMaintenanceService.class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Resource.class) && !field.getName().equals("projectService")) {
                    var resourceName = field.getAnnotation(Resource.class).name();
                    context.getBeanFactory().registerSingleton(
                            resourceName.isEmpty() ? field.getName() : resourceName, mock(field.getType()));
                }
            }
            context.registerBean(AgentMcpMaintenanceService.class);
            context.refresh();
            assertSame(agentProjectService, ReflectionTestUtils.getField(
                    context.getBean(AgentMcpMaintenanceService.class), "projectService"));
            assertSame(context.getBean("agentBugWriteService"), ReflectionTestUtils.getField(
                    context.getBean(AgentMcpMaintenanceService.class), "bugService"));
        }
    }
}
