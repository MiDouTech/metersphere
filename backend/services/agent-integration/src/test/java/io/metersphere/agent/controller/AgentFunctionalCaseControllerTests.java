package io.metersphere.agent.controller;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentAttachmentService;
import io.metersphere.agent.service.AgentBatchSubmitService;
import io.metersphere.agent.service.AgentExecLogService;
import io.metersphere.agent.service.AgentFunctionalCaseSearchService;
import io.metersphere.agent.service.AgentFunctionalCaseSubmitService;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFunctionalCaseControllerTests {

    @InjectMocks
    private AgentFunctionalCaseController controller;

    @Mock
    private AgentFunctionalCaseSearchService agentFunctionalCaseSearchService;
    @Mock
    private AgentFunctionalCaseSubmitService agentFunctionalCaseSubmitService;
    @Mock
    private AgentBatchSubmitService agentBatchSubmitService;
    @Mock
    private AgentExecLogService agentExecLogService;
    @Mock
    private AgentAttachmentService agentAttachmentService;

    @AfterEach
    void tearDown() {
        AgentTokenContext.clear();
    }

    @Test
    void healthShouldReturnOk() {
        Assertions.assertEquals("ok", controller.health());
    }

    @Test
    void searchShouldRequireReadScopeWhenTokenPresent() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_READ);
        AgentTokenContext.set(token);

        when(agentFunctionalCaseSearchService.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AgentCaseSearchResponse());

        Assertions.assertDoesNotThrow(() -> controller.search(new io.metersphere.agent.dto.AgentCaseSearchRequest()));
    }

    @Test
    void submitDelegatesRegardlessOfHistoricalReadScope() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_READ);
        AgentTokenContext.set(token);

        AgentCaseSubmitRequest request = new AgentCaseSubmitRequest();
        request.setProjectId("proj-001");
        request.setCaseId("fc-001");
        request.setLastExecResult("SUCCESS");

        controller.submit(request);
        verify(agentFunctionalCaseSubmitService).submit(request);
    }

    @Test
    void getShouldDelegateToSearchService() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_READ);
        AgentTokenContext.set(token);

        AgentCaseDTO dto = new AgentCaseDTO();
        dto.setCaseId("fc-001");
        when(agentFunctionalCaseSearchService.getById("fc-001", true, "plan-001")).thenReturn(dto);

        AgentCaseDTO result = controller.get("fc-001", true, "plan-001");
        Assertions.assertEquals("fc-001", result.getCaseId());
    }

    @Test
    void modulesShouldDelegateToSearchService() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_ALL);
        AgentTokenContext.set(token);

        AgentModuleDTO module = new AgentModuleDTO();
        module.setId("mod-order");
        module.setPath("订单");
        when(agentFunctionalCaseSearchService.listModules("proj-001")).thenReturn(List.of(module));

        List<AgentModuleDTO> modules = controller.modules("proj-001");
        Assertions.assertEquals(1, modules.size());
        verify(agentFunctionalCaseSearchService).listModules(eq("proj-001"));
    }

    @Test
    void searchDelegatesRegardlessOfHistoricalSubmitScope() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_SUBMIT);
        AgentTokenContext.set(token);

        Assertions.assertDoesNotThrow(() -> controller.search(new io.metersphere.agent.dto.AgentCaseSearchRequest()));
    }

    @Test
    void submitShouldDelegateWhenScopeAllows() {
        AgentToken token = new AgentToken();
        token.setScopes(AgentTokenScope.FUNCTIONAL_ALL);
        AgentTokenContext.set(token);

        AgentCaseSubmitRequest request = new AgentCaseSubmitRequest();
        request.setProjectId("proj-001");
        request.setCaseId("fc-001");
        request.setTestPlanId("plan-001");
        request.setTestPlanCaseId("relate-001");
        request.setLastExecResult("SUCCESS");

        controller.submit(request);
        verify(agentFunctionalCaseSubmitService).submit(request);
    }
}
