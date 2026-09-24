package io.metersphere.agent.quality;

import io.metersphere.agent.controller.AgentExecutionExceptionHandler;
import io.metersphere.agent.controller.QualityPolicyController;
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

class QualityPolicyControllerTests {
    private final QualityPolicyService service = mock(QualityPolicyService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new QualityPolicyController(service))
            .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
            .setControllerAdvice(new AgentExecutionExceptionHandler(new AgentSafeErrorMapper())).build();

    @Test void malformedHttpBodyIsValidationFailureNotInternalError() throws Exception {
        mvc.perform(post("/quality/policies").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
    @Test void invalidOuterFieldsNeverReachService() throws Exception {
        mvc.perform(post("/quality/policies").contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":\"\",\"rulesJson\":\"{}\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
        verifyNoInteractions(service);
    }
    @Test void legacyWritesNeverBecomeGlobalWrites() throws Exception {
        mvc.perform(post("/api/quality/policies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"project\",\"rulesJson\":\"{}\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("QUALITY_POLICY_LEGACY_WRITE_FORBIDDEN"));
        verifyNoInteractions(service);
    }
    @Test void paginationAndDetailRoutesForwardProjectScope() throws Exception {
        mvc.perform(get("/quality/policies").param("projectId", "p").param("page", "6").param("pageSize", "20"))
                .andExpect(status().isOk());
        verify(service).list("p", 6, 20);
        mvc.perform(get("/api/quality/policies/old-version").param("projectId", "p"))
                .andExpect(status().isOk());
        verify(service).detail("old-version", "p");
    }
}
