package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentAttachmentIntentPermissionTests {
    @Test void bugUploadChecksBugScopeAndCaseUploadChecksCaseScope(){
        var service=new AgentAttachmentUploadIntentService();var access=mock(AgentBusinessAccess.class);var jdbc=mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"jdbc",jdbc);
        var token=new AgentToken();token.setId("t");token.setUserId("u");AgentTokenContext.set(token);
        try {
            for(String purpose:List.of("BUG_DETAIL","BUG_COMMENT","CASE_DETAIL","CASE_COMMENT")){
                var result=service.prepare(new AgentAttachmentUploadIntentService.Prepare("p",purpose,"a.png","image/png",10,"a".repeat(64),"r-"+purpose));
                assertNotNull(result.uploadId());
            }
            verify(access,times(2)).project("p","PROJECT_BUG:READ+UPDATE",true,"BUG_ATTACHMENT");
            verify(access,times(2)).project("p","FUNCTIONAL_CASE:READ",true,"CASE_ATTACHMENT");
        } finally {AgentTokenContext.clear();}
    }
    @Test void permissionFailureNeverReservesUpload(){
        var service=new AgentAttachmentUploadIntentService();var access=mock(AgentBusinessAccess.class);var jdbc=mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"jdbc",jdbc);
        when(access.project("p","PROJECT_BUG:READ+UPDATE",true,"BUG_ATTACHMENT")).thenThrow(AgentBusinessAccess.denied());
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.prepare(new AgentAttachmentUploadIntentService.Prepare("p","BUG_DETAIL","a.png","image/png",10,"a".repeat(64),"r")));
        verifyNoInteractions(jdbc);
    }
}
