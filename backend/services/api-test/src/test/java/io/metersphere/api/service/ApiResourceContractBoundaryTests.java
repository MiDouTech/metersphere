package io.metersphere.api.service;

import io.metersphere.api.controller.definition.ApiDefinitionController;
import io.metersphere.api.dto.definition.ApiDefinitionCopyRequest;
import io.metersphere.api.mapper.ApiDefinitionMapper;
import io.metersphere.api.service.scenario.ApiScenarioService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.system.security.CheckOwner;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiResourceContractBoundaryTests {
    @Test
    void copyOwnershipExpressionReadsActualSingularRequestIdentity() throws Exception {
        var method = ApiDefinitionController.class.getMethod("copy", ApiDefinitionCopyRequest.class);
        ApiDefinitionCopyRequest request = new ApiDefinitionCopyRequest();
        request.setId("definition-a");
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("request", request);
        assertEquals("definition-a", new SpelExpressionParser().parseExpression(
                method.getAnnotation(CheckOwner.class).resourceId()).getValue(context, String.class));
    }

    @Test
    void unknownResourceTypeIsRejectedBeforeAnyResourceLookup() {
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("invalid_parameter")).thenReturn("Invalid parameter");
            assertEquals("Invalid parameter", assertThrows(MSException.class,
                    () -> new ApiScenarioService().getStepResourceInfo("resource", "unsupported-value")).getMessage());
        }
    }

    @Test
    void omittedOrBlankResourceTypeNeverReachesNullSwitch() {
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get("invalid_parameter")).thenReturn("Invalid parameter");
            for (String value : java.util.Arrays.asList(null, "", " ")) {
                assertThrows(MSException.class, () -> new ApiScenarioService().getStepResourceInfo("resource", value));
            }
        }
    }

    @Test
    void validTypeWithDeletedResourcePreservesExistingNullResultContract() {
        ApiScenarioService service = new ApiScenarioService();
        ApiDefinitionMapper mapper = mock(ApiDefinitionMapper.class);
        ReflectionTestUtils.setField(service, "apiDefinitionMapper", mapper);
        assertNull(service.getStepResourceInfo("deleted-resource", "API"));
        verify(mapper).selectByPrimaryKey("deleted-resource");
    }
}
