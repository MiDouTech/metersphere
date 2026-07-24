package io.metersphere.plan.hub.service;

import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.InternalUserRole;
import io.metersphere.sdk.constants.UserRoleType;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.service.PermissionCheckService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 手动同步权限：项目管理员或组织管理员
 */
@Service
public class DefaultHubSyncAccessService {

    @Resource
    private PermissionCheckService permissionCheckService;
    @Resource
    private ProjectMapper projectMapper;

    public void assertManualSyncPermission(String userId, String projectId) {
        UserDTO user = permissionCheckService.getUserDTO(userId);
        if (user == null) {
            throw new MSException("unauthorized");
        }
        if (permissionCheckService.checkAdmin(user)) {
            return;
        }
        if (StringUtils.isBlank(projectId)) {
            boolean orgAdmin = user.getUserRoleRelations().stream()
                    .anyMatch(r -> StringUtils.equals(r.getRoleId(), InternalUserRole.ORG_ADMIN.getValue()));
            if (!orgAdmin) {
                throw new MSException("requires organization admin for global sync");
            }
            return;
        }
        boolean projectAdmin = user.getUserRoleRelations().stream()
                .anyMatch(r -> StringUtils.equals(r.getSourceId(), projectId)
                        && StringUtils.equals(r.getRoleId(), InternalUserRole.PROJECT_ADMIN.getValue()));
        if (projectAdmin) {
            return;
        }
        Project project = projectMapper.selectByPrimaryKey(projectId);
        if (project != null) {
            boolean orgAdmin = user.getUserRoleRelations().stream()
                    .anyMatch(r -> StringUtils.equals(r.getSourceId(), project.getOrganizationId())
                            && StringUtils.equals(r.getRoleId(), InternalUserRole.ORG_ADMIN.getValue()));
            if (orgAdmin) {
                return;
            }
        }
        throw new MSException("requires project admin or organization admin");
    }
}
