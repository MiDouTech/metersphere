package io.metersphere.agent.service;

import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.utils.SessionUtils;
import org.springframework.stereotype.Service;

/** Use the same project/system inheritance as BugService, with a freshly loaded user on every check. */
@Service
public class AgentBugImagePermission {
    private final UserLoginService users;
    private final ProjectMapper projects;
    public AgentBugImagePermission(UserLoginService users, ProjectMapper projects) { this.users = users; this.projects = projects; }

    public void require(String userId, String projectId) {
        var project = projects.selectByPrimaryKey(projectId);
        if (project == null || Boolean.TRUE.equals(project.getDeleted())) throw AgentBusinessAccess.denied();
        io.metersphere.system.dto.user.UserDTO user;
        try { user = users.getUserDTO(userId); }
        catch (org.apache.shiro.authc.DisabledAccountException disabled) { throw AgentBusinessAccess.denied(); }
        if (user == null || !Boolean.TRUE.equals(user.getEnable()) || Boolean.TRUE.equals(user.getDeleted())) throw AgentBusinessAccess.denied();
        // This creates only an in-memory permission view, never a login/session or a business record.
        if (!SessionUtils.hasPermission(SessionUser.fromUser(user, "bug-image-read"), null, projectId, PermissionConstants.PROJECT_BUG_READ))
            throw AgentBusinessAccess.denied();
    }
}
