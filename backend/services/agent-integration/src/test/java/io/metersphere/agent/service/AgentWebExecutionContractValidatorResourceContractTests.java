package io.metersphere.agent.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentWebExecutionContractValidatorResourceContractTests {

    @Test
    void validatorFieldsUseExplicitAgentBeanName() throws NoSuchFieldException {
        List<Class<?>> serviceTypes = List.of(
                AgentExecutionPlanningService.class,
                AgentLoginProfileService.class,
                AgentPageObjectService.class
        );

        for (Class<?> serviceType : serviceTypes) {
            Field field = serviceType.getDeclaredField("validator");
            Resource resource = field.getAnnotation(Resource.class);
            assertEquals("agentWebExecutionContractValidator", resource.name(), serviceType.getSimpleName());
        }
    }
}
