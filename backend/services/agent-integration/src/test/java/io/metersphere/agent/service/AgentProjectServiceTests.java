package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentProjectSearchRequest;
import io.metersphere.agent.dto.AgentProjectSearchResponse;
import io.metersphere.agent.mapper.ExtAgentProjectMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.mapper.OrganizationMapper;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.OrganizationProjectService;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentProjectServiceTests {

    @Mock
    private OrganizationProjectService organizationProjectService;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ExtAgentProjectMapper extAgentProjectMapper;
    @Mock
    private UserRoleRelationMapper userRoleRelationMapper;
    @Mock
    private AgentExecLogService agentExecLogService;

    @InjectMocks
    private AgentProjectService agentProjectService;

    @AfterEach
    void tearDown() {
        AgentTokenContext.clear();
    }

    @Test
    void escapeLikeShouldEscapeWildcards() {
        Assertions.assertEquals("a\\\\b\\%c\\_d", AgentProjectService.escapeLike("a\\b%c_d"));
    }

    @Test
    void searchShouldPaginateViaMapper() {
        UserRoleRelation relation = new UserRoleRelation();
        relation.setSourceId("proj-1");
        when(userRoleRelationMapper.selectByExample(any())).thenReturn(List.of(relation));
        Project project = new Project();
        project.setId("proj-1");
        when(projectMapper.selectByExample(any())).thenReturn(List.of(project));

        Project row = new Project();
        row.setId("proj-1");
        row.setName("Alpha");
        row.setNum(12L);
        row.setOrganizationId("org-1");
        row.setEnable(true);
        when(extAgentProjectMapper.countSearch(anyList(), eq("Alpha"), anyString(), eq(false))).thenReturn(1L);
        when(extAgentProjectMapper.search(anyList(), eq("Alpha"), anyString(), eq(false), eq(0), eq(20)))
                .thenReturn(List.of(row));
        when(organizationMapper.selectByPrimaryKey("org-1")).thenReturn(null);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            AgentProjectSearchRequest request = new AgentProjectSearchRequest();
            request.setKeyword("Alpha");
            request.setPage(1);
            request.setPageSize(20);
            AgentProjectSearchResponse response = agentProjectService.search(request);
            Assertions.assertEquals(1, response.getItems().size());
            Assertions.assertEquals(1L, response.getTotal());
            Assertions.assertFalse(response.isHasMore());
            Assertions.assertEquals("Alpha", response.getItems().get(0).getName());
        }
        verify(extAgentProjectMapper).countSearch(anyList(), eq("Alpha"), eq("alpha"), eq(false));
    }

    @Test
    void searchShouldReturnEmptyWhenNoAccessibleProjects() {
        when(userRoleRelationMapper.selectByExample(any())).thenReturn(List.of());
        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            AgentProjectSearchResponse response = agentProjectService.search(new AgentProjectSearchRequest());
            Assertions.assertTrue(response.getItems().isEmpty());
            Assertions.assertEquals(0L, response.getTotal());
            Assertions.assertFalse(response.isHasMore());
        }
    }

    @Test
    void searchShouldCapPageSizeAt100() {
        UserRoleRelation relation = new UserRoleRelation();
        relation.setSourceId("proj-1");
        when(userRoleRelationMapper.selectByExample(any())).thenReturn(List.of(relation));
        Project project = new Project();
        project.setId("proj-1");
        when(projectMapper.selectByExample(any())).thenReturn(List.of(project));
        when(extAgentProjectMapper.countSearch(anyList(), any(), anyString(), anyBoolean())).thenReturn(0L);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            AgentProjectSearchRequest request = new AgentProjectSearchRequest();
            request.setPageSize(200);
            AgentProjectSearchResponse response = agentProjectService.search(request);
            Assertions.assertEquals(100, response.getPageSize());
        }
        verify(extAgentProjectMapper).countSearch(anyList(), eq(""), eq(""), eq(false));
    }
}
