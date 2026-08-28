package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentWebActionDTO;
import io.metersphere.agent.dto.AgentWebAssertionDTO;
import io.metersphere.agent.dto.AgentWebLocatorDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class AgentWebExecutionContractValidatorTests {
    private final AgentWebExecutionContractValidator validator = new AgentWebExecutionContractValidator();

    @Test
    void rejectsUnknownActionAndMissingAssertionExpected() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setContractVersion("v1");
        action.setType("JAVASCRIPT");
        Assertions.assertThrows(MSException.class, () -> validator.validateAction(action));

        AgentWebAssertionDTO assertion = new AgentWebAssertionDTO();
        assertion.setContractVersion("v1");
        assertion.setType("TEXT");
        assertion.setTarget(textLocator("完成"));
        Assertions.assertThrows(MSException.class, () -> validator.validateAssertions(List.of(assertion)));
    }

    @Test
    void highRiskActionIsNeverRetryable() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setContractVersion("v1");
        action.setId("action-1");
        action.setIdempotencyKey("idem-action-1");
        action.setTimeoutMs(1000);
        action.setType("CLICK");
        action.setTarget(textLocator("删除"));
        action.setRetryable(true);
        validator.validateAction(action);
        Assertions.assertEquals("HIGH", action.getRiskLevel());
        Assertions.assertFalse(action.getRetryable());
    }

    @Test
    void validatesStrictRootContractAndScopeLimit() {
        AgentWebActionDTO action = new AgentWebActionDTO();
        action.setContractVersion("v1"); action.setId("a1"); action.setIdempotencyKey("12345678");
        action.setType("CLICK"); action.setTarget(textLocator("保存")); action.setTimeoutMs(1000);
        action.setRetryable(false); action.setRiskLevel("LOW");
        AgentWebAssertionDTO assertion = new AgentWebAssertionDTO();
        assertion.setContractVersion("v1"); assertion.setType("VISIBLE"); assertion.setTarget(textLocator("成功")); assertion.setTimeoutMs(1000);
        Map<String,Object> step = Map.of("stepId","s1","action",action,"assertions",List.of(assertion),"onFailure","STOP_CASE","evidencePolicy","ON_FAILURE");
        Map<String,Object> item = Map.of("caseId","c1","assetVersionId","v1","name","case","steps",List.of(step),"cleanupActions",List.of());
        Map<String,Object> contract = Map.of("contractVersion","v1","taskId","t1","snapshotHash","a".repeat(64),
                "scope",Map.of("caseIds",List.of("c1"),"addedCaseIds",List.of()),"environmentProfileVersion",1,
                "credentialRole","NONE","runnerRequirements",List.of("BROWSER"),"cases",List.of(item),"generatedAt",1L);
        Assertions.assertDoesNotThrow(() -> validator.validateContract(contract));
        Assertions.assertThrows(MSException.class, () -> validator.validateScopeExpansion(List.of("c1"), List.of("c2","c3")));
    }

    private AgentWebLocatorDTO textLocator(String text) {
        AgentWebLocatorDTO locator = new AgentWebLocatorDTO();
        locator.setStrategy("TEXT");
        locator.setText(text);
        return locator;
    }
}
