package io.metersphere.system.service;

import io.metersphere.system.domain.PermissionResource;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRolePermission;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.dto.permission.UserUiPermissionsDTO;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.dto.user.UserRoleResourceDTO;
import io.metersphere.system.mapper.PermissionResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionUiServiceTests {

    @Mock
    private PermissionResourceMapper permissionResourceMapper;
    private PermissionUiService service;

    @BeforeEach
    void setUp() {
        service = new PermissionUiService();
        ReflectionTestUtils.setField(service, "permissionResourceMapper", permissionResourceMapper);
    }

    @Test
    void aggregateProjectsPageVisibilityFromBusinessPermissionAndParentInheritance() {
        when(permissionResourceMapper.selectEnabled()).thenReturn(projectResources());

        UserUiPermissionsDTO result = service.aggregate(projectUser("custom-project-role", "PROJECT_TEST_PLAN:READ"));

        assertTrue(result.getProject().getVisible().contains("TEST_PLAN_PAGE"));
        assertTrue(result.getProject().getVisible().contains("TEST_PLAN_OVERVIEW_TAB"));
        assertTrue(result.getProject().getVisibleRoutes().contains("testPlanIndex"));
        assertFalse(result.getProject().getVisible().contains("TEST_PLAN_DELETE_BUTTON"));
        assertFalse(result.getProject().getOperable().contains("TEST_PLAN_DELETE_BUTTON"));
    }

    @Test
    void aggregateDoesNotAllowLegacyUiGrantWithoutBusinessPermission() {
        when(permissionResourceMapper.selectEnabled()).thenReturn(projectResources());

        UserUiPermissionsDTO result = service.aggregate(projectUser("custom-project-role", null));

        assertFalse(result.getProject().getVisible().contains("TEST_PLAN_PAGE"));
        assertFalse(result.getProject().getVisible().contains("TEST_PLAN_OVERVIEW_TAB"));
        assertFalse(result.getProject().getVisibleRoutes().contains("testPlanIndex"));
    }

    @Test
    void aggregateGrantsAllProjectResourcesToProjectAdministratorInCurrentProject() {
        when(permissionResourceMapper.selectEnabled()).thenReturn(projectResources());

        UserUiPermissionsDTO result = service.aggregate(projectUser("project_admin", null));

        assertTrue(result.getProject().getVisible().contains("TEST_PLAN_PAGE"));
        assertTrue(result.getProject().getVisible().contains("TEST_PLAN_OVERVIEW_TAB"));
        assertTrue(result.getProject().getVisible().contains("TEST_PLAN_DELETE_BUTTON"));
        assertTrue(result.getProject().getOperable().contains("TEST_PLAN_DELETE_BUTTON"));
    }

    private UserDTO projectUser(String roleId, String permissionId) {
        UserRole role = new UserRole();
        role.setId(roleId);
        role.setType("PROJECT");
        role.setEnabled(true);

        UserRoleRelation relation = new UserRoleRelation();
        relation.setRoleId(roleId);
        relation.setSourceId("project-1");

        UserRoleResourceDTO roleResource = new UserRoleResourceDTO();
        roleResource.setUserRole(role);
        if (permissionId == null) {
            roleResource.setUserRolePermissions(List.of());
        } else {
            UserRolePermission permission = new UserRolePermission();
            permission.setRoleId(roleId);
            permission.setPermissionId(permissionId);
            roleResource.setUserRolePermissions(List.of(permission));
        }

        UserDTO user = new UserDTO();
        user.setLastProjectId("project-1");
        user.setLastOrganizationId("org-1");
        user.setUserRoles(List.of(role));
        user.setUserRoleRelations(List.of(relation));
        user.setUserRolePermissions(List.of(roleResource));
        return user;
    }

    private List<PermissionResource> projectResources() {
        PermissionResource page = resource("TEST_PLAN_PAGE", "PAGE", null,
                "testPlanIndex", "PROJECT_TEST_PLAN:READ");
        PermissionResource tab = resource("TEST_PLAN_OVERVIEW_TAB", "TAB", "TEST_PLAN_PAGE", null, null);
        PermissionResource button = resource("TEST_PLAN_DELETE_BUTTON", "BUTTON", "TEST_PLAN_PAGE", null,
                "PROJECT_TEST_PLAN:READ+DELETE");
        return List.of(page, tab, button);
    }

    private PermissionResource resource(String code, String type, String parentCode,
                                        String routeName, String permissionId) {
        PermissionResource resource = new PermissionResource();
        resource.setCode(code);
        resource.setType(type);
        resource.setScopeType("PROJECT");
        resource.setParentCode(parentCode);
        resource.setRouteName(routeName);
        resource.setPermissionId(permissionId);
        resource.setEnabled(true);
        return resource;
    }
}
