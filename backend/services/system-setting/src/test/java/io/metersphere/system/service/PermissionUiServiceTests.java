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

    @Test
    void globalAssetProjectionSeparatesOrganizationPagesFromProjectRelationsAndExposesOnlyMenuShell() {
        var menu = resource("TEST_ASSET_MENU", "MENU", null, "testAsset", null);
        menu.setScopeType("ORGANIZATION");
        var data = resource("TEST_ASSET_DATASETS_PAGE", "PAGE", menu.getCode(), "testAssetDatasets", "TEST_DATA_ASSET:READ");
        data.setScopeType("ORGANIZATION");
        var relation = resource("TEST_ASSET_RELATIONS_PAGE", "PAGE", menu.getCode(), "testAssetRelations", "PROJECT_FILE_MANAGEMENT:READ");
        when(permissionResourceMapper.selectEnabled()).thenReturn(List.of(menu,data,relation));
        var orgUser = projectUser("custom-org", "TEST_DATA_ASSET:READ");
        orgUser.setLastProjectId(null);
        orgUser.getUserRoles().getFirst().setType("ORGANIZATION");
        orgUser.getUserRoleRelations().getFirst().setSourceId("org-1");
        var result = service.aggregate(orgUser);
        assertTrue(result.getOrganization().getVisibleRoutes().contains("testAssetDatasets"));
        assertTrue(result.getOrganization().getVisibleRoutes().contains("testAsset"));
        assertFalse(result.getProject().getVisibleRoutes().contains("testAssetRelations"));
        var project = service.aggregate(projectUser("custom-project", "PROJECT_FILE_MANAGEMENT:READ"));
        assertTrue(project.getProject().getVisibleRoutes().contains("testAssetRelations"));
        assertTrue(project.getOrganization().getVisibleRoutes().contains("testAsset"));
        assertFalse(project.getOrganization().getVisibleRoutes().contains("testAssetDatasets"));
        var denied = service.aggregate(projectUser("custom-project", null));
        assertFalse(denied.getOrganization().getVisibleRoutes().contains("testAsset"));
    }

    @Test
    void eachGlobalPageUsesItsOwnOrganizationBusinessPermission() {
        var definitions = java.util.Map.of(
                "testAssetDocuments", "BUSINESS_ASSET:READ",
                "testAssetSharedConfiguration", "SHARED_CONFIGURATION:READ");
        var pages = definitions.entrySet().stream().map(entry -> {
            var page = resource(entry.getKey(), "PAGE", null, entry.getKey(), entry.getValue());
            page.setScopeType("ORGANIZATION");
            return page;
        }).toList();
        when(permissionResourceMapper.selectEnabled()).thenReturn(pages);
        for (var grant : definitions.entrySet()) {
            var user = projectUser("custom-org", grant.getValue());
            user.setLastProjectId(null);
            user.getUserRoles().getFirst().setType("ORGANIZATION");
            user.getUserRoleRelations().getFirst().setSourceId("org-1");
            var projection = service.aggregate(user);
            assertTrue(projection.getOrganization().getVisibleRoutes().contains(grant.getKey()));
            assertTrue(projection.getOrganization().getVisibleRoutes().size() == 1);
            assertTrue(projection.getProject().getVisibleRoutes().isEmpty());
        }
    }

    @Test
    void versionsReadProjectionAcceptsReadButNeverWriteOnlyOrProjectRolePromotion() {
        var versions = resource("TEST_ASSET_VERSIONS_PAGE", "PAGE", null, "testAssetVersions", null);
        versions.setScopeType("ORGANIZATION");
        when(permissionResourceMapper.selectEnabled()).thenReturn(List.of(versions));
        for (String permission : List.of("BUSINESS_ASSET:READ", "SHARED_CONFIGURATION:READ", "TEST_DATA_ASSET:READ", "FUNCTIONAL_CASE:READ")) {
            var user = projectUser("org-reader", permission);
            user.setLastProjectId(null);
            user.getUserRoles().getFirst().setType("ORGANIZATION");
            user.getUserRoleRelations().getFirst().setSourceId("org-1");
            assertTrue(service.aggregate(user).getOrganization().getVisibleRoutes().contains("testAssetVersions"));
        }
        for (String permission : List.of("BUSINESS_ASSET:WRITE", "SHARED_CONFIGURATION:WRITE", "TEST_ASSET_VERSION:MANAGE")) {
            var user = projectUser("org-writer", permission);
            user.setLastProjectId(null);
            user.getUserRoles().getFirst().setType("ORGANIZATION");
            user.getUserRoleRelations().getFirst().setSourceId("org-1");
            assertFalse(service.aggregate(user).getOrganization().getVisibleRoutes().contains("testAssetVersions"));
        }
        assertFalse(service.aggregate(projectUser("project-writer", "BUSINESS_ASSET:WRITE")).getOrganization().getVisibleRoutes().contains("testAssetVersions"));
    }

    @Test
    void projectSourceReaderCanOpenCompatibilityVersionsWithoutGlobalAssetGrants() {
        var versions = resource("TEST_ASSET_VERSIONS_PAGE", "PAGE", "TEST_ASSET_MENU", "testAssetVersions", null);
        versions.setScopeType("ORGANIZATION");
        var menu = resource("TEST_ASSET_MENU", "MENU", null, "testAsset", null);
        menu.setScopeType("ORGANIZATION");
        var data = resource("TEST_ASSET_DATASETS_PAGE", "PAGE", menu.getCode(), "testAssetDatasets", "TEST_DATA_ASSET:READ");
        data.setScopeType("ORGANIZATION");
        when(permissionResourceMapper.selectEnabled()).thenReturn(List.of(menu, versions, data));
        var projects = org.mockito.Mockito.mock(io.metersphere.project.mapper.ProjectMapper.class);
        ReflectionTestUtils.setField(service, "projectMapper", projects);
        var source = new io.metersphere.project.domain.Project();
        source.setOrganizationId("org-1"); source.setDeleted(false);
        when(projects.selectByPrimaryKey("project-1")).thenReturn(source);
        var result = service.aggregate(projectUser("source-reader", "FUNCTIONAL_CASE:READ"));
        assertTrue(result.getOrganization().getVisibleRoutes().contains("testAssetVersions"));
        assertTrue(result.getOrganization().getVisibleRoutes().contains("testAsset"));
        assertFalse(result.getOrganization().getVisibleRoutes().contains("testAssetDatasets"));
        for (String permission : List.of("FUNCTIONAL_CASE:READ+UPDATE", "TEST_ASSET_VERSION:MANAGE"))
            assertFalse(service.aggregate(projectUser("source-writer", permission)).getOrganization().getVisibleRoutes().contains("testAssetVersions"));
        var switched = projectUser("source-reader", "FUNCTIONAL_CASE:READ");
        switched.setLastProjectId("unrelated-current-project");
        assertTrue(service.aggregate(switched).getOrganization().getVisibleRoutes().contains("testAssetVersions"));
        source.setOrganizationId("other-org");
        assertFalse(service.aggregate(projectUser("source-reader", "FUNCTIONAL_CASE:READ")).getOrganization().getVisibleRoutes().contains("testAssetVersions"));
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
