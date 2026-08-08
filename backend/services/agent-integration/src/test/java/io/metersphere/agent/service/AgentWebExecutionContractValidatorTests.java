package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentWebActionDTO;
import io.metersphere.agent.dto.AgentWebAssertionDTO;
import io.metersphere.agent.dto.AgentWebLocatorDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AgentWebExecutionContractValidatorTests {
    private final AgentWebExecutionContractValidator validator = new AgentWebExecutionContractValidator();

    @Test
    void rejectsUnknownActionAndMissingAssertionExpected() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setType("JAVASCRIPT");
        Assertions.assertThrows(MSException.class, () -> validator.validateAction(action));

        AgentWebAssertionDTO assertion = new AgentWebAssertionDTO();
        assertion.setType("TEXT");
        assertion.setTarget(textLocator("完成"));
        Assertions.assertThrows(MSException.class, () -> validator.validateAssertions(List.of(assertion)));
    }

    @Test
    void highRiskActionIsNeverRetryable() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setType("CLICK");
        action.setTarget(textLocator("删除"));
        action.setRetryable(true);
        validator.validateAction(action);
        Assertions.assertEquals("HIGH", action.getRiskLevel());
        Assertions.assertFalse(action.getRetryable());
    }

    private AgentWebLocatorDTO textLocator(String text) {
        AgentWebLocatorDTO locator = new AgentWebLocatorDTO();
        locator.setStrategy("TEXT");
        locator.setText(text);
        return locator;
    }
}
