package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentCheckpointResumeTests {
    private AgentExecutionCheckpointService service;
    private JdbcTemplate jdbc;
    private AgentExecutionPreflightService preflight;
    private Map<String,Object> cp,task;
    private AgentCheckpointResumeRequest request;
    @BeforeEach void before(){
        service=new AgentExecutionCheckpointService();jdbc=mock(JdbcTemplate.class);preflight=mock(AgentExecutionPreflightService.class);
        ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(service,"preflightService",preflight);
        ReflectionTestUtils.setField(service,"projectService",mock(AgentProjectService.class));AgentExecutionActorContext.bind("actor");
        request=new AgentCheckpointResumeRequest();request.setResumeToken("a".repeat(43));request.setPreflightId("new-preflight");
        cp=new HashMap<>(Map.of("status","ACTIVE","resume_token_hash",DigestUtils.sha256Hex(request.getResumeToken()),"state_snapshot","snapshot","state_hash",DigestUtils.sha256Hex("snapshot"),"execution_id","execution","checkpoint_version",1,"reason","paused","created_at",10L));
        task=new HashMap<>(Map.of("status","WAITING_HUMAN","project_id","project","current_execution_id","execution","task_origin","PERSONAL_MCP"));
        when(jdbc.queryForMap(startsWith("SELECT * FROM ai_execution_checkpoint"),eq("cp"),eq("task"))).thenReturn(cp);
        when(jdbc.queryForMap(startsWith("SELECT project_id"),eq("task"))).thenReturn(task);
        AgentExecutionPreflightDTO passed=new AgentExecutionPreflightDTO();passed.setId("new-preflight");passed.setStatus("PASSED");passed.setProjectId("project");when(preflight.get("new-preflight")).thenReturn(passed);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
    }
    @AfterEach void clear(){AgentExecutionActorContext.clear();}
    @Test void successfulResumeConsumesPreflightAndRejectsUsedCheckpoint(){
        assertEquals("RESUMED",service.resume("task","cp",request).getStatus());
        verify(preflight).consume("new-preflight","project","actor","PERSONAL_MCP","task");
        cp.put("status","RESUMED");assertThrows(MSException.class,()->service.resume("task","cp",request));
        verify(preflight,times(1)).consume(anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void badTokenOrCorruptedSnapshotCannotWrite(){
        request.setResumeToken("wrong");assertThrows(MSException.class,()->service.resume("task","cp",request));
        request.setResumeToken("a".repeat(43));cp.put("state_snapshot","modified");assertThrows(MSException.class,()->service.resume("task","cp",request));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void canceledOrExpiredTaskCannotResume(){
        task.put("status","CANCELED");assertThrows(MSException.class,()->service.resume("task","cp",request));
        task.put("status","WAITING_HUMAN");task.put("timeout_at",1L);assertThrows(MSException.class,()->service.resume("task","cp",request));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void failedPreflightCannotResume(){
        AgentExecutionPreflightDTO failed=new AgentExecutionPreflightDTO();failed.setStatus("BLOCKED");when(preflight.get("new-preflight")).thenReturn(failed);
        assertThrows(MSException.class,()->service.resume("task","cp",request));verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
