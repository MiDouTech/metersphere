package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentMcpBatchTests {
    @AfterEach void clear(){AgentTokenContext.clear();}

    @Test void partialFailureIsSafeAndDoesNotStopLaterRows(){
        var service=new AgentBatchSubmitService();var items=mock(AgentMcpBatchItemService.class);
        ReflectionTestUtils.setField(service,"mcpBatchItemService",items);
        ReflectionTestUtils.setField(service,"safeErrorMapper",new AgentSafeErrorMapper());
        var request=request();
        doThrow(new IllegalStateException("SQL secret-password stacktrace")).when(items).submit(same(request.getResults().get(1)),anyString());
        var result=service.batchSubmit(request,"batch");
        assertEquals(2,result.getSuccess());assertEquals(1,result.getFailed());assertEquals(0,result.getSkipped());
        assertFalse(result.getErrors().getFirst().getMessage().contains("SQL"));assertNotNull(result.getErrors().getFirst().getTraceId());
        verify(items).submit(same(request.getResults().get(2)),anyString());
    }
    @Test void failFastReportsUnattemptedRows(){
        var service=new AgentBatchSubmitService();var items=mock(AgentMcpBatchItemService.class);
        ReflectionTestUtils.setField(service,"mcpBatchItemService",items);ReflectionTestUtils.setField(service,"safeErrorMapper",new AgentSafeErrorMapper());
        var request=request();request.setFailFast(true);
        doThrow(new MSException("VALIDATION_ERROR")).when(items).submit(same(request.getResults().getFirst()),anyString());
        var result=service.batchSubmit(request,"batch");
        assertEquals(0,result.getSuccess());assertEquals(1,result.getFailed());assertEquals(2,result.getSkipped());
        verify(items,times(1)).submit(any(),anyString());
    }
    @Test void itemReplayLocksTokenAndSkipsAlreadyCommittedWrite(){
        var service=new AgentMcpBatchItemService();var jdbc=mock(JdbcTemplate.class);var idem=mock(AgentIdempotencyService.class);var submit=mock(AgentFunctionalCaseSubmitService.class);
        ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(service,"idempotencyService",idem);ReflectionTestUtils.setField(service,"submitService",submit);
        AgentToken token=new AgentToken();token.setId("token");AgentTokenContext.set(token);
        when(idem.findCachedResponse(anyString(),eq("row-key"),anyMap())).thenReturn(Optional.empty(),Optional.of(Map.of("ok",true)));
        var item=request().getResults().getFirst();service.submit(item,"row-key");service.submit(item,"row-key");
        verify(jdbc,times(2)).queryForObject(contains("FOR UPDATE"),eq(String.class),eq("token"));
        verify(submit,times(1)).submit(item);verify(idem,times(1)).save(anyString(),eq("row-key"),anyMap(),anyMap());
    }
    private AgentBatchSubmitRequest request(){var r=new AgentBatchSubmitRequest();r.setProjectId("project");var rows=new ArrayList<AgentCaseSubmitRequest>();for(int i=0;i<3;i++){var row=new AgentCaseSubmitRequest();row.setCaseId("case-"+i);row.setLastExecResult("SUCCESS");rows.add(row);}r.setResults(rows);return r;}
}
