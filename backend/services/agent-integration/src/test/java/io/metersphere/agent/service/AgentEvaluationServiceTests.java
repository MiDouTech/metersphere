package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentEvaluationHistoryDTO;
import io.metersphere.agent.dto.AgentEvaluationRequest;
import io.metersphere.agent.dto.AgentExecutionEvaluationDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentEvaluationMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEvaluationServiceTests {

    @Test
    void manualEvaluationAppendsHistory() {
        AgentEvaluationMapper mapper = mock(AgentEvaluationMapper.class);
        AgentExecutionService executions = mock(AgentExecutionService.class);
        AgentExecLogService logs = mock(AgentExecLogService.class);
        AgentEvaluationService service = service(mapper, executions, logs);
        AgentExecutionEvaluationDTO evaluation = new AgentExecutionEvaluationDTO();
        evaluation.setTaskId("task-1");
        evaluation.setProjectId("project-1");
        when(executions.get("task-1")).thenReturn(new AgentExecutionTaskDTO());
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
        AgentEvaluationService service = service(mock(AgentEvaluationMapper.class),
                mock(AgentExecutionService.class), mock(AgentExecLogService.class));
        AgentProjectService projects = mock(AgentProjectService.class);
        when(projects.resolveProjectId("project-1")).thenReturn("project-1");
        ReflectionTestUtils.setField(service, "agentProjectService", projects);
        assertThrows(MSException.class, () -> service.page("project-1", "TYPO", null, null, 1, 20));
    }

    private AgentEvaluationService service(AgentEvaluationMapper mapper, AgentExecutionService executions,
                                            AgentExecLogService logs) {
        AgentEvaluationService service = new AgentEvaluationService();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "executionService", executions);
        ReflectionTestUtils.setField(service, "execLogService", logs);
        return service;
    }
}
