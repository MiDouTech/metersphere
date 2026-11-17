package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenRateLimiter;
import io.metersphere.agent.tool.*;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BugImageNativeProtocolTests {
    @Test void realNativeEnvelopeReturnsImageAndMetadataWithoutAWriteCache() {
        var images=mock(AgentBugImageService.class);var tool=new BugImageReadTool(images);
        var service=new AgentMcpStreamableService();var limiter=mock(AgentTokenRateLimiter.class);
        when(limiter.tryAcquireTool(anyString(),anyString())).thenReturn(true);
        ReflectionTestUtils.setField(service,"agentMcpToolRegistry",new AgentMcpToolRegistry(List.of(tool)));
        ReflectionTestUtils.setField(service,"agentTokenRateLimiter",limiter);
        ReflectionTestUtils.setField(service,"safeErrorMapper",new AgentSafeErrorMapper());
        String ref="bi1_"+"a".repeat(64)+"_"+"b".repeat(64);
        when(images.read("p","b",ref)).thenReturn(new AgentMcpImageResult("AQID","image/png",Map.of("sizeBytes",3)));
        var token=new AgentToken();token.setId("t");token.setScopes("BUG_READ");AgentTokenContext.set(token);
        try {
            var envelope=service.handle(Map.of("jsonrpc","2.0","id",1,"method","tools/call","params",
                    Map.of("name",tool.name(),"arguments",Map.of("projectId","p","bugId","b","imageRef",ref))));
            var result=(Map<?,?>)envelope.get("result");assertNotNull(result,envelope.toString());
            var content=(List<?>)result.get("content");assertEquals(2,content.size());
            assertEquals(Map.of("type","image","mimeType","image/png","data","AQID"),content.getFirst());
            assertEquals(Map.of("result",Map.of("sizeBytes",3)),result.get("structuredContent"));
        } finally {AgentTokenContext.clear();}
    }
    @Test void missingExtraAndWrongTypeArgumentsNeverReachTheImageService() {
        var images=mock(AgentBugImageService.class);var tool=new BugImageReadTool(images);
        for(var args:List.of(Map.<String,Object>of("projectId","p","bugId","b"),
                Map.<String,Object>of("projectId","p","bugId","b","imageRef",42),
                Map.<String,Object>of("projectId","p","bugId","b","imageRef","ref","url","https://example.org")))
            assertThrows(io.metersphere.sdk.exception.MSException.class,()->tool.execute(args));
        verifyNoInteractions(images);
    }
}
