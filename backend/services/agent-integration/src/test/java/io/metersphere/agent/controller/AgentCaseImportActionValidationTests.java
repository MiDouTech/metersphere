package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentCaseImportService;
import io.metersphere.agent.service.ImportFollowupCoordinator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentCaseImportActionValidationTests {
    @Test void stopAndRetryAcceptEmptyJsonWithoutCommitPolicyValidation() throws Exception {
        var controller=new AgentCaseImportController();var service=mock(AgentCaseImportService.class);var followups=mock(ImportFollowupCoordinator.class);
        ReflectionTestUtils.setField(controller,"service",service);ReflectionTestUtils.setField(controller,"followups",followups);
        when(service.action(anyString(),anyString(),anyString(),anyString())).thenReturn(Map.of("state","STOPPED"));
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        try(var session=mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user");
            mvc.perform(post("/ai/case-imports/job/stop").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
            mvc.perform(post("/ai/case-imports/job/retry").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
            verify(service).action("job","stop","user","WEB");verify(service).action("job","retry","user","WEB");
            verify(followups).retry("job","user");verifyNoMoreInteractions(followups);
        }
    }
    @Test void commitStillRejectsAnEmptyPolicyBeforeAnyBusinessWrite() throws Exception {
        var controller=new AgentCaseImportController();var service=mock(AgentCaseImportService.class);var followups=mock(ImportFollowupCoordinator.class);
        ReflectionTestUtils.setField(controller,"service",service);ReflectionTestUtils.setField(controller,"followups",followups);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/ai/case-imports/job/commit").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service,followups);
    }
    @Test void importFailuresRetainTheirSafeReasonAndTrace() {
        var mapper=new io.metersphere.agent.service.AgentSafeErrorMapper();
        for(String code:java.util.List.of("IMPORT_CASE_SHEET_NOT_FOUND","IMPORT_FILE_PARSE_FAILED","IMPORT_FILE_READ_FAILED")) {
            var error=mapper.toApiError(new io.metersphere.sdk.exception.MSException(code),"import-trace");
            org.junit.jupiter.api.Assertions.assertEquals(code,error.getCode());
            org.junit.jupiter.api.Assertions.assertEquals("import-trace",error.getTraceId());
            org.junit.jupiter.api.Assertions.assertFalse(error.getMessage().contains("请求未能完成"));
            org.junit.jupiter.api.Assertions.assertFalse(error.getMessage().contains("Exception"));
        }
    }
}
