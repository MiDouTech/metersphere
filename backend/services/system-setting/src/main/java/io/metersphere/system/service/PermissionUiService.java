package io.metersphere.system.service;

import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.PermissionResource;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRolePermission;
import io.metersphere.system.domain.UserRoleRelation;
import io.metersphere.system.dto.permission.PermissionResourceDTO;
import io.metersphere.system.dto.permission.UiPermissionSetDTO;
import io.metersphere.system.dto.permission.UserUiPermissionsDTO;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.dto.user.UserRoleResourceDTO;
import io.metersphere.system.mapper.PermissionResourceMapper;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
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
    private ProjectMapper projectMapper;

    public List<PermissionResourceDTO> getResourceTree(String scopeType) {
        List<PermissionResource> resources = permissionResourceMapper.selectEnabledByScopeType(scopeType);
        return buildTree(resources);
    }

    public List<PermissionResourceDTO> getAllResourceTree() {
        return buildTree(permissionResourceMapper.selectEnabled());
    }

    public UserUiPermissionsDTO aggregate(UserDTO userDTO) {
        UserUiPermissionsDTO result = new UserUiPermissionsDTO();
        if (userDTO == null) return result;
        List<PermissionResource> resources = permissionResourceMapper.selectEnabled();
        // Version navigation has no additional business grant; its data APIs retain source authorization.
        resources.stream().filter(resource -> "TEST_ASSET_VERSIONS_PAGE".equals(resource.getCode()))
                .findFirst().ifPresent(resource -> {
                    registerManagedRoute(result.getOrganization(), resource);
                    result.getOrganization().getVisible().add(resource.getCode());
                    addVisibleRoute(result.getOrganization(), resource);
                });
        if (CollectionUtils.isEmpty(userDTO.getUserRoleRelations()) || CollectionUtils.isEmpty(userDTO.getUserRoles())) {
            exposeMenuAncestors(result, resources);
            return result;
        }
        Map<String, List<PermissionResource>> resourcesByScope = resources.stream()
                .collect(Collectors.groupingBy(PermissionResource::getScopeType, LinkedHashMap::new, Collectors.toList()));
        Map<String, UserRole> rolesById = userDTO.getUserRoles().stream()
                .filter(role -> BooleanUtils.isNotFalse(role.getEnabled()))
                .collect(Collectors.toMap(UserRole::getId, role -> role, (a, b) -> a));
        Map<String, Set<String>> oldPermissionsByRole = getOldPermissionsByRole(userDTO);
        mergeScope(result.getSystem(), resourcesByScope.get(UserRoleType.SYSTEM.name()), userDTO, rolesById,
                oldPermissionsByRole, relation -> {
                    UserRole role = rolesById.get(relation.getRoleId());
                    return role != null && StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name());
                });
        mergeScope(result.getOrganization(), resourcesByScope.get(UserRoleType.ORGANIZATION.name()), userDTO, rolesById,
                oldPermissionsByRole, relation -> {
                    UserRole role = rolesById.get(relation.getRoleId());
                    return role != null
                            && ((StringUtils.equals(role.getType(), UserRoleType.ORGANIZATION.name())
                            && StringUtils.equals(relation.getSourceId(), userDTO.getLastOrganizationId()))
                            || isGlobalSystemRole(relation, role));
                });
        mergeScope(result.getProject(), resourcesByScope.get(UserRoleType.PROJECT.name()), userDTO, rolesById,
                oldPermissionsByRole, relation -> {
                    UserRole role = rolesById.get(relation.getRoleId());
                    return role != null
                            && ((StringUtils.equals(role.getType(), UserRoleType.PROJECT.name())
                            && StringUtils.equals(relation.getSourceId(), userDTO.getLastProjectId()))
                            || (StringUtils.equals(role.getType(), UserRoleType.ORGANIZATION.name())
                            && StringUtils.equals(relation.getSourceId(), userDTO.getLastOrganizationId())
                            && projectBelongsToOrganization(userDTO.getLastProjectId(), userDTO.getLastOrganizationId()))
                            || isGlobalSystemRole(relation, role));
                });
        projectSourceVersionReader(result, resources, userDTO, rolesById, oldPermissionsByRole);
        exposeMenuAncestors(result, resources);
        return result;
    }

    private static final Set<String> VERSION_READ_PERMISSIONS = Set.of(
            "TEST_DATA_ASSET:READ", "BUSINESS_ASSET:READ", "SHARED_CONFIGURATION:READ",
            "CASE_ASSET:READ", "FUNCTIONAL_CASE:READ", "FUNCTIONAL_CASE_AI:READ",
            "PROJECT_FILE_MANAGEMENT:READ", "PROJECT_ENVIRONMENT:READ", "PROJECT_API_SCENARIO:READ",
            "PROJECT_API_DEFINITION:READ", "AI_EXECUTION:READ", "PROJECT_BUG:READ");

    /** Compatibility versions remain readable through an authorized source project, without global grants. */
    private void projectSourceVersionReader(UserUiPermissionsDTO result, List<PermissionResource> resources,
            UserDTO user, Map<String, UserRole> roles, Map<String, Set<String>> permissions) {
        PermissionResource versions = resources.stream().filter(resource -> "TEST_ASSET_VERSIONS_PAGE".equals(resource.getCode()))
                .findFirst().orElse(null);
        if (versions == null) return;
        registerManagedRoute(result.getOrganization(), versions);
        for (UserRoleRelation relation : user.getUserRoleRelations()) {
            UserRole role = roles.get(relation.getRoleId());
            if (role == null || !"PROJECT".equals(role.getType())) continue;
            boolean readable = "project_admin".equals(role.getId()) || VERSION_READ_PERMISSIONS.stream()
                    .anyMatch(permissions.getOrDefault(role.getId(), Set.of())::contains);
            if (!readable || !projectBelongsToOrganization(relation.getSourceId(), user.getLastOrganizationId())) continue;
            result.getOrganization().getVisible().add(versions.getCode());
            addVisibleRoute(result.getOrganization(), versions);
            return;
        }
    }

    /** A readable child exposes navigation shells across scopes, never sibling pages or APIs. */
    private void exposeMenuAncestors(UserUiPermissionsDTO result, List<PermissionResource> resources) {
        Map<String, PermissionResource> byCode = resources.stream().collect(Collectors.toMap(
                PermissionResource::getCode, item -> item, (a,b) -> a));
        Map<String, UiPermissionSetDTO> scopes = Map.of("SYSTEM", result.getSystem(),
                "ORGANIZATION", result.getOrganization(), "PROJECT", result.getProject());
        Set<String> visible = scopes.values().stream().flatMap(set -> set.getVisible().stream()).collect(Collectors.toSet());
        for (PermissionResource child : resources) {
            if (!visible.contains(child.getCode())) continue;
            Set<String> visited = new LinkedHashSet<>();
            PermissionResource parent = byCode.get(child.getParentCode());
            while (parent != null && visited.add(parent.getCode())) {
                if ("MENU".equals(parent.getType()) && scopes.containsKey(parent.getScopeType())) {
                    UiPermissionSetDTO target = scopes.get(parent.getScopeType());
                    target.getVisible().add(parent.getCode());
                    addVisibleRoute(target, parent);
                }
                parent = byCode.get(parent.getParentCode());
            }
        }
    }

    private boolean isGlobalSystemRole(UserRoleRelation relation, UserRole role) {
        return StringUtils.equals(role.getType(), UserRoleType.SYSTEM.name())
                && StringUtils.equals(relation.getSourceId(), "system");
    }

    private boolean projectBelongsToOrganization(String projectId, String organizationId) {
        if (StringUtils.isBlank(projectId) || StringUtils.isBlank(organizationId)) {
            return false;
        }
        Project project = projectMapper.selectByPrimaryKey(projectId);
        return project != null
                && BooleanUtils.isNotTrue(project.getDeleted())
                && StringUtils.equals(project.getOrganizationId(), organizationId);
    }

    private void mergeScope(UiPermissionSetDTO target,
                            List<PermissionResource> resources,
                            UserDTO userDTO,
                            Map<String, UserRole> rolesById,
                            Map<String, Set<String>> oldPermissionsByRole,
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
            Set<String> oldPermissions = oldPermissionsByRole.getOrDefault(roleId, Set.of());
            for (PermissionResource resource : resources) {
                registerManagedButtonPermission(target, resource);
                registerManagedRoute(target, resource);
                mergeUnifiedPermission(target, resourceMap, resource, oldPermissions);
            }
        }
    }

    private void addAllScopePermissions(UiPermissionSetDTO target, List<PermissionResource> resources) {
        for (PermissionResource resource : resources) {
            registerManagedButtonPermission(target, resource);
            registerManagedRoute(target, resource);
            target.getVisible().add(resource.getCode());
            addVisibleRoute(target, resource);
            addButtonPermission(target.getVisibleButtonPermissions(), resource);
            if (StringUtils.equals(resource.getType(), "BUTTON") || StringUtils.equals(resource.getType(), "API")) {
                target.getOperable().add(resource.getCode());
                addButtonPermission(target.getOperableButtonPermissions(), resource);
            }
        }
    }

    /**
     * UI resources are projections of business permissions. The legacy UI grant table is deliberately
     * not consulted here, so it can neither grant access without an API permission nor hide a page that
     * the same role is authorized to read. Resources without their own permission inherit the nearest
     * mapped parent (for example, presentation-only tabs under a readable page).
     */
    private void mergeUnifiedPermission(UiPermissionSetDTO target,
                                        Map<String, PermissionResource> resourceMap,
                                        PermissionResource resource,
                                        Set<String> permissions) {
        String permissionId = resolvePermissionId(resourceMap, resource);
        boolean versionsRead = "TEST_ASSET_VERSIONS_PAGE".equals(resource.getCode())
                && VERSION_READ_PERMISSIONS.stream().anyMatch(permissions::contains);
        if ("TEST_ASSET_VERSIONS_PAGE".equals(resource.getCode()) ? !versionsRead
                : StringUtils.isBlank(permissionId) || !permissions.contains(permissionId)) {
            return;
        }
        target.getVisible().add(resource.getCode());
        addVisibleRoute(target, resource);
        addButtonPermission(target.getVisibleButtonPermissions(), resource);
        addParentsVisible(target, resourceMap, resource);
        if (StringUtils.equals(resource.getType(), "BUTTON") || StringUtils.equals(resource.getType(), "API")) {
            target.getOperable().add(resource.getCode());
            addButtonPermission(target.getOperableButtonPermissions(), resource);
        }
    }

    private String resolvePermissionId(Map<String, PermissionResource> resourceMap, PermissionResource resource) {
        PermissionResource current = resource;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current.getCode())) {
            if (StringUtils.isNotBlank(current.getPermissionId())) {
                return current.getPermissionId();
            }
            current = resourceMap.get(current.getParentCode());
        }
        return null;
    }

    private void registerManagedButtonPermission(UiPermissionSetDTO target, PermissionResource resource) {
        if (StringUtils.equals(resource.getType(), "BUTTON") && StringUtils.isNotBlank(resource.getPermissionId())) {
            target.getManagedButtonPermissions().add(resource.getPermissionId());
        }
    }

    private void addButtonPermission(Set<String> target, PermissionResource resource) {
        if (StringUtils.equals(resource.getType(), "BUTTON") && StringUtils.isNotBlank(resource.getPermissionId())) {
            target.add(resource.getPermissionId());
        }
    }

    private void registerManagedRoute(UiPermissionSetDTO target, PermissionResource resource) {
        if (StringUtils.isNotBlank(resource.getRouteName())) {
            target.getManagedRoutes().add(resource.getRouteName());
        }
    }

    private void addVisibleRoute(UiPermissionSetDTO target, PermissionResource resource) {
        if (StringUtils.isNotBlank(resource.getRouteName())) {
            target.getVisibleRoutes().add(resource.getRouteName());
        }
    }

    private void addParentsVisible(UiPermissionSetDTO target, Map<String, PermissionResource> resourceMap, PermissionResource resource) {
        String parentCode = resource.getParentCode();
        while (StringUtils.isNotBlank(parentCode)) {
            target.getVisible().add(parentCode);
            PermissionResource parent = resourceMap.get(parentCode);
            if (parent != null) {
                addVisibleRoute(target, parent);
            }
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

    public boolean isAdminRole(String roleId) {
        return StringUtils.equalsAny(roleId,
                InternalUserRole.ADMIN.getValue(),
                InternalUserRole.ORG_ADMIN.getValue(),
                InternalUserRole.PROJECT_ADMIN.getValue());
    }
}
