package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentAttachmentDeleteContractTests {
    @Test void publicResourceFieldIsUsedWithoutChangingReplayPayload(){
        var operations=mock(AgentOperationExecutor.class);var access=mock(AgentBusinessAccess.class);
        var projects=mock(AgentProjectService.class);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(projects.resolveProjectId("p")).thenReturn("p");
        when(operations.executeWithLegacyArguments(anyString(),anyString(),any(),eq(Map.class),any(Runnable.class),any(),any()))
                .thenAnswer(call->{((Runnable)call.getArgument(4)).run();return ((java.util.function.Supplier<?>)call.getArgument(5)).get();});
        var service=new AgentGenericMcpWriteService(operations,access,projects,jdbc);
        var args=Map.<String,Object>of("projectId","p","resourceId","c","attachmentId","f","confirm",true,"requestId","r");
        service.execute("metersphere.functional.attachment.delete",args,()->Map.of("ok",true));
        verify(access).caseProject("c","p","FUNCTIONAL_CASE:READ+UPDATE",true,"CASE_ATTACHMENT");
        assertFalse(args.containsKey("caseId"));
        assertThrows(MSException.class,()->AgentGenericMcpWriteService.resourceId(Map.of("resourceId","c","caseId","other"),"caseId"));
    }
    @Test void bugDeletionUsesResourceIdInProjectCheck(){
        var operations=mock(AgentOperationExecutor.class);var access=mock(AgentBusinessAccess.class);
        var projects=mock(AgentProjectService.class);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(projects.resolveProjectId("p")).thenReturn("p");
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq("b"),eq("p"))).thenReturn(1L);
        when(operations.executeWithLegacyArguments(anyString(),anyString(),any(),eq(Map.class),any(Runnable.class),any(),any()))
                .thenAnswer(call->{((Runnable)call.getArgument(4)).run();return Map.of("ok",true);});
        new AgentGenericMcpWriteService(operations,access,projects,jdbc).execute("metersphere.bug.attachment.delete",
                Map.of("projectId","p","resourceId","b","attachmentId","f","requestId","r"),()->Map.of());
        verify(jdbc).queryForObject(anyString(),eq(Long.class),eq("b"),eq("p"));
    }
}
