package io.metersphere.agent.quality;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.utils.SessionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Component;

@Component
public class QualityPolicyAccess {
    private final AgentProjectService projects;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public QualityPolicyAccess(@org.springframework.beans.factory.annotation.Qualifier("agentProjectService") AgentProjectService projects,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.projects = projects;
        this.jdbc = jdbc;
    }

    public String requireSystem(String permission) {
        String actor = SessionUtils.getUserId();
        if (StringUtils.isBlank(actor)) throw new MSException("AUTHENTICATION_REQUIRED");
        if (AgentTokenContext.get() != null) throw new MSException("QUALITY_POLICY_FORBIDDEN");
        if (!java.util.Set.of(PermissionConstants.SYSTEM_QUALITY_READ,
                PermissionConstants.SYSTEM_QUALITY_MANAGE, PermissionConstants.SYSTEM_QUALITY_PUBLISH).contains(permission)) {
            throw new MSException("PERMISSION_DENIED");
        }
        Integer grants = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role_relation rel
                JOIN user_role role ON role.id=rel.role_id
                WHERE rel.user_id=? AND rel.source_id='system' AND role.type='SYSTEM' AND role.enabled=1
                  AND (role.id='admin' OR EXISTS (SELECT 1 FROM user_role_permission perm
                       WHERE perm.role_id=role.id AND perm.permission_id=?))
                """, Integer.class, actor, permission);
        if (grants == null || grants == 0) throw new MSException("PERMISSION_DENIED");
        return actor;
    }

    public String require(String projectId, String permission) {
        String actor = SessionUtils.getUserId();
        if (StringUtils.isBlank(actor)) throw new MSException("AUTHENTICATION_REQUIRED");
        // Policy management is never inherited from a token owner's broad role.
        if (AgentTokenContext.get() != null) throw new MSException("QUALITY_POLICY_FORBIDDEN");
        if (StringUtils.isBlank(projectId) || !projectId.equals(SessionUtils.getCurrentProjectId())) {
            throw new MSException("QUALITY_POLICY_PROJECT_FORBIDDEN");
        }
        SecurityUtils.getSubject().checkPermission(permission);
        Integer grants = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_role_relation rel
                JOIN user_role role ON role.id=rel.role_id
                JOIN user_role_permission perm ON perm.role_id=role.id
                WHERE rel.user_id=? AND rel.source_id=? AND role.type='PROJECT'
                  AND role.enabled=1 AND perm.permission_id=?
                """, Integer.class, actor, projectId, permission);
        if (grants == null || grants == 0) throw new MSException("PERMISSION_DENIED");
        if (!projectId.equals(projects.resolveProjectId(projectId))) throw new MSException("QUALITY_POLICY_PROJECT_FORBIDDEN");
        if (!PermissionConstants.QUALITY_READ.equals(permission)) {
            Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0 AND enable=1",
                    Integer.class, projectId);
            if (active == null || active == 0) throw new MSException("QUALITY_POLICY_PROJECT_DISABLED");
        }
        return actor;
    }
}
