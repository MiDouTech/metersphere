package io.metersphere.project.service;

import io.metersphere.project.mapper.ExtProjectMapper;
import io.metersphere.project.request.ProjectPageRequest;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.ProjectDTO;
import io.metersphere.system.mapper.UserRoleRelationMapper;
import io.metersphere.system.service.CommonProjectService;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {
    @Mock
    private ExtProjectMapper extProjectMapper;
    @Mock
    private UserRoleRelationMapper userRoleRelationMapper;
    @Mock
    private CommonProjectService commonProjectService;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService();
        ReflectionTestUtils.setField(service, "extProjectMapper", extProjectMapper);
        ReflectionTestUtils.setField(service, "userRoleRelationMapper", userRoleRelationMapper);
        ReflectionTestUtils.setField(service, "commonProjectService", commonProjectService);
    }

    @Test
    void disabledProjectCannotBeAccessedEvenWithGlobalPermission() {
        when(extProjectMapper.projectIsActive("project-1")).thenReturn(false);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null,
                    PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ)).thenReturn(true);
            assertFalse(service.canAccessProject("project-1", "user-1"));
        }

        verify(userRoleRelationMapper, never()).countByExample(any());
        verify(extProjectMapper, never()).userHasProjectRelation(any(), any());
    }

    @Test
    void activeProjectCanBeAccessedWithGlobalPermission() {
        when(extProjectMapper.projectIsActive("project-1")).thenReturn(true);
        when(userRoleRelationMapper.countByExample(any())).thenReturn(0L);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null,
                    PermissionConstants.SYSTEM_ORGANIZATION_PROJECT_READ)).thenReturn(true);
            assertTrue(service.canAccessProject("project-1", "user-1"));
        }

        verify(extProjectMapper, never()).userHasProjectRelation(any(), any());
    }

    @Test
    void caseAssetProjectPageDelegatesPermissionIntersectionToMapper() {
        ProjectPageRequest request = new ProjectPageRequest();
        ProjectDTO project = new ProjectDTO();
        project.setId("project-1");
        when(userRoleRelationMapper.countByExample(any())).thenReturn(0L);
        when(extProjectMapper.pageCaseAssetProject(request, "user-1", false,
                PermissionConstants.FUNCTIONAL_CASE_READ)).thenReturn(List.of(project));
        when(commonProjectService.buildUserInfo(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProjectDTO> result = service.pageCaseAssetProject(request, "user-1");

        assertTrue(result.stream().anyMatch(item -> "project-1".equals(item.getId())));
        verify(extProjectMapper).pageCaseAssetProject(eq(request), eq("user-1"), eq(false),
                eq(PermissionConstants.FUNCTIONAL_CASE_READ));
    }
}
