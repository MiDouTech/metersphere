package io.metersphere.system.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
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
import io.metersphere.system.domain.Organization;
import io.metersphere.system.domain.OrganizationExample;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.WorkflowDefinition;
import io.metersphere.system.domain.WorkflowRole;
import io.metersphere.system.domain.UserRoleUiPermission;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.control.PermissionControlFlowMatrixDTO;
import io.metersphere.system.dto.permission.control.RoleAssignmentRuleRequest;
import io.metersphere.system.dto.permission.control.RoleDeleteImpactDTO;
import io.metersphere.system.dto.permission.control.RoleMemberUpdateRequest;
import io.metersphere.system.dto.permission.control.RoleSaveRequest;
import io.metersphere.system.dto.permission.control.WorkflowRolePermissionRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class PermissionControlService {

    private static final String MEMBER_ROLE_ID = "permission_member";
    private static final String MEMBER_PERMISSION_INIT_VERSION = "V3.7.2_50_CACHE_V1";

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
    private PermissionMigrationAuditService permissionMigrationAuditService;
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

    public List<UserRole> listRoles() {
        return globalUserRoleService.list().stream()
                .filter(role -> StringUtils.equals(role.getId(), "admin")
                        || StringUtils.equals(role.getId(), "permission_member")
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

    public List<UserRoleUiPermission> getRoleUiPermission(String roleId) {
        return permissionUiService.getRoleUiPermissions(roleId);
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
            saveRequest.setUiPermissions(List.of());
        } else {
            saveRequest.setPermissions(flattenPermissionUpdates(getRolePermission(request.getId())));
            saveRequest.setUiPermissions(getRoleUiPermission(request.getId()).stream().map(item -> {
                io.metersphere.system.dto.permission.RoleUiPermissionDTO value =
                        new io.metersphere.system.dto.permission.RoleUiPermissionDTO();
                value.setResourceCode(item.getResourceCode());
                value.setVisible(item.getVisible());
                value.setOperable(item.getOperable());
                return value;
            }).toList());
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
        List<String> affectedUsers = permissionControlMapper.selectRoleMemberUserIds(roleId, null);
        UserRole result = globalUserRoleService.enable(roleId, enabled);
        permissionSessionRefreshService.refreshUsersAfterCommit(affectedUsers);
        return result;
    }

    public void deleteRole(String roleId) {
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
        request.setUiPermissions(normalizeLinkedButtonUiPermissions(request.getUiPermissions(), effectiveType));
        validateUiPermissionScope(request.getUiPermissions(), effectiveType);

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
        List<PermissionSettingUpdateRequest.PermissionUpdateRequest> normalizedPermissions =
                normalizeUiOperableDependencies(request.getPermissions(), request.getUiPermissions(), effectiveType, allowedPermissionIds);
        permissionRequest.setPermissions(normalizePermissionDependencies(normalizedPermissions, allowedPermissionIds));
        permissionRequest.setUiPermissions(request.getUiPermissions());
        globalUserRoleService.updatePermissionSetting(permissionRequest);
        permissionSessionRefreshService.refreshUsersAfterCommit(
                permissionControlMapper.selectRoleMemberUserIds(saved.getId(), null));
        return globalUserRoleService.get(saved.getId());
    }

    private List<PermissionSettingUpdateRequest.PermissionUpdateRequest> normalizeUiOperableDependencies(
            List<PermissionSettingUpdateRequest.PermissionUpdateRequest> permissions,
            List<io.metersphere.system.dto.permission.RoleUiPermissionDTO> uiPermissions,
            String roleType,
            Set<String> allowedPermissionIds) {
        Map<String, PermissionSettingUpdateRequest.PermissionUpdateRequest> normalized = permissions.stream()
                .collect(Collectors.toMap(PermissionSettingUpdateRequest.PermissionUpdateRequest::getId,
                        item -> new PermissionSettingUpdateRequest.PermissionUpdateRequest(item.getId(), BooleanUtils.isTrue(item.getEnable())),
                        (a, b) -> b, java.util.LinkedHashMap::new));
        Map<String, String> resourcePermissionIds = getResourceTree(roleType).stream()
                .flatMap(root -> flattenResources(root).stream())
                .filter(resource -> StringUtils.isNotBlank(resource.getPermissionId()))
                .collect(Collectors.toMap(PermissionResourceDTO::getCode, PermissionResourceDTO::getPermissionId, (a, b) -> a));
        uiPermissions.stream()
                .filter(item -> BooleanUtils.isTrue(item.getOperable()))
                .map(item -> resourcePermissionIds.get(item.getResourceCode()))
                .filter(permissionId -> StringUtils.isNotBlank(permissionId) && allowedPermissionIds.contains(permissionId))
                .forEach(permissionId -> normalized
                        .computeIfAbsent(permissionId, id -> new PermissionSettingUpdateRequest.PermissionUpdateRequest(id, true))
                        .setEnable(true));
        return new ArrayList<>(normalized.values());
    }

    private List<io.metersphere.system.dto.permission.RoleUiPermissionDTO> normalizeLinkedButtonUiPermissions(
            List<io.metersphere.system.dto.permission.RoleUiPermissionDTO> uiPermissions, String roleType) {
        Map<String, io.metersphere.system.dto.permission.RoleUiPermissionDTO> normalized = uiPermissions.stream()
                .collect(Collectors.toMap(io.metersphere.system.dto.permission.RoleUiPermissionDTO::getResourceCode,
                        item -> item, (a, b) -> b, java.util.LinkedHashMap::new));
        Map<String, List<PermissionResourceDTO>> linkedButtons = getResourceTree(roleType).stream()
                .flatMap(root -> flattenResources(root).stream())
                .filter(resource -> StringUtils.equals(resource.getType(), "BUTTON")
                        && StringUtils.isNotBlank(resource.getPermissionId()))
                .collect(Collectors.groupingBy(PermissionResourceDTO::getPermissionId));
        for (List<PermissionResourceDTO> group : linkedButtons.values()) {
            List<io.metersphere.system.dto.permission.RoleUiPermissionDTO> configured = group.stream()
                    .map(resource -> normalized.get(resource.getCode()))
                    .filter(Objects::nonNull)
                    .toList();
            if (CollectionUtils.isEmpty(configured)) {
                continue;
            }
            boolean visible = BooleanUtils.isTrue(configured.getFirst().getVisible())
                    || BooleanUtils.isTrue(configured.getFirst().getOperable());
            boolean operable = BooleanUtils.isTrue(configured.getFirst().getOperable());
            boolean inconsistent = configured.stream().anyMatch(item ->
                    (BooleanUtils.isTrue(item.getVisible()) || BooleanUtils.isTrue(item.getOperable())) != visible
                            || BooleanUtils.isTrue(item.getOperable()) != operable);
            if (inconsistent) {
                throw new MSException("关联同一接口权限的兼容按钮必须使用一致的可见和可操作设置："
                        + group.getFirst().getPermissionId());
            }
            for (PermissionResourceDTO resource : group) {
                io.metersphere.system.dto.permission.RoleUiPermissionDTO item = normalized.computeIfAbsent(
                        resource.getCode(), code -> {
                            io.metersphere.system.dto.permission.RoleUiPermissionDTO value =
                                    new io.metersphere.system.dto.permission.RoleUiPermissionDTO();
                            value.setResourceCode(code);
                            return value;
                        });
                item.setVisible(visible);
                item.setOperable(operable);
            }
        }
        return new ArrayList<>(normalized.values());
    }

    public void synchronizeMemberRolePermissions() {
        UserRole memberRole = globalUserRoleService.get(MEMBER_ROLE_ID);
        if (memberRole == null || permissionControlMapper.countMemberInitialization(
                MEMBER_ROLE_ID, MEMBER_PERMISSION_INIT_VERSION) > 0) {
            return;
        }
        try {
            synchronizeMemberRolePermissions(memberRole);
        } catch (Exception e) {
            permissionMigrationAuditService.recordFailure(MEMBER_PERMISSION_INIT_VERSION, MEMBER_ROLE_ID, null,
                    "CACHE_PERMISSION_INITIALIZATION", e);
            throw new IllegalStateException("初始化成员角色权限失败", e);
        }
    }

    private void synchronizeMemberRolePermissions(UserRole memberRole) {
        List<PermissionSettingUpdateRequest.PermissionUpdateRequest> permissions = collectPermissionIds(
                globalUserRoleService.getPermissionDefinitionForControl(UserRoleType.SYSTEM.name())).stream()
                .map(permissionId -> new PermissionSettingUpdateRequest.PermissionUpdateRequest(permissionId, true))
                .toList();
        if (CollectionUtils.isEmpty(permissions)) {
            throw new MSException("统一权限定义为空，不能初始化成员角色权限");
        }
        PermissionSettingUpdateRequest request = new PermissionSettingUpdateRequest();
        request.setUserRoleId(memberRole.getId());
        request.setPermissions(permissions);
        request.setUiPermissions(permissionUiService.getAllResourceTree().stream()
                .flatMap(root -> flattenResources(root).stream())
                .map(resource -> {
                    io.metersphere.system.dto.permission.RoleUiPermissionDTO value = new io.metersphere.system.dto.permission.RoleUiPermissionDTO();
                    value.setResourceCode(resource.getCode());
                    value.setVisible(true);
                    value.setOperable(StringUtils.equalsAny(resource.getType(), "BUTTON", "API"));
                    return value;
                }).toList());
        globalUserRoleService.updatePermissionSetting(request);
        permissionControlMapper.insertMemberInitialization(MEMBER_ROLE_ID, MEMBER_PERMISSION_INIT_VERSION,
                System.currentTimeMillis());
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

    private void validateUiPermissionScope(List<io.metersphere.system.dto.permission.RoleUiPermissionDTO> permissions,
                                           String roleType) {
        Set<String> allowedResourceCodes = getResourceTree(roleType).stream()
                .flatMap(root -> flattenResources(root).stream())
                .map(PermissionResourceDTO::getCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        List<String> invalidResourceCodes = permissions.stream()
                .map(io.metersphere.system.dto.permission.RoleUiPermissionDTO::getResourceCode)
                .filter(code -> StringUtils.isBlank(code) || !allowedResourceCodes.contains(code))
                .distinct()
                .toList();
        if (CollectionUtils.isNotEmpty(invalidResourceCodes)) {
            throw new MSException("存在不属于当前角色范围的页面或按钮权限：" + String.join(", ", invalidResourceCodes));
        }
    }

    private List<PermissionResourceDTO> flattenResources(PermissionResourceDTO node) {
        List<PermissionResourceDTO> result = new ArrayList<>();
        result.add(node);
        if (CollectionUtils.isNotEmpty(node.getChildren())) {
            node.getChildren().forEach(child -> result.addAll(flattenResources(child)));
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
        permissionSessionRefreshService.refreshUsersAfterCommit(request.getUserIds());
    }

    private UserRole validateRoleForMemberManagement(String roleId, String sourceId, boolean mutable) {
        UserRole role = globalUserRoleService.getWithCheck(roleId);
        globalUserRoleService.checkGlobalUserRole(role);
        if (StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())) {
            globalUserRoleService.checkSystemUserGroup(role);
        }
        if (mutable) {
            globalUserRoleService.checkAdminUserRole(role);
        }
        assertTargetScopeMemberPermission(role, resolveMemberSourceId(role, sourceId), mutable);
        return role;
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
        List<io.metersphere.system.dto.permission.RoleUiPermissionDTO> uiPermissions =
                request.getUiPermissions() == null ? permissionUiService.getRoleUiPermissions(role.getId()).stream().map(item -> {
                    io.metersphere.system.dto.permission.RoleUiPermissionDTO dto = new io.metersphere.system.dto.permission.RoleUiPermissionDTO();
                    dto.setResourceCode(item.getResourceCode());
                    dto.setVisible(item.getVisible());
                    dto.setOperable(item.getOperable());
                    return dto;
                }).toList() : request.getUiPermissions();
        uiPermissions = normalizeLinkedButtonUiPermissions(uiPermissions, role.getType());
        validateUiPermissionScope(uiPermissions, role.getType());
        request.setPermissions(normalizePermissionDependencies(
                normalizeUiOperableDependencies(request.getPermissions(), uiPermissions, role.getType(), allowedPermissionIds),
                allowedPermissionIds));
        request.setUiPermissions(request.getUiPermissions() == null ? null : uiPermissions);
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
        permissionControlMapper.deleteWorkflowRolesByFlowId(flowId);
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
        permissionControlMapper.deleteStatusFlowRolePermissionsByWorkflowRoleId(roleId);
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
