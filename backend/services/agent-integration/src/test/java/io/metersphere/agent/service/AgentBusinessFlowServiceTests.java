package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBusinessFlowDTO;
import io.metersphere.agent.dto.AgentBusinessFlowRequest;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentBusinessFlowServiceTests {

    @Test
    void createUsesOneJdbcArgumentForEveryInsertPlaceholder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AgentProjectService projects = mock(AgentProjectService.class);
        AgentBusinessFlowService service = spy(new AgentBusinessFlowService());
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "projects", projects);
        ReflectionTestUtils.setField(service, "versions", mock(TestAssetVersionService.class));
        when(projects.resolveProjectId("project-1")).thenReturn("project-1");
        when(jdbc.queryForObject(anyString(), any(Class.class), any())).thenReturn("org-1");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        AgentBusinessFlowDTO result = new AgentBusinessFlowDTO();
        result.setId("flow-1");
        result.setStatus("DRAFT");
        doReturn(result).when(service).get("flow-1");

        AgentBusinessFlowRequest request = new AgentBusinessFlowRequest();
        request.setProjectId("project-1");
        request.setName("flow");
        request.setNodes(List.of(Map.of("id", "start", "name", "start")));
        request.setEdges(List.of());
        request.setEntryNodeId("start");
        request.setExitConditions(List.of(Map.of("type", "SUCCESS")));
        request.setAllowedActions(List.of("NAVIGATE"));
        request.setStatus("DRAFT");

        try (MockedStatic<IDGenerator> ids = mockStatic(IDGenerator.class);
             MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("flow-1");
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            service.create(request);
        }

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), parameters.capture());
        assertEquals(14, sql.getValue().chars().filter(value -> value == '?').count());
        assertEquals(14, parameters.getValue().length);
    }
}
