package io.metersphere.agent.quality;

import io.metersphere.agent.controller.AgentExecutionExceptionHandler;
import io.metersphere.agent.controller.GlobalQualityPolicyController;
import io.metersphere.agent.service.AgentSafeErrorMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalQualityPolicyControllerTests {
    private final GlobalQualityPolicyService service = mock(GlobalQualityPolicyService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new GlobalQualityPolicyController(service))
            .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
            .setControllerAdvice(new AgentExecutionExceptionHandler(new AgentSafeErrorMapper())).build();

    @Test void malformedHttpBodyIsValidationFailureNotInternalError() throws Exception {
        mvc.perform(post("/system/quality/policies").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
    @Test void invalidOuterFieldsNeverReachService() throws Exception {
        mvc.perform(post("/system/quality/policies").contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":\"\",\"rulesJson\":\"{}\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
        verifyNoInteractions(service);
    }
    @Test void conflictingPublishReturnsSafe409() throws Exception {
        when(service.publish(eq("p1"), any())).thenThrow(new MSException("QUALITY_POLICY_VERSION_CONFLICT"));
        mvc.perform(post("/system/quality/policies/p1/publish").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"changeReason\":\"change\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUALITY_POLICY_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
    @Test void invalidPolicyPreservesSafeFieldPointers() throws Exception {
        when(service.create(any())).thenThrow(new QualityPolicyValidationException(List.of(new QualityPolicyValidator.Issue("/rules", "规则不合法"))));
        mvc.perform(post("/api/system/quality/policies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rulesJson\":\"{}\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.errors[0].path").value("/rules"));
    }
    @Test void paginationAndDetailRoutesHaveNoProjectScope() throws Exception {
        mvc.perform(get("/system/quality/policies").param("page", "6").param("pageSize", "20"))
                .andExpect(status().isOk());
        verify(service).list(6, 20);
        mvc.perform(get("/api/system/quality/policies/old-version"))
                .andExpect(status().isOk());
        verify(service).detail("old-version");
    }
}
