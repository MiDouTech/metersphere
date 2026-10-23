package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentExternalImageServiceTests {
    @Test void committedReplayReauthorizesAndDoesNotDependOnExpiredSource() {
        var service=new AgentExternalImageService();
        var access=mock(AgentBusinessAccess.class);
        var operations=mock(AgentOperationExecutor.class);
        var downloader=mock(AgentExternalImageDownloader.class);
        ReflectionTestUtils.setField(service,"enabled",true);
        ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"operations",operations);
        ReflectionTestUtils.setField(service,"downloader",downloader);
        Map<String,Object> args=Map.of("projectId","p","caseId","c","url","https://example.com/expired.png","requestId","r");
        when(operations.completed(eq("external.image.case"),eq("r"),eq(args),eq(Map.class),any(Runnable.class))).thenAnswer(call->{
            ((Runnable)call.getArgument(4)).run();
            return Optional.of(Map.of("state","ATTACHED","fileId","saved"));
        });
        assertEquals("saved",service.importImage("case",args).get("fileId"));
        verify(access).caseProject("c","p","FUNCTIONAL_CASE:READ+UPDATE",true,"CASE_ATTACHMENT");
        verifyNoInteractions(downloader);
    }
}
