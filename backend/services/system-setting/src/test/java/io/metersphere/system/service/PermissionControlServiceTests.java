package io.metersphere.system.service;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.dto.permission.Permission;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.RoleUiPermissionDTO;
import io.metersphere.system.dto.permission.control.RoleDeleteImpactDTO;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.RoleSaveRequest;
import io.metersphere.system.dto.request.GlobalUserRoleRelationQueryRequest;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.system.dto.user.UserRoleRelationUserDTO;
import io.metersphere.system.mapper.PermissionControlMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.mapper.ExtUserRoleRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionControlServiceTests {
    @Mock
    private GlobalUserRoleService globalUserRoleService;
    @Mock
    private PermissionUiService permissionUiService;
    @Mock
    private PermissionControlMapper permissionControlMapper;
    @Mock
    private PermissionMigrationAuditService permissionMigrationAuditService;
    @Mock
    private GlobalUserRoleRelationService globalUserRoleRelationService;
    @Mock
    private ExtUserRoleRelationMapper extUserRoleRelationMapper;
    @Mock
    private PermissionSessionRefreshService permissionSessionRefreshService;
    @Mock
    private ProjectMapper projectMapper;

    private PermissionControlService service;

    @BeforeEach
    void setUp() {
        service = new PermissionControlService();
        ReflectionTestUtils.setField(service, "globalUserRoleService", globalUserRoleService);
        ReflectionTestUtils.setField(service, "permissionUiService", permissionUiService);
        ReflectionTestUtils.setField(service, "permissionControlMapper", permissionControlMapper);
        ReflectionTestUtils.setField(service, "permissionMigrationAuditService", permissionMigrationAuditService);
        ReflectionTestUtils.setField(service, "globalUserRoleRelationService", globalUserRoleRelationService);
        ReflectionTestUtils.setField(service, "extUserRoleRelationMapper", extUserRoleRelationMapper);
        ReflectionTestUtils.setField(service, "permissionSessionRefreshService", permissionSessionRefreshService);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
    }

    @Test
    void saveRoleCreatesRoleAndNormalizesWritePermissionDependency() {
        RoleSaveRequest request = new RoleSaveRequest();
        request.setName("开发成员");
        request.setType("SYSTEM");
        request.setEnabled(true);
        request.setUiPermissions(List.of());
        request.setPermissions(List.of(
                new PermissionSettingUpdateRequest.PermissionUpdateRequest("SYSTEM_USER:READ", false),
                new PermissionSettingUpdateRequest.PermissionUpdateRequest("SYSTEM_USER:READ+UPDATE", true)));
        UserRole saved = new UserRole();
        saved.setId("role-1");
        saved.setEnabled(true);
        saved.setType("SYSTEM");
        Permission read = new Permission();
        read.setId("SYSTEM_USER:READ");
        Permission update = new Permission();
        update.setId("SYSTEM_USER:READ+UPDATE");
        PermissionDefinitionItem definition = new PermissionDefinitionItem();
        definition.setPermissions(List.of(read, update));
        when(globalUserRoleService.getPermissionDefinitionForControl("SYSTEM")).thenReturn(List.of(definition));
        when(permissionUiService.getAllResourceTree()).thenReturn(List.of());
        when(globalUserRoleService.add(any())).thenReturn(saved);
        when(globalUserRoleService.get("role-1")).thenReturn(saved);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null, PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)).thenReturn(true);
            session.when(SessionUtils::getUserId).thenReturn("admin");
            service.saveRole(request);
        }

        ArgumentCaptor<PermissionSettingUpdateRequest> captor = ArgumentCaptor.forClass(PermissionSettingUpdateRequest.class);
        verify(globalUserRoleService).updatePermissionSetting(captor.capture());
        assertTrue(captor.getValue().getPermissions().stream()
                .anyMatch(item -> item.getId().equals("SYSTEM_USER:READ") && Boolean.TRUE.equals(item.getEnable())));
    }

    @Test
    void saveRoleRejectsPermissionOutsideRoleType() {
        RoleSaveRequest request = new RoleSaveRequest();
        request.setName("成员");
        request.setType("PROJECT");
        request.setEnabled(true);
        request.setUiPermissions(List.of());
        request.setPermissions(List.of(
                new PermissionSettingUpdateRequest.PermissionUpdateRequest("SYSTEM_USER:READ", true)));
        when(globalUserRoleService.getPermissionDefinitionForControl("PROJECT")).thenReturn(List.of());
        when(permissionUiService.getResourceTree("PROJECT")).thenReturn(List.of());

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null, PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)).thenReturn(true);
            assertThrows(RuntimeException.class, () -> service.saveRole(request));
        }
    }

    @Test
    void saveRoleEnablesAssociatedApiPermissionForOperableUiResource() {
        RoleSaveRequest request = new RoleSaveRequest();
        request.setName("操作员");
        request.setType("SYSTEM");
        request.setEnabled(true);
        request.setPermissions(List.of(
                new PermissionSettingUpdateRequest.PermissionUpdateRequest("SYSTEM_USER:READ", false)));
        RoleUiPermissionDTO uiPermission = new RoleUiPermissionDTO();
        uiPermission.setResourceCode("SYSTEM_USER_PAGE");
        uiPermission.setVisible(true);
        uiPermission.setOperable(true);
        request.setUiPermissions(List.of(uiPermission));

        Permission read = new Permission();
        read.setId("SYSTEM_USER:READ");
        PermissionDefinitionItem definition = new PermissionDefinitionItem();
        definition.setPermissions(List.of(read));
        PermissionResourceDTO resource = new PermissionResourceDTO();
        resource.setCode("SYSTEM_USER_PAGE");
        resource.setPermissionId("SYSTEM_USER:READ");
        UserRole saved = new UserRole();
        saved.setId("role-ui");
        saved.setType("SYSTEM");
        saved.setEnabled(true);

        when(globalUserRoleService.getPermissionDefinitionForControl("SYSTEM")).thenReturn(List.of(definition));
        when(permissionUiService.getAllResourceTree()).thenReturn(List.of(resource));
        when(globalUserRoleService.add(any())).thenReturn(saved);
        when(globalUserRoleService.get("role-ui")).thenReturn(saved);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, null, PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD)).thenReturn(true);
            session.when(SessionUtils::getUserId).thenReturn("admin");
            service.saveRole(request);
        }

        ArgumentCaptor<PermissionSettingUpdateRequest> captor = ArgumentCaptor.forClass(PermissionSettingUpdateRequest.class);
        verify(globalUserRoleService).updatePermissionSetting(captor.capture());
        assertTrue(captor.getValue().getPermissions().stream()
                .anyMatch(item -> item.getId().equals("SYSTEM_USER:READ") && Boolean.TRUE.equals(item.getEnable())));
    }

    @Test
    void deleteImpactReturnsBothCounts() {
        UserRole role = new UserRole();
        role.setId("role-1");
        role.setScopeId("global");
        when(globalUserRoleService.getWithCheck("role-1")).thenReturn(role);
        when(permissionControlMapper.countRoleMembers("role-1")).thenReturn(12L);
        when(permissionControlMapper.countUsersWithoutOtherBusinessRole("role-1")).thenReturn(3L);

        RoleDeleteImpactDTO impact = service.getRoleDeleteImpact("role-1");

        assertEquals(12L, impact.getMemberCount());
        assertEquals(3L, impact.getUsersWithoutOtherBusinessRoleCount());
    }

    @Test
    void memberInitializationRecordsFailureWhenPermissionDefinitionIsEmpty() {
        UserRole member = new UserRole();
        member.setId("permission_member");
        when(globalUserRoleService.get("permission_member")).thenReturn(member);
        when(permissionControlMapper.countMemberInitialization("permission_member", "V3.7.2_50_CACHE_V1")).thenReturn(0L);
        when(globalUserRoleService.getPermissionDefinitionForControl("SYSTEM")).thenReturn(List.of());

        assertThrows(IllegalStateException.class, service::synchronizeMemberRolePermissions);

        verify(permissionMigrationAuditService).recordFailure(
                org.mockito.ArgumentMatchers.eq("V3.7.2_50_CACHE_V1"),
                org.mockito.ArgumentMatchers.eq("permission_member"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("CACHE_PERMISSION_INITIALIZATION"),
                any(Exception.class));
    }

    @Test
    void listRoleMembersValidatesGlobalSystemRoleBeforeQuery() {
        UserRole role = new UserRole();
        role.setId("role-1");
        role.setType("SYSTEM");
        role.setScopeId("global");
        GlobalUserRoleRelationQueryRequest request = new GlobalUserRoleRelationQueryRequest();
        request.setRoleId("role-1");
        UserRoleRelationUserDTO member = new UserRoleRelationUserDTO();
        member.setUserId("user-1");
        when(globalUserRoleService.getWithCheck("role-1")).thenReturn(role);
        when(extUserRoleRelationMapper.selectRoleMembers("role-1", "system", null)).thenReturn(List.of(member));

        Pager<List<UserRoleRelationUserDTO>> result = service.listRoleMembers(request);

        assertEquals(1, result.getList().size());
        verify(globalUserRoleService).checkGlobalUserRole(role);
        verify(globalUserRoleService).checkSystemUserGroup(role);
    }

    @Test
    void addRoleMembersRejectsProtectedAdminRoleBeforeMutation() {
        UserRole admin = new UserRole();
        admin.setId("admin");
        admin.setType("SYSTEM");
        admin.setScopeId("global");
        RoleMemberUpdateRequest request = new RoleMemberUpdateRequest();
        request.setRoleId("admin");
        request.setUserIds(List.of("user-1"));
        when(globalUserRoleService.getWithCheck("admin")).thenReturn(admin);
        doThrow(new RuntimeException("管理员角色不可修改"))
                .when(globalUserRoleService).checkAdminUserRole(admin);

        assertThrows(RuntimeException.class, () -> service.addRoleMembers(request));

        verify(globalUserRoleRelationService, never()).add(any());
    }

    @Test
    void listProjectRoleMembersRequiresReadPermissionOnRequestedProjectScope() {
        UserRole role = new UserRole();
        role.setId("project-role");
        role.setType("PROJECT");
        role.setScopeId("global");
        Project project = new Project();
        project.setId("project-1");
        project.setDeleted(false);
        project.setEnable(true);
        GlobalUserRoleRelationQueryRequest request = new GlobalUserRoleRelationQueryRequest();
        request.setRoleId("project-role");
        request.setSourceId("project-1");
        when(globalUserRoleService.getWithCheck("project-role")).thenReturn(role);
        when(projectMapper.selectByPrimaryKey("project-1")).thenReturn(project);

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class)) {
            session.when(() -> SessionUtils.hasPermission(null, "project-1", PermissionConstants.PROJECT_GROUP_READ))
                    .thenReturn(false);
            assertThrows(RuntimeException.class, () -> service.listRoleMembers(request));
        }

        verify(extUserRoleRelationMapper, never()).selectRoleMembers(any(), any(), any());
    }
}
