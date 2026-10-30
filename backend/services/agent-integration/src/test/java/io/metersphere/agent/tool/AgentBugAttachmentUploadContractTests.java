package io.metersphere.agent.tool;

import io.metersphere.agent.tool.functional.AgentAttachmentUploadTools;
import io.metersphere.agent.service.AgentAttachmentUploadIntentService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBugAttachmentUploadContractTests {
    @Test void dedicatedBugUploadHasCorrectScopeAndPurpose(){
        var config=new AgentAttachmentUploadTools();var service=mock(AgentAttachmentUploadIntentService.class);
        var prepare=config.prepareBugAttachmentUpload(service);var complete=config.completeBugAttachmentUpload(service);
        assertEquals("metersphere.bug.attachment.upload.prepare",prepare.name());
        assertEquals("BUG_ATTACHMENT",prepare.requiredScope());assertEquals("BUG_ATTACHMENT",complete.requiredScope());
        var properties=(Map<?,?>)prepare.inputSchema().get("properties");
        assertEquals(List.of("BUG_DETAIL","BUG_COMMENT"),((Map<?,?>)properties.get("purpose")).get("enum"));
        prepare.execute(Map.of("projectId","p","purpose","BUG_DETAIL","fileName","a.png","contentType","image/png","size",12,"sha256","a".repeat(64),"requestId","r"));
        verify(service).prepare(argThat(r->"BUG_DETAIL".equals(r.purpose())));
    }
}
