package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Approved activation/configuration has a transactional database boundary, independent of transport retries. */
@Service
public class AgentProtocolPolicyService {
    public static final List<String> NUMBERS=List.of("limits.title-scalars","limits.body-scalars","limits.reference-scalars","limits.content-utf8-bytes","limits.import-bytes","limits.evidence-total-bytes","budget.calls","budget.recoveries","budget.recovery-seconds","lease-seconds");
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;private final AgentSessionProtocolConfiguration config;
    public AgentProtocolPolicyService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,AgentSessionProtocolConfiguration config){this.jdbc=jdbc;this.store=store;this.access=access;this.config=config;}
    public Map<String,Object> current(String org,String env,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM msp_protocol_policy WHERE organization_id=? AND environment_id=?"+(lock?" FOR UPDATE":""),org,env);
        require(rows.size()==1&&Boolean.TRUE.equals(rows.getFirst().get("enabled")),"PROTOCOL_POLICY_NOT_ACTIVATED");var result=store.data(rows.getFirst());result.put("version",rows.getFirst().get("version"));return result;
    }
    public long limit(Map<String,Object>session,String key){return number(current((String)session.get("organization_id"),(String)session.get("environment_id"),true),key);}
    public Map<String,Object> configure(String org,Map<String,Object>args) {
        fields(args,"environmentId","expectedVersion","enabled","allowedActions","actionRules","approvalRecord");
        return store.transaction(()->{
            access.trustedAdministrator(org);String env=id(args,"environmentId");
            var rows=jdbc.queryForList("SELECT * FROM msp_protocol_policy WHERE organization_id=? AND environment_id=? FOR UPDATE",org,env);long version=rows.isEmpty()?0:((Number)rows.getFirst().get("version")).longValue();require(number(args,"expectedVersion")==version,"VERSION_CONFLICT");
            require(args.get("enabled") instanceof Boolean,"AGENT_VALIDATION_ERROR");var snapshot=new LinkedHashMap<String,Object>();
            if(Boolean.TRUE.equals(args.get("enabled"))) {
                config.requireExecution();require(org.equals(config.reference("pilot.organization-id"))&&env.equals(config.reference("pilot.environment-id")),"PROTOCOL_PILOT_SCOPE_DENIED");
                for(String key:NUMBERS)snapshot.put(key,config.number(key));snapshot.put("approvalVersion",config.reference("approval.version"));
                snapshot.put("approvalRecord",text(args,"approvalRecord"));snapshot.put("retentionPolicy",config.reference("retention.policy-version"));snapshot.put("retentionApproval",config.reference("retention.approval-record"));
                Long deadline=config.verificationDeadlineMillis();if(deadline!=null)snapshot.put("verificationDeadlineMillis",deadline);
                snapshot.put("allowedActions",strings(args.get("allowedActions")));require(!strings(args.get("allowedActions")).isEmpty(),"PROTOCOL_AUTHORIZATION_REQUIRED");
                var rules=object(args.get("actionRules"));require(rules.keySet().equals(new HashSet<>(strings(args.get("allowedActions")))),"PROTOCOL_AUTHORIZATION_REQUIRED");for(var value:rules.values()){var rule=object(value);fields(rule,"effect","entity");require(Set.of("READ","CREATE","MUTATE","DELETE").contains(text(rule,"effect")),"PROTOCOL_ACTION_DENIED");id(rule,"entity");}snapshot.put("actionRules",rules);
            } else if(!rows.isEmpty())snapshot.putAll(store.data(rows.getFirst()));
            jdbc.update("INSERT INTO msp_protocol_policy(organization_id,environment_id,version,enabled,payload) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE version=VALUES(version),enabled=VALUES(enabled),payload=VALUES(payload)",org,env,version+1,args.get("enabled"),store.payload(snapshot));
            access.trustedAdministrator(org);store.event("POLICY:"+org,null,uuid(),"POLICY_CONFIGURED",io.metersphere.system.utils.SessionUtils.getUserId(),Map.of("environmentId",env,"version",version+1,"enabled",args.get("enabled")));
            return Map.of("version",version+1,"enabled",args.get("enabled"));
        });
    }
    public Map<String,Object> authorize(String project,String type,String source,Map<String,Object>args) {
        fields(args,"environmentId","expectedVersion","allowedActions","expiresAt","mutationsOwnDataOnly","reason","handoffExecutionId","handoffLeaseId","handoffEvidenceRef","handoffAuthorityQuery");String org=access.organizationForProject(project);
        return store.transaction(()->{
            var actor=access.actor(org,true);access.project(actor,project,true);String env=id(args,"environmentId");access.lockGrants(actor,env);access.capability(actor,env,"AUTHORIZE_TASK",true);
            require(Set.of("PERSONAL","TASK").contains(type),"AGENT_VALIDATION_ERROR");
            if("TASK".equals(type))access.task(actor,project,source,true);else access.caseIdentity(project,source);
            String table="PERSONAL".equals(type)?"functional_case":"ai_execution_task";
            require(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND project_id=?",Long.class,source,project)==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");
            var policy=current(org,env,true);require(strings(policy.get("allowedActions")).containsAll(strings(args.get("allowedActions")))&&Boolean.TRUE.equals(args.get("mutationsOwnDataOnly")),"PROTOCOL_ACTION_DENIED");
            var rows=jdbc.queryForList("SELECT * FROM msp_protocol_authorization WHERE source_type=? AND source_id=? FOR UPDATE",type,source);long version=rows.isEmpty()?0:((Number)rows.getFirst().get("version")).longValue();require(number(args,"expectedVersion")==version,"VERSION_CONFLICT");
            require(rows.isEmpty()||(org.equals(rows.getFirst().get("organization_id"))&&env.equals(rows.getFirst().get("environment_id"))),"PROTOCOL_ENVIRONMENT_MISMATCH");
            boolean quiesced=false;var clearance=new LinkedHashMap<String,Object>();
            if("TASK".equals(type)&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_session WHERE source_type='TASK' AND source_id=? AND source_execution_id=(SELECT current_execution_id FROM ai_execution_task WHERE id=?)",Long.class,source,source)>0){if(!rows.isEmpty())clearance.putAll(object(store.data(rows.getFirst()).getOrDefault("handoffClearance",Map.of())));}
            else if("TASK".equals(type)){quiesced=true;String execution=id(args,"handoffExecutionId"),lease=id(args,"handoffLeaseId");var previous=rows.isEmpty()?Map.<String,Object>of():object(store.data(rows.getFirst()).getOrDefault("handoffClearance",Map.of()));boolean reserved=execution.equals(previous.get("executionId"))&&lease.equals(previous.get("leaseId"));var live=jdbc.queryForList("SELECT t.id,l.id AS actual_lease_id,t.current_execution_id FROM ai_execution_task t JOIN ai_runner_lease l ON l.id=t.runner_lease_id AND l.task_id=t.id AND l.execution_id=t.current_execution_id WHERE t.id=? AND t.current_execution_id=? AND t.runner_lease_id=? AND ((t.status='PREPARING_BROWSER' AND l.status='ACTIVE' AND l.expire_time>?) OR (?=true AND t.status='PAUSED' AND t.blocked_reason='PROTOCOL_HANDOFF_VERIFIED' AND l.status='RELEASED')) AND l.executor_channel='EXTERNAL_MCP_AGENT' FOR UPDATE",source,execution,lease,System.currentTimeMillis(),reserved);require(live.size()==1&&source.equals(live.getFirst().get("id"))&&execution.equals(live.getFirst().get("current_execution_id"))&&lease.equals(live.getFirst().get("actual_lease_id")),"PROTOCOL_SOURCE_HANDOFF_REQUIRED");require(jdbc.queryForObject("SELECT COUNT(*) FROM ai_execution_step_result WHERE task_id=? AND execution_id=?",Long.class,source,execution)==0,"PROTOCOL_SOURCE_HANDOFF_REQUIRED");clearance.put("executionId",execution);clearance.put("leaseId",lease);clearance.put("evidenceRef",limited(text(args,"handoffEvidenceRef"),config.number("limits.reference-scalars"),config.number("limits.content-utf8-bytes")));clearance.put("authorityQuery",limited(text(args,"handoffAuthorityQuery"),config.number("limits.body-scalars"),config.number("limits.content-utf8-bytes")));clearance.put("authorizer",actor.user());clearance.put("authorizerPerson",actor.person());clearance.put("verifiedAt",System.currentTimeMillis());long handoffTime=System.currentTimeMillis();jdbc.update("UPDATE ai_runner_lease SET status='RELEASED',version=version+1,update_time=? WHERE id=? AND status='ACTIVE'",handoffTime,lease);jdbc.update("UPDATE ai_execution_task SET status='PAUSED',blocked_reason='PROTOCOL_HANDOFF_VERIFIED',version=version+1,update_time=? WHERE id=? AND current_execution_id=?",handoffTime,source,execution);}
            else require(args.keySet().stream().noneMatch(key->key.startsWith("handoff")),"PROTOCOL_UNKNOWN_FIELD");
            var snapshot=new LinkedHashMap<>(args);snapshot.keySet().removeIf(key->key.startsWith("handoff"));snapshot.put("authorizer",actor.user());snapshot.put("authorizerPerson",actor.person());if(!clearance.isEmpty())snapshot.put("handoffClearance",clearance);snapshot.put("policyVersion",policy.get("version"));snapshot.put("projectId",project);
            var rules=new LinkedHashMap<String,Object>();for(String action:strings(args.get("allowedActions")))rules.put(action,object(policy.get("actionRules")).get(action));snapshot.put("actionRules",rules);
            jdbc.update("INSERT INTO msp_protocol_authorization(source_type,source_id,version,organization_id,environment_id,expires_at,payload) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE version=VALUES(version),expires_at=VALUES(expires_at),payload=VALUES(payload)",type,source,version+1,org,env,number(args,"expiresAt"),store.payload(snapshot));
            store.event("AUTHORIZATION:"+type+":"+source,null,uuid(),"TASK_AUTHORIZED",actor.user(),Map.of("version",version+1,"reason",text(args,"reason")));return Map.of("version",version+1,"handoffPaused",quiesced);
        });
    }
    public Map<String,Object> currentAuthorization(String project,String type,String source,String env){require(Set.of("PERSONAL","TASK").contains(type),"AGENT_VALIDATION_ERROR");String org=access.organizationForProject(project);var actor=access.actor(org,false);access.project(actor,project,false);access.capability(actor,env,"AUTHORIZE_TASK",true);if("TASK".equals(type))access.task(actor,project,source,false);else access.caseIdentity(project,source);var rows=jdbc.queryForList("SELECT version,organization_id,environment_id,expires_at FROM msp_protocol_authorization WHERE source_type=? AND source_id=?",type,source);if(rows.isEmpty())return Map.of("version",0,"configured",false);var row=rows.getFirst();require(org.equals(row.get("organization_id"))&&env.equals(row.get("environment_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");return Map.of("version",row.get("version"),"configured",true,"environmentId",env,"expiresAt",row.get("expires_at"));}
    public Map<String,Object> authorization(String org,String env,String type,String source,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM msp_protocol_authorization WHERE source_type=? AND source_id=?"+(lock?" FOR UPDATE":""),type,source);
        require(rows.size()==1&&org.equals(rows.getFirst().get("organization_id"))&&env.equals(rows.getFirst().get("environment_id"))&&((Number)rows.getFirst().get("expires_at")).longValue()>System.currentTimeMillis(),"PROTOCOL_AUTHORIZATION_REQUIRED");return store.data(rows.getFirst());
    }
}
