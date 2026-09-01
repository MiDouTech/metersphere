package io.metersphere.agent.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentExecutionPreflightResourceContractTests {

    @Test
    void aliasedDependenciesUseExplicitAgentBeanNames() throws NoSuchFieldException {
        Map<String, String> expectedBeanNames = Map.of(
                "testPlanService", "agentTestPlanQueryService",
                "environmentService", "agentEnvironmentProfileService"
        );

        for (Map.Entry<String, String> entry : expectedBeanNames.entrySet()) {
            Field field = AgentExecutionPreflightService.class.getDeclaredField(entry.getKey());
            Resource resource = field.getAnnotation(Resource.class);
            assertEquals(entry.getValue(), resource.name(), entry.getKey());
        }
    }
}
