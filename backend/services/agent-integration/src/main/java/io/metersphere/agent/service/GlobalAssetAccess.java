package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.system.utils.SessionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Organization asset authorization; execution and source permissions remain project scoped. */
@Service
public class GlobalAssetAccess {
  public record Scope(String organizationId, String actorId) {}

  private final JdbcTemplate jdbc;
  private final AgentBusinessAccess access;

  public GlobalAssetAccess(JdbcTemplate jdbc, AgentBusinessAccess access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  public Scope context(String sourceProjectId) {
    String actor;
    String organization;
    if (AgentTokenContext.get() != null) {
      var token = access.activeToken();
      if (StringUtils.isBlank(sourceProjectId)
          || !AgentTokenProjectAccess.allows(token, sourceProjectId))
        throw AgentBusinessAccess.denied();
      var organizations =
          jdbc.queryForList(
              "SELECT organization_id FROM project WHERE id=? AND deleted=0 AND enable=1",
              String.class,
              sourceProjectId);
      if (organizations.size() != 1) throw AgentBusinessAccess.denied();
      organization = organizations.getFirst();
      actor = token.getUserId();
    } else {
      actor = SessionUtils.getUserId();
      organization = SessionUtils.getCurrentOrganizationId();
      if (StringUtils.isBlank(actor) || !SecurityUtils.getSubject().isAuthenticated())
        throw AgentBusinessAccess.denied();
    }
    if (StringUtils.isBlank(organization)) throw AgentBusinessAccess.denied();
    if (AgentTokenContext.get() == null && StringUtils.isNotBlank(sourceProjectId)) {
      Long sameOrganization =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM project WHERE id=? AND organization_id=? AND deleted=0",
              Long.class,
              sourceProjectId,
              organization);
      if (sameOrganization == null || sameOrganization != 1) throw AgentBusinessAccess.denied();
    }
    Long active =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM user u JOIN organization o ON o.id=? WHERE u.id=? AND u.enable=1"
                + " AND u.deleted=0 AND o.enable=1 AND o.deleted=0",
            Long.class,
            organization,
            actor);
    if (active == null || active != 1) throw AgentBusinessAccess.denied();
    return new Scope(organization, actor);
  }

  public Scope authorize(String sourceProjectId, String permission) {
    Scope scope = context(sourceProjectId);
    return authorizeScope(scope, permission);
  }

  /** Internal execution delegation only. Actor must come from a bound persisted preflight. */
  Scope authorizeForActor(String project, String actor, String permission) {
    if (StringUtils.isAnyBlank(project, actor)) throw AgentBusinessAccess.denied();
    var organizations =
        jdbc.queryForList(
            "SELECT organization_id FROM project WHERE id=? AND deleted=0 AND enable=1",
            String.class,
            project);
    if (organizations.size() != 1) throw AgentBusinessAccess.denied();
    return authorizeScope(new Scope(organizations.getFirst(), actor), permission);
  }

  private Scope authorizeScope(Scope scope, String permission) {
    String organization = scope.organizationId();
    String actor = scope.actorId();
    Long permitted =
        jdbc.queryForObject(
            """
SELECT COUNT(*) FROM user u JOIN user_role_relation rel ON rel.user_id=u.id
JOIN user_role role ON role.id=rel.role_id AND role.enabled=1
JOIN organization org ON org.id=? AND org.enable=1 AND org.deleted=0
WHERE u.id=? AND u.enable=1 AND u.deleted=0 AND (
  (rel.source_id='system' AND role.type='SYSTEM' AND (role.id='admin' OR EXISTS
    (SELECT 1 FROM user_role_permission p WHERE p.role_id=role.id AND p.permission_id=?)))
  OR (rel.source_id=org.id AND role.type='ORGANIZATION' AND (role.id='org_admin' OR EXISTS
    (SELECT 1 FROM user_role_permission p WHERE p.role_id=role.id AND p.permission_id=?))))
""",
            Long.class,
            organization,
            actor,
            permission,
            permission);
    if (permitted == null || permitted < 1) throw AgentBusinessAccess.denied();
    return new Scope(organization, actor);
  }
}
