package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentTestPlanQueryRoleTests {
    @Test void projectMembershipCannotReplacePlanReadPermission() {
        var service=new AgentTestPlanQueryService(); var projects=mock(AgentProjectService.class);
        var mapper=mock(AgentExecutionMapper.class); var access=mock(AgentBusinessAccess.class);
        when(projects.resolveProjectId("project")).thenReturn("project");
        doThrow(AgentBusinessAccess.denied()).when(access).project("project",io.metersphere.sdk.constants.PermissionConstants.TEST_PLAN_READ,false);
        ReflectionTestUtils.setField(service,"agentProjectService",projects);
        ReflectionTestUtils.setField(service,"agentExecutionMapper",mapper);
        // The field must exist: its absence is a regression in the authorization boundary.
        ReflectionTestUtils.setField(service,"access",access);
        var request=new AgentTestPlanSearchRequest();request.setProjectId("project");
        assertThrows(MSException.class,()->service.search(request));
        verifyNoInteractions(mapper);
    }
}
