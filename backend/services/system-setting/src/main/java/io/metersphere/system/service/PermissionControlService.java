package io.metersphere.system.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.constants.UserRoleScope;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.domain.RoleAssignmentRule;
import io.metersphere.system.domain.StatusFlowRolePermission;
import io.metersphere.system.domain.User;
import io.metersphere.system.domain.Organization;
import io.metersphere.system.domain.OrganizationExample;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.control.PermissionControlFlowMatrixDTO;
import io.metersphere.system.dto.permission.control.RoleAssignmentRuleRequest;
import io.metersphere.system.dto.permission.control.RoleDeleteImpactDTO;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.RoleSaveRequest;
import io.metersphere.system.dto.permission.control.WorkflowRolePermissionRequest;
import io.metersphere.system.dto.permission.control.WorkflowDesignerDTO;
import io.metersphere.system.dto.permission.control.WorkflowValidationDTO;
import io.metersphere.system.dto.permission.control.WorkflowMigrationPreviewDTO;
import io.metersphere.system.dto.permission.control.WorkflowMigrationRequest;
import io.metersphere.system.dto.permission.control.WorkflowMigrationCandidateRequest;
import io.metersphere.system.dto.StatusItemDTO;
import io.metersphere.system.dto.request.GlobalUserRoleRelationQueryRequest;
import io.metersphere.system.dto.sdk.request.GlobalUserRoleRelationUpdateRequest;
import io.metersphere.system.dto.sdk.request.PermissionSettingUpdateRequest;
import io.metersphere.system.dto.sdk.request.UserRoleUpdateRequest;
import io.metersphere.system.dto.user.UserExcludeOptionDTO;
import io.metersphere.system.dto.user.UserRoleRelationUserDTO;
import io.metersphere.system.mapper.PermissionControlMapper;
import io.metersphere.system.mapper.ExtUserRoleRelationMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.domain.ProjectExample;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.system.mapper.OrganizationMapper;
import io.metersphere.system.dto.sdk.OptionDTO;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class PermissionControlService {

    private static final Set<String> REQUIRED_ROLE_IDS = Set.of(
            InternalUserRole.ADMIN.getValue(), InternalUserRole.MEMBER.getValue(),
            InternalUserRole.ORG_ADMIN.getValue(), InternalUserRole.ORG_MEMBER.getValue(),
            InternalUserRole.PROJECT_ADMIN.getValue(), InternalUserRole.PROJECT_MEMBER.getValue());

    @Resource
    private GlobalUserRoleService globalUserRoleService;
    @Resource
    private GlobalUserRoleRelationService globalUserRoleRelationService;
    @Resource
    private PermissionUiService permissionUiService;
    @Resource
    private PermissionControlMapper permissionControlMapper;
    @Resource
    private ExtUserRoleRelationMapper extUserRoleRelationMapper;
    @Resource
    private PermissionSessionRefreshService permissionSessionRefreshService;
    @Resource
    private SimpleUserService simpleUserService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private OrganizationMapper organizationMapper;
    @Resource
    private BaseStatusFlowSettingService baseStatusFlowSettingService;
    @Resource
    private BaseStatusFlowService baseStatusFlowService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private WorkflowMigrationService workflowMigrationService;

    public List<UserRole> listRoles() {
        return globalUserRoleService.list().stream()
                .filter(role -> REQUIRED_ROLE_IDS.contains(role.getId())
                        || (!BooleanUtils.isTrue(role.getInternal())
                        && !StringUtils.startsWith(role.getDescription(), "[已迁移旧用户组]")))
                .toList();
    }

    public UserRole getRole(String roleId) {
        return globalUserRoleService.getWithCheck(roleId);
    }

    public List<PermissionDefinitionItem> getRolePermission(String roleId) {
        return globalUserRoleService.getPermissionSettingForControl(roleId);
    }

    public List<PermissionDefinitionItem> getPermissionDefinition(String roleType) {
        return globalUserRoleService.getPermissionDefinitionForControl(roleType);
    }

    public UserRole addRole(UserRoleUpdateRequest request) {
        return saveRoleMetadata(request);
    }

    public UserRole updateRole(UserRoleUpdateRequest request) {
        return saveRoleMetadata(request);
    }

    /**
     * Compatibility entry for historical split role writes. All metadata writes are converted to the
     * atomic role-save command so administrator protection, scope validation, audit and session refresh
     * cannot diverge from the permission-control page.
     */
    public UserRole saveRoleMetadata(UserRoleUpdateRequest request) {
        RoleSaveRequest saveRequest = new RoleSaveRequest();
        saveRequest.setId(request.getId());
        saveRequest.setName(request.getName());
        saveRequest.setDescription(request.getDescription());
        saveRequest.setType(request.getType());
        saveRequest.setEnabled(request.getEnabled());
        if (StringUtils.isBlank(request.getId())) {
            saveRequest.setPermissions(List.of());
        } else {
            saveRequest.setPermissions(flattenPermissionUpdates(getRolePermission(request.getId())));
        }
        return saveRole(saveRequest);
    }

    private List<PermissionSettingUpdateRequest.PermissionUpdateRequest> flattenPermissionUpdates(
            List<PermissionDefinitionItem> definitions) {
        List<PermissionSettingUpdateRequest.PermissionUpdateRequest> result = new ArrayList<>();
        collectPermissionUpdates(definitions, result);
        return result;
    }

    private void collectPermissionUpdates(List<PermissionDefinitionItem> definitions,
                                          List<PermissionSettingUpdateRequest.PermissionUpdateRequest> result) {
        if (CollectionUtils.isEmpty(definitions)) {
            return;
        }
        for (PermissionDefinitionItem definition : definitions) {
            if (CollectionUtils.isNotEmpty(definition.getPermissions())) {
                definition.getPermissions().forEach(permission -> result.add(
                        new PermissionSettingUpdateRequest.PermissionUpdateRequest(
                                permission.getId(), BooleanUtils.isTrue(permission.getEnable()))));
            }
            collectPermissionUpdates(definition.getChildren(), result);
        }
    }

    public UserRole enableRole(String roleId, Boolean enabled) {
        if (REQUIRED_ROLE_IDS.contains(roleId) && BooleanUtils.isFalse(enabled)) {
            throw new MSException("系统必备角色不可停用");
        }
        List<String> affectedUsers = permissionControlMapper.selectRoleMemberUserIds(roleId, null);
        UserRole result = globalUserRoleService.enable(roleId, enabled);
        permissionSessionRefreshService.refreshUsersAfterCommit(affectedUsers);
        return result;
    }

    public void deleteRole(String roleId) {
        if (REQUIRED_ROLE_IDS.contains(roleId)) {
            throw new MSException("系统必备角色不可删除");
        }
        List<String> affectedUsers = permissionControlMapper.selectRoleMemberUserIds(roleId, null);
        globalUserRoleService.delete(roleId, SessionUtils.getUserId());
        permissionSessionRefreshService.refreshUsersAfterCommit(affectedUsers);
    }

    public RoleDeleteImpactDTO getRoleDeleteImpact(String roleId) {
        UserRole role = globalUserRoleService.getWithCheck(roleId);
        globalUserRoleService.checkGlobalUserRole(role);
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            globalUserRoleService.checkSystemUserGroup(role);
        }
        globalUserRoleService.checkAdminUserRole(role);
        return new RoleDeleteImpactDTO(permissionControlMapper.countRoleMembers(roleId),
                permissionControlMapper.countUsersWithoutOtherBusinessRole(roleId));
    }

    public UserRole saveRole(RoleSaveRequest request) {
        String requiredPermission = StringUtils.isBlank(request.getId())
                ? PermissionConstants.SYSTEM_PERMISSION_CONTROL_ADD
                : PermissionConstants.SYSTEM_PERMISSION_CONTROL_UPDATE;
        if (!SessionUtils.hasPermission(null, null, requiredPermission)) {
            throw new MSException("无权保存当前角色设置");
        }
        UserRole originalRole = null;
        String effectiveType = request.getType();
        if (StringUtils.isNotBlank(request.getId())) {
            originalRole = globalUserRoleService.getWithCheck(request.getId());
            globalUserRoleService.checkAdminUserRole(originalRole);
            if (!StringUtils.equals(originalRole.getType(), request.getType())) {
                throw new MSException("角色类型创建后不可修改");
            }
            effectiveType = originalRole.getType();
        }
        Set<String> allowedPermissionIds = collectPermissionIds(
                globalUserRoleService.getPermissionDefinitionForControl(effectiveType));
        UserRole role = new UserRole();
        role.setId(request.getId());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setType(request.getType());
        role.setEnabled(request.getEnabled());
        UserRole saved;
        if (StringUtils.isBlank(request.getId())) {
            role.setCreateUser(SessionUtils.getUserId());
            saved = globalUserRoleService.add(role);
        } else {
            saved = globalUserRoleService.update(role);
            if (!Objects.equals(originalRole.getEnabled(), request.getEnabled())) {
                saved = globalUserRoleService.enable(saved.getId(), request.getEnabled());
            }
        }
        PermissionSettingUpdateRequest permissionRequest = new PermissionSettingUpdateRequest();
        permissionRequest.setUserRoleId(saved.getId());
        permissionRequest.setPermissions(normalizePermissionDependencies(request.getPermissions(), allowedPermissionIds));
        globalUserRoleService.updatePermissionSetting(permissionRequest);
        permissionSessionRefreshService.refreshUsersAfterCommit(
                permissionControlMapper.selectRoleMemberUserIds(saved.getId(), null));
        return globalUserRoleService.get(saved.getId());
    }

    private List<PermissionSettingUpdateRequest.PermissionUpdateRequest> normalizePermissionDependencies(
            List<PermissionSettingUpdateRequest.PermissionUpdateRequest> permissions,
            Set<String> allowedPermissionIds) {
        List<String> invalidPermissionIds = permissions.stream()
                .map(PermissionSettingUpdateRequest.PermissionUpdateRequest::getId)
                .filter(id -> StringUtils.isBlank(id) || !allowedPermissionIds.contains(id))
                .distinct()
                .toList();
        if (CollectionUtils.isNotEmpty(invalidPermissionIds)) {
            throw new MSException("存在不属于当前角色范围的接口权限：" + String.join(", ", invalidPermissionIds));
        }
        Map<String, PermissionSettingUpdateRequest.PermissionUpdateRequest> normalized = permissions.stream()
                .collect(Collectors.toMap(PermissionSettingUpdateRequest.PermissionUpdateRequest::getId,
                        item -> new PermissionSettingUpdateRequest.PermissionUpdateRequest(item.getId(), BooleanUtils.isTrue(item.getEnable())),
                        (a, b) -> b, java.util.LinkedHashMap::new));
        List<String> enabledWritePermissions = normalized.values().stream()
                .filter(item -> BooleanUtils.isTrue(item.getEnable()))
                .map(PermissionSettingUpdateRequest.PermissionUpdateRequest::getId)
                .filter(id -> StringUtils.contains(id, ":") && !StringUtils.endsWith(id, ":READ"))
                .toList();
        for (String permissionId : enabledWritePermissions) {
            String readPermissionId = StringUtils.substringBefore(permissionId, ":") + ":READ";
            if (allowedPermissionIds.contains(readPermissionId)) {
                normalized.computeIfAbsent(readPermissionId,
                        id -> new PermissionSettingUpdateRequest.PermissionUpdateRequest(id, true)).setEnable(true);
            }
        }
        return new ArrayList<>(normalized.values());
    }

    private Set<String> collectPermissionIds(List<PermissionDefinitionItem> definitions) {
        Set<String> result = new java.util.LinkedHashSet<>();
        if (CollectionUtils.isEmpty(definitions)) {
            return result;
        }
        for (PermissionDefinitionItem item : definitions) {
            if (CollectionUtils.isNotEmpty(item.getPermissions())) {
                item.getPermissions().stream()
                        .map(io.metersphere.system.dto.permission.Permission::getId)
                        .filter(StringUtils::isNotBlank)
                        .forEach(result::add);
            }
            result.addAll(collectPermissionIds(item.getChildren()));
        }
        return result;
    }

    public Pager<List<UserRoleRelationUserDTO>> listRoleMembers(GlobalUserRoleRelationQueryRequest request) {
        UserRole role = validateRoleForMemberManagement(request.getRoleId(), request.getSourceId(), false);
        String sourceId = resolveMemberSourceId(role, request.getSourceId());
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(), true);
        List<UserRoleRelationUserDTO> members = extUserRoleRelationMapper.selectRoleMembers(role.getId(), sourceId, request.getKeyword());
        return PageUtils.setPageInfo(page, members);
    }

    public List<UserExcludeOptionDTO> getRoleMemberOptions(String roleId, String sourceId, String keyword) {
        UserRole role = validateRoleForMemberManagement(roleId, sourceId, true);
        return extUserRoleRelationMapper.selectRoleMemberOptions(roleId, resolveMemberSourceId(role, sourceId), keyword);
    }

    public List<OptionDTO> getRoleMemberScopeOptions(String roleId, String keyword) {
        UserRole role = globalUserRoleService.getWithCheck(roleId);
        globalUserRoleService.checkGlobalUserRole(role);
        String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase();
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            return List.of(new OptionDTO(UserRoleScope.SYSTEM, "系统"));
        }
        if (StringUtils.equals(role.getType(), UserRoleType.ORGANIZATION.name())) {
            OrganizationExample example = new OrganizationExample();
            example.createCriteria().andDeletedEqualTo(false).andEnableEqualTo(true);
            return organizationMapper.selectByExample(example).stream()
                    .filter(item -> StringUtils.isBlank(normalizedKeyword)
                            || StringUtils.containsIgnoreCase(item.getName(), normalizedKeyword)
                            || StringUtils.containsIgnoreCase(item.getId(), normalizedKeyword))
                    .filter(item -> SessionUtils.hasPermission(item.getId(), null, PermissionConstants.ORGANIZATION_USER_ROLE_READ))
                    .limit(100)
                    .map(item -> new OptionDTO(item.getId(), item.getName()))
                    .toList();
        }
        ProjectExample example = new ProjectExample();
        example.createCriteria().andDeletedEqualTo(false).andEnableEqualTo(true);
        return projectMapper.selectByExample(example).stream()
                .filter(item -> StringUtils.isBlank(normalizedKeyword)
                        || StringUtils.containsIgnoreCase(item.getName(), normalizedKeyword)
                        || StringUtils.containsIgnoreCase(item.getId(), normalizedKeyword))
                .filter(item -> SessionUtils.hasPermission(null, item.getId(), PermissionConstants.PROJECT_GROUP_READ))
                .limit(100)
                .map(item -> new OptionDTO(item.getId(), item.getName()))
                .toList();
    }

    public void addRoleMembers(RoleMemberUpdateRequest request) {
        UserRole role = validateRoleForMemberManagement(request.getRoleId(), request.getSourceId(), true);
        String sourceId = resolveMemberSourceId(role, request.getSourceId());
        simpleUserService.checkUserLegality(request.getUserIds());
        if (!StringUtils.equals(sourceId, UserRoleScope.SYSTEM)
                && permissionControlMapper.countUsersInScope(sourceId, request.getUserIds()) != request.getUserIds().size()) {
            throw new MSException("存在不属于目标组织或项目的成员");
        }
        Set<String> existingUsers = permissionControlMapper
                .selectExistingRoleRelations(role.getId(), sourceId, request.getUserIds()).stream()
                .map(UserRoleRelation::getUserId).collect(Collectors.toSet());
        String organizationId = resolveOrganizationId(role, sourceId);
        long now = System.currentTimeMillis();
        List<UserRoleRelation> relations = request.getUserIds().stream()
                .filter(userId -> !existingUsers.contains(userId))
                .map(userId -> {
                    UserRoleRelation relation = new UserRoleRelation();
                    relation.setId(IDGenerator.nextStr());
                    relation.setUserId(userId);
                    relation.setRoleId(role.getId());
                    relation.setSourceId(sourceId);
                    relation.setOrganizationId(organizationId);
                    relation.setCreateTime(now);
                    relation.setCreateUser(SessionUtils.getUserId());
                    return relation;
                }).toList();
        if (CollectionUtils.isNotEmpty(relations)) {
            permissionControlMapper.batchInsertRoleRelations(relations);
        }
        permissionSessionRefreshService.refreshUsersAfterCommit(request.getUserIds());
    }

    public void removeRoleMembers(RoleMemberUpdateRequest request) {
        UserRole role = validateRoleForMemberManagement(request.getRoleId(), request.getSourceId(), true);
        String sourceId = resolveMemberSourceId(role, request.getSourceId());
        List<UserRoleRelation> relations = permissionControlMapper.selectExistingRoleRelations(request.getRoleId(), sourceId, request.getUserIds());
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            for (UserRoleRelation relation : relations) {
                globalUserRoleRelationService.delete(relation.getId());
            }
        } else {
            permissionControlMapper.deleteRoleRelations(request.getRoleId(), sourceId, request.getUserIds());
        }
        String fallbackRoleId = fallbackMemberRoleId(role.getId());
        if (fallbackRoleId != null) {
            RoleMemberUpdateRequest memberRequest = new RoleMemberUpdateRequest();
            memberRequest.setRoleId(fallbackRoleId);
            memberRequest.setSourceId(sourceId);
            memberRequest.setUserIds(request.getUserIds());
            addRoleMembers(memberRequest);
        }
        permissionSessionRefreshService.refreshUsersAfterCommit(request.getUserIds());
    }

    private UserRole validateRoleForMemberManagement(String roleId, String sourceId, boolean mutable) {
        UserRole role = globalUserRoleService.getWithCheck(roleId);
        globalUserRoleService.checkGlobalUserRole(role);
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            globalUserRoleService.checkSystemUserGroup(role);
        }
        assertTargetScopeMemberPermission(role, resolveMemberSourceId(role, sourceId), mutable);
        return role;
    }

    private String fallbackMemberRoleId(String roleId) {
        if (StringUtils.equals(roleId, InternalUserRole.ADMIN.getValue())) {
            return InternalUserRole.MEMBER.getValue();
        }
        if (StringUtils.equals(roleId, InternalUserRole.ORG_ADMIN.getValue())) {
            return InternalUserRole.ORG_MEMBER.getValue();
        }
        if (StringUtils.equals(roleId, InternalUserRole.PROJECT_ADMIN.getValue())) {
            return InternalUserRole.PROJECT_MEMBER.getValue();
        }
        return null;
    }

    private String resolveMemberSourceId(UserRole role, String requestedSourceId) {
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            return UserRoleScope.SYSTEM;
        }
        if (StringUtils.isBlank(requestedSourceId) || StringUtils.equals(requestedSourceId, UserRoleScope.SYSTEM)) {
            throw new MSException("组织或项目角色必须指定成员关系作用域");
        }
        return requestedSourceId;
    }

    private void assertTargetScopeMemberPermission(UserRole role, String sourceId, boolean mutable) {
        String organizationPermission = mutable
                ? PermissionConstants.ORGANIZATION_USER_ROLE_READ_UPDATE
                : PermissionConstants.ORGANIZATION_USER_ROLE_READ;
        String projectPermission = mutable
                ? PermissionConstants.PROJECT_GROUP_UPDATE
                : PermissionConstants.PROJECT_GROUP_READ;
        if (StringUtils.equals(role.getType(), UserRoleType.ORGANIZATION.name())) {
            Organization organization = organizationMapper.selectByPrimaryKey(sourceId);
            if (organization == null || BooleanUtils.isTrue(organization.getDeleted()) || BooleanUtils.isFalse(organization.getEnable())) {
                throw new MSException("目标组织不存在或已停用");
            }
            if (!SessionUtils.hasPermission(sourceId, null, organizationPermission)) {
                throw new MSException(mutable ? "无权维护目标组织的角色成员" : "无权查看目标组织的角色成员");
            }
        }
        if (StringUtils.equals(role.getType(), UserRoleType.PROJECT.name())) {
            Project project = projectMapper.selectByPrimaryKey(sourceId);
            if (project == null || BooleanUtils.isTrue(project.getDeleted()) || BooleanUtils.isFalse(project.getEnable())) {
                throw new MSException("目标项目不存在或已停用");
            }
            if (!SessionUtils.hasPermission(null, sourceId, projectPermission)) {
                throw new MSException(mutable ? "无权维护目标项目的角色成员" : "无权查看目标项目的角色成员");
            }
        }
    }

    private String resolveOrganizationId(UserRole role, String sourceId) {
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            return UserRoleScope.SYSTEM;
        }
        if (StringUtils.equals(role.getType(), UserRoleType.ORGANIZATION.name())) {
            return sourceId;
        }
        Project project = projectMapper.selectByPrimaryKey(sourceId);
        if (project == null || BooleanUtils.isTrue(project.getDeleted())) {
            throw new MSException("目标项目不存在");
        }
        return project.getOrganizationId();
    }

    public RoleAssignmentRule assignByPosition(RoleAssignmentRuleRequest request) {
        UserRole assignmentRole = globalUserRoleService.getWithCheck(request.getRoleId());
        String assignmentSourceId;
        if (StringUtils.equals(assignmentRole.getType(), UserRoleType.SYSTEM.name())) {
            assignmentSourceId = UserRoleScope.SYSTEM;
        } else if (StringUtils.equals(assignmentRole.getType(), UserRoleType.ORGANIZATION.name())) {
            assignmentSourceId = request.getOrganizationId();
        } else {
            throw new MSException("按组织岗位分配暂不支持项目范围角色");
        }
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
            Set<String> existedUserIds = permissionControlMapper.selectExistingRoleRelations(request.getRoleId(), assignmentSourceId, userIds)
                    .stream()
                    .map(UserRoleRelation::getUserId)
                    .collect(Collectors.toSet());
            List<String> addUserIds = userIds.stream().filter(userId -> !existedUserIds.contains(userId)).toList();
            if (CollectionUtils.isNotEmpty(addUserIds)) {
                RoleMemberUpdateRequest memberRequest = new RoleMemberUpdateRequest();
                memberRequest.setRoleId(request.getRoleId());
                memberRequest.setSourceId(assignmentSourceId);
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
        if (StringUtils.equals(scopeType, UserRoleType.SYSTEM.name())) {
            return permissionUiService.getAllResourceTree();
        }
        return permissionUiService.getResourceTree(scopeType);
    }

    public void saveRolePermission(PermissionSettingUpdateRequest request) {
        UserRole role = globalUserRoleService.getWithCheck(request.getUserRoleId());
        Set<String> allowedPermissionIds = collectPermissionIds(
                globalUserRoleService.getPermissionDefinitionForControl(role.getType()));
        request.setPermissions(normalizePermissionDependencies(request.getPermissions(), allowedPermissionIds));
        globalUserRoleService.updatePermissionSetting(request);
        permissionSessionRefreshService.refreshUsersAfterCommit(
                permissionControlMapper.selectRoleMemberUserIds(role.getId(), null));
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
        workflowDefinition.setDefaultFlow(false);
        workflowDefinition.setActiveForNew(false);
        workflowDefinition.setVersion(request.getVersion() == null ? 1 : request.getVersion());
        workflowDefinition.setLifecycle("DRAFT");
        workflowDefinition.setSourceFlowId(request.getCopyFromFlowId());
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
        Map<String, String> statusIdMap = new LinkedHashMap<>();
        jdbcTemplate.queryForList("SELECT id, status_code, name, remark, initial_status, terminal_status, enabled, pos "
                        + "FROM status_item WHERE flow_id = ? ORDER BY pos", copyFromFlowId)
                .forEach(row -> {
                    String sourceId = String.valueOf(row.get("id"));
                    String targetId = IDGenerator.nextStr();
                    statusIdMap.put(sourceId, targetId);
                    jdbcTemplate.update("INSERT INTO status_item "
                                    + "(id, flow_id, status_code, name, scene, remark, internal, scope_type, ref_id, scope_id, pos, "
                                    + "initial_status, terminal_status, enabled) VALUES (?, ?, ?, ?, 'BUG', ?, b'0', 'SYSTEM', NULL, 'system', ?, ?, ?, ?)",
                            targetId, targetFlowId, row.get("status_code"), row.get("name"), row.get("remark"), row.get("pos"),
                            row.get("initial_status"), row.get("terminal_status"), row.get("enabled"));
                });
        Map<String, String> transitionIdMap = new LinkedHashMap<>();
        jdbcTemplate.queryForList("SELECT id, from_id, to_id, enabled FROM status_flow WHERE flow_id = ?", copyFromFlowId)
                .forEach(row -> {
                    String fromId = statusIdMap.get(String.valueOf(row.get("from_id")));
                    String toId = statusIdMap.get(String.valueOf(row.get("to_id")));
                    if (StringUtils.isAnyBlank(fromId, toId)) {
                        return;
                    }
                    String targetId = IDGenerator.nextStr();
                    transitionIdMap.put(String.valueOf(row.get("id")), targetId);
                    jdbcTemplate.update("INSERT INTO status_flow (id, flow_id, from_id, to_id, enabled) VALUES (?, ?, ?, ?, ?)",
                            targetId, targetFlowId, fromId, toId, row.get("enabled"));
                });
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
            targetPermission.setStatusFlowId(transitionIdMap.get(sourcePermission.getStatusFlowId()));
            if (StringUtils.isBlank(targetPermission.getStatusFlowId())) {
                continue;
            }
            targetPermission.setCreateTime(System.currentTimeMillis());
            targetPermission.setUpdateTime(System.currentTimeMillis());
            permissionControlMapper.insertStatusFlowRolePermission(targetPermission);
        }
    }

    public WorkflowDefinition updateFlow(WorkflowDefinition request) {
        if (StringUtils.isBlank(request.getId())) {
            throw new MSException("流程 ID 不能为空");
        }
        WorkflowDefinition current = permissionControlMapper.selectWorkflowDefinitionById(request.getId());
        if (current == null) {
            throw new MSException("流程不存在");
        }
        if (StringUtils.equals(current.getLifecycle(), "ARCHIVED")) {
            throw new MSException("已归档流程不可修改");
        }
        request.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.updateWorkflowDefinition(request);
        return permissionControlMapper.selectWorkflowDefinitionById(request.getId());
    }

    @Transactional
    public void deleteFlow(String flowId) {
        WorkflowDefinition workflowDefinition = permissionControlMapper.selectWorkflowDefinitionById(flowId);
        if (workflowDefinition == null) {
            return;
        }
        Map<String, Object> impact = getFlowImpact(flowId);
        if (!Boolean.TRUE.equals(impact.get("deletable"))) throw new MSException(String.valueOf(impact.get("reason")));
        jdbcTemplate.update("DELETE FROM workflow_position_sync_log WHERE flow_id = ?", flowId);
        jdbcTemplate.update("DELETE FROM status_flow_role_permission WHERE flow_id = ?", flowId);
        jdbcTemplate.update("DELETE FROM status_flow WHERE flow_id = ?", flowId);
        jdbcTemplate.update("DELETE FROM status_item WHERE flow_id = ?", flowId);
        permissionControlMapper.deleteStatusFlowRolePermissionsByFlowId(flowId);
        permissionControlMapper.deleteWorkflowRolesByFlowId(flowId);
        permissionControlMapper.deleteWorkflowDefinition(flowId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFlowImpact(String flowId) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        Long bugCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bug WHERE workflow_id=?", Long.class, flowId);
        Long historyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bug_status_transition_history WHERE workflow_id=?",
                Long.class, flowId);
        long associated = bugCount == null ? 0 : bugCount;
        long history = historyCount == null ? 0 : historyCount;
        boolean active = BooleanUtils.isTrue(flow.getActiveForNew());
        boolean deleteStateAllowed = !active && (BooleanUtils.isFalse(flow.getEnabled())
                || !StringUtils.equals(flow.getLifecycle(), "PUBLISHED"));
        boolean deletable = deleteStateAllowed && associated == 0 && history == 0;
        String reason = deletable ? "" : active ? "当前使用中的流程不可删除，请先开启替代流程"
                : !deleteStateAllowed ? "已发布且启用的流程不可删除，请先禁用流程"
                : "该流程已关联缺陷或流转历史，为保证历史数据完整性不可删除";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("associatedBugCount", associated);
        result.put("transitionHistoryCount", history);
        result.put("activeForNew", active);
        result.put("lifecycle", flow.getLifecycle());
        result.put("unpublished", StringUtils.equals(flow.getLifecycle(), "DRAFT"));
        result.put("deleteStateAllowed", deleteStateAllowed);
        result.put("deletable", deletable);
        result.put("archiveRequired", !active && !deletable);
        result.put("reason", reason);
        return result;
    }

    public WorkflowDesignerDTO getWorkflowDesigner(String flowId) {
        getWorkflowWithCheck(flowId);
        WorkflowDesignerDTO result = new WorkflowDesignerDTO();
        jdbcTemplate.query("SELECT id, status_code, name, remark, initial_status, terminal_status, enabled, pos "
                        + "FROM status_item WHERE flow_id = ? ORDER BY pos, id", rs -> {
                    WorkflowDesignerDTO.Status item = new WorkflowDesignerDTO.Status();
                    item.setId(rs.getString("id"));
                    item.setCode(rs.getString("status_code"));
                    item.setName(rs.getString("name"));
                    item.setRemark(rs.getString("remark"));
                    item.setInitial(rs.getBoolean("initial_status"));
                    item.setTerminal(rs.getBoolean("terminal_status"));
                    item.setEnabled(rs.getBoolean("enabled"));
                    item.setPos(rs.getInt("pos"));
                    result.getStatuses().add(item);
                }, flowId);
        jdbcTemplate.query("SELECT id, from_id, to_id, enabled FROM status_flow WHERE flow_id = ? ORDER BY id", rs -> {
            WorkflowDesignerDTO.Transition item = new WorkflowDesignerDTO.Transition();
            item.setId(rs.getString("id"));
            item.setFromId(rs.getString("from_id"));
            item.setToId(rs.getString("to_id"));
            item.setEnabled(rs.getBoolean("enabled"));
            result.getTransitions().add(item);
        }, flowId);
        return result;
    }

    public WorkflowDesignerDTO saveWorkflowDesigner(String flowId, WorkflowDesignerDTO request) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        assertEditableWorkflow(flowId);
        List<WorkflowDesignerDTO.Status> statuses = request.getStatuses() == null ? List.of() : request.getStatuses();
        List<WorkflowDesignerDTO.Transition> transitions = request.getTransitions() == null ? List.of() : request.getTransitions();
        Map<String, String> statusIdMap = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (WorkflowDesignerDTO.Status status : statuses) {
            if (StringUtils.isAnyBlank(status.getCode(), status.getName())) {
                throw new MSException("状态编码和名称不能为空");
            }
            if (!codes.add(status.getCode().trim().toUpperCase())) {
                throw new MSException("状态编码重复: " + status.getCode());
            }
            String sourceId = StringUtils.defaultIfBlank(status.getId(), IDGenerator.nextStr());
            statusIdMap.put(sourceId, sourceId);
            status.setId(sourceId);
        }
        Set<String> transitionIds = transitions.stream().map(WorkflowDesignerDTO.Transition::getId)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        if (transitionIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM status_flow_role_permission WHERE flow_id = ?", flowId);
        } else {
            String placeholders = transitionIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>();
            args.add(flowId);
            args.addAll(transitionIds);
            jdbcTemplate.update("DELETE FROM status_flow_role_permission WHERE flow_id = ? AND status_flow_id NOT IN ("
                    + placeholders + ")", args.toArray());
        }
        jdbcTemplate.update("DELETE FROM status_flow WHERE flow_id = ?", flowId);
        jdbcTemplate.update("DELETE FROM status_item WHERE flow_id = ?", flowId);
        for (WorkflowDesignerDTO.Status status : statuses) {
            jdbcTemplate.update("INSERT INTO status_item "
                            + "(id, flow_id, status_code, name, scene, remark, internal, scope_type, ref_id, scope_id, pos, "
                            + "initial_status, terminal_status, enabled) VALUES (?, ?, ?, ?, 'BUG', ?, b'0', 'SYSTEM', NULL, 'system', ?, ?, ?, ?)",
                    status.getId(), flowId, status.getCode().trim(), status.getName().trim(), status.getRemark(),
                    status.getPos() == null ? 0 : status.getPos(), BooleanUtils.isTrue(status.getInitial()),
                    BooleanUtils.isTrue(status.getTerminal()), BooleanUtils.isNotFalse(status.getEnabled()));
        }
        Set<String> edges = new HashSet<>();
        for (WorkflowDesignerDTO.Transition transition : transitions) {
            if (!statusIdMap.containsKey(transition.getFromId()) || !statusIdMap.containsKey(transition.getToId())
                    || StringUtils.equals(transition.getFromId(), transition.getToId())) {
                throw new MSException("流转端点无效");
            }
            if (!edges.add(transition.getFromId() + "->" + transition.getToId())) {
                throw new MSException("存在重复流转");
            }
            transition.setId(StringUtils.defaultIfBlank(transition.getId(), IDGenerator.nextStr()));
            jdbcTemplate.update("INSERT INTO status_flow (id, flow_id, from_id, to_id, enabled) VALUES (?, ?, ?, ?, ?)",
                    transition.getId(), flowId, transition.getFromId(), transition.getToId(),
                    BooleanUtils.isNotFalse(transition.getEnabled()));
        }
        grantAllRolesToUnconfiguredTransitionsByDefault(flowId);
        return getWorkflowDesigner(flowId);
    }

    public WorkflowValidationDTO validateWorkflow(String flowId) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        WorkflowDesignerDTO designer = getWorkflowDesigner(flowId);
        WorkflowValidationDTO result = new WorkflowValidationDTO();
        List<WorkflowDesignerDTO.Status> enabledStatuses = designer.getStatuses().stream()
                .filter(status -> BooleanUtils.isNotFalse(status.getEnabled())).toList();
        long initialCount = enabledStatuses.stream().filter(status -> BooleanUtils.isTrue(status.getInitial())).count();
        if (initialCount != 1) result.getErrors().add("必须且只能配置一个启用的初始状态");
        if (enabledStatuses.stream().noneMatch(status -> BooleanUtils.isTrue(status.getTerminal()))) {
            result.getErrors().add("至少配置一个启用的结束状态");
        }
        Set<String> enabledIds = enabledStatuses.stream().map(WorkflowDesignerDTO.Status::getId).collect(Collectors.toSet());
        List<WorkflowDesignerDTO.Transition> enabledTransitions = designer.getTransitions().stream()
                .filter(item -> BooleanUtils.isNotFalse(item.getEnabled())).toList();
        for (WorkflowDesignerDTO.Transition transition : enabledTransitions) {
            if (!enabledIds.contains(transition.getFromId()) || !enabledIds.contains(transition.getToId())) {
                result.getErrors().add("流转 " + transition.getId() + " 指向停用或不存在状态");
            }
            Integer roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM status_flow_role_permission "
                            + "WHERE status_flow_id = ? AND enabled = b'1' AND operable = b'1'", Integer.class, transition.getId());
            if (roleCount == null || roleCount == 0) result.getErrors().add("流转 " + transition.getId() + " 未配置可执行角色");
        }
        enabledStatuses.stream().filter(status -> !BooleanUtils.isTrue(status.getTerminal())).forEach(status -> {
            if (enabledTransitions.stream().noneMatch(item -> StringUtils.equals(item.getFromId(), status.getId()))) {
                result.getErrors().add("非结束状态“" + status.getName() + "”没有出边");
            }
        });
        WorkflowDesignerDTO.Status initial = enabledStatuses.stream().filter(status -> BooleanUtils.isTrue(status.getInitial())).findFirst().orElse(null);
        if (initial != null) {
            Set<String> reachable = new HashSet<>();
            reachable.add(initial.getId());
            boolean changed;
            do {
                changed = false;
                for (WorkflowDesignerDTO.Transition edge : enabledTransitions) {
                    if (reachable.contains(edge.getFromId()) && reachable.add(edge.getToId())) changed = true;
                }
            } while (changed);
            enabledStatuses.stream().filter(status -> !reachable.contains(status.getId()))
                    .forEach(status -> result.getErrors().add("状态“" + status.getName() + "”从初始状态不可达"));
        }
        if (!StringUtils.equals(flow.getScopeType(), UserRoleType.SYSTEM.name())
                || !StringUtils.equals(flow.getScopeId(), UserRoleScope.SYSTEM)) {
            result.getErrors().add("全局缺陷流程必须使用 SYSTEM/system 作用域");
        }
        result.setValid(result.getErrors().isEmpty());
        return result;
    }

    public WorkflowDefinition publishWorkflow(String flowId, Integer expectedVersion) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        if (expectedVersion == null || !Objects.equals(flow.getVersion(), expectedVersion)) {
            throw new MSException(MsHttpResultCode.CONFLICT, "流程版本已变化，请刷新后重试");
        }
        if (!StringUtils.equals(flow.getLifecycle(), "DRAFT")) throw new MSException("仅草稿流程可发布");
        grantAllRolesToUnconfiguredTransitionsByDefault(flowId);
        WorkflowValidationDTO validation = validateWorkflow(flowId);
        if (!validation.isValid()) throw new MSException("流程发布校验失败: " + String.join("；", validation.getErrors()));
        int published = jdbcTemplate.update("UPDATE workflow_definition SET lifecycle = 'PUBLISHED', default_flow = b'0', "
                        + "active_for_new = b'0', enabled = b'1', "
                        + "published_time = ?, published_by = ?, update_time = ? WHERE id = ? AND lifecycle = 'DRAFT'",
                System.currentTimeMillis(), SessionUtils.getUserId(), System.currentTimeMillis(), flowId);
        if (published != 1) throw new MSException(MsHttpResultCode.CONFLICT, "流程已被其他操作修改，请刷新后重试");
        return getWorkflowWithCheck(flowId);
    }

    public WorkflowDefinition activateWorkflow(String flowId) {
        jdbcTemplate.queryForList("SELECT id FROM workflow_definition WHERE scene='BUG' AND scope_type='SYSTEM' "
                + "AND scope_id='system' FOR UPDATE", String.class);
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        if (!StringUtils.equals(flow.getLifecycle(), "PUBLISHED")) throw new MSException("仅已发布流程可开启使用");
        if (BooleanUtils.isFalse(flow.getEnabled())) throw new MSException("已禁用流程不可开启使用");
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE workflow_definition SET active_for_new=b'0', default_flow=b'0', update_time=? "
                + "WHERE scene='BUG' AND scope_type='SYSTEM' AND scope_id='system' AND active_for_new=b'1'", now);
        int updated = jdbcTemplate.update("UPDATE workflow_definition SET active_for_new=b'1', default_flow=b'1', update_time=? "
                + "WHERE id=? AND lifecycle='PUBLISHED' AND enabled=b'1'", now, flowId);
        if (updated != 1) throw new MSException(MsHttpResultCode.CONFLICT, "流程状态已变化，请刷新后重试");
        return getWorkflowWithCheck(flowId);
    }

    public WorkflowDefinition enableWorkflow(String flowId, Boolean enabled) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        if (StringUtils.equals(flow.getLifecycle(), "ARCHIVED")) {
            throw new MSException("已归档流程不可启用或禁用");
        }
        boolean targetEnabled = BooleanUtils.isNotFalse(enabled);
        long now = System.currentTimeMillis();
        if (!targetEnabled) {
            jdbcTemplate.update("UPDATE workflow_definition SET enabled=b'0', active_for_new=b'0', "
                    + "default_flow=b'0', update_time=? WHERE id=?", now, flowId);
        } else {
            jdbcTemplate.update("UPDATE workflow_definition SET enabled=b'1', update_time=? WHERE id=?", now, flowId);
        }
        return getWorkflowWithCheck(flowId);
    }

    public WorkflowDefinition archiveWorkflow(String flowId) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        if (BooleanUtils.isTrue(flow.getActiveForNew())) throw new MSException("当前使用中的流程不可归档，请先开启替代流程");
        if (StringUtils.equals(flow.getLifecycle(), "ARCHIVED")) return flow;
        if (!StringUtils.equals(flow.getLifecycle(), "PUBLISHED")) {
            throw new MSException("仅已发布且未使用的流程可归档");
        }
        jdbcTemplate.update("UPDATE workflow_definition SET lifecycle = 'ARCHIVED', default_flow = b'0', active_for_new=b'0', update_time = ? WHERE id = ?",
                System.currentTimeMillis(), flowId);
        return getWorkflowWithCheck(flowId);
    }

    public WorkflowDefinition copyWorkflow(String flowId) {
        WorkflowDefinition source = getWorkflowWithCheck(flowId);
        WorkflowDefinition request = new WorkflowDefinition();
        request.setCode(source.getCode());
        request.setName(source.getName() + " v" + (source.getVersion() + 1));
        request.setScene(source.getScene());
        request.setScopeType(UserRoleType.SYSTEM.name());
        request.setScopeId(UserRoleScope.SYSTEM);
        Integer nextVersion = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version), 0) + 1 FROM workflow_definition "
                        + "WHERE code = ? AND scope_type = ? AND scope_id = ?", Integer.class,
                source.getCode(), source.getScopeType(), source.getScopeId());
        request.setVersion(nextVersion == null ? source.getVersion() + 1 : nextVersion);
        request.setCopyFromFlowId(source.getId());
        request.setDescription(source.getDescription());
        return addFlow(request);
    }

    private WorkflowDefinition getWorkflowWithCheck(String flowId) {
        WorkflowDefinition flow = permissionControlMapper.selectWorkflowDefinitionById(flowId);
        if (flow == null) throw new MSException("流程不存在");
        return flow;
    }

    public WorkflowMigrationPreviewDTO previewWorkflowMigration(String targetFlowId) {
        WorkflowDefinition target = getWorkflowWithCheck(targetFlowId);
        if (!StringUtils.equals(target.getLifecycle(), "PUBLISHED") || !BooleanUtils.isTrue(target.getActiveForNew())) {
            throw new MSException("仅可将历史缺陷关联到当前使用中的已发布流程");
        }
        WorkflowMigrationPreviewDTO preview = new WorkflowMigrationPreviewDTO();
        preview.setTargetFlowId(target.getId());
        preview.setTargetVersion(target.getVersion());
        List<Map<String, Object>> targets = jdbcTemplate.queryForList("SELECT id, status_code, name FROM status_item "
                + "WHERE flow_id = ? AND enabled = b'1'", targetFlowId);
        Map<String, String> targetByKey = new LinkedHashMap<>();
        targets.forEach(row -> {
            String id = String.valueOf(row.get("id"));
            String code = StringUtils.defaultString((String) row.get("status_code"));
            String name = StringUtils.defaultString((String) row.get("name"));
            if (StringUtils.isNotBlank(code)) targetByKey.put(code.trim().toLowerCase(), id);
            if (StringUtils.isNotBlank(name)) targetByKey.put(name.trim().toLowerCase(), id);
            if (StringUtils.startsWithIgnoreCase(code, "BUG_")) targetByKey.put(code.substring(4).toLowerCase(), id);
            preview.getTargetStatuses().add(Map.of("id", id, "code", code, "name", name));
        });
        List<Map<String, Object>> sourceStatuses = jdbcTemplate.queryForList("SELECT b.status status_id, si.status_code, "
                + "si.name status_name, COUNT(*) bug_count, COUNT(DISTINCT b.project_id) project_count "
                + "FROM bug b LEFT JOIN status_item si ON si.id = b.status "
                + "WHERE b.deleted = b'0' AND b.workflow_id IS NULL "
                + "GROUP BY b.status, si.status_code, si.name ORDER BY si.name, b.status");
        for (Map<String, Object> source : sourceStatuses) {
            String sourceId = String.valueOf(source.get("status_id"));
            String sourceCode = StringUtils.defaultString((String) source.get("status_code"));
            String sourceName = StringUtils.defaultString((String) source.get("status_name"));
            String targetId = StringUtils.isBlank(sourceCode) ? null : targetByKey.get(sourceCode.trim().toLowerCase());
            if (targetId == null && StringUtils.isNotBlank(sourceName)) {
                targetId = targetByKey.get(sourceName.trim().toLowerCase());
            }
            if (targetId == null && StringUtils.startsWithIgnoreCase(sourceCode, "BUG_")) {
                targetId = targetByKey.get(sourceCode.substring(4).toLowerCase());
            }
            if (targetId == null) targetId = targetByKey.get(sourceId.toLowerCase());

            WorkflowMigrationPreviewDTO.SourceStatus sourceStatus = new WorkflowMigrationPreviewDTO.SourceStatus();
            sourceStatus.setId(sourceId);
            sourceStatus.setCode(sourceCode);
            sourceStatus.setName(StringUtils.defaultIfBlank(sourceName, "未知历史状态"));
            sourceStatus.setNameMissing(StringUtils.isBlank(sourceName));
            sourceStatus.setBugCount(((Number) source.get("bug_count")).longValue());
            sourceStatus.setProjectCount(((Number) source.get("project_count")).longValue());
            sourceStatus.setSuggestedTargetStatusId(targetId);
            sourceStatus.setAutoMapped(targetId != null);
            preview.getSourceStatuses().add(sourceStatus);
            if (targetId == null) preview.getUnresolvedStatusIds().add(sourceId);
            else preview.getSuggestedMappings().put(sourceId, targetId);
        }
        Long affected = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bug WHERE deleted = b'0' AND workflow_id IS NULL",
                Long.class);
        preview.setAffectedBugCount(affected == null ? 0 : affected);
        jdbcTemplate.query("SELECT b.project_id, p.name project_name, COUNT(*) bug_count "
                        + "FROM bug b JOIN project p ON p.id = b.project_id WHERE b.deleted = b'0' AND b.workflow_id IS NULL "
                        + "GROUP BY b.project_id, p.name ORDER BY p.name", rs -> {
                    WorkflowMigrationPreviewDTO.ProjectDifference item = new WorkflowMigrationPreviewDTO.ProjectDifference();
                    item.setProjectId(rs.getString("project_id"));
                    item.setProjectName(rs.getString("project_name"));
                    item.setBugCount(rs.getLong("bug_count"));
                    List<String> statusIds = jdbcTemplate.query("SELECT DISTINCT status FROM bug WHERE project_id = ? "
                                    + "AND deleted = b'0' AND workflow_id IS NULL", (statusRs, rowNum) -> statusRs.getString(1),
                            item.getProjectId());
                    item.setSourceStatusIds(statusIds);
                    item.setUnresolvedStatusIds(statusIds.stream()
                            .filter(id -> !preview.getSuggestedMappings().containsKey(id)).toList());
                    preview.getProjects().add(item);
                });
        return preview;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pageWorkflowMigrationCandidates(String targetFlowId,
                                                               WorkflowMigrationCandidateRequest request,
                                                               boolean idsOnly) {
        WorkflowDefinition target = getWorkflowWithCheck(targetFlowId);
        if (!StringUtils.equals(target.getLifecycle(), "PUBLISHED") || !BooleanUtils.isTrue(target.getActiveForNew())) {
            throw new MSException("仅可查询当前使用中流程的历史缺陷候选项");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE b.deleted=b'0' AND b.workflow_id IS NULL");
        if (CollectionUtils.isNotEmpty(request.getProjectIds())) {
            where.append(" AND b.project_id IN (")
                    .append(request.getProjectIds().stream().map(id -> "?").collect(Collectors.joining(","))).append(")");
            args.addAll(request.getProjectIds());
        }
        if (CollectionUtils.isNotEmpty(request.getSourceStatusIds())) {
            where.append(" AND b.status IN (")
                    .append(request.getSourceStatusIds().stream().map(id -> "?").collect(Collectors.joining(","))).append(")");
            args.addAll(request.getSourceStatusIds());
        }
        if (request.getCreateTimeStart() != null) {
            where.append(" AND b.create_time>=?");
            args.add(request.getCreateTimeStart());
        }
        if (request.getCreateTimeEnd() != null) {
            where.append(" AND b.create_time<=?");
            args.add(request.getCreateTimeEnd());
        }
        if (StringUtils.isNotBlank(request.getKeyword())) {
            where.append(" AND (b.id LIKE ? OR b.title LIKE ? OR CAST(b.num AS CHAR) LIKE ?)");
            String keyword = "%" + StringUtils.trim(request.getKeyword()) + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        if (idsOnly) {
            List<String> ids = jdbcTemplate.query("SELECT b.id FROM bug b" + where + " ORDER BY b.create_time,b.id LIMIT 10000",
                    (rs, rowNum) -> rs.getString(1), args.toArray());
            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bug b" + where, Long.class, args.toArray());
            if (total != null && total > 10000) throw new MSException("当前筛选结果超过 10000 条，请缩小筛选范围后再全选");
            return Map.of("ids", ids, "total", total == null ? 0 : total);
        }
        int current = Math.max(1, request.getCurrent());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bug b" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((current - 1) * pageSize);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT b.id,b.num,b.title,b.project_id projectId," +
                        "p.name projectName,b.status sourceStatusId,COALESCE(si.name,'未知状态') sourceStatusName," +
                        "b.create_time createTime FROM bug b JOIN project p ON p.id=b.project_id " +
                        "LEFT JOIN status_item si ON si.id=b.status" + where + " ORDER BY b.create_time,b.id LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total == null ? 0 : total);
        result.put("current", current);
        result.put("pageSize", pageSize);
        return result;
    }

    public Map<String, Object> migrateWorkflow(WorkflowMigrationRequest request) {
        if (CollectionUtils.isEmpty(request.getBugIds())) {
            throw new MSException("请选择需要关联的历史缺陷，禁止空选择执行关联");
        }
        WorkflowMigrationPreviewDTO preview = previewWorkflowMigration(request.getTargetFlowId());
        Map<String, String> mappings = request.getStatusMappings() == null || request.getStatusMappings().isEmpty()
                ? preview.getSuggestedMappings() : request.getStatusMappings();
        String batchId = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        WorkflowDefinition target = getWorkflowWithCheck(request.getTargetFlowId());
        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            Integer targetStatusCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM status_item WHERE id = ? AND flow_id = ? "
                            + "AND enabled = b'1'", Integer.class, mapping.getValue(), target.getId());
            if (targetStatusCount == null || targetStatusCount == 0) {
                throw new MSException("目标状态不属于目标流程: " + mapping.getValue());
            }
        }
        List<Object> selectionArgs = new ArrayList<>();
        StringBuilder selectionSql = new StringBuilder("SELECT id,status,workflow_id,workflow_version FROM bug "
                + "WHERE deleted=b'0' AND workflow_id IS NULL");
        if (CollectionUtils.isNotEmpty(request.getBugIds())) {
            selectionSql.append(" AND id IN (").append(request.getBugIds().stream().map(id -> "?").collect(Collectors.joining(","))).append(")");
            selectionArgs.addAll(request.getBugIds());
        }
        if (CollectionUtils.isNotEmpty(request.getProjectIds())) {
            selectionSql.append(" AND project_id IN (").append(request.getProjectIds().stream().map(id -> "?").collect(Collectors.joining(","))).append(")");
            selectionArgs.addAll(request.getProjectIds());
        }
        selectionSql.append(" ORDER BY id");
        List<Map<String, Object>> migrationItems = jdbcTemplate.queryForList(selectionSql.toString(), selectionArgs.toArray());
        Set<String> requestedBugIds = request.getBugIds().stream().filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> eligibleBugIds = migrationItems.stream().map(item -> String.valueOf(item.get("id")))
                .collect(Collectors.toSet());
        if (requestedBugIds.isEmpty()) {
            throw new MSException("请选择需要关联的历史缺陷，禁止空选择执行关联");
        }
        if (!eligibleBugIds.containsAll(requestedBugIds)) {
            requestedBugIds.removeAll(eligibleBugIds);
            throw new MSException("部分选中缺陷已删除、已关联流程或不在筛选范围，请刷新后重试: "
                    + String.join(",", requestedBugIds));
        }
        Set<String> selectedSourceStatuses = migrationItems.stream().map(item -> String.valueOf(item.get("status"))).collect(Collectors.toSet());
        Set<String> selectedUnresolved = selectedSourceStatuses.stream().filter(status -> !mappings.containsKey(status)).collect(Collectors.toSet());
        if (!selectedUnresolved.isEmpty()) throw new MSException("选中缺陷存在未映射状态，禁止关联: " + String.join(",", selectedUnresolved));
        jdbcTemplate.update("INSERT INTO workflow_migration_batch "
                        + "(id, target_flow_id, dry_run, status, mapping_snapshot, total_count, create_user, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batchId, request.getTargetFlowId(), request.isDryRun(),
                request.isDryRun() ? "PREVIEWED" : "PENDING", io.metersphere.sdk.util.JSON.toJSONString(mappings),
                migrationItems.size(), SessionUtils.getUserId(), now, now);
        if (request.isDryRun()) {
            jdbcTemplate.update("UPDATE workflow_migration_batch SET status = 'COMPLETED', update_time=?, finish_time = ? WHERE id = ?", now, now, batchId);
            return Map.of("batchId", batchId, "dryRun", true, "total", migrationItems.size(), "migrated", 0);
        }
        jdbcTemplate.batchUpdate("INSERT INTO workflow_migration_item (id,batch_id,bug_id,source_status_id,source_workflow_id,"
                        + "source_workflow_version,target_status_id,target_workflow_id,target_workflow_version,status,create_time,update_time) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,?)", migrationItems, 500, (statement, item) -> {
                    long itemNow = System.currentTimeMillis();
                    String sourceStatus = String.valueOf(item.get("status"));
                    statement.setString(1, IDGenerator.nextStr());
                    statement.setString(2, batchId);
                    statement.setString(3, String.valueOf(item.get("id")));
                    statement.setString(4, sourceStatus);
                    statement.setObject(5, item.get("workflow_id"));
                    statement.setObject(6, item.get("workflow_version"));
                    statement.setString(7, mappings.get(sourceStatus));
                    statement.setString(8, target.getId());
                    statement.setInt(9, target.getVersion());
                    statement.setLong(10, itemNow);
                    statement.setLong(11, itemNow);
                });
        jdbcTemplate.update("UPDATE workflow_migration_batch SET total_count=?,update_time=? WHERE id=?",
                migrationItems.size(), System.currentTimeMillis(), batchId);
        runAfterCommit(() -> workflowMigrationService.execute(batchId, target.getId(), target.getVersion(), mappings));
        return Map.of("batchId", batchId, "dryRun", false, "total", migrationItems.size(),
                "status", "PENDING", "migrated", 0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWorkflowMigrationBatch(String batchId) {
        return workflowMigrationService.getBatch(batchId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWorkflowMigrationBatches(String flowId) {
        getWorkflowWithCheck(flowId);
        return workflowMigrationService.listBatches(flowId);
    }

    public Map<String, Object> resumeWorkflowMigration(String batchId) {
        Map<String, Object> batch = workflowMigrationService.getBatch(batchId);
        String status = String.valueOf(batch.get("status"));
        if (!StringUtils.equalsAny(status, "FAILED", "PARTIAL_SUCCESS")) {
            throw new MSException("仅失败或部分成功批次可断点续跑");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT target_flow_id,mapping_snapshot FROM workflow_migration_batch WHERE id=?", batchId);
        WorkflowDefinition target = getWorkflowWithCheck(String.valueOf(rows.getFirst().get("target_flow_id")));
        @SuppressWarnings("unchecked")
        Map<String, String> mappings = io.metersphere.sdk.util.JSON.parseObject(
                String.valueOf(rows.getFirst().get("mapping_snapshot")), Map.class);
        jdbcTemplate.update("DELETE FROM workflow_migration_exception WHERE batch_id=?", batchId);
        jdbcTemplate.update("UPDATE workflow_migration_item SET status='PENDING',update_time=? WHERE batch_id=? AND status='FAILED'",
                System.currentTimeMillis(), batchId);
        jdbcTemplate.update("UPDATE workflow_migration_batch SET status='PENDING',failed_count=0,finish_time=NULL,update_time=? WHERE id=?",
                System.currentTimeMillis(), batchId);
        runAfterCommit(() -> workflowMigrationService.execute(batchId, target.getId(), target.getVersion(), mappings));
        return Map.of("batchId", batchId, "status", "PENDING");
    }

    public Map<String, Object> rollbackWorkflowMigration(String batchId) {
        workflowMigrationService.rollback(batchId);
        return workflowMigrationService.getBatch(batchId);
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
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
        assertEditableWorkflow(request.getFlowId());
        validateWorkflowRole(request);
        WorkflowRole workflowRole = new WorkflowRole();
        BeanUtils.copyBean(workflowRole, request);
        workflowRole.setId(StringUtils.defaultIfBlank(workflowRole.getId(), IDGenerator.nextStr()));
        workflowRole.setSourceType(StringUtils.defaultIfBlank(workflowRole.getSourceType(), "MANUAL"));
        workflowRole.setMatchMode(StringUtils.defaultIfBlank(workflowRole.getMatchMode(), "CONTAINS"));
        workflowRole.setEnabled(BooleanUtils.isNotFalse(workflowRole.getEnabled()));
        workflowRole.setCreateTime(System.currentTimeMillis());
        workflowRole.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.insertWorkflowRole(workflowRole);
        grantRoleToAllTransitionsByDefault(workflowRole);
        return workflowRole;
    }

    public WorkflowRole updateFlowRole(WorkflowRole request) {
        if (StringUtils.isBlank(request.getId())) {
            throw new MSException("流程角色 ID 不能为空");
        }
        WorkflowRole current = permissionControlMapper.selectWorkflowRoleById(request.getId());
        if (current == null) throw new MSException("流程角色不存在");
        assertEditableWorkflow(current.getFlowId());
        request.setFlowId(current.getFlowId());
        validateWorkflowRole(request);
        request.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.updateWorkflowRole(request);
        return permissionControlMapper.selectWorkflowRoleById(request.getId());
    }

    public void deleteFlowRole(String roleId) {
        WorkflowRole current = permissionControlMapper.selectWorkflowRoleById(roleId);
        if (current == null) return;
        assertEditableWorkflow(current.getFlowId());
        permissionControlMapper.deleteStatusFlowRolePermissionsByWorkflowRoleId(roleId);
        permissionControlMapper.deleteWorkflowRole(roleId);
    }

    public List<StatusFlowRolePermission> listFlowRolePermissions(String flowId) {
        return permissionControlMapper.selectStatusFlowRolePermissions(flowId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewWecomPositions(String flowId) {
        getWorkflowWithCheck(flowId);
        List<Map<String, Object>> positions = jdbcTemplate.queryForList("SELECT TRIM(position) position, "
                + "LOWER(TRIM(position)) sourceKey, COUNT(*) memberCount FROM user "
                + "WHERE deleted=b'0' AND enable=b'1' AND wecom_userid IS NOT NULL AND wecom_userid<>'' "
                + "AND position IS NOT NULL AND TRIM(position)<>'' GROUP BY TRIM(position), LOWER(TRIM(position)) ORDER BY position");
        Set<String> existing = permissionControlMapper.selectWorkflowRoles(flowId).stream()
                .filter(role -> StringUtils.equals(role.getSourceType(), "WECOM_POSITION"))
                .map(WorkflowRole::getSourceKey).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        positions.forEach(item -> item.put("existing", existing.contains(String.valueOf(item.get("sourceKey")))));
        return positions;
    }

    @Transactional
    public Map<String, Object> syncWecomPositions(String flowId) {
        assertWecomPositionSyncAllowed(flowId);
        List<Map<String, Object>> positions = previewWecomPositions(flowId);
        long now = System.currentTimeMillis();
        Set<String> activeKeys = positions.stream().map(item -> String.valueOf(item.get("sourceKey"))).collect(Collectors.toSet());
        List<Map<String, Object>> details = new ArrayList<>();
        int disabled = 0;
        for (WorkflowRole role : permissionControlMapper.selectWorkflowRoles(flowId)) {
            if (StringUtils.equals(role.getSourceType(), "WECOM_POSITION") && !activeKeys.contains(role.getSourceKey())
                    && BooleanUtils.isNotFalse(role.getEnabled())) {
                role.setEnabled(false);
                role.setUpdateTime(now);
                permissionControlMapper.updateWorkflowRole(role);
                details.add(Map.of("action", "DISABLED", "roleId", role.getId(), "name", role.getName(),
                        "sourceKey", StringUtils.defaultString(role.getSourceKey())));
                disabled++;
            }
        }
        int created = 0;
        int updated = 0;
        for (Map<String, Object> item : positions) {
            String name = String.valueOf(item.get("position"));
            String key = String.valueOf(item.get("sourceKey"));
            List<WorkflowRole> matches = permissionControlMapper.selectWorkflowRoles(flowId).stream()
                    .filter(role -> StringUtils.equals(role.getSourceType(), "WECOM_POSITION")
                            && StringUtils.equals(role.getSourceKey(), key)).toList();
            if (matches.isEmpty()) {
                WorkflowRole role = new WorkflowRole();
                role.setFlowId(flowId);
                role.setCode("WECOM_" + org.apache.commons.codec.digest.DigestUtils.sha256Hex(key).substring(0, 16).toUpperCase(Locale.ROOT));
                role.setName(name);
                role.setRoleType("POSITION");
                role.setFieldKey(name);
                role.setSourceType("WECOM_POSITION");
                role.setSourceKey(key);
                role.setMatchMode("EXACT");
                role.setEnabled(true);
                role.setId(IDGenerator.nextStr());
                role.setCreateTime(now);
                role.setUpdateTime(now);
                permissionControlMapper.insertWorkflowRole(role);
                details.add(Map.of("action", "CREATED", "roleId", role.getId(), "name", name, "sourceKey", key));
                created++;
            } else {
                WorkflowRole role = matches.getFirst();
                role.setName(name);
                role.setFieldKey(name);
                role.setMatchMode("EXACT");
                role.setEnabled(true);
                role.setUpdateTime(now);
                permissionControlMapper.updateWorkflowRole(role);
                details.add(Map.of("action", "UPDATED", "roleId", role.getId(), "name", name, "sourceKey", key));
                updated++;
            }
        }
        grantAllEnabledRolesToAllTransitions(flowId);
        String batchId = IDGenerator.nextStr();
        jdbcTemplate.update("INSERT INTO workflow_position_sync_log (id,flow_id,total_count,created_count,updated_count," +
                        "disabled_count,detail_json,create_user,create_time) VALUES (?,?,?,?,?,?,?,?,?)",
                batchId, flowId, positions.size(), created, updated, disabled,
                io.metersphere.sdk.util.JSON.toJSONString(details), SessionUtils.getUserId(), now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("total", positions.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("disabled", disabled);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWecomPositionSyncResults(String flowId) {
        getWorkflowWithCheck(flowId);
        return jdbcTemplate.queryForList("SELECT id batchId,total_count total,created_count created," +
                        "updated_count updated,disabled_count disabled,detail_json detailJson,create_user createUser,create_time createTime " +
                        "FROM workflow_position_sync_log WHERE flow_id=? ORDER BY create_time DESC LIMIT 20", flowId);
    }

    public void saveFlowRolePermissions(WorkflowRolePermissionRequest request) {
        assertEditableWorkflow(request.getFlowId());
        permissionControlMapper.deleteStatusFlowRolePermissionsByFlowId(request.getFlowId());
        if (CollectionUtils.isEmpty(request.getPermissions())) {
            grantAllRolesToUnconfiguredTransitionsByDefault(request.getFlowId());
            return;
        }
        for (StatusFlowRolePermission permission : request.getPermissions()) {
            WorkflowRole role = permissionControlMapper.selectWorkflowRoleById(permission.getWorkflowRoleId());
            if (role == null || !StringUtils.equals(role.getFlowId(), request.getFlowId())) {
                throw new MSException("流程授权引用了其他流程或不存在的角色");
            }
            Integer transitionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM status_flow WHERE id=? AND flow_id=?",
                    Integer.class, permission.getStatusFlowId(), request.getFlowId());
            if (transitionCount == null || transitionCount == 0) {
                throw new MSException("流程授权引用了其他流程或不存在的流转");
            }
            permission.setId(StringUtils.defaultIfBlank(permission.getId(), IDGenerator.nextStr()));
            permission.setFlowId(request.getFlowId());
            permission.setVisible(BooleanUtils.isTrue(permission.getOperable()) || BooleanUtils.isTrue(permission.getVisible()));
            permission.setOperable(BooleanUtils.isTrue(permission.getVisible()) && BooleanUtils.isTrue(permission.getOperable()));
            permission.setEnabled(BooleanUtils.isNotFalse(permission.getEnabled()));
            permission.setCreateTime(System.currentTimeMillis());
            permission.setUpdateTime(System.currentTimeMillis());
            permissionControlMapper.insertStatusFlowRolePermission(permission);
        }
        grantAllRolesToUnconfiguredTransitionsByDefault(request.getFlowId());
    }

    private void assertEditableWorkflow(String flowId) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        if (StringUtils.equals(flow.getLifecycle(), "ARCHIVED")) {
            throw new MSException("已归档流程不可修改角色、流程与流转授权");
        }
    }

    private boolean hasWecomPositionSync(String flowId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_position_sync_log WHERE flow_id=?", Integer.class, flowId);
        return count != null && count > 0;
    }

    private void grantRoleToAllTransitionsByDefault(WorkflowRole role) {
        if (role == null || StringUtils.isBlank(role.getFlowId())) {
            return;
        }
        List<String> transitionIds = jdbcTemplate.queryForList(
                "SELECT id FROM status_flow WHERE flow_id=? AND enabled=b'1'", String.class, role.getFlowId());
        for (String transitionId : transitionIds) {
            insertDefaultFlowRolePermission(role.getFlowId(), transitionId, role.getId());
        }
    }

    private void grantAllEnabledRolesToAllTransitions(String flowId) {
        List<String> roleIds = permissionControlMapper.selectWorkflowRoles(flowId).stream()
                .filter(role -> BooleanUtils.isNotFalse(role.getEnabled()))
                .map(WorkflowRole::getId)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (roleIds.isEmpty()) {
            return;
        }
        List<String> transitionIds = jdbcTemplate.queryForList(
                "SELECT id FROM status_flow WHERE flow_id=? AND enabled=b'1'", String.class, flowId);
        long now = System.currentTimeMillis();
        for (String transitionId : transitionIds) {
            for (String roleId : roleIds) {
                int updated = jdbcTemplate.update("UPDATE status_flow_role_permission SET visible=b'1', operable=b'1', "
                                + "enabled=b'1', update_time=? WHERE flow_id=? AND status_flow_id=? AND workflow_role_id=?",
                        now, flowId, transitionId, roleId);
                if (updated == 0) {
                    insertDefaultFlowRolePermission(flowId, transitionId, roleId);
                }
            }
        }
    }

    private void grantAllRolesToUnconfiguredTransitionsByDefault(String flowId) {
        if (hasWecomPositionSync(flowId)) {
            return;
        }
        List<String> roleIds = permissionControlMapper.selectWorkflowRoles(flowId).stream()
                .filter(role -> BooleanUtils.isNotFalse(role.getEnabled()))
                .map(WorkflowRole::getId)
                .filter(StringUtils::isNotBlank)
                .toList();
        if (roleIds.isEmpty()) {
            return;
        }
        List<String> unconfiguredTransitionIds = jdbcTemplate.queryForList(
                "SELECT sf.id FROM status_flow sf WHERE sf.flow_id=? AND sf.enabled=b'1' "
                        + "AND NOT EXISTS (SELECT 1 FROM status_flow_role_permission p "
                        + "WHERE p.flow_id=sf.flow_id AND p.status_flow_id=sf.id)",
                String.class, flowId);
        for (String transitionId : unconfiguredTransitionIds) {
            for (String roleId : roleIds) {
                insertDefaultFlowRolePermission(flowId, transitionId, roleId);
            }
        }
    }

    private void insertDefaultFlowRolePermission(String flowId, String transitionId, String roleId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM status_flow_role_permission WHERE flow_id=? AND status_flow_id=? AND workflow_role_id=?",
                Integer.class, flowId, transitionId, roleId);
        if (count != null && count > 0) {
            return;
        }
        StatusFlowRolePermission permission = new StatusFlowRolePermission();
        permission.setId(IDGenerator.nextStr());
        permission.setFlowId(flowId);
        permission.setStatusFlowId(transitionId);
        permission.setWorkflowRoleId(roleId);
        permission.setVisible(true);
        permission.setOperable(true);
        permission.setEnabled(true);
        permission.setCreateTime(System.currentTimeMillis());
        permission.setUpdateTime(System.currentTimeMillis());
        permissionControlMapper.insertStatusFlowRolePermission(permission);
    }

    private void assertWecomPositionSyncAllowed(String flowId) {
        WorkflowDefinition flow = getWorkflowWithCheck(flowId);
        if (StringUtils.equals(flow.getLifecycle(), "ARCHIVED")) {
            throw new MSException("归档流程保留历史岗位配置，不允许继续同步企微岗位");
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
        if (isCurrentUserAdmin()) {
            return;
        }
        List<StatusFlowRolePermission> permissions = permissionControlMapper.selectOperableTransitionPermissions(
                TemplateScene.BUG.name(), UserRoleType.PROJECT.name(), projectId, fromStatusId, toStatusId);
        if (CollectionUtils.isEmpty(permissions)) {
            permissions = permissionControlMapper.selectOperableTransitionPermissions(
                    TemplateScene.BUG.name(), UserRoleType.SYSTEM.name(), UserRoleScope.SYSTEM, fromStatusId, toStatusId);
        }
        if (CollectionUtils.isEmpty(permissions)) throw new MSException("该状态流转未配置可执行角色");
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

    public boolean isCurrentUserAdmin() {
        return SessionUtils.getUser() != null
                && CollectionUtils.isNotEmpty(SessionUtils.getUser().getUserRoles())
                && SessionUtils.getUser().getUserRoles().stream()
                .anyMatch(role -> StringUtils.equals(role.getId(), "admin"));
    }

    public List<String> matchWorkflowRoles(String flowId, String statusFlowId, String projectId,
                                           String createUser, String handleUser, boolean operable) {
        if (StringUtils.isAnyBlank(flowId, statusFlowId)) return List.of();
        Map<String, WorkflowRole> roles = permissionControlMapper.selectWorkflowRoles(flowId).stream()
                .filter(role -> BooleanUtils.isNotFalse(role.getEnabled()))
                .collect(Collectors.toMap(WorkflowRole::getId, role -> role, (left, right) -> left));
        return permissionControlMapper.selectStatusFlowRolePermissions(flowId).stream()
                .filter(permission -> StringUtils.equals(permission.getStatusFlowId(), statusFlowId))
                .filter(permission -> BooleanUtils.isNotFalse(permission.getEnabled()))
                .filter(permission -> operable ? BooleanUtils.isTrue(permission.getOperable())
                        : BooleanUtils.isTrue(permission.getVisible()) || BooleanUtils.isTrue(permission.getOperable()))
                .map(permission -> roles.get(permission.getWorkflowRoleId()))
                .filter(Objects::nonNull)
                .filter(role -> matchWorkflowRole(role, projectId, createUser, handleUser))
                .map(WorkflowRole::getId)
                .distinct()
                .toList();
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
        if (StringUtils.equals(role.getRoleType(), "POSITION")) {
            if (SessionUtils.getUser() == null) return false;
            if (StringUtils.equals(role.getMatchMode(), "EXACT")) {
                return StringUtils.equalsIgnoreCase(StringUtils.trim(SessionUtils.getUser().getPosition()),
                        StringUtils.trim(role.getFieldKey()));
            }
            return matchesPositionRule(SessionUtils.getUser().getPosition(), role.getFieldKey());
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

    private void validateWorkflowRole(WorkflowRole role) {
        if (!Set.of("FIELD_USER", "SYSTEM_ROLE", "POSITION").contains(role.getRoleType())) {
            throw new MSException("不支持的流程角色类型");
        }
        if (StringUtils.equals(role.getRoleType(), "FIELD_USER")
                && !Set.of("create_user", "handle_user").contains(role.getFieldKey())) {
            throw new MSException("业务字段用户仅支持创建人或当前处理人");
        }
        if (StringUtils.equals(role.getRoleType(), "SYSTEM_ROLE") && StringUtils.isBlank(role.getRoleId())) {
            throw new MSException("系统角色不能为空");
        }
        if (StringUtils.equals(role.getRoleType(), "POSITION") && StringUtils.isBlank(role.getFieldKey())) {
            throw new MSException("职位关键词不能为空");
        }
    }

    static boolean matchesPositionRule(String position, String rule) {
        if (StringUtils.isBlank(position) || StringUtils.isBlank(rule)) {
            return false;
        }
        if (StringUtils.equals(StringUtils.trim(rule), "*")) {
            return true;
        }
        return Arrays.stream(StringUtils.split(rule, '|'))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .anyMatch(keyword -> StringUtils.containsIgnoreCase(position, keyword));
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
