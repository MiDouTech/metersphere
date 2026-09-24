package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AgentFunctionalCaseSubmitServiceTests {
    @ParameterizedTest
    @ValueSource(strings={"SUCCESS","ERROR","BLOCKED"})
    void legacyResultsCannotWriteEitherPlanOrCase(String result) {
        var service=new AgentFunctionalCaseSubmitService();
        for(boolean plan:new boolean[]{true,false}) {
            var request=new AgentCaseSubmitRequest();
            request.setProjectId("project");request.setCaseId("case");request.setLastExecResult(result);
            if(plan) { request.setTestPlanId("plan");request.setTestPlanCaseId("plan-case"); }
            assertEquals("QUALITY_LEGACY_SUBMIT_FORBIDDEN",assertThrows(MSException.class,()->service.submit(request)).getMessage());
        }
    }
}
