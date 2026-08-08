package io.metersphere.functional.service;

import io.metersphere.functional.dto.AiCaseAvailableModelDTO;
import io.metersphere.functional.dto.AiResourceSelection;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.agent.AiUserAgentFeatureService;
import io.metersphere.system.service.ai.agent.AiUserAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCaseAvailableResourceServiceTests {
    @Mock private AiCaseAvailableModelService modelService;
    @Mock private AiUserAgentService userAgentService;
    @Mock private AiUserAgentFeatureService featureService;
    @Mock private AiGovernanceService governanceService;
    private AiCaseAvailableResourceService service;

    @BeforeEach
    void setup() {
        service = new AiCaseAvailableResourceService(modelService, userAgentService, featureService, governanceService);
    }

    @Test
    void legacyModelSourceIdRemainsModelApiSelection() {
        AiCaseAvailableModelDTO model = new AiCaseAvailableModelDTO();
        model.setId("model-1"); model.setProvider("OpenAI"); model.setSupportsTools(true);
        when(modelService.requireAllowed("project", "model-1", "user")).thenReturn(model);
        AiResourceSelection selection = service.requireAllowed("project", null, null, "model-1", "user");
        assertEquals("MODEL_API", selection.resourceType());
        assertEquals("model-1", selection.modelSourceId());
    }

    @Test
    void rejectsContradictoryLegacyAndUnifiedIds() {
        assertThrows(MSException.class, () -> service.requireAllowed(
                "project", "MODEL_API", "model-2", "model-1", "user"));
    }

    @Test
    void userAgentNeverAcceptsLegacyModelId() {
        assertThrows(MSException.class, () -> service.requireAllowed(
                "project", "USER_AGENT", "connection-1", "model-1", "user"));
    }

    @Test
    void userAgentSelectionIsOwnerCheckedBeforeGovernance() {
        AiUserAgentConnectionDTO connection = new AiUserAgentConnectionDTO();
        connection.setId("connection-1"); connection.setProvider("CODEX"); connection.setCapabilities("{}");
        when(userAgentService.requireAvailable("connection-1", "user")).thenReturn(connection);
        AiResourceSelection selection = service.requireAllowed(
                "project", "USER_AGENT", "connection-1", null, "user");
        assertEquals("connection-1", selection.agentConnectionId());
        assertEquals("CODEX", selection.provider());
    }
}
