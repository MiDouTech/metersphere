package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.utils.SessionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Current, database-backed identities. No legacy scope or administrator role implies human review capability. */
@Service
public class AgentProtocolAccess {
    public record Actor(String organization,String user,String agent,String person,boolean human) {
        public String stableKey(){return hash(List.of(organization,user,agent));}
    }
    private final JdbcTemplate jdbc;
    private final AgentBusinessAccess access;
    public AgentProtocolAccess(JdbcTemplate jdbc,AgentBusinessAccess access){this.jdbc=jdbc;this.access=access;}

    public Actor actor(String organization,boolean lock) {
        return actor(organization,lock,true);
    }
    public Actor sessionActor(String organization,boolean lock){return actor(organization,lock,false);}
    public Actor knowledgeActor(String organization,boolean lock){return actor(organization,lock,false);}
    private Actor actor(String organization,boolean lock,boolean requirePerson) {
        String user;
        String agent="HUMAN";
        boolean human=AgentTokenContext.get()==null;
        if(human) {
            user=SessionUtils.getUserId();
            require(user!=null && organization.equals(SessionUtils.getCurrentOrganizationId()),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        } else {
            var token=access.activeToken(AgentTokenScope.SESSION_READ_V1,AgentTokenScope.SESSION_EXECUTE_V1,
                    AgentTokenScope.KNOWLEDGE_READ_V1,AgentTokenScope.KNOWLEDGE_EDIT_V1);
            user=token.getUserId();
            if(lock)jdbc.queryForList("SELECT id FROM agent_token WHERE id=? FOR UPDATE",token.getId());
            var rows=jdbc.queryForList("SELECT * FROM msp_protocol_subject WHERE token_id=?"+(lock?" FOR UPDATE":""),token.getId());
            if(rows.size()!=1)throw AgentBusinessAccess.denied();
            var subject=rows.getFirst();
            require(organization.equals(subject.get("organization_id")) && user.equals(subject.get("user_id"))
                    && Boolean.TRUE.equals(subject.get("enabled")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
            agent=(String)subject.get("agent_ref");
        }
        if(lock) {
            jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE",user);
            var roles=jdbc.queryForList("SELECT role_id FROM user_role_relation WHERE user_id=? ORDER BY role_id FOR UPDATE",String.class,user).stream().distinct().sorted().toList();
            for(String role:roles){jdbc.queryForList("SELECT id FROM user_role WHERE id=? FOR UPDATE",role);jdbc.queryForList("SELECT permission_id FROM user_role_permission WHERE role_id=? ORDER BY permission_id FOR UPDATE",role);}
        }
        require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,user)==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        require(member(user,organization),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        var identities=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE user_id=? AND organization_id=?"+(lock?" FOR UPDATE":""),user,organization);
        require(!requirePerson||identities.size()==1,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
        return new Actor(organization,user,agent,identities.size()==1?(String)identities.getFirst().get("person_ref"):null,human);
    }

    public void project(Actor actor,String project,boolean write) {
        if(!actor.human()) {
            access.project(project,write?"FUNCTIONAL_CASE:READ+UPDATE":"FUNCTIONAL_CASE:READ",write,
                    write?AgentTokenScope.SESSION_EXECUTE_V1:AgentTokenScope.SESSION_READ_V1);
        } else require(access.userHasPermission(actor.user(),project,write?"FUNCTIONAL_CASE:READ+UPDATE":"FUNCTIONAL_CASE:READ"),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=? AND organization_id=? AND deleted=0"+(write?" AND enable=1":""),Long.class,project,actor.organization())==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
    }

    public void capability(Actor actor,String environment,String capability,boolean humanOnly) {
        require(!humanOnly || actor.human(),"PROTOCOL_HUMAN_REQUIRED");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability=? AND expires_at>?",
                Long.class,actor.organization(),actor.user(),environment,capability,System.currentTimeMillis())==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
    }
    public void caseIdentity(String project,String caseId){var rows=jdbc.queryForList("SELECT id,project_id FROM functional_case WHERE id=? AND project_id=? AND deleted=0",caseId,project);require(rows.size()==1&&caseId.equals(rows.getFirst().get("id"))&&project.equals(rows.getFirst().get("project_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");}
    public void task(Actor actor,String project,String task,boolean write){
        require(access.userHasPermission(actor.user(),project,write?"AI_EXECUTION:RUN":"AI_EXECUTION:READ"),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        var identities=jdbc.queryForList("SELECT id,project_id FROM ai_execution_task WHERE id=? AND project_id=?",task,project);require(identities.size()==1&&task.equals(identities.getFirst().get("id"))&&project.equals(identities.getFirst().get("project_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
    }

    public void lockGrants(Actor actor,String environment) {
        jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? ORDER BY capability FOR UPDATE",actor.organization(),actor.user(),environment);
    }

    public String organizationForProject(String project) {
        var rows=jdbc.queryForList("SELECT organization_id FROM project WHERE id=? AND deleted=0",String.class,project);
        if(rows.size()!=1)throw AgentBusinessAccess.denied();return rows.getFirst();
    }

    public void trustedAdministrator(String organization) {
        require(AgentTokenContext.get()==null,"PROTOCOL_HUMAN_REQUIRED");
        String user=SessionUtils.getUserId();
        require(user!=null && organization.equals(SessionUtils.getCurrentOrganizationId()),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())lockUserAuthorization(user);
        long count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM user u JOIN user_role_relation r ON r.user_id=u.id
            JOIN user_role role ON role.id=r.role_id AND role.enabled=1
            WHERE u.id=? AND u.enable=1 AND u.deleted=0 AND
             ((r.source_id='system' AND role.type='SYSTEM' AND (role.id='admin' OR EXISTS
                (SELECT 1 FROM user_role_permission p WHERE p.role_id=role.id AND p.permission_id='SYSTEM_USER_ROLE:READ+UPDATE')))
              OR (r.source_id=? AND role.type='ORGANIZATION' AND EXISTS
                (SELECT 1 FROM user_role_permission p WHERE p.role_id=role.id AND p.permission_id='ORGANIZATION_USER_ROLE:READ+UPDATE')))
            """,Long.class,user,organization);
        require(count>0,"AGENT_RESOURCE_NOT_ACCESSIBLE");
    }
    public boolean member(String user,String organization) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_role_relation r JOIN user_role role ON role.id=r.role_id AND role.enabled=1
            WHERE r.user_id=? AND ((r.source_id=? AND role.type='ORGANIZATION')
            OR (role.type='PROJECT' AND EXISTS (SELECT 1 FROM project p WHERE p.id=r.source_id AND p.organization_id=? AND p.deleted=0))
            OR (r.source_id='system' AND role.type='SYSTEM' AND role.id='admin'))
            """,Long.class,user,organization,organization)>0;
    }
    public void lockUserAuthorization(String user) {
        jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE",user);
        var roles=jdbc.queryForList("SELECT role_id FROM user_role_relation WHERE user_id=? ORDER BY role_id FOR UPDATE",String.class,user).stream().distinct().sorted().toList();
        for(String role:roles){jdbc.queryForList("SELECT id FROM user_role WHERE id=? FOR UPDATE",role);jdbc.queryForList("SELECT permission_id FROM user_role_permission WHERE role_id=? ORDER BY permission_id FOR UPDATE",role);}
    }
    public void requireAuthorizationSigner(String user,String person,String project,String organization,String environment){lockUserAuthorization(user);require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,user)==1&&member(user,organization)&&access.userHasPermission(user,project,"FUNCTIONAL_CASE:READ+UPDATE")&&access.userHasPermission(user,project,"AI_EXECUTION:RUN"),"AGENT_RESOURCE_NOT_ACCESSIBLE");jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? FOR UPDATE",organization,user,environment);require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='AUTHORIZE_TASK' AND expires_at>?",Long.class,organization,user,environment,System.currentTimeMillis())==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");var identities=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",String.class,organization,user);require(identities.size()==1&&person.equals(identities.getFirst()),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");}
    public void requireAcceptedReviewer(Map<String,Object> session,Map<String,Object> data){
        String user=text(data,"reviewerUserId"),person=text(data,"reviewerPerson"),org=(String)session.get("organization_id"),env=(String)session.get("environment_id");lockUserAuthorization(user);
        require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,user)==1&&member(user,org)&&access.userHasPermission(user,(String)session.get("project_id"),"FUNCTIONAL_CASE:READ"),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? ORDER BY capability FOR UPDATE",org,user,env);
        for(String capability:List.of("RESULT_REVIEW","VERIFY_EXTERNAL","EVIDENCE_READ"))require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability=? AND expires_at>?",Long.class,org,user,env,capability,System.currentTimeMillis())==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
        var identities=new TreeMap<String,Object>();identities.putAll(object(data.getOrDefault("executorIdentities",Map.of())));identities.putAll(object(object(data.getOrDefault("cleanup",Map.of())).getOrDefault("executors",Map.of())));identities.putAll(object(data.getOrDefault("evidenceIdentities",Map.of())));var contract=object(data.get("contract"));identities.put(text(contract,"authorUserId"),text(contract,"authorPerson"));require(!identities.containsKey(user),"PROTOCOL_REVIEW_CONFLICT");identities.put(user,person);
        for(var identity:identities.entrySet()){var actual=jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",String.class,org,identity.getKey());require(actual.size()==1&&Objects.equals(actual.getFirst(),identity.getValue()),"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");if(!user.equals(identity.getKey()))require(!person.equals(actual.getFirst()),"PROTOCOL_REVIEW_CONFLICT");}
        require(!strings(data.getOrDefault("executorPersons",List.of())).contains(person)&&!strings(data.getOrDefault("evidencePersons",List.of())).contains(person)&&!person.equals(contract.get("authorPerson")),"PROTOCOL_REVIEW_CONFLICT");
    }
    public void requireTaskSourceWrite(String user,String project,String organization,String environment){requireSourceWrite(user,project,organization,environment);require(access.userHasPermission(user,project,"AI_EXECUTION:RUN"),"AGENT_RESOURCE_NOT_ACCESSIBLE");}
    public void requireResponsible(String user,String project,String organization,String environment,boolean lock){if(lock)lockUserAuthorization(user);require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,user)==1&&member(user,organization)&&access.userHasPermission(user,project,"FUNCTIONAL_CASE:READ"),"PROTOCOL_GOVERNANCE_REQUIRED");if(lock){jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? ORDER BY capability FOR UPDATE",organization,user,environment);jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE organization_id=? AND user_id=? FOR UPDATE",organization,user);}require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='GOVERN' AND expires_at>?",Long.class,organization,user,environment,System.currentTimeMillis())==1&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_identity WHERE organization_id=? AND user_id=?",Long.class,organization,user)==1,"PROTOCOL_GOVERNANCE_REQUIRED");}
    public void requireSourceWrite(String user,String project,String organization,String environment) {
        lockUserAuthorization(user);
        require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,user)==1&&member(user,organization)&&access.userHasPermission(user,project,"FUNCTIONAL_CASE:READ+UPDATE"),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? ORDER BY capability FOR UPDATE",organization,user,environment);
        require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='EXECUTE' AND expires_at>?",Long.class,organization,user,environment,System.currentTimeMillis())==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
    }
}
