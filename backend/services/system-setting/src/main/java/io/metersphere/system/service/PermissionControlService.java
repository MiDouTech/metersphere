package io.metersphere.system.service;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.constants.UserRoleScope;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.system.domain.RoleAssignmentRule;
import io.metersphere.system.domain.StatusFlowRolePermission;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.control.PermissionControlFlowMatrixDTO;
import io.metersphere.system.dto.permission.control.RoleAssignmentRuleRequest;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.WorkflowRolePermissionRequest;
import io.metersphere.system.dto.StatusItemDTO;
import io.metersphere.system.dto.request.GlobalUserRoleRelationQueryRequest;
import io.metersphere.system.dto.sdk.request.GlobalUserRoleRelationUpdateRequest;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.system.dto.sdk.request.UserRoleUpdateRequest;
import io.metersphere.system.dto.user.UserRoleRelationUserDTO;
import io.metersphere.system.mapper.PermissionControlMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class PermissionControlService {

    @Resource
    private GlobalUserRoleService globalUserRoleService;
    @Resource
    private GlobalUserRoleRelationService globalUserRoleRelationService;
    @Resource
    private PermissionUiService permissionUiService;
    @Resource
    private PermissionControlMapper permissionControlMapper;
    @Resource
    private BaseStatusFlowSettingService baseStatusFlowSettingService;
    @Resource
    private BaseStatusFlowService baseStatusFlowService;

    public List<UserRole> listRoles() {
        return globalUserRoleService.list();
    }

    public UserRole addRole(UserRoleUpdateRequest request) {
        UserRole userRole = new UserRole();
        BeanUtils.copyBean(userRole, request);
        userRole.setCreateUser(SessionUtils.getUserId());
        return globalUserRoleService.add(userRole);
    }

    public UserRole updateRole(UserRoleUpdateRequest request) {
        UserRole userRole = new UserRole();
        BeanUtils.copyBean(userRole, request);
        return globalUserRoleService.update(userRole);
    }

    public UserRole enableRole(String roleId, Boolean enabled) {
        return globalUserRoleService.enable(roleId, enabled);
    }

    public void deleteRole(String roleId) {
        globalUserRoleService.delete(roleId, SessionUtils.getUserId());
    }

    public List<UserRoleRelationUserDTO> listRoleMembers(GlobalUserRoleRelationQueryRequest request) {
        return globalUserRoleRelationService.list(request);
    }

    public void addRoleMembers(RoleMemberUpdateRequest request) {
        GlobalUserRoleRelationUpdateRequest updateRequest = new GlobalUserRoleRelationUpdateRequest();
        updateRequest.setRoleId(request.getRoleId());
        updateRequest.setUserIds(request.getUserIds());
        updateRequest.setCreateUser(SessionUtils.getUserId());
        globalUserRoleRelationService.add(updateRequest);
    }

    public void removeRoleMembers(RoleMemberUpdateRequest request) {
        List<UserRoleRelation> relations = permissionControlMapper.selectExistingRoleRelations(request.getRoleId(), request.getUserIds());
        for (UserRoleRelation relation : relations) {
            globalUserRoleRelationService.delete(relation.getId());
        }
    }

    public RoleAssignmentRule assignByPosition(RoleAssignmentRuleRequest request) {
        RoleAssignmentRule rule = new RoleAssignmentRule();
        BeanUtils.copyBean(rule, request);
        rule.setId(IDGenerator.nextStr());
        rule.setEnabled(BooleanUtils.isNotFalse(request.getEnabled()));
        rule.setSyncMode(StringUtils.defaultIfBlank(request.getSyncMode(), "MANUAL"));
        rule.setCreateTime(System.currentTimeMillis());
        rule.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.insertRoleAssignmentRule(rule);

        List<User> users = permissionControlMapper.selectUsersByPosition(request.getOrganizationId(), request.getDepartmentId(), request.getPositionId());
        if (CollectionUtils.isNotEmpty(users)) {
            List<String> userIds = users.stream().map(User::getId).toList();
            Set<String> existedUserIds = permissionControlMapper.selectExistingRoleRelations(request.getRoleId(), userIds)
                    .stream()
                    .map(UserRoleRelation::getUserId)
                    .collect(Collectors.toSet());
            List<String> addUserIds = userIds.stream().filter(userId -> !existedUserIds.contains(userId)).toList();
            if (CollectionUtils.isNotEmpty(addUserIds)) {
                RoleMemberUpdateRequest memberRequest = new RoleMemberUpdateRequest();
                memberRequest.setRoleId(request.getRoleId());
                memberRequest.setUserIds(addUserIds);
                addRoleMembers(memberRequest);
            }
        }
        return rule;
    }

    public List<RoleAssignmentRule> listAssignmentRules(String roleId) {
        return permissionControlMapper.selectRoleAssignmentRules(roleId);
    }

    public List<PermissionResourceDTO> getResourceTree(String scopeType) {
        return permissionUiService.getResourceTree(scopeType);
    }

    public void saveRolePermission(PermissionSettingUpdateRequest request) {
        globalUserRoleService.updatePermissionSetting(request);
    }

    public List<WorkflowDefinition> listFlows(String scene, String scopeType, String scopeId) {
        return permissionControlMapper.selectWorkflowDefinitions(StringUtils.defaultIfBlank(scene, TemplateScene.BUG.name()),
                StringUtils.defaultIfBlank(scopeType, UserRoleType.SYSTEM.name()), StringUtils.defaultIfBlank(scopeId, UserRoleScope.SYSTEM));
    }

    public WorkflowDefinition addFlow(WorkflowDefinition request) {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        BeanUtils.copyBean(workflowDefinition, request);
        workflowDefinition.setId(StringUtils.defaultIfBlank(workflowDefinition.getId(), IDGenerator.nextStr()));
        workflowDefinition.setScene(StringUtils.defaultIfBlank(workflowDefinition.getScene(), TemplateScene.BUG.name()));
        workflowDefinition.setScopeType(StringUtils.defaultIfBlank(workflowDefinition.getScopeType(), UserRoleType.SYSTEM.name()));
        workflowDefinition.setScopeId(StringUtils.defaultIfBlank(workflowDefinition.getScopeId(), UserRoleScope.SYSTEM));
        workflowDefinition.setEnabled(BooleanUtils.isNotFalse(workflowDefinition.getEnabled()));
        workflowDefinition.setDefaultFlow(BooleanUtils.isTrue(workflowDefinition.getDefaultFlow()));
        workflowDefinition.setCreateTime(System.currentTimeMillis());
        workflowDefinition.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.insertWorkflowDefinition(workflowDefinition);
        copyFlowConfig(request.getCopyFromFlowId(), workflowDefinition.getId());
        return workflowDefinition;
    }

    private void copyFlowConfig(String copyFromFlowId, String targetFlowId) {
        if (StringUtils.isAnyBlank(copyFromFlowId, targetFlowId)) {
            return;
        }
        List<WorkflowRole> sourceRoles = permissionControlMapper.selectWorkflowRoles(copyFromFlowId);
        if (CollectionUtils.isEmpty(sourceRoles)) {
            return;
        }
        Map<String, String> roleIdMap = sourceRoles.stream().collect(Collectors.toMap(WorkflowRole::getId, role -> IDGenerator.nextStr()));
        for (WorkflowRole sourceRole : sourceRoles) {
            WorkflowRole targetRole = new WorkflowRole();
            BeanUtils.copyBean(targetRole, sourceRole);
            targetRole.setId(roleIdMap.get(sourceRole.getId()));
            targetRole.setFlowId(targetFlowId);
            targetRole.setCreateTime(System.currentTimeMillis());
            targetRole.setUpdateTime(System.currentTimeMillis());
            permissionControlMapper.insertWorkflowRole(targetRole);
        }
        List<StatusFlowRolePermission> sourcePermissions = permissionControlMapper.selectStatusFlowRolePermissions(copyFromFlowId);
        for (StatusFlowRolePermission sourcePermission : sourcePermissions) {
            String targetWorkflowRoleId = roleIdMap.get(sourcePermission.getWorkflowRoleId());
            if (StringUtils.isBlank(targetWorkflowRoleId)) {
                continue;
            }
            StatusFlowRolePermission targetPermission = new StatusFlowRolePermission();
            BeanUtils.copyBean(targetPermission, sourcePermission);
            targetPermission.setId(IDGenerator.nextStr());
            targetPermission.setFlowId(targetFlowId);
            targetPermission.setWorkflowRoleId(targetWorkflowRoleId);
            targetPermission.setCreateTime(System.currentTimeMillis());
            targetPermission.setUpdateTime(System.currentTimeMillis());
            permissionControlMapper.insertStatusFlowRolePermission(targetPermission);
        }
    }

    public WorkflowDefinition updateFlow(WorkflowDefinition request) {
        if (StringUtils.isBlank(request.getId())) {
            throw new MSException("流程 ID 不能为空");
        }
        request.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.updateWorkflowDefinition(request);
        return permissionControlMapper.selectWorkflowDefinitionById(request.getId());
    }

    public void deleteFlow(String flowId) {
        WorkflowDefinition workflowDefinition = permissionControlMapper.selectWorkflowDefinitionById(flowId);
        if (workflowDefinition == null) {
            return;
        }
        if (BooleanUtils.isTrue(workflowDefinition.getDefaultFlow())) {
            throw new MSException("默认流程不可删除，请先切换默认流程");
        }
        permissionControlMapper.deleteStatusFlowRolePermissionsByFlowId(flowId);
        permissionControlMapper.deleteWorkflowDefinition(flowId);
    }

    public List<SelectOption> listFlowStatus(String scene, String scopeId) {
        return baseStatusFlowSettingService.getAllStatusOption(StringUtils.defaultIfBlank(scopeId, UserRoleScope.SYSTEM),
                StringUtils.defaultIfBlank(scene, TemplateScene.BUG.name()));
    }

    public PermissionControlFlowMatrixDTO flowMatrix(String scene, String scopeId) {
        List<StatusItemDTO> statuses = baseStatusFlowSettingService.getStatusFlowSetting(
                StringUtils.defaultIfBlank(scopeId, UserRoleScope.SYSTEM),
                StringUtils.defaultIfBlank(scene, TemplateScene.BUG.name()));
        List<String> statusIds = statuses.stream().map(StatusItemDTO::getId).toList();
        PermissionControlFlowMatrixDTO matrix = new PermissionControlFlowMatrixDTO();
        matrix.setStatuses(statuses);
        matrix.setTransitions(baseStatusFlowService.getStatusFlows(statusIds).stream()
                .filter(statusFlow -> statusIds.contains(statusFlow.getFromId()) && statusIds.contains(statusFlow.getToId()))
                .toList());
        return matrix;
    }

    public List<WorkflowRole> listFlowRoles(String flowId) {
        return permissionControlMapper.selectWorkflowRoles(flowId);
    }

    public WorkflowRole addFlowRole(WorkflowRole request) {
        WorkflowRole workflowRole = new WorkflowRole();
        BeanUtils.copyBean(workflowRole, request);
        workflowRole.setId(StringUtils.defaultIfBlank(workflowRole.getId(), IDGenerator.nextStr()));
        workflowRole.setEnabled(BooleanUtils.isNotFalse(workflowRole.getEnabled()));
        workflowRole.setCreateTime(System.currentTimeMillis());
        workflowRole.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.insertWorkflowRole(workflowRole);
        return workflowRole;
    }

    public WorkflowRole updateFlowRole(WorkflowRole request) {
        if (StringUtils.isBlank(request.getId())) {
            throw new MSException("流程角色 ID 不能为空");
        }
        request.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.updateWorkflowRole(request);
        return permissionControlMapper.selectWorkflowRoleById(request.getId());
    }

    public void deleteFlowRole(String roleId) {
        permissionControlMapper.deleteWorkflowRole(roleId);
    }

    public List<StatusFlowRolePermission> listFlowRolePermissions(String flowId) {
        return permissionControlMapper.selectStatusFlowRolePermissions(flowId);
    }

    public void saveFlowRolePermissions(WorkflowRolePermissionRequest request) {
        permissionControlMapper.deleteStatusFlowRolePermissionsByFlowId(request.getFlowId());
        if (CollectionUtils.isEmpty(request.getPermissions())) {
            return;
        }
        for (StatusFlowRolePermission permission : request.getPermissions()) {
            permission.setId(StringUtils.defaultIfBlank(permission.getId(), IDGenerator.nextStr()));
            permission.setFlowId(request.getFlowId());
            permission.setVisible(BooleanUtils.isTrue(permission.getOperable()) || BooleanUtils.isTrue(permission.getVisible()));
            permission.setOperable(BooleanUtils.isTrue(permission.getVisible()) && BooleanUtils.isTrue(permission.getOperable()));
            permission.setEnabled(BooleanUtils.isNotFalse(permission.getEnabled()));
            permission.setCreateTime(System.currentTimeMillis());
            permission.setUpdateTime(System.currentTimeMillis());
            permissionControlMapper.insertStatusFlowRolePermission(permission);
        }
    }

    public boolean hasPermissionControlAccess() {
        return SessionUtils.hasPermission(SessionUtils.getCurrentOrganizationId(), SessionUtils.getCurrentProjectId(),
                PermissionConstants.SYSTEM_PERMISSION_CONTROL_READ);
    }

    public void assertBugTransitionOperable(String projectId,
                                            String fromStatusId,
                                            String toStatusId,
                                            String createUser,
                                            String handleUser) {
        if (StringUtils.isAnyBlank(projectId, fromStatusId, toStatusId) || StringUtils.equals(fromStatusId, toStatusId)) {
            return;
        }
        if (isAdminUser()) {
            return;
        }
        List<StatusFlowRolePermission> permissions = permissionControlMapper.selectOperableTransitionPermissions(
                TemplateScene.BUG.name(), UserRoleType.PROJECT.name(), projectId, fromStatusId, toStatusId);
        if (CollectionUtils.isEmpty(permissions)) {
            permissions = permissionControlMapper.selectOperableTransitionPermissions(
                    TemplateScene.BUG.name(), UserRoleType.SYSTEM.name(), UserRoleScope.SYSTEM, fromStatusId, toStatusId);
        }
        if (CollectionUtils.isEmpty(permissions)) {
            // 兼容旧状态流：未配置流程角色授权时，不改变原有流转行为。
            return;
        }
        Map<String, WorkflowRole> workflowRoleMap = permissions.stream()
                .map(StatusFlowRolePermission::getWorkflowRoleId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .map(permissionControlMapper::selectWorkflowRoleById)
                .filter(Objects::nonNull)
                .filter(role -> BooleanUtils.isNotFalse(role.getEnabled()))
                .collect(Collectors.toMap(WorkflowRole::getId, role -> role, (a, b) -> a));
        boolean allowed = permissions.stream()
                .map(permission -> workflowRoleMap.get(permission.getWorkflowRoleId()))
                .filter(Objects::nonNull)
                .anyMatch(role -> matchWorkflowRole(role, projectId, createUser, handleUser));
        if (!allowed) {
            throw new MSException("当前角色无缺陷状态流转权限");
        }
    }

    private boolean isAdminUser() {
        return SessionUtils.getUser() != null
                && CollectionUtils.isNotEmpty(SessionUtils.getUser().getUserRoles())
                && SessionUtils.getUser().getUserRoles().stream()
                .anyMatch(role -> StringUtils.equals(role.getId(), "admin"));
    }

    private boolean matchWorkflowRole(WorkflowRole role, String projectId, String createUser, String handleUser) {
        String currentUserId = SessionUtils.getUserId();
        if (StringUtils.equals(role.getRoleType(), "FIELD_USER")) {
            if (StringUtils.equals(role.getFieldKey(), "create_user")) {
                return StringUtils.equals(currentUserId, createUser);
            }
            if (StringUtils.equals(role.getFieldKey(), "handle_user")) {
                return parseUserIds(handleUser).contains(currentUserId);
            }
            return false;
        }
        if (StringUtils.equals(role.getRoleType(), "SYSTEM_ROLE") && StringUtils.isNotBlank(role.getRoleId())) {
            return SessionUtils.getUser() != null
                    && CollectionUtils.isNotEmpty(SessionUtils.getUser().getUserRoleRelations())
                    && SessionUtils.getUser().getUserRoleRelations().stream()
                    .anyMatch(relation -> StringUtils.equals(relation.getRoleId(), role.getRoleId())
                            && (StringUtils.equals(relation.getSourceId(), projectId)
                            || StringUtils.equals(relation.getSourceId(), UserRoleScope.SYSTEM)));
        }
        return false;
    }

    private Set<String> parseUserIds(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Set.of();
        }
        String normalized = raw.trim();
        if (StringUtils.startsWith(normalized, "[") && StringUtils.endsWith(normalized, "]")) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .replace("\"", StringUtils.EMPTY)
                    .replace("'", StringUtils.EMPTY);
        }
        return List.of(normalized.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }
}
