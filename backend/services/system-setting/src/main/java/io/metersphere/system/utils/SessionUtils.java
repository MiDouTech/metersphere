package io.metersphere.system.utils;

import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.UserRoleScope;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.domain.UserRolePermission;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;
import java.util.stream.Collectors;

import static io.metersphere.sdk.constants.SessionConstants.ATTR_USER;

public class SessionUtils {

    private static final ThreadLocal<String> projectId = new ThreadLocal<>();
    private static final ThreadLocal<String> organizationId = new ThreadLocal<>();

    public static String getUserId() {
        SessionUser user = getUser();
        return user == null ? null : user.getId();
    }

    public static SessionUser getUser() {
        try {
            Subject subject = SecurityUtils.getSubject();
            Session session = subject.getSession();
            return (SessionUser) session.getAttribute(ATTR_USER);
        } catch (Exception e) {
            LogUtils.warn("后台获取在线用户失败: " + e.getMessage());
            return null;
        }
    }

    public static String getSessionId() {
        return (String) SecurityUtils.getSubject().getSession().getId();
    }

    /**
     * 踢除用户
     *
     * @param username
     */
    public static void kickOutUser(String username) {
        // redis session
        RedisIndexedSessionRepository sessionRepository = CommonBeanFactory.getBean(RedisIndexedSessionRepository.class);
        if (sessionRepository == null) {
            return;
        }
        Map<String, ?> users = sessionRepository.findByPrincipalName(username);
        if (MapUtils.isNotEmpty(users)) {
            users.keySet().forEach(k -> {
                sessionRepository.deleteById(k);
                sessionRepository.getSessionRedisOperations().delete("spring:session:sessions:" + k);
            });
        }
    }

    //
    public static void putUser(SessionUser sessionUser) {
        SecurityUtils.getSubject().getSession().setAttribute(ATTR_USER, sessionUser);
        SecurityUtils.getSubject().getSession().setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, sessionUser.getId());
    }

    /**
     * 权限验证时从 controller 参数列表中找到 organizationId 传入
     */
    public static void setCurrentOrganizationId(String organizationId) {
        SessionUtils.organizationId.set(organizationId);
    }

    /**
     * 权限验证时从 controller 参数列表中找到 projectId 传入
     */
    public static void setCurrentProjectId(String projectId) {
        SessionUtils.projectId.set(projectId);
    }

    public static String getCurrentOrganizationId() {
        if (StringUtils.isNotEmpty(organizationId.get())) {
            return organizationId.get();
        }
        try {
            HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getRequest();
            LogUtils.debug("ORGANIZATION: {}", request.getHeader("ORGANIZATION"));
            if (request.getHeader("ORGANIZATION") != null) {
                return request.getHeader("ORGANIZATION");
            }
        } catch (Exception e) {
            LogUtils.error(e.getMessage(), e);
        }
        return getUser().getLastOrganizationId();
    }

    public static String getCurrentProjectId() {
        if (StringUtils.isNotEmpty(projectId.get())) {
            return projectId.get();
        }
        try {
            HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.currentRequestAttributes())).getRequest();
            LogUtils.debug("PROJECT: {}", request.getHeader("PROJECT"));
            if (request.getHeader("PROJECT") != null) {
                return request.getHeader("PROJECT");
            }
        } catch (Exception e) {
            LogUtils.error(e.getMessage(), e);
        }
        return getUser().getLastProjectId();
    }

    public static String getHttpHeader(String headerName) {
        if (StringUtils.isBlank(headerName)) {
            return null;
        }
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return request.getHeader(headerName);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasPermission(String organizationId, String projectId, String permission) {
        Map<String, List<UserRolePermission>> userRolePermissions = new HashMap<>();
        Map<String, UserRole> role = new HashMap<>();
        SessionUser user = Objects.requireNonNull(SessionUtils.getUser());
        user.getUserRoleRelations().forEach(ug -> user.getUserRolePermissions().forEach(gp -> {
            if (StringUtils.equalsIgnoreCase(gp.getUserRole().getId(), ug.getRoleId())) {
                userRolePermissions.put(ug.getId(), gp.getUserRolePermissions());
                role.put(ug.getId(), gp.getUserRole());
            }
        }));

        if (isAdminForScope(user, role, organizationId, projectId)) {
            return true;
        }


        Set<String> currentProjectPermissions = getCurrentProjectPermissions(userRolePermissions, projectId, role, user);
        if (currentProjectPermissions.contains(permission)) {
            return true;
        }

        Set<String> currentOrganizationPermissions = getCurrentOrganizationPermissions(userRolePermissions, organizationId, role, user);
        if (currentOrganizationPermissions.contains(permission)) {
            return true;
        }

        Set<String> systemPermissions = getSystemPermissions(userRolePermissions, role, user);
        return systemPermissions.contains(permission);
    }

    public static boolean isAdminForScope(String organizationId, String projectId) {
        SessionUser user = SessionUtils.getUser();
        if (user == null) {
            return false;
        }
        Map<String, UserRole> rolesByRelationId = new HashMap<>();
        user.getUserRoleRelations().forEach(relation -> user.getUserRoles().stream()
                .filter(role -> StringUtils.equalsIgnoreCase(role.getId(), relation.getRoleId()))
                .findFirst()
                .ifPresent(role -> rolesByRelationId.put(relation.getId(), role)));
        return isAdminForScope(user, rolesByRelationId, organizationId, projectId);
    }

    private static boolean isAdminForScope(SessionUser user,
                                           Map<String, UserRole> rolesByRelationId,
                                           String organizationId,
                                           String projectId) {
        if (hasActiveRoleAssignment(user, rolesByRelationId, InternalUserRole.ADMIN.getValue(),
                UserRoleType.SYSTEM.name(), UserRoleScope.SYSTEM)) {
            return true;
        }
        if (StringUtils.isNotBlank(projectId) && hasActiveRoleAssignment(user, rolesByRelationId,
                InternalUserRole.PROJECT_ADMIN.getValue(), UserRoleType.PROJECT.name(), projectId)) {
            return true;
        }
        return StringUtils.isNotBlank(organizationId)
                && hasActiveRoleAssignment(user, rolesByRelationId, InternalUserRole.ORG_ADMIN.getValue(),
                UserRoleType.ORGANIZATION.name(), organizationId)
                && projectBelongsToOrganization(projectId, organizationId);
    }

    private static boolean hasActiveRoleAssignment(SessionUser user,
                                                   Map<String, UserRole> rolesByRelationId,
                                                   String roleId,
                                                   String roleType,
                                                   String sourceId) {
        return user.getUserRoleRelations().stream()
                .filter(relation -> StringUtils.equalsIgnoreCase(relation.getRoleId(), roleId))
                .filter(relation -> StringUtils.equalsIgnoreCase(relation.getSourceId(), sourceId))
                .map(relation -> rolesByRelationId.get(relation.getId()))
                .filter(Objects::nonNull)
                .anyMatch(role -> StringUtils.equalsIgnoreCase(role.getId(), roleId)
                        && StringUtils.equalsIgnoreCase(role.getType(), roleType)
                        && BooleanUtils.isNotFalse(role.getEnabled()));
    }

    private static boolean projectBelongsToOrganization(String projectId, String organizationId) {
        if (StringUtils.isBlank(projectId)) {
            return true;
        }
        ProjectMapper projectMapper = CommonBeanFactory.getBean(ProjectMapper.class);
        if (projectMapper == null) {
            return false;
        }
        Project project = projectMapper.selectByPrimaryKey(projectId);
        return project != null
                && BooleanUtils.isNotTrue(project.getDeleted())
                && StringUtils.equals(project.getOrganizationId(), organizationId);
    }

    public static Set<String> getSystemPermissions(Map<String, List<UserRolePermission>> userRolePermissions, Map<String, UserRole> role, SessionUser user) {
        return user.getUserRoleRelations().stream()
                .filter(ug -> role.get(ug.getId()) != null && StringUtils.equalsIgnoreCase(role.get(ug.getId()).getType(), UserRoleType.SYSTEM.name()))
                .filter(ug -> BooleanUtils.isNotFalse(role.get(ug.getId()).getEnabled()))
                .filter(ug -> StringUtils.equalsIgnoreCase(ug.getSourceId(), UserRoleScope.SYSTEM))
                .flatMap(ug -> userRolePermissions.get(ug.getId()).stream())
                .map(UserRolePermission::getPermissionId)
                .collect(Collectors.toSet());
    }

    public static Set<String> getCurrentOrganizationPermissions(Map<String, List<UserRolePermission>> userRolePermissions, String organizationId, Map<String, UserRole> role, SessionUser user) {
        return user.getUserRoleRelations().stream()
                .filter(ug -> role.get(ug.getId()) != null && StringUtils.equalsIgnoreCase(role.get(ug.getId()).getType(), UserRoleType.ORGANIZATION.name()))
                .filter(ug -> BooleanUtils.isNotFalse(role.get(ug.getId()).getEnabled()))
                .filter(ug -> StringUtils.equalsIgnoreCase(ug.getSourceId(), organizationId))
                .flatMap(ug -> userRolePermissions.get(ug.getId()).stream())
                .map(UserRolePermission::getPermissionId)
                .collect(Collectors.toSet());
    }

    public static Set<String> getCurrentProjectPermissions(Map<String, List<UserRolePermission>> userRolePermissions, String projectId, Map<String, UserRole> role, SessionUser user) {
        return user.getUserRoleRelations().stream()
                .filter(ug -> role.get(ug.getId()) != null && StringUtils.equalsIgnoreCase(role.get(ug.getId()).getType(), UserRoleType.PROJECT.name()))
                .filter(ug -> BooleanUtils.isNotFalse(role.get(ug.getId()).getEnabled()))
                .filter(ug -> StringUtils.equalsIgnoreCase(ug.getSourceId(), projectId))
                .flatMap(ug -> userRolePermissions.get(ug.getId()).stream())
                .map(UserRolePermission::getPermissionId)
                .collect(Collectors.toSet());
    }

    public static void clearCurrentOrganizationId() {
        organizationId.remove();
    }

    public static void clearCurrentProjectId() {
        projectId.remove();
    }
}
