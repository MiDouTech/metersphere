package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentRunnerDTO;
import io.metersphere.agent.dto.AgentRunnerRegisterRequest;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AgentRunnerServiceTests {

    @Test
    void registersDeclaredContainerIsolationMode() {
        AgentExecutionMapper mapper = mock(AgentExecutionMapper.class);
        AgentExecLogService logs = mock(AgentExecLogService.class);
        AgentRunnerService service = service(mapper, logs);
        AgentRunnerRegisterRequest request = request("container");

        try (MockedStatic<IDGenerator> ids = Mockito.mockStatic(IDGenerator.class);
             MockedStatic<SessionUtils> session = Mockito.mockStatic(SessionUtils.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("runner-1");
            session.when(SessionUtils::getUserId).thenReturn("admin");
            session.when(SessionUtils::getCurrentOrganizationId).thenReturn("org-1");
            service.register(request);
        }

        ArgumentCaptor<AgentRunnerDTO> runner = ArgumentCaptor.forClass(AgentRunnerDTO.class);
        verify(mapper).insertRunner(runner.capture());
        assertEquals("CONTAINER", runner.getValue().getIsolationMode());
    }

    @Test
    void rejectsUnknownIsolationMode() {
        AgentExecutionMapper mapper = mock(AgentExecutionMapper.class);
        AgentRunnerService service = service(mapper, mock(AgentExecLogService.class));
        try (MockedStatic<IDGenerator> ids = Mockito.mockStatic(IDGenerator.class);
             MockedStatic<SessionUtils> session = Mockito.mockStatic(SessionUtils.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("runner-1");
            session.when(SessionUtils::getUserId).thenReturn("admin");
            session.when(SessionUtils::getCurrentOrganizationId).thenReturn("org-1");
            assertThrows(MSException.class, () -> service.register(request("host")));
        }
        verify(mapper, never()).insertRunner(Mockito.any());
    }

    private AgentRunnerService service(AgentExecutionMapper mapper, AgentExecLogService logs) {
        AgentRunnerService service = new AgentRunnerService();
        ReflectionTestUtils.setField(service, "executionMapper", mapper);
        ReflectionTestUtils.setField(service, "execLogService", logs);
        return service;
    }

    private AgentRunnerRegisterRequest request(String isolationMode) {
        AgentRunnerRegisterRequest request = new AgentRunnerRegisterRequest();
        request.setName("runner");
        request.setRunnerVersion("1.0.0");
        request.setContractVersion("v1");
        request.setIsolationMode(isolationMode);
        return request;
    }
}
