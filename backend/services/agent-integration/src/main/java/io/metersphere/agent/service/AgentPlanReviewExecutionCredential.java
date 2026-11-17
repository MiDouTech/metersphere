package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.functional.planreview.PlanReviewExecutionCredential;
import org.springframework.stereotype.Component;

@Component
public class AgentPlanReviewExecutionCredential implements PlanReviewExecutionCredential,io.metersphere.functional.planreview.PlanReviewCurrentActor {
    @Override public String current(){var token=AgentTokenContext.get();return token==null?null:token.getUserId();}
    @jakarta.annotation.Resource private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Override public String currentReference(){var token=AgentTokenContext.get();return token==null?null:token.getId();}
    @Override public boolean validate(String reference,String project,String actor){
        var token=AgentTokenContext.get();
        if(token==null||!reference.equals(token.getId())||!actor.equals(token.getUserId()))return false;
        // The caller already holds the project grant/current user-role locks. A current row read
        // also fences token revocation; a cached request token or an RR snapshot is insufficient.
        var rows=jdbc.query("SELECT id,user_id,enable,status,revoked_at,expire_time,project_ids,project_id,scopes FROM agent_token WHERE id=? AND user_id=? FOR UPDATE",org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(io.metersphere.system.domain.AgentToken.class),reference,actor);
        if(rows.size()!=1)return false;return allowed(rows.getFirst(),project,actor);
    }
    @Override public boolean validateImport(String reference,String project,String actor){
        if(!validate(reference,project,actor))return false;
        String scopes=jdbc.queryForObject("SELECT scopes FROM agent_token WHERE id=? AND user_id=? FOR UPDATE",String.class,reference,actor);
        return io.metersphere.agent.security.AgentTokenScopeParser.hasScope(scopes,AgentTokenScope.CASE_IMPORT_V2);
    }
    @Override public boolean validateLinkedReview(String reference,String project,String actor){
        if(!validate(reference,project,actor))return false;
        String scopes=jdbc.queryForObject("SELECT scopes FROM agent_token WHERE id=? AND user_id=? FOR UPDATE",String.class,reference,actor);
        return io.metersphere.agent.security.AgentTokenScopeParser.hasScope(scopes,AgentTokenScope.PLAN_READ);
    }
    static boolean allowed(io.metersphere.system.domain.AgentToken current,String project,String actor){
        return identityAllowed(current,project,actor)
                &&io.metersphere.agent.security.AgentTokenScopeParser.hasScope(current.getScopes(),AgentTokenScope.PLAN_WRITE)
                &&io.metersphere.agent.security.AgentTokenScopeParser.hasScope(current.getScopes(),AgentTokenScope.REVIEW_WRITE);
    }
    static boolean identityAllowed(io.metersphere.system.domain.AgentToken current,String project,String actor){
        if(!actor.equals(current.getUserId()))return false;
        if(!Boolean.TRUE.equals(current.getEnable())||current.getRevokedAt()!=null||(current.getStatus()!=null&&!"ACTIVE".equals(current.getStatus()))||(current.getExpireTime()!=null&&current.getExpireTime()<=System.currentTimeMillis()))return false;
        if(current.getProjectIds()!=null&&!current.getProjectIds().isBlank()){
            try{io.metersphere.sdk.util.JSON.parseArray(current.getProjectIds(),String.class);}
            catch(RuntimeException invalid){return false;}
        }
        return io.metersphere.agent.security.AgentTokenProjectAccess.allows(current,project);
    }
}
