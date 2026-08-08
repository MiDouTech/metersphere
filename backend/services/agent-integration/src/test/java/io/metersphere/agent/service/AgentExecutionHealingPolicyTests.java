package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentHealingDecisionDTO;
import io.metersphere.agent.dto.AgentWebActionDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentExecutionHealingPolicyTests {
    private final AgentExecutionHealingPolicy policy = new AgentExecutionHealingPolicy();

    @Test
    void shouldAllowUniqueLowRiskLocatorHealing() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setType("CLICK");
        AgentHealingDecisionDTO decision = policy.decide(
                "LOCATOR_NOT_FOUND", action, 0, 0, 0.9D, 1);
        Assertions.assertTrue(decision.isAllowed());
    }

    @Test
    void shouldRejectHighRiskOrAmbiguousHealing() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setType("CLICK");
        action.setRiskLevel("HIGH");
        Assertions.assertFalse(policy.decide("LOCATOR_NOT_FOUND", action, 0, 0, 0.9D, 1).isAllowed());

        action.setRiskLevel("LOW");
        Assertions.assertFalse(policy.decide("LOCATOR_NOT_FOUND", action, 0, 0, 0.9D, 2).isAllowed());
    }
}
