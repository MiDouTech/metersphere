package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.agent.security.AgentTokenScopeParser;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

/** Shared, current-state authorization for Agent HTTP and MCP application services. */
@Service
public class AgentBusinessAccess {
    private final JdbcTemplate jdbc;
    public AgentBusinessAccess(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String project(String projectId, String permission, boolean write, String... scopes) {
        AgentToken context = AgentTokenContext.get();
        if (context == null || StringUtils.isBlank(context.getId())) throw denied();
        var tokens = jdbc.query("SELECT * FROM agent_token WHERE id=?", BeanPropertyRowMapper.newInstance(AgentToken.class), context.getId());
        if (tokens.size()!=1) throw denied();
        var token=tokens.getFirst();
        if (!Boolean.TRUE.equals(token.getEnable()) || token.getRevokedAt()!=null
                || (token.getStatus()!=null && !"ACTIVE".equals(token.getStatus()))
                || (token.getExpireTime()!=null && token.getExpireTime()<=System.currentTimeMillis())
                || !java.util.Objects.equals(context.getUserId(),token.getUserId())) throw denied();
        if (Arrays.stream(scopes).noneMatch(scope->AgentTokenScopeParser.hasScope(token.getScopes(),scope))) throw denied();
        if (StringUtils.isBlank(projectId) || !AgentTokenProjectAccess.allows(token,projectId)) throw denied();
        long active=jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND deleted=0"+(write?" AND enable=1":""),Long.class,projectId);
        if (active!=1 || !userHasPermission(token.getUserId(),projectId,permission)) throw denied();
        return token.getUserId();
    }

    public boolean userHasPermission(String userId,String projectId,String permission) {
        Long count=jdbc.queryForObject("""
                SELECT COUNT(*) FROM user u JOIN user_role_relation rel ON rel.user_id=u.id
                JOIN user_role role ON role.id=rel.role_id AND role.enabled=1
                WHERE u.id=? AND u.enable=1 AND u.deleted=0 AND (
                  (rel.source_id='system' AND role.type='SYSTEM' AND role.id='admin') OR
                  (rel.source_id=? AND role.type='PROJECT' AND EXISTS
                    (SELECT 1 FROM user_role_permission perm WHERE perm.role_id=role.id AND perm.permission_id=?)))
                """,Long.class,userId,projectId,permission);
        return count!=null && count>0;
    }

    public String caseProject(String caseId,String selectedProject,String permission,boolean write,String... scopes) {
        var projects=jdbc.queryForList("SELECT project_id FROM functional_case WHERE id=? AND deleted=0",String.class,caseId);
        if(projects.size()!=1 || (StringUtils.isNotBlank(selectedProject) && !selectedProject.equals(projects.getFirst())))throw denied();
        project(projects.getFirst(),permission,write,scopes);
        return projects.getFirst();
    }

    public void cases(String projectId,List<String> caseIds) {
        if(caseIds==null)return;
        if(caseIds.size()>100 || caseIds.stream().anyMatch(StringUtils::isBlank) || caseIds.stream().distinct().count()!=caseIds.size())throw new MSException("AGENT_VALIDATION_ERROR");
        for(String id:caseIds) {
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM functional_case WHERE id=? AND project_id=? AND deleted=0",Long.class,id,projectId);
            if(count==null || count!=1)throw denied();
        }
    }

    public void reviewers(String projectId,List<String> reviewers) {
        if(reviewers==null || reviewers.isEmpty() || reviewers.size()>100)throw new MSException("AGENT_VALIDATION_ERROR");
        for(String user:reviewers)if(!userHasPermission(user,projectId,io.metersphere.sdk.constants.PermissionConstants.CASE_REVIEW_REVIEW))throw denied();
    }

    public void module(String projectId,String moduleId,boolean review) {
        if(StringUtils.isBlank(moduleId) || "root".equals(moduleId))return;
        String table=review?"case_review_module":"functional_case_module";
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND project_id=?",Long.class,moduleId,projectId);
        if(count==null || count!=1)throw denied();
    }

    public void commentParent(String caseId, String parentId, String replyUser) {
        if (StringUtils.isBlank(parentId)) throw new MSException("AGENT_VALIDATION_ERROR");
        var users = jdbc.queryForList("SELECT create_user FROM functional_case_comment WHERE id=? AND case_id=?", String.class, parentId, caseId);
        if (users.size()!=1 || !java.util.Objects.equals(users.getFirst(), replyUser)) throw denied();
    }

    public static MSException denied() { return new MSException("AGENT_RESOURCE_NOT_ACCESSIBLE"); }
}
