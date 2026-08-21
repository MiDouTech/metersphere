package io.metersphere.system.service;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.domain.StatusFlowRolePermission;
import io.metersphere.system.dto.permission.Permission;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.RoleUiPermissionDTO;
import io.metersphere.system.dto.permission.control.RoleDeleteImpactDTO;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.RoleSaveRequest;
import io.metersphere.system.dto.permission.control.WorkflowMigrationPreviewDTO;
import io.metersphere.system.dto.permission.control.WorkflowMigrationRequest;
import io.metersphere.system.dto.permission.control.WorkflowRolePermissionRequest;
import io.metersphere.system.dto.request.GlobalUserRoleRelationQueryRequest;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.system.dto.user.UserRoleRelationUserDTO;
import io.metersphere.system.mapper.PermissionControlMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.mapper.ExtUserRoleRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @Mock
    private JdbcTemplate jdbcTemplate;

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
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
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

    @Test
    void positionRuleMatchesWecomPositionByCaseInsensitiveKeyword() {
        assertTrue(PermissionControlService.matchesPositionRule("高级QA工程师", "测试|质量|QA"));
        assertTrue(PermissionControlService.matchesPositionRule("Product Manager", "产品|product"));
        assertFalse(PermissionControlService.matchesPositionRule("研发工程师", "测试|质量|QA"));
    }

    @Test
    void wildcardPositionRuleRequiresANonBlankSyncedPosition() {
        assertTrue(PermissionControlService.matchesPositionRule("其他职位", "*"));
        assertFalse(PermissionControlService.matchesPositionRule("", "*"));
        assertFalse(PermissionControlService.matchesPositionRule(null, "*"));
    }

    @Test
    void archiveRejectsDraftWorkflow() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-draft");
        flow.setLifecycle("DRAFT");
        flow.setActiveForNew(false);
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-draft")).thenReturn(flow);

        assertThrows(RuntimeException.class, () -> service.archiveWorkflow("flow-draft"));
    }

    @Test
    void activateOnlySwitchesActiveFlagsAndDoesNotArchivePreviousWorkflow() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-new");
        flow.setLifecycle("PUBLISHED");
        flow.setEnabled(true);
        flow.setActiveForNew(false);
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-new")).thenReturn(flow);
        when(jdbcTemplate.queryForList(any(String.class), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("flow-old", "flow-new"));
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("active_for_new=b'0'"), any(Long.class)))
                .thenReturn(1);
        when(jdbcTemplate.update(org.mockito.ArgumentMatchers.contains("active_for_new=b'1'"), any(Long.class),
                org.mockito.ArgumentMatchers.eq("flow-new"))).thenReturn(1);

        service.activateWorkflow("flow-new");

        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.contains("ARCHIVED"),
                org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void syncWecomPositionsAllowsPublishedWorkflow() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-published");
        flow.setLifecycle("PUBLISHED");
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-published")).thenReturn(flow);
        when(permissionControlMapper.selectWorkflowRoles("flow-published")).thenReturn(List.of());
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("LOWER(TRIM(position))")))
                .thenReturn(List.of());

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class);
             MockedStatic<IDGenerator> ids = mockStatic(IDGenerator.class)) {
            session.when(SessionUtils::getUserId).thenReturn("admin");
            ids.when(IDGenerator::nextStr).thenReturn("sync-batch-1");
            Map<String, Object> result = service.syncWecomPositions("flow-published");
            assertEquals(0, result.get("total"));
            assertEquals("sync-batch-1", result.get("batchId"));
        }
    }

    @Test
    void syncWecomPositionsRejectsArchivedWorkflow() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-archived");
        flow.setLifecycle("ARCHIVED");
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-archived")).thenReturn(flow);

        assertThrows(RuntimeException.class, () -> service.syncWecomPositions("flow-archived"));
        verify(permissionControlMapper, never()).insertWorkflowRole(any());
    }

    @Test
    void addFlowRoleAllowsPublishedWorkflowAndGrantsEveryTransitionByDefault() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-published");
        flow.setLifecycle("PUBLISHED");
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-published")).thenReturn(flow);
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.contains("workflow_position_sync_log"),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("flow-published"))).thenReturn(0);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("SELECT id FROM status_flow"),
                org.mockito.ArgumentMatchers.eq(String.class),
                org.mockito.ArgumentMatchers.eq("flow-published"))).thenReturn(List.of("transition-1"));
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.contains("status_flow_role_permission"),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("flow-published"),
                org.mockito.ArgumentMatchers.eq("transition-1"),
                org.mockito.ArgumentMatchers.eq("role-1"))).thenReturn(0);

        WorkflowRole request = new WorkflowRole();
        request.setFlowId("flow-published");
        request.setCode("HANDLER");
        request.setName("当前处理人");
        request.setRoleType("FIELD_USER");
        request.setFieldKey("handle_user");

        try (MockedStatic<IDGenerator> ids = mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("role-1", "permission-1");
            service.addFlowRole(request);
        }

        ArgumentCaptor<StatusFlowRolePermission> captor = ArgumentCaptor.forClass(StatusFlowRolePermission.class);
        verify(permissionControlMapper).insertStatusFlowRolePermission(captor.capture());
        assertEquals("transition-1", captor.getValue().getStatusFlowId());
        assertEquals("role-1", captor.getValue().getWorkflowRoleId());
        assertTrue(captor.getValue().getVisible());
        assertTrue(captor.getValue().getOperable());
    }

    @Test
    void saveFlowRolePermissionsAllowsPublishedWorkflow() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-published");
        flow.setLifecycle("PUBLISHED");
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-published")).thenReturn(flow);
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.contains("workflow_position_sync_log"),
                org.mockito.ArgumentMatchers.eq(Integer.class),
                org.mockito.ArgumentMatchers.eq("flow-published"))).thenReturn(1);
        WorkflowRolePermissionRequest request = new WorkflowRolePermissionRequest();
        request.setFlowId("flow-published");
        request.setPermissions(List.of());

        service.saveFlowRolePermissions(request);

        verify(permissionControlMapper).deleteStatusFlowRolePermissionsByFlowId("flow-published");
    }

    @Test
    void disableActivePublishedWorkflowAlsoStopsItFromReceivingNewBugs() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-active");
        flow.setLifecycle("PUBLISHED");
        flow.setEnabled(true);
        flow.setActiveForNew(true);
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-active")).thenReturn(flow);

        service.enableWorkflow("flow-active", false);

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("enabled=b'0', active_for_new=b'0'"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq("flow-active"));
    }

    @Test
    void archivedWorkflowRemainsReadOnly() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-archived");
        flow.setLifecycle("ARCHIVED");
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-archived")).thenReturn(flow);

        WorkflowRole request = new WorkflowRole();
        request.setFlowId("flow-archived");
        request.setRoleType("FIELD_USER");
        request.setFieldKey("handle_user");

        assertThrows(RuntimeException.class, () -> service.addFlowRole(request));
        assertThrows(RuntimeException.class, () -> service.enableWorkflow("flow-archived", true));
        verify(permissionControlMapper, never()).insertWorkflowRole(any());
    }

    @Test
    void deleteUnpublishedWorkflowRemovesPositionSyncHistory() {
        WorkflowDefinition flow = new WorkflowDefinition();
        flow.setId("flow-draft-delete");
        flow.setLifecycle("DRAFT");
        flow.setActiveForNew(false);
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-draft-delete")).thenReturn(flow);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("FROM bug WHERE workflow_id"),
                org.mockito.ArgumentMatchers.eq(Long.class), org.mockito.ArgumentMatchers.eq("flow-draft-delete")))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("bug_status_transition_history"),
                org.mockito.ArgumentMatchers.eq(Long.class), org.mockito.ArgumentMatchers.eq("flow-draft-delete")))
                .thenReturn(0L);

        service.deleteFlow("flow-draft-delete");

        verify(jdbcTemplate).update("DELETE FROM workflow_position_sync_log WHERE flow_id = ?", "flow-draft-delete");
        verify(permissionControlMapper).deleteWorkflowDefinition("flow-draft-delete");
    }

    @Test
    void migrateWorkflowRejectsEmptyManualSelectionBeforeQueryingCandidates() {
        WorkflowMigrationRequest request = new WorkflowMigrationRequest();
        request.setTargetFlowId("flow-active");
        request.setBugIds(List.of());

        assertThrows(RuntimeException.class, () -> service.migrateWorkflow(request));

        verify(permissionControlMapper, never()).selectWorkflowDefinitionById(any());
    }

    @Test
    void migrationPreviewReturnsRecognizableSourceStatusMetadataWithoutLosingInternalIds() {
        WorkflowDefinition target = new WorkflowDefinition();
        target.setId("flow-active");
        target.setVersion(3);
        target.setLifecycle("PUBLISHED");
        target.setActiveForNew(true);
        when(permissionControlMapper.selectWorkflowDefinitionById("flow-active")).thenReturn(target);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.startsWith("SELECT id, status_code, name FROM status_item"),
                org.mockito.ArgumentMatchers.eq("flow-active")))
                .thenReturn(List.of(Map.of("id", "target-created", "status_code", "CREATED", "name", "创建")));

        Map<String, Object> processing = new HashMap<>();
        processing.put("status_id", "source-processing");
        processing.put("status_code", "PROCESSING");
        processing.put("status_name", "处理中");
        processing.put("bug_count", 7L);
        processing.put("project_count", 2L);
        Map<String, Object> missing = new HashMap<>();
        missing.put("status_id", "source-missing");
        missing.put("status_code", null);
        missing.put("status_name", null);
        missing.put("bug_count", 3L);
        missing.put("project_count", 1L);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT b.status status_id")))
                .thenReturn(List.of(processing, missing));
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.startsWith("SELECT COUNT(*) FROM bug"),
                org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenReturn(10L);

        WorkflowMigrationPreviewDTO preview = service.previewWorkflowMigration("flow-active");

        assertEquals(2, preview.getSourceStatuses().size());
        assertEquals("处理中", preview.getSourceStatuses().getFirst().getName());
        assertEquals(7L, preview.getSourceStatuses().getFirst().getBugCount());
        assertEquals(2L, preview.getSourceStatuses().getFirst().getProjectCount());
        assertFalse(preview.getSourceStatuses().getFirst().isAutoMapped());
        assertEquals("未知历史状态", preview.getSourceStatuses().get(1).getName());
        assertTrue(preview.getSourceStatuses().get(1).isNameMissing());
        assertTrue(preview.getUnresolvedStatusIds().containsAll(List.of("source-processing", "source-missing")));
    }
}
