package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCheckpointCreateRequest;
import io.metersphere.agent.dto.AgentCheckpointResumeRequest;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentExecutionCheckpointServiceTests {
    @Mock JdbcTemplate jdbc;
    @Mock AgentExecutionPreflightService preflight;
    @Mock AgentProjectService projects;
    @InjectMocks AgentExecutionCheckpointService service;

    @Test
    void createRejectsExpiredLeaseBeforeAnyCheckpointWrite(){
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of(
                "project_id","p1","current_execution_id","e1","runner_lease_id","l1","status","RUNNING",
                "lease_status","ACTIVE","expire_time",System.currentTimeMillis()-1));
        AgentCheckpointCreateRequest request=new AgentCheckpointCreateRequest();request.setExecutionId("e1");request.setReason("wait");request.setStateSnapshot("{}");
        MSException error=Assertions.assertThrows(MSException.class,()->service.create("t1","l1",request));
        Assertions.assertEquals("CHECKPOINT_LEASE_INVALID_OR_EXPIRED",error.getMessage());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test
    void resumeChecksProjectAccessBeforeMutatingState(){
        String token="abcdefghijklmnopqrstuvwxyz-0123456789-ABCDE";
        Map<String,Object> checkpoint=Map.of("status","ACTIVE","resume_token_hash",DigestUtils.sha256Hex(token),
                "state_snapshot","{}","state_hash",DigestUtils.sha256Hex("{}"),"execution_id","e1");
        Map<String,Object> task=Map.of("project_id","p2","create_user","u2","task_origin","PLATFORM_MANUAL",
                "status","WAITING_HUMAN","current_execution_id","e1","timeout_at",System.currentTimeMillis()+60000);
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(checkpoint,task);
        when(projects.resolveProjectId("p2")).thenThrow(new MSException("PERMISSION_DENIED"));
        AgentCheckpointResumeRequest request=new AgentCheckpointResumeRequest();request.setResumeToken(token);request.setPreflightId("pf1");
        Assertions.assertThrows(MSException.class,()->service.resume("t1","cp1",request));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test
    void duplicateRequestIdReturnsExistingCheckpointWithoutRepeatingSideEffects(){
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(java.util.List.of(Map.of("id","cp1","execution_id","e1","checkpoint_version",1,"status","ACTIVE","reason","wait","created_at",10L)));
        AgentCheckpointCreateRequest request=new AgentCheckpointCreateRequest();request.setExecutionId("e1");request.setReason("wait");request.setStateSnapshot("{}");request.setRequestId("request-1");
        var result=service.create("t1","l1",request);
        Assertions.assertEquals("cp1",result.getId());Assertions.assertNull(result.getResumeToken());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
