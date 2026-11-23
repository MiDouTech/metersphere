package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBugSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBugSearchRoleTests {
    @Test void bugUpdateCannotRemoveAnotherBugsCaseRelation() {
        var service=new AgentBugWriteService();var projects=mock(AgentProjectService.class);
        when(projects.resolveProjectId("project")).thenReturn("project");
        var db=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE bug_relation_case(id VARCHAR(64),bug_id VARCHAR(64))");
        db.update("INSERT INTO bug_relation_case VALUES('own','bug'),('foreign','another-bug')");
        ReflectionTestUtils.setField(service,"agentProjectService",projects);ReflectionTestUtils.setField(service,"jdbc",db);
        ReflectionTestUtils.setField(service,"access",mock(AgentBusinessAccess.class));
        var request=new io.metersphere.agent.dto.AgentBugUpdateRequest();request.setProjectId("project");request.setBugId("bug");
        request.setRemoveRelationIds(java.util.List.of("own","foreign"));
        try(var session=mockStatic(io.metersphere.system.utils.SessionUtils.class)) {
            session.when(io.metersphere.system.utils.SessionUtils::getUserId).thenReturn("user");
            assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.update(request));
            assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM bug_relation_case",Integer.class));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"update","relate"})
    void legacyHttpBugWritesRequireCurrentUserWriteRole(String action) {
        var service=new AgentBugWriteService();var access=mock(AgentBusinessAccess.class);
        var projects=mock(AgentProjectService.class);when(projects.resolveProjectId("project")).thenReturn("project");
        doThrow(AgentBusinessAccess.denied()).when(access).project("project","PROJECT_BUG:READ+UPDATE",true);
        ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"agentProjectService",projects);
        try(var session=mockStatic(io.metersphere.system.utils.SessionUtils.class)) {
            session.when(io.metersphere.system.utils.SessionUtils::getUserId).thenReturn("user");
            if(action.equals("update")) {
                var request=new io.metersphere.agent.dto.AgentBugUpdateRequest();request.setProjectId("project");request.setBugId("bug");
                assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.update(request));
            } else {
                var request=new io.metersphere.agent.dto.AgentBugRelateCaseRequest();request.setProjectId("project");request.setBugId("bug");
                assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.relateCase(request));
            }
            verify(access).project("project","PROJECT_BUG:READ+UPDATE",true);
        }
    }
    @org.junit.jupiter.api.Test void attachmentListRequiresBugReadBeforeFetchingFiles() {
        var service=new AgentBugAttachmentService();
        var access=org.mockito.Mockito.mock(AgentBusinessAccess.class);
        var projects=org.mockito.Mockito.mock(AgentProjectService.class);
        var bugs=org.mockito.Mockito.mock(io.metersphere.bug.mapper.BugMapper.class);
        var files=org.mockito.Mockito.mock(io.metersphere.bug.service.BugAttachmentService.class);
        org.mockito.Mockito.when(projects.resolveProjectId("project")).thenReturn("project");
        var bug=new io.metersphere.bug.domain.Bug();bug.setId("bug");bug.setProjectId("project");
        org.mockito.Mockito.when(bugs.selectByPrimaryKey("bug")).thenReturn(bug);
        org.mockito.Mockito.doThrow(AgentBusinessAccess.denied()).when(access).project("project","PROJECT_BUG:READ",false);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"access",access);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"agentProjectService",projects);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"bugMapper",bugs);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"bugAttachmentService",files);
        org.junit.jupiter.api.Assertions.assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.list("project","bug"));
        org.mockito.Mockito.verifyNoInteractions(files);
    }
    @Test void projectMembershipDoesNotReplaceBugReadPermission() {
        var service=new AgentBugWriteService();
        var projects=mock(AgentProjectService.class);
        var access=mock(AgentBusinessAccess.class);
        var backend=mock(io.metersphere.bug.service.BugService.class);
        when(projects.resolveProjectId("project")).thenReturn("project");
        doThrow(AgentBusinessAccess.denied()).when(access).project("project","PROJECT_BUG:READ",false);
        ReflectionTestUtils.setField(service,"agentProjectService",projects);
        ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"bugService",backend);
        var request=new AgentBugSearchRequest();request.setProjectId("project");
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.search(request));
        verify(access).project("project","PROJECT_BUG:READ",false);
        verifyNoInteractions(backend);
    }
}
