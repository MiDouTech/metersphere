package io.metersphere.system.service;

import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.PermissionResource;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRolePermission;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.domain.UserRoleUiPermission;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.RoleUiPermissionDTO;
import io.metersphere.system.dto.permission.UiPermissionSetDTO;
import io.metersphere.system.dto.permission.UserUiPermissionsDTO;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.dto.user.UserRoleResourceDTO;
import io.metersphere.system.mapper.PermissionResourceMapper;
import io.metersphere.system.mapper.UserRoleUiPermissionMapper;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class PermissionUiService {

    @Resource
    private PermissionResourceMapper permissionResourceMapper;
    @Resource
    private UserRoleUiPermissionMapper userRoleUiPermissionMapper;

    public List<PermissionResourceDTO> getResourceTree(String scopeType) {
        List<PermissionResource> resources = permissionResourceMapper.selectEnabledByScopeType(scopeType);
        return buildTree(resources);
    }

    public List<UserRoleUiPermission> getRoleUiPermissions(String roleId) {
        return userRoleUiPermissionMapper.selectByRoleId(roleId);
    }

    public void updateRoleUiPermissions(String roleId, List<RoleUiPermissionDTO> uiPermissions) {
        if (isAdminRole(roleId)) {
            throw new MSException("内置管理员用户组无法修改 UI 权限");
        }
        userRoleUiPermissionMapper.deleteByRoleId(roleId);
        if (CollectionUtils.isEmpty(uiPermissions)) {
            return;
        }

        List<String> resourceCodes = uiPermissions.stream()
                .map(RoleUiPermissionDTO::getResourceCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(resourceCodes)) {
            return;
        }
        long resourceCount = permissionResourceMapper.countEnabledByCodes(resourceCodes);
        if (resourceCount != resourceCodes.size()) {
            throw new MSException("存在无效或已禁用的 UI 权限资源");
        }
        Map<String, PermissionResource> resourceMap = permissionResourceMapper.selectEnabledByCodes(resourceCodes).stream()
                .collect(Collectors.toMap(PermissionResource::getCode, item -> item, (a, b) -> a));

        List<UserRoleUiPermission> records = new ArrayList<>();
        for (RoleUiPermissionDTO item : uiPermissions) {
            if (StringUtils.isBlank(item.getResourceCode())) {
                continue;
            }
            PermissionResource resource = resourceMap.get(item.getResourceCode());
            boolean operable = resource != null
                    && (StringUtils.equals(resource.getType(), "BUTTON") || StringUtils.equals(resource.getType(), "API"))
                    && BooleanUtils.isTrue(item.getOperable());
            boolean visible = operable || BooleanUtils.isTrue(item.getVisible());
            UserRoleUiPermission record = new UserRoleUiPermission();
            record.setId(IDGenerator.nextStr());
            record.setRoleId(roleId);
            record.setResourceCode(item.getResourceCode());
            record.setVisible(visible);
            record.setOperable(visible && operable);
            records.add(record);
        }
        if (CollectionUtils.isNotEmpty(records)) {
            userRoleUiPermissionMapper.batchInsert(records);
        }
    }

    public UserUiPermissionsDTO aggregate(UserDTO userDTO) {
        UserUiPermissionsDTO result = new UserUiPermissionsDTO();
        if (userDTO == null || CollectionUtils.isEmpty(userDTO.getUserRoleRelations())) {
            return result;
        }
        if (isAdmin(userDTO)) {
            return result;
        }
        if (CollectionUtils.isEmpty(userDTO.getUserRoles())) {
            return result;
        }

        List<PermissionResource> resources = permissionResourceMapper.selectEnabled();
        Map<String, List<PermissionResource>> resourcesByScope = resources.stream()
                .collect(Collectors.groupingBy(PermissionResource::getScopeType, LinkedHashMap::new, Collectors.toList()));
        Map<String, UserRole> rolesById = userDTO.getUserRoles().stream()
                .filter(role -> BooleanUtils.isNotFalse(role.getEnabled()))
                .collect(Collectors.toMap(UserRole::getId, role -> role, (a, b) -> a));
        Map<String, Set<String>> oldPermissionsByRole = getOldPermissionsByRole(userDTO);
        Map<String, List<UserRoleUiPermission>> uiPermissionsByRole = getUiPermissionsByRole(userDTO);

        mergeScope(result.getSystem(), resourcesByScope.get(UserRoleType.SYSTEM.name()), userDTO, rolesById,
                oldPermissionsByRole, uiPermissionsByRole, relation -> {
                    UserRole role = rolesById.get(relation.getRoleId());
                    return role != null && StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name());
                });
        mergeScope(result.getOrganization(), resourcesByScope.get(UserRoleType.ORGANIZATION.name()), userDTO, rolesById,
                oldPermissionsByRole, uiPermissionsByRole, relation -> {
                    UserRole role = rolesById.get(relation.getRoleId());
                    return role != null
                            && StringUtils.equals(role.getType(), UserRoleType.ORGANIZATION.name())
                            && StringUtils.equals(relation.getSourceId(), userDTO.getLastOrganizationId());
                });
        mergeScope(result.getProject(), resourcesByScope.get(UserRoleType.PROJECT.name()), userDTO, rolesById,
                oldPermissionsByRole, uiPermissionsByRole, relation -> {
                    UserRole role = rolesById.get(relation.getRoleId());
                    return role != null
                            && StringUtils.equals(role.getType(), UserRoleType.PROJECT.name())
                            && StringUtils.equals(relation.getSourceId(), userDTO.getLastProjectId());
                });
        return result;
    }

    private void mergeScope(UiPermissionSetDTO target,
                            List<PermissionResource> resources,
                            UserDTO userDTO,
                            Map<String, UserRole> rolesById,
                            Map<String, Set<String>> oldPermissionsByRole,
                            Map<String, List<UserRoleUiPermission>> uiPermissionsByRole,
                            Predicate<UserRoleRelation> relationFilter) {
        if (CollectionUtils.isEmpty(resources)) {
            return;
        }
        List<UserRoleRelation> relations = userDTO.getUserRoleRelations().stream()
                .filter(relationFilter)
                .toList();
        if (CollectionUtils.isEmpty(relations)) {
            return;
        }
        Map<String, PermissionResource> resourceMap = resources.stream()
                .collect(Collectors.toMap(PermissionResource::getCode, item -> item, (a, b) -> a, LinkedHashMap::new));
        for (UserRoleRelation relation : relations) {
            String roleId = relation.getRoleId();
            UserRole role = rolesById.get(roleId);
            if (role == null) {
                continue;
            }
            if (isAdminRole(roleId)) {
                addAllScopePermissions(target, resources);
                continue;
            }
            Map<String, UserRoleUiPermission> configured = uiPermissionsByRole.getOrDefault(roleId, List.of()).stream()
                    .collect(Collectors.toMap(UserRoleUiPermission::getResourceCode, item -> item, (a, b) -> a));
            Set<String> oldPermissions = oldPermissionsByRole.getOrDefault(roleId, Set.of());
            for (PermissionResource resource : resources) {
                UserRoleUiPermission uiPermission = configured.get(resource.getCode());
                if (uiPermission != null) {
                    if (BooleanUtils.isTrue(uiPermission.getVisible()) || BooleanUtils.isTrue(uiPermission.getOperable())) {
                        target.getVisible().add(resource.getCode());
                        addParentsVisible(target.getVisible(), resourceMap, resource);
                    }
                    if (BooleanUtils.isTrue(uiPermission.getOperable())
                            && (StringUtils.equals(resource.getType(), "BUTTON") || StringUtils.equals(resource.getType(), "API"))) {
                        target.getOperable().add(resource.getCode());
                    }
                    continue;
                }
                mergeLegacyPermission(target, resourceMap, resource, oldPermissions);
            }
        }
    }

    private void addAllScopePermissions(UiPermissionSetDTO target, List<PermissionResource> resources) {
        for (PermissionResource resource : resources) {
            target.getVisible().add(resource.getCode());
            if (StringUtils.equals(resource.getType(), "BUTTON") || StringUtils.equals(resource.getType(), "API")) {
                target.getOperable().add(resource.getCode());
            }
        }
    }

    private void mergeLegacyPermission(UiPermissionSetDTO target,
                                       Map<String, PermissionResource> resourceMap,
                                       PermissionResource resource,
                                       Set<String> oldPermissions) {
        String permissionId = resource.getPermissionId();
        if (StringUtils.isBlank(permissionId) || !oldPermissions.contains(permissionId)) {
            return;
        }
        target.getVisible().add(resource.getCode());
        if (StringUtils.equals(resource.getType(), "BUTTON") || StringUtils.equals(resource.getType(), "API")) {
            target.getOperable().add(resource.getCode());
            addParentsVisible(target.getVisible(), resourceMap, resource);
        }
    }

    private void addParentsVisible(Set<String> visible, Map<String, PermissionResource> resourceMap, PermissionResource resource) {
        String parentCode = resource.getParentCode();
        while (StringUtils.isNotBlank(parentCode)) {
            visible.add(parentCode);
            PermissionResource parent = resourceMap.get(parentCode);
            parentCode = parent == null ? null : parent.getParentCode();
        }
    }

    private Map<String, Set<String>> getOldPermissionsByRole(UserDTO userDTO) {
        Map<String, Set<String>> result = new HashMap<>();
        if (CollectionUtils.isEmpty(userDTO.getUserRolePermissions())) {
            return result;
        }
        for (UserRoleResourceDTO item : userDTO.getUserRolePermissions()) {
            if (item.getUserRole() == null) {
                continue;
            }
            Set<String> permissionIds = item.getUserRolePermissions() == null
                    ? new LinkedHashSet<>()
                    : item.getUserRolePermissions().stream()
                    .map(UserRolePermission::getPermissionId)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            result.put(item.getUserRole().getId(), permissionIds);
        }
        return result;
    }

    public Map<String, List<UserRoleUiPermission>> getUiPermissionsByRole(UserDTO userDTO) {
        return getUiPermissionsByRoles(userDTO == null ? null : userDTO.getUserRoles());
    }

    public Map<String, List<UserRoleUiPermission>> getUiPermissionsByRoles(List<UserRole> userRoles) {
        if (CollectionUtils.isEmpty(userRoles)) {
            return new HashMap<>();
        }
        List<String> roleIds = userRoles.stream()
                .filter(role -> role != null && BooleanUtils.isNotFalse(role.getEnabled()))
                .map(UserRole::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(roleIds)) {
            return new HashMap<>();
        }
        List<UserRoleUiPermission> records = userRoleUiPermissionMapper.selectByRoleIds(roleIds);
        return records.stream().collect(Collectors.groupingBy(UserRoleUiPermission::getRoleId));
    }

    private List<PermissionResourceDTO> buildTree(List<PermissionResource> resources) {
        if (CollectionUtils.isEmpty(resources)) {
            return new ArrayList<>();
        }
        Map<String, PermissionResourceDTO> nodeMap = resources.stream()
                .map(PermissionResourceDTO::of)
                .collect(Collectors.toMap(PermissionResourceDTO::getCode, item -> item, (a, b) -> a, LinkedHashMap::new));
        List<PermissionResourceDTO> roots = new ArrayList<>();
        for (PermissionResourceDTO node : nodeMap.values()) {
            PermissionResourceDTO parent = nodeMap.get(node.getParentCode());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private boolean isAdmin(UserDTO userDTO) {
        if (CollectionUtils.isEmpty(userDTO.getUserRoles())) {
            return false;
        }
        return userDTO.getUserRoles().stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> StringUtils.equals(role.getId(), InternalUserRole.ADMIN.getValue()));
    }

    public boolean isAdminRole(String roleId) {
        return StringUtils.equalsAny(roleId,
                InternalUserRole.ADMIN.getValue(),
                InternalUserRole.ORG_ADMIN.getValue(),
                InternalUserRole.PROJECT_ADMIN.getValue());
    }
}
