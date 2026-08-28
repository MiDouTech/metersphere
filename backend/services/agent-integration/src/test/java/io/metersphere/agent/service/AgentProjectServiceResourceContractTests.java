package io.metersphere.agent.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentProjectServiceResourceContractTests {

    @Test
    void projectServiceFieldsUseExplicitAgentBeanName() throws NoSuchFieldException {
        List<Class<?>> serviceTypes = List.of(
                AgentCredentialReferenceService.class,
                AgentEnvironmentProfileService.class,
                AgentExecutionCheckpointService.class,
                AgentExecutionPreflightService.class,
                AgentModelProfileService.class
        );

        for (Class<?> serviceType : serviceTypes) {
            Field field = serviceType.getDeclaredField("projectService");
            Resource resource = field.getAnnotation(Resource.class);
            assertEquals("agentProjectService", resource.name(), serviceType.getSimpleName());
        }
    }
}
