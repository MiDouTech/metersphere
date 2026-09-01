package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentEvaluationHistoryDTO;
import io.metersphere.agent.dto.AgentEvaluationRequest;
import io.metersphere.agent.dto.AgentExecutionEvaluationDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentEvaluationMapper;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEvaluationServiceTests {

    @Test
    void manualEvaluationAppendsHistory() {
        AgentEvaluationMapper mapper = mock(AgentEvaluationMapper.class);
        AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
        AgentProjectService projects = mock(AgentProjectService.class);
        AgentExecLogService logs = mock(AgentExecLogService.class);
        AgentEvaluationService service = service(mapper, executions, projects, logs);
        AgentExecutionEvaluationDTO evaluation = new AgentExecutionEvaluationDTO();
        evaluation.setTaskId("task-1");
        evaluation.setProjectId("project-1");
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setProjectId("project-1");
        when(executions.selectTaskById("task-1")).thenReturn(task);
        when(projects.resolveProjectId("project-1")).thenReturn("project-1");
        when(mapper.selectByTaskId("task-1")).thenReturn(evaluation);
        when(mapper.updateManual(Mockito.eq("task-1"), Mockito.any(), Mockito.any(), Mockito.eq("user-1"), anyLong()))
                .thenReturn(1);
        AgentEvaluationRequest request = new AgentEvaluationRequest();
        request.setScore(BigDecimal.valueOf(90));
        request.setComment("good");

        try (MockedStatic<IDGenerator> ids = Mockito.mockStatic(IDGenerator.class);
             MockedStatic<SessionUtils> session = Mockito.mockStatic(SessionUtils.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("history-1");
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            service.manualEvaluate("task-1", request);
        }

        ArgumentCaptor<AgentEvaluationHistoryDTO> history = ArgumentCaptor.forClass(AgentEvaluationHistoryDTO.class);
        verify(mapper).insertHistory(history.capture());
        assertEquals("project-1", history.getValue().getProjectId());
        assertEquals(BigDecimal.valueOf(90), history.getValue().getScore());
    }

    @Test
    void pageRejectsUnknownFilterInsteadOfSilentlyIgnoringIt() {
        AgentProjectService projects = mock(AgentProjectService.class);
        AgentEvaluationService service = service(mock(AgentEvaluationMapper.class),
                mock(AgentExecutionMapper.class), projects, mock(AgentExecLogService.class));
        when(projects.resolveProjectId("project-1")).thenReturn("project-1");
        assertThrows(MSException.class, () -> service.page("project-1", "TYPO", null, null, 1, 20));
    }

    @Test
    void rejectsTaskOutsideResolvedProjectContext() {
        AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
        AgentProjectService projects = mock(AgentProjectService.class);
        AgentExecutionTaskDTO task = new AgentExecutionTaskDTO();
        task.setProjectId("project-1");
        when(executions.selectTaskById("task-1")).thenReturn(task);
        when(projects.resolveProjectId("project-1")).thenReturn("project-2");
        AgentEvaluationService service = service(mock(AgentEvaluationMapper.class), executions, projects,
                mock(AgentExecLogService.class));

        assertThrows(MSException.class, () -> service.history("task-1", 10));
    }

    @Test
    void doesNotDependOnExecutionService() {
        assertFalse(Arrays.stream(AgentEvaluationService.class.getDeclaredFields())
                .anyMatch(field -> AgentExecutionService.class.equals(field.getType())));
    }

    private AgentEvaluationService service(AgentEvaluationMapper mapper, AgentExecutionMapper executions,
                                            AgentProjectService projects, AgentExecLogService logs) {
        AgentEvaluationService service = new AgentEvaluationService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "executionMapper", executions);
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        ReflectionTestUtils.setField(service, "execLogService", logs);
        return service;
    }
}
