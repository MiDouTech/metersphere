package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentEnvironmentProfileDTO;
import io.metersphere.agent.dto.AgentLoginProfileDTO;
import io.metersphere.agent.dto.AgentLoginProfileRequest;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoginProfileServiceTests {

    @Test
    void configurationCanBeCreatedBeforeItsEnvironmentIsEnabled() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AgentProjectService projects = mock(AgentProjectService.class);
        AgentEnvironmentProfileService environments = mock(AgentEnvironmentProfileService.class);
        AgentLoginProfileService service = spy(new AgentLoginProfileService());
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "projects", projects);
        ReflectionTestUtils.setField(service, "environments", environments);
        ReflectionTestUtils.setField(service, "validator", mock(AgentWebExecutionContractValidator.class));
        when(projects.resolveProjectId("project-1")).thenReturn("project-1");
        when(jdbc.queryForObject(anyString(), any(Class.class), any())).thenReturn("org-1");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        AgentEnvironmentProfileDTO environment = new AgentEnvironmentProfileDTO();
        environment.setId("environment-1");
        environment.setProjectId("project-1");
        environment.setEnabled(false);
        when(environments.get("project-1", "environment-1")).thenReturn(environment);

        AgentLoginProfileDTO result = new AgentLoginProfileDTO();
        result.setId("login-1");
        doReturn(result).when(service).get("login-1");

        AgentLoginProfileRequest request = new AgentLoginProfileRequest();
        request.setProjectId("project-1");
        request.setEnvironmentProfileId("environment-1");
        request.setName("login");
        request.setLoginType("FORM");
        request.setLoginUrl("https://test.example.com/login");
        request.setUsernameLocator("{\"strategy\":\"LABEL\",\"label\":\"username\"}");
        request.setPasswordLocator("{\"strategy\":\"LABEL\",\"label\":\"password\"}");
        request.setSubmitLocator("{\"strategy\":\"ROLE\",\"role\":\"button\",\"name\":\"login\"}");
        request.setSuccessAssertion("{\"contractVersion\":\"v1\",\"type\":\"URL\",\"operator\":\"NOT_EQUALS\",\"expected\":\"/login\"}");
        request.setMfaPolicy("CHECKPOINT");
        request.setTimeoutMs(15_000);
        request.setEnabled(true);

        try (MockedStatic<IDGenerator> ids = mockStatic(IDGenerator.class);
             MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("login-1");
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            assertEquals("login-1", service.create(request).getId());
        }

        verify(environments).get("project-1", "environment-1");
        verify(environments).assertTargetAllowed(environment, request.getLoginUrl());
        verify(environments, never()).resolveForTask(anyString(), anyString());
    }
}
