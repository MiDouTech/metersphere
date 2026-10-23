package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class FunctionalCaseCommentAccess {
    @Resource private FunctionalCaseMapper cases;
    @Resource private ProjectMapper projects;
    @Resource private UserLoginService users;

    public FunctionalCase requireCase(String caseId, String userId, boolean write) {
        FunctionalCase owner = cases.selectByPrimaryKey(caseId);
        if (owner == null || Boolean.TRUE.equals(owner.getDeleted())
                || !hasPermission(userId, owner.getProjectId(), write ? PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT : PermissionConstants.FUNCTIONAL_CASE_READ))
            throw denied();
        return owner;
    }

    public boolean hasPermission(String userId, String projectId, String permission) {
        if (userId == null || userId.isBlank() || projectId == null || projectId.isBlank()) return false;
        var project = projects.selectByPrimaryKey(projectId);
        if (project == null || !Boolean.TRUE.equals(project.getEnable())) return false;
        var user = users.getUserDTO(userId);
        return user != null && SessionUtils.hasPermission(SessionUser.fromUser(user, ""), project.getOrganizationId(), projectId, permission);
    }

    public boolean mayModerate(String userId, String projectId) {
        return hasPermission(userId, projectId, PermissionConstants.FUNCTIONAL_CASE_READ_COMMENT)
                && hasPermission(userId, projectId, PermissionConstants.FUNCTIONAL_CASE_READ_DELETE);
    }

    public static MSException denied() {
        return new MSException(MsHttpResultCode.FORBIDDEN, "评论不存在或当前账号无权操作");
    }
    public static MSException invalid(String message) { return new MSException(MsHttpResultCode.VALIDATE_FAILED, message); }
    public static MSException conflict() { return new MSException(MsHttpResultCode.CONFLICT, "评论已变更，请刷新后重试"); }
}
