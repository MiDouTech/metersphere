package io.metersphere.agent.service;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.functional.service.FunctionalExecutionViewService;
import io.metersphere.sdk.util.FunctionalCaseBlobCodec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AgentBatchBodyFailureTests {
    private AgentCaseDTO item(String id) {var item=new AgentCaseDTO();item.setCaseId(id);item.setProjectId("p");return item;}
    private AgentFunctionalCaseExportService service(FunctionalCaseService cases, AgentCaseSchemaMapper mapper) {
        var result=new AgentFunctionalCaseExportService();
        ReflectionTestUtils.setField(result,"functionalCaseService",cases);
        ReflectionTestUtils.setField(result,"agentCaseSchemaMapper",mapper);
        ReflectionTestUtils.setField(result,"executionViews",mock(FunctionalExecutionViewService.class));
        return result;
    }
    @Test void isolatesBadContentWithoutPretendingThatItHasZeroSteps() {
        var cases=mock(FunctionalCaseService.class);var mapper=mock(AgentCaseSchemaMapper.class);
        when(cases.getAgentCaseDetailsForSearch(anyList(),eq("p"))).thenAnswer(call->{
            List<String> ids=call.getArgument(0);
            if(ids.contains("bad"))throw new RuntimeException(new FunctionalCaseBlobCodec.BodyFormatException());
            var detail=new FunctionalCaseDetailDTO();detail.setId("good");detail.setProjectId("p");detail.setSteps("[]");
            return Map.of("good",detail);
        });
        var good=item("good");var bad=item("bad");var warnings=new ArrayList<String>();
        String previous=org.slf4j.MDC.get("traceId");
        try {
            org.slf4j.MDC.put("traceId","trace-fixture");
            service(cases,mapper).enrichWithSteps(List.of(good,bad),warnings);
            assertEquals("trace-fixture",bad.getContentErrorTraceId());
        } finally {
            if(previous==null)org.slf4j.MDC.remove("traceId");else org.slf4j.MDC.put("traceId",previous);
        }
        verify(mapper).enrichDetail(eq(good),any(),eq(warnings));
        assertNull(good.getContentError());assertEquals("CASE_BODY_INVALID",bad.getContentError());
        assertNull(bad.getSteps());assertEquals(List.of("CASE_BODY_INVALID:bad"),warnings);
    }
    @Test void neverConvertsPermissionOrDatabaseFailuresToPartialSuccess() {
        var cases=mock(FunctionalCaseService.class);
        when(cases.getAgentCaseDetailsForSearch(anyList(),anyString())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class,()->service(cases,mock(AgentCaseSchemaMapper.class)).enrichWithSteps(List.of(item("one")),new ArrayList<>()));
    }
    @Test void capsIsolationWorkForAnEntirePageOfBadRecords() {
        var cases=mock(FunctionalCaseService.class);
        when(cases.getAgentCaseDetailsForSearch(anyList(),anyString())).thenThrow(new FunctionalCaseBlobCodec.BodyFormatException());
        var items=new ArrayList<AgentCaseDTO>();for(int i=0;i<200;i++)items.add(item("case"+i));
        var warnings=new ArrayList<String>();service(cases,mock(AgentCaseSchemaMapper.class)).enrichWithSteps(items,warnings);
        verify(cases,atMost(64)).getAgentCaseDetailsForSearch(anyList(),anyString());
        assertEquals(200,warnings.size());assertTrue(items.stream().allMatch(v->v.getContentError()!=null && v.getSteps()==null));
        assertTrue(items.stream().anyMatch(v->"CASE_CONTENT_READ_LIMIT".equals(v.getContentError())));
    }
}
