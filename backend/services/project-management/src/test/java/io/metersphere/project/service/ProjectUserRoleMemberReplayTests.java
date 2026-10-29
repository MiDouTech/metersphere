package io.metersphere.project.service;

import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.request.ProjectUserRoleMemberEditRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.mapper.UserRoleMapper;
import io.metersphere.system.service.RoleRelationWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectUserRoleMemberReplayTests {
    private ProjectUserRoleService service;
    private RoleRelationWriter writer;
    private UserRole role;
    private ProjectUserRoleMemberEditRequest request;

    @BeforeEach
    void setup() {
        service = new ProjectUserRoleService();
        ProjectMapper projects = mock(ProjectMapper.class);
        UserRoleMapper roles = mock(UserRoleMapper.class);
        UserMapper users = mock(UserMapper.class);
        writer = mock(RoleRelationWriter.class);
        ReflectionTestUtils.setField(service, "projectMapper", projects);
        ReflectionTestUtils.setField(service, "userRoleMapper", roles);
        ReflectionTestUtils.setField(service, io.metersphere.system.service.BaseUserRoleService.class,
                "userRoleMapper", roles, UserRoleMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "roleRelationWriter", writer);
        Project project = new Project();
        project.setId("project-a");
        project.setOrganizationId("organization-a");
        when(projects.selectByPrimaryKey("project-a")).thenReturn(project);
        role = new UserRole();
        role.setId("role-a");
        role.setType("PROJECT");
        role.setScopeId("project-a");
        when(roles.selectByPrimaryKey("role-a")).thenReturn(role);
        when(users.selectByPrimaryKey("user-a")).thenReturn(new User());
        request = new ProjectUserRoleMemberEditRequest();
        request.setProjectId("project-a");
        request.setUserRoleId("role-a");
        request.setUserIds(List.of("user-a", "user-a"));
    }

    @Test
    void duplicateInputUsesAuthorizedNaturalKeyOnceAndAcceptsExistingRelation() {
        // Writer returns false for an existing relationship; caller must still succeed.
        try (var ids = mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("new-relationship-id");
            assertDoesNotThrow(() -> service.addMember(request, "operator"));
        }
        ArgumentCaptor<UserRoleRelation> relation = ArgumentCaptor.forClass(UserRoleRelation.class);
        verify(writer, times(1)).ensurePresent(relation.capture());
        assertEquals("user-a", relation.getValue().getUserId());
        assertEquals("role-a", relation.getValue().getRoleId());
        assertEquals("project-a", relation.getValue().getSourceId());
        assertEquals("organization-a", relation.getValue().getOrganizationId());
        assertEquals("operator", relation.getValue().getCreateUser());
    }

    @Test
    void roleFromAnotherProjectCannotBeBound() {
        role.setScopeId("project-b");
        assertThrows(MSException.class, () -> service.addMember(request, "operator"));
        verifyNoInteractions(writer);
    }

    @Test
    void nonProjectRoleCannotBeBound() {
        role.setType("ORGANIZATION");
        assertThrows(MSException.class, () -> service.addMember(request, "operator"));
        verifyNoInteractions(writer);
    }

    @Test
    void missingProjectDoesNotReachWriter() {
        request.setProjectId("missing");
        try (var translator = mockStatic(io.metersphere.sdk.util.Translator.class)) {
            translator.when(() -> io.metersphere.sdk.util.Translator.get("project_not_exist"))
                    .thenReturn("Project does not exist");
            assertThrows(MSException.class, () -> service.addMember(request, "operator"));
        }
        verifyNoInteractions(writer);
    }
}
