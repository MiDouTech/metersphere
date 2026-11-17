package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Durable execution control. Browser writes remain external and require prepared intentions. */
@Service
public class AgentGovernedExecutionService {
    public static final Set<String> GOVERNANCE=Set.of("checkpoint.get","action.status","action.resolve","cleanup.resolve","observation.append","cancel","result.evidence.append","result.status.get","result.history.get","result.close-unaccepted","result.revalidate","result.verify","result.correction.request","result.sync","governance.transfer","human.resolve","knowledge.substitution.propose","knowledge.substitution.confirm","finish");
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    private final AgentSessionProtocolConfiguration config;private final AgentStructuredContractService contracts;
    private final AgentKnowledgeService knowledge;private final AgentEnvironmentProfileService environments;
    private final AgentProtocolEvidenceService evidence;private final org.springframework.beans.factory.ObjectProvider<AgentProtocolResultService> results;
    private final AgentDirectExecutionService direct;
    private java.time.Clock verificationClock=java.time.Clock.systemUTC();
    @jakarta.annotation.Resource private AgentProtocolPolicyService policies;
    @jakarta.annotation.Resource private AgentProtocolTaskLifecycleService taskLifecycle;
    public AgentGovernedExecutionService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,
            AgentSessionProtocolConfiguration config,AgentStructuredContractService contracts,AgentKnowledgeService knowledge,
            AgentEnvironmentProfileService environments,AgentProtocolEvidenceService evidence,
            org.springframework.beans.factory.ObjectProvider<AgentProtocolResultService> results,AgentDirectExecutionService direct) {
        this.jdbc=jdbc;this.store=store;this.access=access;this.config=config;this.contracts=contracts;this.knowledge=knowledge;this.environments=environments;this.evidence=evidence;this.results=results;this.direct=direct;
    }
    public Map<String,Object> start(Map<String,Object> args) {
        require("1.0".equals(args.get("protocolVersion")),"PROTOCOL_VERSION_UNSUPPORTED");
        fields(args,"projectId","sourceType","caseId","contractVersion","taskId","environmentProfileId","clientCapabilities","memoryOnlyCache","governanceUserId","sourceLeaseId","noLegacyBusinessActionStarted");
        String project=id(args,"projectId"),org=access.organizationForProject(project),type=text(args,"sourceType");
        require(Set.of("PERSONAL","TASK").contains(type),"AGENT_VALIDATION_ERROR");
        String source="PERSONAL".equals(type)?id(args,"caseId"):id(args,"taskId");
        return store.execute("session.start",type+":"+source+":"+Objects.toString(args.get("contractVersion"),""),args,lock->{var actor=access.sessionActor(org,lock);access.project(actor,project,false);return actor;},()->{},ctx->{
            require(ctx.actor().person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
            config.requireExecution();require(ctx.actor().person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");access.project(ctx.actor(),project,true);
            limited(encode(args),Long.MAX_VALUE,config.number("limits.content-utf8-bytes"));
            require(org.equals(config.reference("pilot.organization-id")),"PROTOCOL_PILOT_SCOPE_DENIED");
            require(Boolean.TRUE.equals(args.get("memoryOnlyCache"))&&strings(args.get("clientCapabilities")).containsAll(AgentSessionProtocolService.REQUIRED_OPERATIONS),"PROTOCOL_CLIENT_INCOMPATIBLE");
            var environment=environments.resolveForTask(id(args,"environmentProfileId"),project);
            require(!"PRODUCTION".equals(environment.getEnvironmentType())&&config.reference("pilot.environment-id").equals(environment.getEnvironmentId()),"PROTOCOL_PILOT_SCOPE_DENIED");
            String env=environment.getEnvironmentId();access.lockGrants(ctx.actor(),env);access.capability(ctx.actor(),env,"EXECUTE",false);
            var policy=policies.current(org,env,true);var authorization=policies.authorization(org,env,type,source,true);
            String responsible=id(args,"governanceUserId");access.requireResponsible(responsible,project,org,env,true);require(responsible.equals(config.reference("governance.responsibility-reference")),"PROTOCOL_GOVERNANCE_REQUIRED");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='GOVERN' AND expires_at>?",Long.class,org,responsible,env,System.currentTimeMillis())==1,"PROTOCOL_GOVERNANCE_REQUIRED");
            var selections=new ArrayList<Map<String,Object>>();String execution=null;
            if("PERSONAL".equals(type))selections.add(Map.of("caseId",source,"contractVersion",number(args,"contractVersion")));
            else {
                var clearance=object(authorization.getOrDefault("handoffClearance",Map.of()));require(args.get("sourceLeaseId") instanceof String&&Objects.equals(args.get("sourceLeaseId"),clearance.get("leaseId")),"PROTOCOL_SOURCE_HANDOFF_REQUIRED");access.requireAuthorizationSigner(text(clearance,"authorizer"),text(clearance,"authorizerPerson"),project,org,env);
                access.task(ctx.actor(),project,source,true);
                var tasks=jdbc.queryForList("SELECT current_execution_id,project_id,status,runner_lease_id,blocked_reason FROM ai_execution_task WHERE id=? FOR UPDATE",source);
                require(tasks.size()==1&&project.equals(tasks.getFirst().get("project_id"))&&tasks.getFirst().get("current_execution_id")!=null,"AGENT_RESOURCE_NOT_ACCESSIBLE");
                execution=(String)tasks.getFirst().get("current_execution_id");
                require(Objects.equals(object(authorization.get("handoffClearance")).get("executionId"),tasks.getFirst().get("current_execution_id")),"PROTOCOL_SOURCE_HANDOFF_REQUIRED");
                require(!ctx.actor().human()&&Boolean.TRUE.equals(args.get("noLegacyBusinessActionStarted"))&&"PAUSED".equals(tasks.getFirst().get("status"))&&"PROTOCOL_HANDOFF_VERIFIED".equals(tasks.getFirst().get("blocked_reason"))&&Objects.equals(id(args,"sourceLeaseId"),tasks.getFirst().get("runner_lease_id")),"PROTOCOL_SOURCE_HANDOFF_REQUIRED");
                var leases=jdbc.queryForList("SELECT * FROM ai_runner_lease WHERE id=? AND task_id=? AND execution_id=? AND status='RELEASED' AND executor_channel='EXTERNAL_MCP_AGENT' FOR UPDATE",args.get("sourceLeaseId"),source,execution);
                require(leases.size()==1&&"MCP_TOKEN".equals(leases.getFirst().get("lease_owner_type"))&&jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_subject WHERE token_id=? AND user_id=? AND organization_id=? AND agent_ref=?",Long.class,leases.getFirst().get("lease_owner_id"),ctx.actor().user(),org,ctx.actor().agent())==1,"PROTOCOL_SOURCE_HANDOFF_REQUIRED");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM ai_execution_step_result WHERE task_id=? AND execution_id=?",Long.class,source,execution)==0,"PROTOCOL_SOURCE_HANDOFF_REQUIRED");
                jdbc.update("UPDATE ai_runner_lease SET status='RELEASED',version=version+1,update_time=? WHERE id=?",System.currentTimeMillis(),args.get("sourceLeaseId"));
                jdbc.update("UPDATE ai_execution_task SET status='RUNNING',blocked_reason=NULL,version=version+1,update_time=? WHERE id=?",System.currentTimeMillis(),source);
                jdbc.update("UPDATE ai_execution_attempt SET status='RUNNING',version=version+1,update_time=? WHERE id=? AND task_id=?",System.currentTimeMillis(),execution,source);
                require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_session WHERE source_type='TASK' AND source_id=? AND state<>'ENDED'",Long.class,source)==0,"PROTOCOL_SOURCE_ALREADY_OWNED");
                for(var row:jdbc.queryForList("SELECT id,case_id,case_version FROM ai_execution_case WHERE task_id=? ORDER BY id FOR UPDATE",source)) {
                    var versions=jdbc.queryForList("SELECT version FROM msp_case_contract WHERE case_id=? AND state='PUBLISHED' ORDER BY version DESC LIMIT 1",Long.class,row.get("case_id"));
                    require(!versions.isEmpty(),"PROTOCOL_CONTRACT_UNAVAILABLE");selections.add(Map.of("caseId",row.get("case_id"),"sourceCaseId",row.get("id"),"contractVersion",versions.getFirst(),"sourceVersion",Objects.toString(row.get("case_version"),row.get("case_id").toString())));
                }
            }
            require(!selections.isEmpty(),"PROTOCOL_CONTRACT_UNAVAILABLE");
            String session=uuid();var sessionData=new LinkedHashMap<String,Object>();sessionData.put("variables",new LinkedHashMap<>());
            sessionData.put("maxPayloadBytes",config.number("limits.content-utf8-bytes"));
            sessionData.put("policy",policy);sessionData.put("authorization",authorization);
            sessionData.put("responsibleUserId",responsible);sessionData.put("environmentProfileId",environment.getId());sessionData.put("environmentVersion",environment.getVersion());
            sessionData.put("clientCapabilities",strings(args.get("clientCapabilities")));sessionData.put("budget",Map.of("calls",number(policy,"budget.calls"),"recoveries",number(policy,"budget.recoveries"),"recoverySeconds",number(policy,"budget.recovery-seconds")));
            if("TASK".equals(type))sessionData.put("sourceLeaseId",args.get("sourceLeaseId"));
            jdbc.update("INSERT INTO msp_protocol_session(id,organization_id,project_id,owner_id,agent_ref,source_type,source_id,source_execution_id,environment_id,state,version,context_version,payload,create_time) VALUES (?,?,?,?,?,?,?,?,?,'OPEN',1,1,?,?)",session,org,project,ctx.actor().user(),ctx.actor().agent(),type,source,execution,env,store.payload(sessionData),System.currentTimeMillis());
            var instances=new ArrayList<Map<String,Object>>();long necessaryCalls=2;
            for(var selection:selections) {
                String caseId=text(selection,"caseId");var contract=contracts.published(project,caseId,number(selection,"contractVersion"));
                require(env.equals(contract.get("environmentId")),"PROTOCOL_ENVIRONMENT_MISMATCH");
                require(strings(authorization.get("allowedActions")).containsAll(strings(contract.getOrDefault("allowedActions",List.of()))),"PROTOCOL_ACTION_DENIED");
                access.caseIdentity(project,caseId);
                long lowerBound=2+5+3L*objects(contract.getOrDefault("steps",List.of())).size();
                necessaryCalls+=lowerBound-2;
                require(number(policy,"budget.calls")>=lowerBound,"PROTOCOL_BUDGET_INSUFFICIENT");
                String instance=uuid();var data=new LinkedHashMap<String,Object>();data.put("contract",contract);data.put("variables",new LinkedHashMap<>());data.put("actions",new ArrayList<>());data.put("bindings",new LinkedHashMap<>());data.put("steps",new ArrayList<>());data.put("humanRequests",new ArrayList<>());data.put("knowledge",new ArrayList<>());data.put("executorPersons",List.of(ctx.actor().person()));data.put("evidencePersons",new ArrayList<>());
                var legacy=direct.protocolSource(project,caseId);data.put("legacy",legacy);
                if("TASK".equals(type)){
                    var frozen=jdbc.queryForMap("SELECT execution_contract,execution_contract_hash FROM ai_execution_task WHERE id=?",source);String raw=(String)frozen.get("execution_contract");require(raw!=null&&DigestUtils.sha256Hex(raw).equals(frozen.get("execution_contract_hash")),"QUALITY_CONTRACT_INVALID");
                    var assertions=new LinkedHashMap<String,Object>();for(var step:jdbc.queryForList("SELECT id,pos FROM ai_execution_step WHERE task_id=? AND execution_case_id=?",source,selection.get("sourceCaseId")))for(var c:objects(decode(raw).get("cases")))for(var declaredStep:objects(c.get("steps")))if(step.get("id").equals(declaredStep.get("stepId")))assertions.put(Objects.toString(step.get("pos")),declaredStep.getOrDefault("assertions",List.of()));data.put("legacyStepAssertions",assertions);
                }
                data.put("executorIdentities",Map.of(ctx.actor().user(),ctx.actor().person()));data.put("evidenceIdentities",new LinkedHashMap<>());
                var frozenKnowledge=new ArrayList<Map<String,Object>>();var references=new ArrayList<>(objects(contract.getOrDefault("knowledgeRefs",List.of())));
                for(var requirement:objects(contract.getOrDefault("dataRequirements",List.of()))){String reference=text(requirement,"preparationMethodRef");int separator=reference.lastIndexOf(":v");require(separator>0&&reference.substring(separator+2).matches("[0-9]{1,18}"),"PROTOCOL_CONTRACT_INCOMPLETE");references.add(Map.of("knowledgeId",reference.substring(0,separator),"version",Long.parseLong(reference.substring(separator+2))));}
                for(var ref:references){String kid=id(ref,"knowledgeId");long knowledgeVersion=number(ref,"version");var fixed=knowledge.fixed(org,kid,knowledgeVersion,ctx.actor(),true);require(frozenKnowledge.stream().noneMatch(existing->kid.equals(existing.get("knowledgeId"))&&knowledgeVersion!=number(existing,"version")),"PROTOCOL_CONTRACT_INCOMPLETE");if(frozenKnowledge.stream().noneMatch(existing->kid.equals(existing.get("knowledgeId"))))frozenKnowledge.add(Map.of("knowledgeId",kid,"version",knowledgeVersion,"safetyGeneration",fixed.get("safetyGeneration"),"snapshot",knowledgeBoundary(fixed)));}
                data.put("knowledge",frozenKnowledge);
                require(Objects.equals(contract.get("sourceVersion"),Objects.toString(object(legacy.get("definition")).get("versionId"),caseId)),"VERSION_CONFLICT");
                if("TASK".equals(type))require(Objects.equals(contract.get("sourceVersion"),selection.get("sourceVersion")),"VERSION_CONFLICT");
                limited(encode(data),Long.MAX_VALUE,number(policy,"limits.content-utf8-bytes"));
                jdbc.update("INSERT INTO msp_protocol_instance(id,session_id,case_id,source_case_id,phase,channel,verification,version,result_version,revision,generation,lease_owner,lease_until,calls_used,recoveries_used,recovery_ms,payload) VALUES (?,?,?,?,'PRECHECK','OPEN','NONE',1,0,0,1,?,?,0,0,0,?)",instance,session,caseId,selection.get("sourceCaseId"),ctx.actor().stableKey(),leaseUntil(),store.payload(data));
                instances.add(Map.of("caseExecutionId",instance,"caseId",caseId,"contextVersion",1,"leaseGeneration",1));
            }
            require(number(policy,"budget.calls")>=necessaryCalls,"PROTOCOL_BUDGET_INSUFFICIENT");
            store.event(session,null,ctx.operationId(),"SESSION_STARTED",ctx.actor().user(),Map.of("sourceType",type,"sourceId",source));
            jdbc.update("UPDATE msp_protocol_session SET calls_used=1 WHERE id=?",session);
            return Map.of("sessionId",session,"instances",instances,"sessionVersion",1,"executionContextVersion",1);
        });
    }
    long leaseUntil(){return Math.addExact(System.currentTimeMillis(),Math.multiplyExact(config.number("lease-seconds"),1000));}
    Map<String,Object> session(String id,boolean lock){var rows=jdbc.queryForList("SELECT * FROM msp_protocol_session WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.size()!=1)throw AgentBusinessAccess.denied();return rows.getFirst();}
    Map<String,Object> instance(String session,String id,boolean lock){var rows=jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? AND id=?"+(lock?" FOR UPDATE":""),session,id);if(rows.size()!=1)throw AgentBusinessAccess.denied();return rows.getFirst();}
    AgentProtocolAccess.Actor authorize(String sessionId,boolean lock) {
        var s=session(sessionId,false);var actor=access.sessionActor((String)s.get("organization_id"),lock);access.project(actor,(String)s.get("project_id"),false);
        if("TASK".equals(s.get("source_type")))access.task(actor,(String)s.get("project_id"),(String)s.get("source_id"),false);
        require(actor.user().equals(s.get("owner_id"))||canGovern(actor,(String)s.get("environment_id"))||canReview(actor,(String)s.get("environment_id")),"AGENT_RESOURCE_NOT_ACCESSIBLE");
        if(lock){session(sessionId,true);access.lockGrants(actor,(String)s.get("environment_id"));}return actor;
    }
    boolean canGovern(AgentProtocolAccess.Actor actor,String environment){try{access.capability(actor,environment,"GOVERN",false);return true;}catch(MSException denied){return false;}}
    boolean canReview(AgentProtocolAccess.Actor actor,String environment){try{access.capability(actor,environment,"RESULT_REVIEW",true);return true;}catch(MSException denied){return false;}}
    public List<Map<String,Object>> list(String org,String project,String task) {
        var actor=access.sessionActor(org,false);var result=new ArrayList<Map<String,Object>>();
        for(var row:jdbc.queryForList("SELECT id,project_id,owner_id,environment_id,source_type,source_id,state,create_time FROM msp_protocol_session WHERE organization_id=? ORDER BY create_time DESC,id",org)) {
            if(project!=null&&!project.isBlank()&&!project.equals(row.get("project_id")))continue;
            if(task!=null&&!task.isBlank()&&!("TASK".equals(row.get("source_type"))&&task.equals(row.get("source_id"))))continue;
            try{access.project(actor,(String)row.get("project_id"),false);if("TASK".equals(row.get("source_type")))access.task(actor,(String)row.get("project_id"),(String)row.get("source_id"),false);if(actor.user().equals(row.get("owner_id"))||canGovern(actor,(String)row.get("environment_id"))||canReview(actor,(String)row.get("environment_id")))result.add(row);}catch(MSException denied){if(!"AGENT_RESOURCE_NOT_ACCESSIBLE".equals(denied.getMessage()))throw denied;}
        }
        return result;
    }
    public Map<String,Object> read(String operation,Map<String,Object> args) {
        fields(args,"sessionId","caseExecutionId","actionId","resultVersion");String id=id(args,"sessionId");var actor=authorize(id,false);var s=session(id,false);
        if("result.history.get".equals(operation)){id(args,"caseExecutionId");number(args,"resultVersion");}else require(!args.containsKey("resultVersion"),"PROTOCOL_UNKNOWN_FIELD");
        expirePending(s);
        var response=new LinkedHashMap<String,Object>();response.put("sessionId",id);response.put("state",s.get("state"));response.put("sessionVersion",s.get("version"));response.put("executionContextVersion",s.get("context_version"));
        var visible=new ArrayList<Map<String,Object>>();
        for(var i:jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? ORDER BY id",id)) {
            if(args.containsKey("caseExecutionId")&&!args.get("caseExecutionId").equals(i.get("id")))continue;
            Map<String,Object> data;
            if("result.history.get".equals(operation)){var history=jdbc.queryForList("SELECT payload FROM msp_protocol_result_revision WHERE instance_id=? AND result_version=?",i.get("id"),number(args,"resultVersion"));require(history.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");var snapshot=store.data(history.getFirst());i=object(snapshot.get("instance"));data=object(snapshot.get("data"));if(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_publication WHERE instance_id=? AND result_version=?",Long.class,i.get("id"),i.get("result_version"))>0)data.put("syncStatus","SYNCED");}else data=store.data(i);
            var item=new LinkedHashMap<>(data);item.put("historicalOnly","result.history.get".equals(operation));
            item.put("assertionHashes",objects(object(data.get("contract")).get("assertions")).stream().collect(java.util.stream.Collectors.toMap(a->text(a,"id"),AgentProtocolData::hash)));if(data.containsKey("substitution"))item.put("substitutionHash",hash(data.get("substitution")));
            item.put("resultHistory",jdbc.queryForList("SELECT result_version,submission_revision,result,verification,create_time FROM msp_protocol_result_revision WHERE instance_id=? ORDER BY result_version",i.get("id")));
            try{checkKnowledge(s,data,actor);}catch(MSException unavailable){if(!Set.of("PROTOCOL_KNOWLEDGE_UNAVAILABLE","PROTOCOL_UPSTREAM_DISPUTED","AGENT_RESOURCE_NOT_ACCESSIBLE").contains(unavailable.getMessage()))throw unavailable;item.put("knowledgeUnavailable",true);item.put("knowledge",List.of());item.remove("knowledgeSegments");item.remove("substitution");if(actor.human()&&data.containsKey("substitution"))try{access.capability(actor,(String)s.get("environment_id"),"CONFIRM_SUBSTITUTION",true);require(actor.person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");var proposal=object(data.get("substitution"));knowledge.get(actor.organization(),text(proposal,"knowledgeId"),number(proposal,"oldVersion"));knowledge.get(actor.organization(),text(proposal,"newKnowledgeId"),number(proposal,"newVersion"));item.put("substitution",proposal);item.put("knowledge",objects(data.get("knowledge")).stream().map(ref->Map.of("knowledgeId",ref.get("knowledgeId"),"version",ref.get("version"))).toList());}catch(MSException denied){item.remove("substitutionHash");}}
            item.put("caseExecutionId",i.get("id"));item.put("caseId",i.get("case_id"));item.put("phase",i.get("phase"));item.put("channel",i.get("channel"));item.put("verification",i.get("verification"));item.put("result",i.get("result"));item.put("contextVersion",i.get("version"));item.put("resultVersion",i.get("result_version"));item.put("submissionRevision",i.get("revision"));item.put("leaseGeneration",i.get("generation"));item.put("leaseUntil",i.get("lease_until"));
            visible.add(item);
            if(actor.human()&&canReview(actor,(String)s.get("environment_id"))){item.put("proposedOutputs",data.getOrDefault("outputs",Map.of()));item.put("proposedOutputsHash",hash(data.getOrDefault("outputs",Map.of())));}
            if(!"ACCEPTED".equals(i.get("verification"))||!"PASS".equals(i.get("result"))||!"SYNCED".equals(data.get("syncStatus"))||Boolean.TRUE.equals(data.get("disputed"))||Boolean.TRUE.equals(item.get("knowledgeUnavailable")))item.put("outputs",Map.of());
        }
        response.put("instances",visible);var context=store.data(s);
        try{access.capability(actor,(String)s.get("environment_id"),"BUDGET_READ",false);context.put("budgetConsumption",Map.of("callsUsed",s.get("calls_used"),"recoveriesUsed",s.get("recoveries_used")));for(var item:visible){var current=instance(id,(String)item.get("caseExecutionId"),false);var consumption=new LinkedHashMap<String,Object>(Map.of("callsUsed",current.get("calls_used"),"recoveriesUsed",current.get("recoveries_used")));try{consumption.put("recoveryMillis",recoveryElapsed(current));}catch(MSException unknown){consumption.put("recoveryTimeState","UNVERIFIABLE");}item.put("budgetConsumption",consumption);}}
        catch(MSException denied){context.remove("budget");context.remove("policy");}
        try{access.requireResponsible(text(context,"responsibleUserId"),(String)s.get("project_id"),(String)s.get("organization_id"),(String)s.get("environment_id"),false);context.put("responsibilityState","AVAILABLE");}catch(MSException denied){context.put("responsibilityState","REASSIGN_REQUIRED");}response.put("context",context);
        response.put("capabilities",jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND expires_at>?",String.class,actor.organization(),actor.user(),s.get("environment_id"),System.currentTimeMillis()));response.put("actorType",actor.human()?"HUMAN":"AGENT");
        var events=new ArrayList<Map<String,Object>>();for(var event:jdbc.queryForList("SELECT id,instance_id,kind,actor_id,operation_id,create_time,payload FROM msp_protocol_event WHERE session_id=? ORDER BY create_time,id",id)){var item=new LinkedHashMap<>(event);item.remove("payload");boolean restricted=visible.stream().anyMatch(instance->Objects.equals(instance.get("caseExecutionId"),event.get("instance_id"))&&Boolean.TRUE.equals(instance.get("knowledgeUnavailable")));item.put("detail",restricted&&event.get("kind").toString().toLowerCase(Locale.ROOT).contains("knowledge")?Map.of("restricted",true):store.data(event));events.add(item);}response.put("events",events);
        var sanitized=object(withoutPrivateIdentity(response));try{access.capability(actor,(String)s.get("environment_id"),"EVIDENCE_READ",false);}catch(MSException denied){return object(withoutEvidence(sanitized));}
        return sanitized;
    }
    private Object withoutPrivateIdentity(Object value){if(value instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();map.forEach((key,entry)->{if(!Set.of("resumeTokenHash","authorPerson","executorPersons","evidencePersons","executorIdentities","evidenceIdentities","reviewerPerson","authorizerPerson","contributorUserId","contributorPerson","safetyRevocations","person","executors").contains(key.toString()))result.put(key.toString(),withoutPrivateIdentity(entry));});return result;}if(value instanceof List<?> list)return list.stream().map(this::withoutPrivateIdentity).toList();return value;}
    private Object withoutEvidence(Object value){if(value instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();map.forEach((key,entry)->{if(!Set.of("evidence","evidenceRef","preflightEvidence","ownershipEvidence","queryEvidence","authorityQuery","evidenceIdentities").contains(key.toString()))result.put(key.toString(),withoutEvidence(entry));});return result;}if(value instanceof List<?> list)return list.stream().map(this::withoutEvidence).toList();return value;}
    public Map<String,Object> knowledgeRead(String operation,Map<String,Object> args){
        fields(args,"sessionId","caseExecutionId","organizationId","knowledgeId","version","keyword","page","size","scope","system","module","type");require("1.0".equals(args.get("protocolVersion")),"PROTOCOL_VERSION_UNSUPPORTED");String sid=id(args,"sessionId"),iid=id(args,"caseExecutionId"),org=id(args,"organizationId");
        var receipt=store.execute(operation,sid+":"+iid,args,lock->{var actor=authorize(sid,lock);instance(sid,iid,lock);require(actor.organization().equals(org),"AGENT_RESOURCE_NOT_ACCESSIBLE");if("knowledge.get".equals(operation))knowledge.get(org,id(args,"knowledgeId"),number(args,"version"));return actor;},()->meter(sid,iid),ctx->{var session=session(sid,true);var instance=instance(sid,iid,true);config.requireExecution();access.capability(ctx.actor(),(String)session.get("environment_id"),"EXECUTE",false);require("OPEN".equals(session.get("state"))&&"OPEN".equals(instance.get("channel")),"PROTOCOL_EXECUTION_CLOSED");policies.current(ctx.actor().organization(),(String)session.get("environment_id"),true);policies.authorization(ctx.actor().organization(),(String)session.get("environment_id"),(String)session.get("source_type"),(String)session.get("source_id"),true);
            // Receipts contain no knowledge body. Replays always materialize against current source ACLs.
            return Map.of("queryRecorded",true);
        });
        var response=new LinkedHashMap<String,Object>();if("knowledge.get".equals(operation))response.putAll(knowledge.get(org,id(args,"knowledgeId"),number(args,"version")));else{var filters=new LinkedHashMap<String,String>();for(String key:List.of("scope","system","module","type"))if(args.containsKey(key))filters.put(key,text(args,key));response.putAll(knowledge.search(org,Objects.toString(args.get("keyword"),""),Math.toIntExact(number(args,"page")),Math.toIntExact(number(args,"size")),filters));}response.putAll(receipt);return response;
    }
    public Map<String,Object> write(String operation,Map<String,Object> args) {
        require(AgentProtocolToolService.OPERATIONS.contains(operation)||"result.verify".equals(operation),"AGENT_VALIDATION_ERROR");
        require("1.0".equals(args.get("protocolVersion")),"PROTOCOL_VERSION_UNSUPPORTED");
        String sid=id(args,"sessionId");String iid=args.containsKey("caseExecutionId")?id(args,"caseExecutionId"):null;
        return store.execute("session."+operation,iid==null?sid:sid+":"+iid,args,lock -> authorize(sid,lock),
                () -> {if(!GOVERNANCE.contains(operation)){if(iid!=null)meter(sid,iid);else meterSession(sid);}},ctx -> {
                    var s=session(sid,true);var actor=ctx.actor();var sessionData=store.data(s);
                    limited(encode(args),Long.MAX_VALUE,number(sessionData,"maxPayloadBytes"));
                    if(iid==null)return sessionWrite(operation,args,s,sessionData,ctx);
                    var i=instance(sid,iid,true);var data=store.data(i);
                    boolean cleanup=operation.startsWith("cleanup.");boolean governance=GOVERNANCE.contains(operation);
                    if(governance) {
                        if(operation.startsWith("result.")&&!Set.of("result.correction.request","result.verify").contains(operation))require(!"ENDED".equals(i.get("phase")),"PROTOCOL_INSTANCE_ENDED");
                        if(operation.startsWith("result.")||"cancel".equals(operation)) {
                            number(args,"expectedResultVersion");
                            if(!"result.verify".equals(operation))require(number(args,"expectedResultVersion")==((Number)i.get("result_version")).longValue(),"VERSION_CONFLICT");
                        }
                        if("result.verify".equals(operation)){checkKnowledge(s,data,actor);require(!Boolean.TRUE.equals(data.get("needsRevalidation")),"PROTOCOL_PREFLIGHT_REQUIRED");}
                    } else {
                        config.requireExecution();require(actor.person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");access.project(actor,(String)s.get("project_id"),true);access.capability(actor,(String)s.get("environment_id"),"EXECUTE",false);
                        if(cleanup&&"TASK".equals(s.get("source_type"))){var task=jdbc.queryForMap("SELECT current_execution_id FROM ai_execution_task WHERE id=? FOR UPDATE",s.get("source_id"));require(Objects.equals(task.get("current_execution_id"),s.get("source_execution_id")),"QUALITY_ATTEMPT_INVALID");}else taskLifecycle.requireCurrent(s);
                        if("TASK".equals(s.get("source_type")))access.task(actor,(String)s.get("project_id"),(String)s.get("source_id"),true);
                        if(Set.of("action.prepare","recovery.record","cleanup.prepare","cleanup.recovery.record").contains(operation))access.requireResponsible(text(sessionData,"responsibleUserId"),(String)s.get("project_id"),actor.organization(),(String)s.get("environment_id"),true);
                        policies.current(actor.organization(),(String)s.get("environment_id"),true);
                        policies.authorization(actor.organization(),(String)s.get("environment_id"),(String)s.get("source_type"),(String)s.get("source_id"),true);
                        if(i.get("recovery_since")!=null&&Set.of("action.prepare","recovery.record","cleanup.prepare","cleanup.recovery.record").contains(operation))require(recoveryElapsed(i)<Math.multiplyExact(Math.min(number(object(sessionData.get("budget")),"recoverySeconds"),policies.limit(s,"budget.recovery-seconds")),1000),"PROTOCOL_RECOVERY_BUDGET_EXHAUSTED");
                        if(cleanup)require("ENDED".equals(i.get("phase"))&&!Boolean.TRUE.equals(data.get("disputed"))&&(!"ACCEPTED".equals(i.get("verification"))||!Set.of("PASS","FAIL").contains(i.get("result"))||"SYNCED".equals(data.get("syncStatus"))),"PROTOCOL_CLEANUP_NOT_READY");else require("OPEN".equals(s.get("state"))&&"OPEN".equals(i.get("channel"))&&!Set.of("ENDED","VERIFYING").contains(i.get("phase")),"PROTOCOL_EXECUTION_CLOSED");
                        if(!Set.of("lease.takeover","cleanup.lease.takeover").contains(operation))require(actor.stableKey().equals(i.get("lease_owner"))&&((Number)i.get("lease_until")).longValue()>System.currentTimeMillis()&&number(args,"leaseGeneration")==((Number)i.get("generation")).longValue(),"PROTOCOL_LEASE_INVALID");
                        require(number(args,"expectedContextVersion")==((Number)i.get("version")).longValue(),"VERSION_CONFLICT");
                        if(!cleanup&&!Set.of("lease.takeover","case.prepare").contains(operation)){
                            checkKnowledge(s,data,actor);require(!Boolean.TRUE.equals(data.get("needsRevalidation"))&&!Set.of("PAUSED","WAITING_HUMAN","PRECHECK").contains(i.get("phase")),"PROTOCOL_PREFLIGHT_REQUIRED");
                        }
                    }
                    Map<String,Object> output;try(var referenced=evidence.captureReferences(iid)){output=instanceWrite(operation,args,s,i,data,ctx);referenced.recordInto(data);}
                    if(!governance&&!cleanup) {
                        var persons=new HashSet<>(strings(data.getOrDefault("executorPersons",List.of())));persons.add(actor.person());data.put("executorPersons",new ArrayList<>(persons));
                        var identities=object(data.getOrDefault("executorIdentities",Map.of()));identities.put(actor.user(),actor.person());data.put("executorIdentities",identities);
                    }
                    if("observation.append".equals(operation)||Boolean.TRUE.equals(output.get("auditOnly"))) {
                        store.event(sid,iid,ctx.operationId(),operation,actor.user(),args);return output;
                    }
                    if(!cleanup&&i.get("recovery_since")!=null&&(i.get("result")!=null||"CLOSED".equals(i.get("channel"))))stopRecovery(i);
                    if(Boolean.TRUE.equals(i.get("recovery_clock_unverifiable")))data.put("recoveryTimingIssue","CLOCK_UNVERIFIABLE");
                    limited(encode(data),Long.MAX_VALUE,number(sessionData,"maxPayloadBytes"));
                    // All new state, variables and event facts commit with their stable operation receipt.
                    jdbc.update("UPDATE msp_protocol_instance SET phase=?,channel=?,verification=?,result=?,version=version+1,result_version=?,revision=?,generation=?,lease_owner=?,lease_until=?,payload=? WHERE id=?",
                            i.get("phase"),i.get("channel"),i.get("verification"),i.get("result"),i.get("result_version"),i.get("revision"),i.get("generation"),i.get("lease_owner"),i.get("lease_until"),store.payload(data),iid);
                    var audit=new LinkedHashMap<>(args);audit.put("actorType",actor.human()?"HUMAN":"AGENT");audit.put("recordSource","result.verify".equals(operation)?"INDEPENDENT_HUMAN":actor.human()?"HUMAN_REPORTED":"AGENT_REPORTED");if("step.save".equals(operation))audit.put("serverStepAttempt",output.get("stepAttempt"));String eventId=store.event(sid,iid,ctx.operationId(),operation,actor.user(),audit);
                    results.getObject().snapshot(instance(sid,iid,false),data,ctx.operationId());
                    taskLifecycle.reconcile(sid);
                    var response=new LinkedHashMap<>(output);response.put("eventId",eventId);response.put("contextVersion",((Number)i.get("version")).longValue()+1);response.put("resultVersion",i.get("result_version"));response.put("submissionRevision",i.get("revision"));response.put("phase",i.get("phase"));response.put("verification",i.get("verification"));return response;
                });
    }
    private void meter(String sid,String iid) {
        var i=instance(sid,iid,true);var s=session(sid,false);var budget=object(store.data(s).get("budget"));
        long maximum=Math.min(number(budget,"calls"),policies.limit(s,"budget.calls"));
        require(((Number)s.get("calls_used")).longValue()<maximum&&((Number)i.get("calls_used")).longValue()<maximum,"PROTOCOL_CALL_BUDGET_EXHAUSTED");
        jdbc.update("UPDATE msp_protocol_instance SET calls_used=calls_used+1 WHERE id=?",iid);
        jdbc.update("UPDATE msp_protocol_session SET calls_used=calls_used+1 WHERE id=?",sid);
    }
    private void meterSession(String sid){var s=session(sid,true);long maximum=Math.min(number(object(store.data(s).get("budget")),"calls"),policies.limit(s,"budget.calls"));require(((Number)s.get("calls_used")).longValue()<maximum,"PROTOCOL_CALL_BUDGET_EXHAUSTED");jdbc.update("UPDATE msp_protocol_session SET calls_used=calls_used+1 WHERE id=?",sid);}
    private Map<String,Object> sessionWrite(String operation,Map<String,Object> args,Map<String,Object> s,Map<String,Object> data,AgentProtocolStore.WriteContext ctx) {
        String sid=(String)s.get("id");
        if("governance.transfer".equals(operation)){
            fields(args,"sessionId","expectedSessionVersion","targetUserId","reason");access.capability(ctx.actor(),(String)s.get("environment_id"),"GOVERN",true);
            require(number(args,"expectedSessionVersion")==((Number)s.get("version")).longValue(),"VERSION_CONFLICT");String target=id(args,"targetUserId");text(args,"reason");access.lockUserAuthorization(target);
            require(access.member(target,(String)s.get("organization_id"))&&jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,target)==1,"PROTOCOL_GOVERNANCE_REQUIRED");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='GOVERN' AND expires_at>?",Long.class,s.get("organization_id"),target,s.get("environment_id"),System.currentTimeMillis())==1,"PROTOCOL_GOVERNANCE_REQUIRED");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_identity WHERE organization_id=? AND user_id=?",Long.class,s.get("organization_id"),target)==1,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
            access.requireResponsible(target,(String)s.get("project_id"),(String)s.get("organization_id"),(String)s.get("environment_id"),true);data.put("responsibleUserId",target);jdbc.update("UPDATE msp_protocol_session SET payload=?,version=version+1 WHERE id=?",store.payload(data),sid);store.event(sid,null,ctx.operationId(),"GOVERNANCE_TRANSFERRED",ctx.actor().user(),Map.of("targetUserId",target,"reason",args.get("reason")));return Map.of("responsibleUserId",target,"sessionVersion",((Number)s.get("version")).longValue()+1);
        }
        if("finish".equals(operation)) {
            access.capability(ctx.actor(),(String)s.get("environment_id"),"GOVERN",false);
            fields(args,"sessionId","expectedSessionVersion");require(number(args,"expectedSessionVersion")==((Number)s.get("version")).longValue(),"VERSION_CONFLICT");
            var pending=taskLifecycle.blockers(sid);
            String state=pending.isEmpty()?"ENDED":"CLOSING";jdbc.update("UPDATE msp_protocol_session SET state=?,version=version+1 WHERE id=?",state,sid);
            taskLifecycle.reconcile(sid);
            return Map.of("state",state,"blockingInstances",pending,"sessionVersion",((Number)s.get("version")).longValue()+1);
        }
        if("TASK".equals(s.get("source_type")))access.task(ctx.actor(),(String)s.get("project_id"),(String)s.get("source_id"),true);
        if("resume".equals(operation)) {
            fields(args,"sessionId");require(!"ENDED".equals(s.get("state")),"PROTOCOL_SESSION_ENDED");config.requireExecution();require(ctx.actor().person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");access.project(ctx.actor(),(String)s.get("project_id"),true);access.capability(ctx.actor(),(String)s.get("environment_id"),"EXECUTE",false);taskLifecycle.requireCurrent(s);
            policies.current(ctx.actor().organization(),(String)s.get("environment_id"),true);policies.authorization(ctx.actor().organization(),(String)s.get("environment_id"),(String)s.get("source_type"),(String)s.get("source_id"),true);
            var resumptions=new ArrayList<Map<String,Object>>();for(var row:jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? AND channel='OPEN' AND phase NOT IN ('ENDED','VERIFYING') ORDER BY id FOR UPDATE",sid)){
                if(!ctx.actor().stableKey().equals(row.get("lease_owner"))){resumptions.add(Map.of("caseExecutionId",row.get("id"),"nextAction","LEASE_TAKEOVER"));continue;}
                var state=store.data(row);var actions=objects(state.get("actions"));for(var action:actions)if("PREPARED".equals(action.get("state")))action.put("state","UNKNOWN");state.put("actions",actions);state.put("needsRevalidation",true);String token=issueResumeToken(state);
                jdbc.update("UPDATE msp_protocol_instance SET payload=?,version=version+1,generation=generation+1,phase='PRECHECK',lease_until=? WHERE id=?",store.payload(state),leaseUntil(),row.get("id"));resumptions.add(Map.of("caseExecutionId",row.get("id"),"nextAction","PREFLIGHT","resumeToken",token));
            }
            store.event(sid,null,ctx.operationId(),"SESSION_RESUMED",ctx.actor().user(),Map.of("requiresPreflight",true));return Map.of("sessionId",sid,"sessionVersion",s.get("version"),"instances",resumptions,"requiresPreflight",true);
        }
        if("context.save".equals(operation)) {
            fields(args,"sessionId","executionContextVersion","variables");access.project(ctx.actor(),(String)s.get("project_id"),true);
            config.requireExecution();require(ctx.actor().person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");access.capability(ctx.actor(),(String)s.get("environment_id"),"EXECUTE",false);
            taskLifecycle.requireCurrent(s);
            policies.current(ctx.actor().organization(),(String)s.get("environment_id"),true);
            policies.authorization(ctx.actor().organization(),(String)s.get("environment_id"),(String)s.get("source_type"),(String)s.get("source_id"),true);
            require(number(args,"executionContextVersion")==((Number)s.get("context_version")).longValue()&&"OPEN".equals(s.get("state")),"VERSION_CONFLICT");
            var declarations=new LinkedHashMap<String,Object>();for(var instance:jdbc.queryForList("SELECT payload FROM msp_protocol_instance WHERE session_id=?",sid))for(var variable:object(object(store.data(instance).get("contract")).getOrDefault("variables",Map.of())).entrySet())if("EXECUTION".equals(object(variable.getValue()).get("scope"))){require(!declarations.containsKey(variable.getKey())||declarations.get(variable.getKey()).equals(variable.getValue()),"PROTOCOL_TYPE_INVALID");declarations.put(variable.getKey(),variable.getValue());}
            AgentProtocolTypes.values(declarations,object(args.get("variables")),false);
            data.put("variables",object(args.get("variables")));jdbc.update("UPDATE msp_protocol_session SET payload=?,context_version=context_version+1 WHERE id=?",store.payload(data),sid);
            return Map.of("executionContextVersion",((Number)s.get("context_version")).longValue()+1);
        }
        throw new MSException("AGENT_VALIDATION_ERROR");
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000,initialDelay=60000)
    public void expireDueResults(){for(var row:jdbc.queryForList("SELECT DISTINCT s.* FROM msp_protocol_session s JOIN msp_protocol_instance i ON i.session_id=s.id WHERE i.verification='PENDING'"))try{expirePending(row);}catch(RuntimeException failed){AgentProtocolDiagnostics.warn(org.slf4j.LoggerFactory.getLogger(getClass()),"VERIFICATION_EXPIRY","EXPIRY_FAILED",(String)row.get("id"),failed);}}
    private void expirePending(Map<String,Object> session){
        var policy=object(store.data(session).getOrDefault("policy",Map.of()));if(!policy.containsKey("verificationDeadlineMillis"))return;long deadline=number(policy,"verificationDeadlineMillis");
        store.transaction(()->{session((String)session.get("id"),true);for(var row:jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? AND verification='PENDING' ORDER BY id FOR UPDATE",session.get("id"))){var data=store.data(row);if(data.get("pendingSince") instanceof Number since&&verificationClock.millis()-since.longValue()>=deadline){jdbc.update("UPDATE msp_protocol_instance SET verification='EXPIRED',phase='WAITING_HUMAN',result_version=result_version+1,version=version+1 WHERE id=?",row.get("id"));store.event((String)session.get("id"),(String)row.get("id"),uuid(),"VERIFICATION_EXPIRED","SYSTEM",Map.of("deadlineMillis",deadline));var updated=instance((String)session.get("id"),(String)row.get("id"),false);results.getObject().snapshot(updated,data,"SYSTEM_EXPIRY");}}return null;});
    }
    private Map<String,Object> instanceWrite(String op,Map<String,Object> args,Map<String,Object> s,Map<String,Object> i,Map<String,Object> data,AgentProtocolStore.WriteContext ctx) {
        String sid=(String)s.get("id"),iid=(String)i.get("id"),env=(String)s.get("environment_id");
        var actor=ctx.actor();
        if(op.startsWith("cleanup."))return cleanup(op,args,s,i,data,actor);
        if("lease.takeover".equals(op)) {
            fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","preflight");
            require(((Number)i.get("lease_until")).longValue()<=System.currentTimeMillis()||actor.stableKey().equals(i.get("lease_owner")),"PROTOCOL_LEASE_OCCUPIED");
            require(number(args,"expectedContextVersion")==((Number)i.get("version")).longValue(),"VERSION_CONFLICT");
            if(!actor.user().equals(s.get("owner_id")))access.capability(actor,env,"GOVERN",false);
            var actions=objects(data.get("actions"));for(var action:actions)if("PREPARED".equals(action.get("state")))action.put("state","UNKNOWN");data.put("actions",actions);
            i.put("generation",((Number)i.get("generation")).longValue()+1);i.put("lease_owner",actor.stableKey());i.put("lease_until",leaseUntil());data.put("needsRevalidation",true);
            return Map.of("leaseGeneration",i.get("generation"),"requiresPreflight",true,"resumeToken",issueResumeToken(data));
        }
        if("case.prepare".equals(op)) {
            fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","preflight","dependencyInstances");
            preflight(s,i,data,args,actor);i.put("phase","DATA_PREPARING");return Map.of("nextAction","PREPARE_DATA","contract",data.get("contract"));
        }
        if(op.startsWith("action."))return action(op,args,s,i,data,actor);
        if("observation.append".equals(op)) {
            fields(args,"sessionId","caseExecutionId","actionId","originalGeneration","observation","evidence");
            String observedAction=id(args,"actionId");require(objects(data.get("actions")).stream().anyMatch(action->observedAction.equals(action.get("actionId"))&&number(args,"originalGeneration")==((Number)action.get("generation")).longValue()),"PROTOCOL_ACTION_UNKNOWN");text(args,"observation");evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,iid,args.getOrDefault("evidence",List.of()),"UNKNOWN");
            return Map.of("historicalOnly",true); // no variable or action-state delta accepted
        }
        if("data.record".equals(op))return recordData(args,s,i,data,actor);
        if("step.save".equals(op))return saveStep(args,s,i,data);
        if("recovery.record".equals(op))return recovery(args,s,i,data);
        if("human.request".equals(op)||"human.resolve".equals(op))return human(op,args,s,i,data,actor);
        if("knowledge.adopt".equals(op)) {
            fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","knowledgeId","knowledgeVersion","stepId","purpose");
            String kid=id(args,"knowledgeId");long version=number(args,"knowledgeVersion");var currentReferences=objects(data.get("knowledge"));boolean frozen=currentReferences.stream().anyMatch(ref->kid.equals(ref.get("knowledgeId"))&&version==number(ref,"version"));var fixed=knowledge.fixed((String)s.get("organization_id"),kid,version,actor,!frozen);
            require(objects(object(data.get("contract")).get("steps")).stream().anyMatch(step->id(args,"stepId").equals(step.get("id"))),"PROTOCOL_STEP_UNKNOWN");
            require(objects(data.get("steps")).stream().noneMatch(step->id(args,"stepId").equals(step.get("stepId"))),"PROTOCOL_STEP_ALREADY_SAVED");
            var refs=new ArrayList<>(currentReferences);require(frozen||refs.stream().noneMatch(ref->kid.equals(ref.get("knowledgeId"))),"PROTOCOL_KNOWLEDGE_ALREADY_ADOPTED");if(!frozen)refs.add(Map.of("knowledgeId",kid,"version",version,"safetyGeneration",fixed.get("safetyGeneration"),"snapshot",knowledgeBoundary(fixed)));data.put("knowledge",refs);
            jdbc.update("INSERT INTO msp_knowledge_usage(id,knowledge_id,knowledge_version,instance_id,step_id,state) VALUES (?,?,?,?,?,'ADOPTED_PENDING_OUTCOME') ON DUPLICATE KEY UPDATE id=id",uuid(),kid,version,iid,id(args,"stepId"));return Map.of("adopted",true);
        }
        if(op.startsWith("knowledge.substitution."))return substitute(op,args,s,i,data,actor);
        if("cancel".equals(op)) {
            fields(args,"sessionId","caseExecutionId","expectedResultVersion","reason");
            access.capability(actor,env,"GOVERN",false);
            if("ENDED".equals(i.get("phase"))||Boolean.TRUE.equals(data.get("cancelRequested")))return Map.of("unchanged",true,"auditOnly",true);
            i.put("channel","CLOSED");data.put("cancelRequested",true);
            if(i.get("result")==null) {
                i.put("result",objects(data.get("steps")).isEmpty()&&objects(data.get("actions")).isEmpty()?"NOT_RUN":"EXECUTION_ERROR");
                i.put("verification","PENDING");i.put("revision",((Number)i.get("revision")).longValue()+1);i.put("result_version",((Number)i.get("result_version")).longValue()+1);i.put("phase","VERIFYING");data.put("reason","USER_CANCELLED");data.put("pendingSince",System.currentTimeMillis());data.put("syncStatus","NOT_ACCEPTED");
            }
            return Map.of("cancelRequested",true,"resultPreserved",true);
        }
        if("result.revalidate".equals(op)){fields(args,"sessionId","caseExecutionId","expectedResultVersion","preflight","dependencyInstances");access.capability(actor,env,"RESULT_REVIEW",true);require("CLOSED".equals(i.get("channel"))&&Set.of("PENDING","NEEDS_EVIDENCE").contains(i.get("verification")),"PROTOCOL_RESULT_STATE_CONFLICT");preflight(s,i,data,args,actor);return Map.of("revalidated",true,"permissionToAct",false);}
        if("case.finish".equals(op)||op.startsWith("result."))return results.getObject().apply(op,args,s,i,data,actor,ctx.operationId());
        throw new MSException("AGENT_VALIDATION_ERROR");
    }
    private void preflight(Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,Map<String,Object>args,AgentProtocolAccess.Actor actor) {
        var p=object(args.get("preflight"));fields(p,"accountId","environmentId","evidence","resumeToken","bindings");text(p,"accountId");require(s.get("environment_id").equals(p.get("environmentId")),"PROTOCOL_ENVIRONMENT_MISMATCH");
        require(!unresolved(data),"PROTOCOL_ACTION_UNKNOWN");var knowledgeContext=data;
        if(Boolean.TRUE.equals(data.get("upstreamDisputed"))&&objects(data.get("steps")).isEmpty()&&objects(data.get("actions")).isEmpty()&&object(data.getOrDefault("dependencies",Map.of())).isEmpty()){knowledgeContext=new LinkedHashMap<>(data);knowledgeContext.put("upstreamDisputed",false);}checkKnowledge(s,knowledgeContext,actor);
        access.capability(actor,(String)s.get("environment_id"),"VERIFY_EXTERNAL",false);
        var environment=environments.resolveForTask(text(store.data(s),"environmentProfileId"),(String)s.get("project_id"));
        require(Objects.equals(environment.getVersion(),store.data(s).get("environmentVersion"))&&s.get("environment_id").equals(environment.getEnvironmentId()),"PROTOCOL_ENVIRONMENT_MISMATCH");
        var proof=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),p.getOrDefault("evidence",List.of()),"RESULT");
        require(!proof.isEmpty(),"PROTOCOL_PREFLIGHT_EVIDENCE_REQUIRED");
        if(!objects(data.getOrDefault("preflightEvidence",List.of())).isEmpty()){var history=new ArrayList<>(objects(data.getOrDefault("preflightHistory",List.of())));history.add(Map.of("evidence",data.get("preflightEvidence")));data.put("preflightHistory",history);}data.put("preflightEvidence",proof);
        var currentBindings=object(data.get("bindings"));var rechecked=object(p.getOrDefault("bindings",Map.of()));
        for(var entry:currentBindings.entrySet()){var original=object(entry.getValue());var checked=object(rechecked.getOrDefault(entry.getKey(),Map.of()));require(Objects.equals(original.get("objectId"),checked.get("objectId"))&&object(checked.getOrDefault("conditions",Map.of())).entrySet().containsAll(object(original.get("conditions")).entrySet()),"PROTOCOL_DATA_CONDITIONS_INCOMPLETE");var checkedProof=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),checked.getOrDefault("evidence",List.of()),"RESULT");require(!checkedProof.isEmpty(),"PROTOCOL_PREFLIGHT_EVIDENCE_REQUIRED");proof.addAll(checkedProof);}
        for(var request:objects(data.getOrDefault("humanRequests",List.of())))if("OPEN".equals(request.get("state")))require(Boolean.TRUE.equals(request.get("recheckRequested")),"PROTOCOL_HUMAN_STATE_CONFLICT");
        if(data.containsKey("substitution")){var substitution=object(data.get("substitution"));if("CONFIRMED".equals(substitution.get("state"))){
            access.lockUserAuthorization(text(substitution,"confirmer"));jdbc.queryForList("SELECT capability FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? FOR UPDATE",s.get("organization_id"),substitution.get("confirmer"),s.get("environment_id"));jdbc.queryForList("SELECT person_ref FROM msp_protocol_identity WHERE user_id=? AND organization_id=? FOR UPDATE",substitution.get("confirmer"),s.get("organization_id"));
            require(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND enable=1 AND deleted=0",Long.class,substitution.get("confirmer"))==1&&access.member(text(substitution,"confirmer"),(String)s.get("organization_id")),"PROTOCOL_SUBSTITUTION_UNPROVEN");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='CONFIRM_SUBSTITUTION' AND expires_at>?",Long.class,s.get("organization_id"),substitution.get("confirmer"),s.get("environment_id"),System.currentTimeMillis())==1,"PROTOCOL_SUBSTITUTION_UNPROVEN");
            require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_identity WHERE user_id=? AND organization_id=? AND person_ref=?",Long.class,substitution.get("confirmer"),s.get("organization_id"),substitution.get("person"))==1,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");
        }}
        String responsible=text(store.data(s),"responsibleUserId");access.requireResponsible(responsible,(String)s.get("project_id"),(String)s.get("organization_id"),(String)s.get("environment_id"),true);require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_grant WHERE organization_id=? AND user_id=? AND environment_id=? AND capability='GOVERN' AND expires_at>?",Long.class,s.get("organization_id"),responsible,s.get("environment_id"),System.currentTimeMillis())==1,"PROTOCOL_GOVERNANCE_REQUIRED");
        if(Boolean.TRUE.equals(data.get("needsRevalidation")))require(p.get("resumeToken") instanceof String&&Objects.equals(data.get("resumeTokenHash"),hash(p.get("resumeToken"))),"PROTOCOL_RESUME_TOKEN_INVALID");
        data.remove("resumeTokenHash");data.put("needsRevalidation",false);data.put("preflightId",uuid());data.put("accountId",p.get("accountId"));
        var dependencies=new LinkedHashMap<String,Object>();var selected=object(args.getOrDefault("dependencyInstances",Map.of()));
        for(var dependency:objects(object(data.get("contract")).getOrDefault("dependencies",List.of()))) {
            String alias=id(dependency,"alias");var selection=object(selected.get(alias));fields(selection,"instanceId","resultVersion");var producer=instance((String)s.get("id"),id(selection,"instanceId"),false);require(number(selection,"resultVersion")==((Number)producer.get("result_version")).longValue(),"PROTOCOL_DEPENDENCY_UNAVAILABLE");var previous=object(object(data.getOrDefault("dependencies",Map.of())).getOrDefault(alias,Map.of()));if(!objects(data.get("steps")).isEmpty())require(Objects.equals(previous.get("instanceId"),producer.get("id"))&&Objects.equals(previous.get("resultVersion"),producer.get("result_version")),"PROTOCOL_DEPENDENCY_UNAVAILABLE");
            require(dependency.get("caseId").equals(producer.get("case_id"))&&"ACCEPTED".equals(producer.get("verification"))&&"PASS".equals(producer.get("result")),"PROTOCOL_DEPENDENCY_UNAVAILABLE");
            var production=store.data(producer);require(!Boolean.TRUE.equals(production.get("disputed")),"PROTOCOL_DEPENDENCY_UNAVAILABLE");
            require(!i.get("id").equals(producer.get("id"))&&number(object(production.get("contract")),"contractVersion")==number(dependency,"contractVersion"),"PROTOCOL_DEPENDENCY_UNAVAILABLE");
            require("SYNCED".equals(production.get("syncStatus")),"PROTOCOL_DEPENDENCY_UNAVAILABLE");
            dependencies.put(alias,Map.of("instanceId",producer.get("id"),"resultVersion",producer.get("result_version"),"outputs",production.getOrDefault("outputs",Map.of())));
            var delta=new LinkedHashMap<String,Object>();for(var binding:object(dependency.getOrDefault("bindings",Map.of())).entrySet()){var outputs=object(production.getOrDefault("outputs",Map.of()));require(binding.getValue() instanceof String&&outputs.containsKey(binding.getValue()),"PROTOCOL_DEPENDENCY_UNAVAILABLE");require("CASE".equals(object(object(object(data.get("contract")).getOrDefault("variables",Map.of())).get(binding.getKey())).get("scope")),"PROTOCOL_VARIABLE_SCOPE_CONFLICT");delta.put(binding.getKey(),outputs.get(binding.getValue()));}AgentProtocolTypes.values(object(object(data.get("contract")).getOrDefault("variables",Map.of())),delta,false);var variables=object(data.get("variables"));variables.putAll(delta);data.put("variables",variables);
        }
        data.put("dependencies",dependencies);data.put("upstreamDisputed",false);
        var requests=objects(data.getOrDefault("humanRequests",List.of()));for(var request:requests)if(Boolean.TRUE.equals(request.get("recheckRequested")))request.put("state","RESOLVED_AFTER_PREFLIGHT");data.put("humanRequests",requests);
    }
    void checkKnowledge(Map<String,Object>s,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        require(!Boolean.TRUE.equals(data.get("upstreamDisputed")),"PROTOCOL_UPSTREAM_DISPUTED");
        for(var ref:objects(data.getOrDefault("knowledge",List.of())))knowledge.fixed((String)s.get("organization_id"),id(ref,"knowledgeId"),number(ref,"version"),actor,false);
        for(var dependency:object(data.getOrDefault("dependencies",Map.of())).values()){
            var binding=object(dependency);var producer=instance((String)s.get("id"),id(binding,"instanceId"),false);var production=store.data(producer);
            require("ACCEPTED".equals(producer.get("verification"))&&"PASS".equals(producer.get("result"))&&!Boolean.TRUE.equals(production.get("disputed"))&&((Number)producer.get("result_version")).longValue()==number(binding,"resultVersion"),"PROTOCOL_UPSTREAM_DISPUTED");
            for(var ref:objects(production.getOrDefault("knowledge",List.of())))knowledge.fixed((String)s.get("organization_id"),id(ref,"knowledgeId"),number(ref,"version"),actor,false);
        }
    }
    static boolean unresolved(Map<String,Object>data){return objects(data.getOrDefault("actions",List.of())).stream().anyMatch(a->Set.of("PREPARED","UNKNOWN").contains(a.get("state")));}
    private long recoveryElapsed(Map<String,Object> instance){long time=((Number)instance.get("recovery_ms")).longValue();if(instance.get("recovery_since") instanceof Number since){require(AgentProtocolClock.now()>=since.longValue(),"PROTOCOL_CLOCK_UNVERIFIABLE");time+=AgentProtocolClock.now()-since.longValue();}return time;}
    private Map<String,Object> action(String op,Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","actionId","actionType","target","targetEntity","parameters","expectedEffect","businessKey","queryMethod","knowledgeId","evidence","resolution","terminalProof","objectId","queryEvidence","retryOf","recoveryPermitId","stepId");
        var actions=new ArrayList<>(objects(data.get("actions")));
        if("action.prepare".equals(op)) {
            require(!unresolved(data),"PROTOCOL_ACTION_UNKNOWN");require(!Set.of("WAITING_HUMAN","PAUSED").contains(i.get("phase")),"PROTOCOL_PREFLIGHT_REQUIRED");
            String type=text(args,"actionType");require(strings(object(data.get("contract")).getOrDefault("allowedActions",List.of())).contains(type),"PROTOCOL_ACTION_DENIED");
            require(strings(policies.current(actor.organization(),(String)s.get("environment_id"),true).get("allowedActions")).contains(type)
                    &&strings(policies.authorization(actor.organization(),(String)s.get("environment_id"),(String)s.get("source_type"),(String)s.get("source_id"),true).get("allowedActions")).contains(type),"PROTOCOL_ACTION_DENIED");
            for(String key:List.of("target","expectedEffect","businessKey","queryMethod"))text(args,key);
            var activePolicy=policies.current(actor.organization(),(String)s.get("environment_id"),true);var rule=object(object(activePolicy.get("actionRules")).get(type));var frozenRules=object(object(store.data(s).get("authorization")).get("actionRules"));require(rule.equals(frozenRules.get(type)),"PROTOCOL_POLICY_CHANGED");
            String entity=id(args,"targetEntity");require(entity.equals(rule.get("entity")),"PROTOCOL_ACTION_DENIED");String effect=text(rule,"effect");
            if(Set.of("MUTATE","DELETE").contains(effect))require(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_owned_object WHERE session_id=? AND entity_type=? AND object_id=?",Long.class,s.get("id"),entity,id(args,"objectId"))==1,"PROTOCOL_DATA_SCOPE_DENIED");
            if("CREATE".equals(effect))require(!args.containsKey("objectId"),"PROTOCOL_DATA_SCOPE_DENIED");
            if("READ".equals(effect))return Map.of("readOnly",true,"permissionToRead",true,"permissionToAct",false);
            String actionFingerprint=hash(List.of(type,entity,text(args,"businessKey")));
            String targetKey=Set.of("MUTATE","DELETE").contains(effect)?"OBJECT:"+id(args,"objectId"):"CREATE:"+text(args,"businessKey");if("DELETE".equals(effect))require(objects(data.get("actions")).stream().noneMatch(previous->targetKey.equals(previous.get("targetKey"))&&"DELETE".equals(previous.get("effect"))&&"CONFIRMED_EFFECT".equals(previous.get("state"))),"PROTOCOL_ACTION_ALREADY_EFFECTIVE");
            for(var sibling:jdbc.queryForList("SELECT id,payload FROM msp_protocol_instance WHERE session_id=? AND id<>?",s.get("id"),i.get("id")))for(var other:java.util.stream.Stream.of(objects(store.data(sibling).get("actions")),objects(object(store.data(sibling).getOrDefault("cleanup",Map.of())).getOrDefault("actions",List.of()))).flatMap(Collection::stream).toList())if(entity.equals(other.get("targetEntity"))&&targetKey.equals(other.get("targetKey"))){require(!Set.of("PREPARED","UNKNOWN").contains(other.get("state")),"PROTOCOL_ACTION_UNKNOWN");if("CREATE".equals(effect)||"DELETE".equals(effect)&&"DELETE".equals(other.get("effect")))require(!"CONFIRMED_EFFECT".equals(other.get("state")),"PROTOCOL_ACTION_ALREADY_EFFECTIVE");}
            var prior=actions.stream().filter(item->actionFingerprint.equals(item.get("fingerprint"))).reduce((first,last)->last);
            if(prior.isPresent()){require("CONFIRMED_NO_EFFECT".equals(prior.get().get("state")),"PROTOCOL_ACTION_ALREADY_EFFECTIVE");require(Objects.equals(prior.get().get("actionId"),args.get("retryOf"))&&i.get("recovery_since")!=null,"PROTOCOL_RECOVERY_REQUIRED");}
            if(i.get("recovery_since")!=null){var permit=object(data.get("recoveryPermit"));require(Objects.equals(permit.get("id"),args.get("recoveryPermitId"))&&!Boolean.TRUE.equals(permit.get("consumed"))&&number(permit,"generation")==((Number)i.get("generation")).longValue()&&actor.stableKey().equals(permit.get("owner")),"PROTOCOL_RECOVERY_REQUIRED");permit.put("consumed",true);data.put("recoveryPermit",permit);}
            require(!io.metersphere.agent.security.AgentSensitiveDataSanitizer.containsSensitive(encode(args.getOrDefault("parameters",Map.of()))),"PROTOCOL_SENSITIVE_CONTENT");
            var action=new LinkedHashMap<String,Object>();for(String key:List.of("actionType","target","parameters","expectedEffect","businessKey","queryMethod","knowledgeId"))if(args.containsKey(key))action.put(key,args.get(key));
            action.put("targetEntity",entity);action.put("effect",effect);action.put("fingerprint",actionFingerprint);if(args.containsKey("objectId"))action.put("targetObjectId",id(args,"objectId"));if(args.containsKey("stepId")){require(objects(object(data.get("contract")).get("steps")).stream().anyMatch(step->args.get("stepId").equals(step.get("id"))),"PROTOCOL_STEP_UNKNOWN");action.put("stepId",args.get("stepId"));}if(args.containsKey("retryOf"))action.put("retryOf",args.get("retryOf"));
            action.put("targetKey",targetKey);
            action.put("actionId",uuid());action.put("state","PREPARED");action.put("generation",i.get("generation"));action.put("createTime",System.currentTimeMillis());actions.add(action);data.put("actions",actions);i.put("phase",i.get("recovery_since")==null?"EXECUTING":"RECOVERING");
            return Map.of("actionId",action.get("actionId"),"state","PREPARED","permissionToAct",true);
        }
        String actionId=id(args,"actionId");var action=actions.stream().filter(a->actionId.equals(a.get("actionId"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
        String resolution=text(args,"resolution");require(Set.of("CONFIRMED_EFFECT","CONFIRMED_NO_EFFECT","UNKNOWN").contains(resolution),"AGENT_VALIDATION_ERROR");
        require(Set.of("PREPARED","UNKNOWN").contains(action.get("state")),"PROTOCOL_ACTION_ALREADY_RESOLVED");
        access.capability(actor,(String)s.get("environment_id"),"VERIFY_EXTERNAL",false);
        require(actor.stableKey().equals(i.get("lease_owner"))||(actor.human()&&canGovern(actor,(String)s.get("environment_id"))),"PROTOCOL_LEASE_INVALID");
        if(!"UNKNOWN".equals(resolution)) {
            text(args,"queryEvidence");if("CONFIRMED_NO_EFFECT".equals(resolution)){var proof=object(args.get("terminalProof"));require(Set.of("CANCELLED_TERMINAL","FAILED_TERMINAL","UNIQUE_REQUEST_TERMINAL_NO_EFFECT").contains(proof.get("type")),"PROTOCOL_TERMINAL_PROOF_REQUIRED");text(proof,"reference");}
            else action.put("objectId",id(args,"objectId"));
            if("CONFIRMED_EFFECT".equals(resolution)&&action.containsKey("targetObjectId"))require(Objects.equals(action.get("targetObjectId"),args.get("objectId")),"PROTOCOL_DATA_ACTION_MISMATCH");
            action.put("evidence",evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),args.getOrDefault("evidence",List.of()),"UNKNOWN"));
            require(!objects(action.get("evidence")).isEmpty(),"PROTOCOL_EVIDENCE_UNAVAILABLE");
            if("CONFIRMED_EFFECT".equals(resolution)&&"CREATE".equals(action.get("effect"))){var original=jdbc.queryForList("SELECT action_id FROM msp_protocol_owned_object WHERE session_id=? AND entity_type=? AND object_id=? FOR UPDATE",String.class,s.get("id"),action.get("targetEntity"),args.get("objectId"));require(original.isEmpty()||actionId.equals(original.getFirst()),"PROTOCOL_EXTERNAL_ID_CONFLICT");jdbc.update("INSERT INTO msp_protocol_owned_object(session_id,entity_type,object_id,instance_id,action_id,create_time) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE action_id=action_id",s.get("id"),action.get("targetEntity"),args.get("objectId"),i.get("id"),actionId,System.currentTimeMillis());}
        }
        if("CONFIRMED_EFFECT".equals(resolution)&&i.get("recovery_since")!=null){stopRecovery(i);i.put("phase","EXECUTING");}
        action.put("state",resolution);action.put("queryEvidence",args.getOrDefault("queryEvidence",""));data.put("actions",actions);
        return Map.of("actionId",actionId,"state",resolution);
    }
    private Map<String,Object> recordData(Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","key","objectId","conditions","actionId","evidence");
        String key=id(args,"key");var requirement=objects(object(data.get("contract")).getOrDefault("dataRequirements",List.of())).stream().filter(r->key.equals(r.get("key"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
        var expected=object(requirement.get("conditions"));var actual=object(args.get("conditions"));AgentProtocolTypes.values(object(requirement.getOrDefault("fields",Map.of())),actual,false);require(actual.entrySet().containsAll(expected.entrySet()),"PROTOCOL_DATA_CONDITIONS_INCOMPLETE");
        access.capability(actor,(String)s.get("environment_id"),"VERIFY_EXTERNAL",false);
        if(args.containsKey("actionId"))require(objects(data.get("actions")).stream().anyMatch(a->args.get("actionId").equals(a.get("actionId"))&&"CONFIRMED_EFFECT".equals(a.get("state"))&&args.get("objectId").equals(a.get("objectId"))),"PROTOCOL_DATA_ACTION_MISMATCH");
        var bindings=object(data.get("bindings"));if(bindings.containsKey(key)&&!Objects.equals(object(bindings.get(key)).get("objectId"),args.get("objectId")))require(objects(data.get("actions")).isEmpty()&&objects(data.get("steps")).isEmpty(),"PROTOCOL_NEW_INSTANCE_REQUIRED");
        var proof=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),args.getOrDefault("evidence",List.of()),"RESULT");require(!proof.isEmpty(),"PROTOCOL_EVIDENCE_UNAVAILABLE");bindings.put(key,Map.of("objectId",id(args,"objectId"),"conditions",actual,"evidence",proof,"resultSource",actor.human()?"HUMAN_REPORTED":"AGENT_REPORTED"));data.put("bindings",bindings);
        String method=text(requirement,"preparationMethodRef");int separator=method.lastIndexOf(":v");require(separator>0,"PROTOCOL_CONTRACT_INCOMPLETE");String methodId=method.substring(0,separator);long version=Long.parseLong(method.substring(separator+2));if(data.containsKey("substitution")){var substitution=object(data.get("substitution"));if("CONFIRMED".equals(substitution.get("state"))&&methodId.equals(substitution.get("knowledgeId"))&&version==number(substitution,"oldVersion")){methodId=text(substitution,"newKnowledgeId");version=number(substitution,"newVersion");}}
        jdbc.update("INSERT INTO msp_knowledge_usage(id,knowledge_id,knowledge_version,instance_id,step_id,state) VALUES (?,?,?,?,?,'OUTCOME_RECORDED') ON DUPLICATE KEY UPDATE id=id",uuid(),methodId,version,i.get("id"),"data."+key);return Map.of("bound",true,"key",key);
    }
    private Map<String,Object> saveStep(Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data) {
        fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","stepId","observation","evidence","variables","outcome","legacyAssertions","recoveryPermitId");
        require(!unresolved(data),"PROTOCOL_ACTION_UNKNOWN");var contract=object(data.get("contract"));String step=id(args,"stepId");
        require(objects(contract.get("steps")).stream().anyMatch(x->step.equals(x.get("id"))),"PROTOCOL_STEP_UNKNOWN");
        var bindings=object(data.get("bindings"));require(objects(contract.getOrDefault("dataRequirements",List.of())).stream().allMatch(r->bindings.containsKey(r.get("key"))),"PROTOCOL_DATA_CONDITIONS_INCOMPLETE");
        var steps=new ArrayList<>(objects(data.get("steps")));var previous=steps.stream().filter(x->step.equals(x.get("stepId"))).findFirst();long attempt=1;
        if(previous.isPresent()){require(!"SATISFIED".equals(previous.get().get("outcome")),"PROTOCOL_STEP_ALREADY_SAVED");var permit=object(data.get("recoveryPermit"));require(Objects.equals(args.get("recoveryPermitId"),permit.get("id"))&&Objects.equals(step,permit.get("stepId"))&&!Boolean.TRUE.equals(permit.get("stepRecorded"))&&number(permit,"generation")==((Number)i.get("generation")).longValue(),"PROTOCOL_RECOVERY_REQUIRED");permit.put("consumed",true);permit.put("stepRecorded",true);data.put("recoveryPermit",permit);var history=new ArrayList<>(objects(data.getOrDefault("stepAttempts",List.of())));history.add(previous.get());data.put("stepAttempts",history);attempt=((Number)previous.get().getOrDefault("attempt",1)).longValue()+1;steps.remove(previous.get());}
        require(Set.of("SATISFIED","VIOLATED","UNKNOWN","NOT_REACHED").contains(text(args,"outcome")),"AGENT_VALIDATION_ERROR");
        var saved=new LinkedHashMap<String,Object>();saved.put("stepId",step);saved.put("observation",text(args,"observation"));saved.put("outcome",args.get("outcome"));saved.put("evidence",evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),args.getOrDefault("evidence",List.of()),"RESULT"));
        saved.put("attempt",attempt);if("SATISFIED".equals(args.get("outcome"))&&i.get("recovery_since")!=null)stopRecovery(i);
        if("TASK".equals(s.get("source_type")))saved.put("legacyAssertions",objects(args.get("legacyAssertions")));steps.add(saved);
        jdbc.update("UPDATE msp_knowledge_usage SET state='OUTCOME_RECORDED' WHERE instance_id=? AND step_id=?",i.get("id"),step);
        data.put("steps",steps);var variables=object(data.get("variables"));var delta=object(args.getOrDefault("variables",Map.of()));var declarations=object(contract.getOrDefault("variables",Map.of()));declarations.entrySet().removeIf(entry->"EXECUTION".equals(object(entry.getValue()).get("scope")));AgentProtocolTypes.values(declarations,delta,false);variables.putAll(delta);data.put("variables",variables);i.put("phase","EXECUTING");return Map.of("stepId",step,"saved",true,"stepAttempt",attempt);
    }
    private Map<String,Object> recovery(Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data) {
        fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","failureEventId","observation","changedCondition","proposedAction","expectedObservation","evidence");
        String event=id(args,"failureEventId");var failureRows=jdbc.queryForList("SELECT kind,payload FROM msp_protocol_event WHERE id=? AND instance_id=?",event,i.get("id"));require(failureRows.size()==1,"PROTOCOL_RECOVERY_EVENT_INVALID");
        var failure=store.data(failureRows.getFirst());boolean stepFailure="step.save".equals(failureRows.getFirst().get("kind"))&&Set.of("VIOLATED","UNKNOWN").contains(failure.get("outcome"))&&objects(data.get("steps")).stream().anyMatch(step->Objects.equals(step.get("stepId"),failure.get("stepId"))&&!"SATISFIED".equals(step.get("outcome"))&&Objects.equals(step.get("attempt"),failure.get("serverStepAttempt")));boolean actionFailure=Set.of("action.resolve","cleanup.resolve").contains(failureRows.getFirst().get("kind"))&&"CONFIRMED_NO_EFFECT".equals(failure.get("resolution"));require(stepFailure||actionFailure,"PROTOCOL_RECOVERY_EVENT_INVALID");
        require(!unresolved(data),"PROTOCOL_ACTION_UNKNOWN");for(String key:List.of("observation","changedCondition","proposedAction","expectedObservation"))text(args,key);
        String fingerprint=hash(List.of(args.get("failureEventId"),args.get("changedCondition"),args.get("proposedAction"),args.getOrDefault("evidence",List.of())));
        require(!fingerprint.equals(data.get("lastRecovery")),"PROTOCOL_RECOVERY_REQUIRES_NEW_EVIDENCE");
        var proof=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),args.getOrDefault("evidence",List.of()),"RESULT");require(!proof.isEmpty(),"PROTOCOL_RECOVERY_REQUIRES_NEW_EVIDENCE");
        var priorProof=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),failure.getOrDefault("evidence",List.of()),"RESULT");require(proof.stream().anyMatch(ref->priorProof.stream().noneMatch(old->Objects.equals(old.get("sha256"),ref.get("sha256")))),"PROTOCOL_RECOVERY_REQUIRES_NEW_EVIDENCE");
        var budget=object(store.data(s).get("budget"));long maxN=Math.min(number(budget,"recoveries"),policies.limit(s,"budget.recoveries"));
        long consumed=((Number)i.get("recoveries_used")).longValue(),elapsed=((Number)i.get("recovery_ms")).longValue();
        if(i.get("recovery_since")!=null)elapsed=recoveryElapsed(i);
        require(consumed<maxN&&((Number)s.get("recoveries_used")).longValue()<maxN&&elapsed<Math.multiplyExact(Math.min(number(budget,"recoverySeconds"),policies.limit(s,"budget.recovery-seconds")),1000),"PROTOCOL_RECOVERY_BUDGET_EXHAUSTED");
        jdbc.update("UPDATE msp_protocol_session SET recoveries_used=recoveries_used+1 WHERE id=?",s.get("id"));
        jdbc.update("UPDATE msp_protocol_instance SET recoveries_used=recoveries_used+1,recovery_ms=?,recovery_since=? WHERE id=?",elapsed,AgentProtocolClock.now(),i.get("id"));data.put("lastRecovery",fingerprint);String permitId=uuid();var permit=new LinkedHashMap<String,Object>(Map.of("id",permitId,"generation",i.get("generation"),"owner",i.get("lease_owner"),"failureEventId",event,"consumed",false,"stepRecorded",false));if(stepFailure)permit.put("stepId",failure.get("stepId"));data.put("recoveryPermit",permit);i.put("phase","RECOVERING");return Map.of("recoveryPermitted",true,"recoveriesUsed",consumed+1,"recoveryPermitId",permitId);
    }
    private Map<String,Object> human(String op,Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration","condition","reason","attempts","requestId","resolution","evidence");
        var requests=new ArrayList<>(objects(data.get("humanRequests")));
        if("human.request".equals(op)) {
            String condition=text(args,"condition");
            if("METHOD_NOT_KNOWN".equals(condition))return Map.of("nextAction","SEARCH_KNOWLEDGE","humanRequired",false);
            require(Set.of("MISSING_CREDENTIAL","OUT_OF_SCOPE","UNRESOLVED_REQUIREMENT_CONFLICT","UNKNOWN_ACTION","INDEPENDENT_REVIEW_UNAVAILABLE").contains(condition),"PROTOCOL_HUMAN_REASON_INVALID");
            text(args,"reason");String fingerprint=hash(condition);var existing=requests.stream().filter(r->fingerprint.equals(r.get("fingerprint"))&&"OPEN".equals(r.get("state"))).findFirst();
            if(existing.isPresent())return existing.get();var request=new LinkedHashMap<String,Object>(Map.of("id",uuid(),"fingerprint",fingerprint,"condition",condition,"reason",args.get("reason"),"state","OPEN"));requests.stream().filter(old->fingerprint.equals(old.get("fingerprint"))&&"CANCELLED".equals(old.get("state"))).reduce((first,second)->second).ifPresent(old->request.put("supersedesRequestId",old.get("id")));requests.add(request);data.put("humanRequests",requests);i.put("phase","WAITING_HUMAN");return request;
        }
        access.capability(actor,(String)s.get("environment_id"),"GOVERN",true);String id=id(args,"requestId");
        var request=requests.stream().filter(r->id.equals(r.get("id"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
        require("OPEN".equals(request.get("state")),"PROTOCOL_HUMAN_STATE_CONFLICT");
        String resolution=text(args,"resolution");require(Set.of("CANCELLED","RECHECK_REQUESTED").contains(resolution),"AGENT_VALIDATION_ERROR");
        String resume="";if("CANCELLED".equals(resolution))request.put("state","CANCELLED");else{request.put("recheckRequested",true);data.put("needsRevalidation",true);resume=issueResumeToken(data);}
        data.put("humanRequests",requests);return Map.of("request",request,"resumeToken",resume);
    }
    private Map<String,Object> substitute(String op,Map<String,Object>args,Map<String,Object>s,Map<String,Object>i,Map<String,Object>data,AgentProtocolAccess.Actor actor) {
        fields(args,"sessionId","caseExecutionId","knowledgeId","oldVersion","newVersion","difference","expectedResultVersion","reason","proposalHash","newKnowledgeId");
        String kid=id(args,"knowledgeId"),newKid=args.containsKey("newKnowledgeId")?id(args,"newKnowledgeId"):kid;var replacement=knowledge.fixed((String)s.get("organization_id"),newKid,number(args,"newVersion"),actor,true);
        require(!"ENDED".equals(i.get("phase")),"PROTOCOL_INSTANCE_ENDED");
        var original=objects(data.get("knowledge")).stream().filter(ref->kid.equals(ref.get("knowledgeId"))&&number(args,"oldVersion")==number(ref,"version")).findFirst().orElseThrow(AgentBusinessAccess::denied);
        require(!kid.equals(newKid)||number(args,"oldVersion")!=number(args,"newVersion"),"PROTOCOL_SUBSTITUTION_UNPROVEN");
        if(op.endsWith("propose")){var difference=object(args.get("difference"));fields(difference,"goal","expected","assertions","entryPoint","authorization","environment","dataScope","risk","irreversibleEffects","preconditions","outputs","inputMapping","outputMapping","evidence","assertionChecks");var proposal=new LinkedHashMap<String,Object>();proposal.put("knowledgeId",kid);proposal.put("newKnowledgeId",newKid);proposal.put("oldVersion",number(args,"oldVersion"));proposal.put("newVersion",number(args,"newVersion"));proposal.put("difference",object(args.get("difference")));proposal.put("state","PROPOSED");proposal.put("checkpointHash",hash(List.of(data.get("contract"),data.get("bindings"),data.get("steps"),data.get("actions"))));proposal.put("oldBoundary",original.containsKey("snapshot")?original.get("snapshot"):knowledgeBoundary(knowledge.fixed((String)s.get("organization_id"),kid,number(args,"oldVersion"),actor,false)));proposal.put("newBoundary",knowledgeBoundary(replacement));data.put("substitution",proposal);i.put("phase","PAUSED");return Map.of("state","PROPOSED","proposalHash",hash(proposal));}
        access.capability(actor,(String)s.get("environment_id"),"CONFIRM_SUBSTITUTION",true);require(actor.person()!=null,"PROTOCOL_INDEPENDENCE_UNVERIFIABLE");var proposal=object(data.get("substitution"));
        require(kid.equals(proposal.get("knowledgeId"))&&newKid.equals(proposal.get("newKnowledgeId"))&&number(args,"newVersion")==number(proposal,"newVersion"),"VERSION_CONFLICT");
        require("PROPOSED".equals(proposal.get("state"))&&hash(List.of(data.get("contract"),data.get("bindings"),data.get("steps"),data.get("actions"))).equals(proposal.get("checkpointHash"))&&knowledgeBoundary(replacement).equals(proposal.get("newBoundary")),"VERSION_CONFLICT");
        require(hash(proposal).equals(text(args,"proposalHash")),"VERSION_CONFLICT");proposal.put("confirmedProposalHash",args.get("proposalHash"));
        var difference=object(proposal.get("difference"));
        if(args.containsKey("difference"))require(difference.equals(object(args.get("difference"))),"VERSION_CONFLICT");
        var oldBoundary=object(proposal.get("oldBoundary"));var newBoundary=object(proposal.get("newBoundary"));
        require(Objects.equals(oldBoundary.get("scope"),newBoundary.get("scope"))&&Objects.equals(oldBoundary.get("type"),newBoundary.get("type")),"PROTOCOL_NEW_INSTANCE_REQUIRED");
        compatibleMapping(object(oldBoundary.getOrDefault("inputs",Map.of())),object(newBoundary.getOrDefault("inputs",Map.of())),object(difference.getOrDefault("inputMapping",Map.of())));
        compatibleMapping(object(oldBoundary.getOrDefault("outputs",Map.of())),object(newBoundary.getOrDefault("outputs",Map.of())),object(difference.getOrDefault("outputMapping",Map.of())));
        var proof=evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),difference.getOrDefault("evidence",List.of()),"KNOWLEDGE");require(!proof.isEmpty(),"PROTOCOL_SUBSTITUTION_UNPROVEN");
        var completed=objects(data.get("steps")).stream().map(step->step.get("stepId")).toList();var assertionChecks=objects(difference.getOrDefault("assertionChecks",List.of()));
        for(var assertion:objects(object(data.get("contract")).get("assertions")))if(completed.contains(assertion.get("stepId"))){var check=assertionChecks.stream().filter(item->assertion.get("id").equals(item.get("assertionId"))).findFirst().orElseThrow(()->new MSException("PROTOCOL_SUBSTITUTION_UNPROVEN"));require(Boolean.TRUE.equals(check.get("stillApplicable"))&&hash(assertion).equals(check.get("assertionHash")),"PROTOCOL_SUBSTITUTION_UNPROVEN");require(!evidence.holdOwned((String)s.get("project_id"),(String)i.get("case_id"),"TASK".equals(s.get("source_type"))?(String)s.get("source_id"):null,(String)i.get("id"),check.getOrDefault("evidence",List.of()),"KNOWLEDGE").isEmpty(),"PROTOCOL_SUBSTITUTION_UNPROVEN");}
        for(String boundary:List.of("goal","expected","assertions","entryPoint","authorization","environment","dataScope","risk","irreversibleEffects"))require("UNCHANGED".equals(difference.get(boundary)),"PROTOCOL_NEW_INSTANCE_REQUIRED");
        require("COMPATIBLE_WITH_EVIDENCE".equals(difference.get("preconditions"))&&"LOSSLESS_WITH_EVIDENCE".equals(difference.get("outputs")),"PROTOCOL_SUBSTITUTION_UNPROVEN");
        text(args,"reason");proposal.put("state","CONFIRMED");proposal.put("confirmer",actor.user());proposal.put("person",actor.person());proposal.put("reason",args.get("reason"));data.put("substitution",proposal);
        var segments=new ArrayList<>(objects(data.getOrDefault("knowledgeSegments",List.of())));segments.add(Map.of("knowledge",new ArrayList<>(objects(data.get("knowledge"))),"steps",data.get("steps"),"checkpointHash",proposal.get("checkpointHash"),"confirmation",new LinkedHashMap<>(proposal)));data.put("knowledgeSegments",segments);
        var refs=new ArrayList<>(objects(data.get("knowledge")));refs.removeIf(ref->kid.equals(ref.get("knowledgeId")));refs.add(Map.of("knowledgeId",newKid,"version",number(args,"newVersion"),"safetyGeneration",replacement.get("safetyGeneration"),"snapshot",knowledgeBoundary(replacement)));data.put("knowledge",refs);
        data.put("needsRevalidation",true);return Map.of("state","CONFIRMED","resumeToken",issueResumeToken(data),"requiresPreflight",true);
    }
    private Map<String,Object> cleanup(String op,Map<String,Object> args,Map<String,Object> session,Map<String,Object> instance,Map<String,Object> data,AgentProtocolAccess.Actor actor){
        String phase=(String)instance.get("phase");var cleanup=object(data.getOrDefault("cleanup",Map.of()));var state=new LinkedHashMap<>(data);state.put("actions",objects(cleanup.getOrDefault("actions",List.of())));state.put("steps",List.of());state.put("recoveryPermit",cleanup.getOrDefault("recoveryPermit",Map.of()));state.put("lastRecovery",cleanup.getOrDefault("lastRecovery",""));
        Map<String,Object> response;
        if("cleanup.lease.takeover".equals(op)){fields(args,"sessionId","caseExecutionId","expectedContextVersion","leaseGeneration");require(((Number)instance.get("lease_until")).longValue()<=System.currentTimeMillis()||actor.stableKey().equals(instance.get("lease_owner")),"PROTOCOL_LEASE_OCCUPIED");if(!actor.user().equals(session.get("owner_id")))access.capability(actor,(String)session.get("environment_id"),"GOVERN",false);var actions=objects(state.get("actions"));for(var action:actions)if("PREPARED".equals(action.get("state")))action.put("state","UNKNOWN");state.put("actions",actions);instance.put("generation",((Number)instance.get("generation")).longValue()+1);instance.put("lease_owner",actor.stableKey());instance.put("lease_until",leaseUntil());response=Map.of("leaseGeneration",instance.get("generation"),"permissionToAct",false);}
        else if("cleanup.prepare".equals(op)){if(!"ACCEPTED".equals(instance.get("verification"))){access.capability(actor,(String)session.get("environment_id"),"GOVERN",true);access.capability(actor,(String)session.get("environment_id"),"VERIFY_EXTERNAL",true);var proof=evidence.holdOwned((String)session.get("project_id"),(String)instance.get("case_id"),"TASK".equals(session.get("source_type"))?(String)session.get("source_id"):null,(String)instance.get("id"),args.getOrDefault("evidence",List.of()),"RESULT");require(!proof.isEmpty(),"PROTOCOL_CLEANUP_OWNERSHIP_UNVERIFIED");cleanup.put("ownershipEvidence",proof);}require(!unresolved(data),"PROTOCOL_ACTION_UNKNOWN");var rule=object(object(policies.current(actor.organization(),(String)session.get("environment_id"),true).get("actionRules")).get(text(args,"actionType")));require("DELETE".equals(rule.get("effect")),"PROTOCOL_ACTION_DENIED");response=action("action.prepare",args,session,instance,state,actor);cleanup.put("state","CLEANUP_PREPARED");var executors=object(cleanup.getOrDefault("executors",Map.of()));executors.put(actor.user(),actor.person());cleanup.put("executors",executors);}
        else if("cleanup.resolve".equals(op)){response=action("action.resolve",args,session,instance,state,actor);String resolution=text(args,"resolution");cleanup.put("state",switch(resolution){case "CONFIRMED_EFFECT"->"CLEANED";case "CONFIRMED_NO_EFFECT"->"CLEANUP_FAILED";default->"CLEANUP_UNKNOWN";});if("CONFIRMED_NO_EFFECT".equals(resolution))cleanup.put("failureCode","EXTERNAL_DELETE_NO_EFFECT");}
        else if("cleanup.recovery.record".equals(op))response=recovery(args,session,instance,state);
        else throw new MSException("AGENT_VALIDATION_ERROR");
        for(String key:List.of("actions","recoveryPermit","lastRecovery"))cleanup.put(key,state.getOrDefault(key,key.equals("actions")?List.of():key.equals("recoveryPermit")?Map.of():""));data.put("cleanup",cleanup);instance.put("phase",phase);return response;
    }
    private String issueResumeToken(Map<String,Object> data){String token=uuid()+uuid();data.put("resumeTokenHash",hash(token));return token;}
    private void stopRecovery(Map<String,Object> instance){long elapsed=((Number)instance.get("recovery_ms")).longValue();try{elapsed=recoveryElapsed(instance);}catch(MSException unavailable){if(!"PROTOCOL_CLOCK_UNVERIFIABLE".equals(unavailable.getMessage()))throw unavailable;instance.put("recovery_clock_unverifiable",true);store.event((String)instance.get("session_id"),(String)instance.get("id"),uuid(),"RECOVERY_CLOCK_UNVERIFIABLE","SYSTEM",Map.of("newRecoveryPermission",false));}jdbc.update("UPDATE msp_protocol_instance SET recovery_ms=?,recovery_since=NULL WHERE id=?",elapsed,instance.get("id"));instance.put("recovery_ms",elapsed);instance.put("recovery_since",null);}
    private Map<String,Object> knowledgeBoundary(Map<String,Object> fixed){var result=new LinkedHashMap<String,Object>();for(String key:List.of("type","scope","preconditions","inputs","outputs","preparation"))if(fixed.containsKey(key))result.put(key,fixed.get(key));return result;}
    private void compatibleMapping(Map<String,Object> before,Map<String,Object> after,Map<String,Object> mapping){
        if(before.equals(after)&&mapping.isEmpty())return;before=AgentProtocolTypes.leafFields(before);after=AgentProtocolTypes.leafFields(after);require(mapping.keySet().equals(before.keySet())&&new HashSet<>(mapping.values()).size()==mapping.size()&&new HashSet<>(mapping.values()).equals(after.keySet()),"PROTOCOL_SUBSTITUTION_UNPROVEN");
        for(var field:before.entrySet())require(mapping.get(field.getKey()) instanceof String&&AgentProtocolTypes.compatible(object(field.getValue()),object(after.get(mapping.get(field.getKey())))),"PROTOCOL_NEW_INSTANCE_REQUIRED");
    }
}
