package io.metersphere.agent.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseComment;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.mapper.FunctionalCaseCommentMapper;
import io.metersphere.functional.request.FunctionalCaseCommentRequest;
import io.metersphere.functional.service.FunctionalCaseCommentService;
import io.metersphere.functional.service.FunctionalCaseNoticeService;
import io.metersphere.agent.security.AgentTokenScopeParser;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentCommentEditTests {
    @Test void refusesOtherAuthorCrossCaseAndStaleVersionBeforeUpdate() {
        for (Map<String,Object> row : List.<Map<String,Object>>of(
                Map.of("case_id","c","create_user","another","update_time",10L),
                Map.of("case_id","other-case","create_user","u","update_time",10L),
                Map.of("case_id","c","create_user","u","update_time",11L))) {
            var fixture = fixture(row);
            assertThrows(MSException.class, () -> fixture.service.updateComment(request(), "u"));
            verifyNoInteractions(fixture.comments);
        }
    }
    @Test void validEditAdvancesVersionAndDoesNotChangeReplyStructure() {
        var fixture = fixture(Map.of("case_id","c","create_user","u","update_time",10L));
        FunctionalCaseComment result = fixture.service.updateComment(request(), "u");
        assertEquals("edited",result.getContent());
        assertNull(result.getParentId()); // selective update preserves the stored parent
        assertNull(result.getCreateUser());
        assertTrue(result.getUpdateTime()>10);
        verify(fixture.comments).updateByPrimaryKeySelective(result);
    }
    @Test void legacyWildcardCannotAcquireCommentEdit() {
        assertFalse(AgentTokenScopeParser.hasScope(Set.of("AGENT_ALL"),"CASE_COMMENT_EDIT_V2"));
        assertTrue(AgentTokenScopeParser.hasScope(Set.of("CASE_COMMENT_EDIT_V2"),"CASE_COMMENT_EDIT_V2"));
    }
    private FunctionalCaseCommentRequest request() {
        var request = new FunctionalCaseCommentRequest();
        request.setId("comment"); request.setCaseId("c"); request.setExpectedUpdateTime(10L);
        request.setContent("edited"); request.setEvent("COMMENT");
        return request;
    }
    private Fixture fixture(Map<String,Object> row) {
        var service = new FunctionalCaseCommentService();
        var cases = mock(FunctionalCaseMapper.class);
        var comments = mock(FunctionalCaseCommentMapper.class);
        var jdbc = mock(JdbcTemplate.class);
        var ownerCase = new FunctionalCase(); ownerCase.setId("c"); ownerCase.setProjectId("p");
        when(cases.selectByPrimaryKey("c")).thenReturn(ownerCase);
        when(jdbc.queryForList(anyString(),eq("comment"))).thenReturn(List.of(row));
        ReflectionTestUtils.setField(service,"functionalCaseMapper",cases);
        ReflectionTestUtils.setField(service,"functionalCaseCommentMapper",comments);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        ReflectionTestUtils.setField(service,"functionalCaseNoticeService",mock(FunctionalCaseNoticeService.class));
        return new Fixture(service,comments);
    }
    private record Fixture(FunctionalCaseCommentService service,FunctionalCaseCommentMapper comments) {}
}
