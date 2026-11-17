package io.metersphere.agent.service;

import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.quality.GlobalQualityGate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Formal publication is a retryable sync intent; it never repeats a browser operation. */
@Service
public class AgentProtocolPublicationService {
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;private final AgentProtocolAccess access;
    private final AgentDirectExecutionService direct;private final AgentExecutionMapper mapper;
    private final GlobalQualityGate gate;private final AgentExecutionCaseWritebackService writeback;
    @jakarta.annotation.Resource private AgentProtocolArtifactMaterializer materializer;
    @jakarta.annotation.Resource private io.metersphere.agent.security.AgentFactCipher cipher;
    @jakarta.annotation.Resource private AgentProtocolTaskLifecycleService lifecycle;
    public AgentProtocolPublicationService(JdbcTemplate jdbc,AgentProtocolStore store,AgentProtocolAccess access,AgentDirectExecutionService direct,AgentExecutionMapper mapper,GlobalQualityGate gate,AgentExecutionCaseWritebackService writeback){this.jdbc=jdbc;this.store=store;this.access=access;this.direct=direct;this.mapper=mapper;this.gate=gate;this.writeback=writeback;}
    /** Reserve the logical request separately: task writeback has its own REQUIRES_NEW transaction. */
    public Map<String,Object> syncRequest(Map<String,Object> args){
        fields(args,"sessionId","caseExecutionId","expectedResultVersion");require("1.0".equals(args.get("protocolVersion")),"PROTOCOL_VERSION_UNSUPPORTED");String sid=id(args,"sessionId"),iid=id(args,"caseExecutionId"),key=id(args,"idempotencyKey");
        var session=jdbc.queryForMap("SELECT * FROM msp_protocol_session WHERE id=?",sid);var actor=access.actor((String)session.get("organization_id"),false);access.project(actor,(String)session.get("project_id"),false);access.capability(actor,(String)session.get("environment_id"),"RESULT_REVIEW",true);if("TASK".equals(session.get("source_type")))access.task(actor,(String)session.get("project_id"),(String)session.get("source_id"),false);
        String scope=hash(List.of(actor.organization(),actor.user(),actor.agent(),"result.sync",sid,iid)),fingerprint=requestHash(args);
        var receipt=store.transaction(()->{jdbc.update("INSERT INTO msp_protocol_operation(scope_key,request_key,operation_id,request_hash,status,metered,create_time) VALUES (?,?,?,?,'SYNC_PENDING',false,?) ON DUPLICATE KEY UPDATE scope_key=scope_key",scope,key,uuid(),fingerprint,System.currentTimeMillis());var row=jdbc.queryForMap("SELECT * FROM msp_protocol_operation WHERE scope_key=? AND request_key=? FOR UPDATE",scope,key);require(fingerprint.equals(row.get("request_hash")),"IDEMPOTENCY_CONFLICT");return row;});
        if("SUCCEEDED".equals(receipt.get("status"))){var response=store.data(Map.of("payload",receipt.get("response")));response.put("replayed",true);response.put("permissionToAct",false);return response;}
        require(number(args,"expectedResultVersion")==((Number)locklessInstance(sid,iid).get("result_version")).longValue(),"VERSION_CONFLICT");var result=sync(sid,iid);var response=new LinkedHashMap<>(result);response.put("operationId",receipt.get("operation_id"));response.put("permissionToAct",false);
        store.transaction(()->{jdbc.update("UPDATE msp_protocol_operation SET status='SUCCEEDED',response=? WHERE scope_key=? AND request_key=?",store.payload(response),scope,key);return null;});return response;
    }
    public Map<String,Object> sync(String sessionId,String instanceId) {
        var rows=jdbc.queryForList("SELECT * FROM msp_protocol_session WHERE id=?",sessionId);require(rows.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");var session=rows.getFirst();
        var actor=access.actor((String)session.get("organization_id"),false);access.project(actor,(String)session.get("project_id"),false);access.capability(actor,(String)session.get("environment_id"),"RESULT_REVIEW",true);
        var existing=locklessInstance(sessionId,instanceId);if(jdbc.queryForObject("SELECT COUNT(*) FROM msp_protocol_publication WHERE instance_id=? AND result_version=?",Long.class,instanceId,existing.get("result_version"))>0)return Map.of("syncStatus","SYNCED","alreadyPublished",true,"disputed",Boolean.TRUE.equals(store.data(existing).get("disputed")));
        try {
            if("PERSONAL".equals(session.get("source_type")))return store.transaction(()->{
                var current=lockInstance(sessionId,instanceId);var data=store.data(current);recheckReviewer(session,data);require(!Boolean.TRUE.equals(data.get("disputed")),"PROTOCOL_CORRECTION_REQUIRED");
                if(!Set.of("PASS","FAIL").contains(current.get("result")))return Map.of("syncStatus","NOT_APPLICABLE");
                var result=direct.publishGovernedPersonal(session,current,data);data.putAll(result);jdbc.update("UPDATE msp_protocol_instance SET payload=? WHERE id=?",store.payload(data),instanceId);lifecycle.reconcile(sessionId);return result;
            });
            var classification=locklessInstance(sessionId,instanceId);require("ACCEPTED".equals(classification.get("verification")),"PROTOCOL_RESULT_NOT_ACCEPTED");if(!Set.of("PASS","FAIL").contains(classification.get("result")))return Map.of("syncStatus","NOT_APPLICABLE");
            recordTaskFacts(session,instanceId);
            var current=locklessInstance(sessionId,instanceId);var task=mapper.selectTaskById((String)session.get("source_id"));
            var executionCase=mapper.selectCasesByTaskId(task.getId()).stream().filter(c->c.getId().equals(current.get("source_case_id"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
            var steps=mapper.selectStepsByTaskId(task.getId()).stream().filter(step->step.getExecutionCaseId().equals(executionCase.getId())).toList();
            writeback.writebackGoverned(task,executionCase,steps,instanceId,((Number)current.get("result_version")).longValue());
            return store.transaction(()->{var row=lockInstance(sessionId,instanceId);require(row.get("result_version").equals(current.get("result_version")),"VERSION_CONFLICT");var data=store.data(row);data.put("syncStatus","SYNCED");jdbc.update("UPDATE msp_protocol_instance SET payload=? WHERE id=?",store.payload(data),instanceId);jdbc.update("INSERT INTO msp_protocol_publication(instance_id,result_version,source_id,create_time) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE source_id=source_id",instanceId,row.get("result_version"),row.get("source_case_id"),System.currentTimeMillis());lifecycle.reconcile(sessionId);return Map.of("syncStatus","SYNCED");});
        } catch(RuntimeException failure) {
            store.transaction(()->{var current=lockInstance(sessionId,instanceId);var data=store.data(current);data.put("syncStatus","FAILED");data.put("syncError","PROTOCOL_FORMAL_SYNC_FAILED");jdbc.update("UPDATE msp_protocol_instance SET payload=? WHERE id=?",store.payload(data),instanceId);return null;});
            AgentProtocolDiagnostics.warn(org.slf4j.LoggerFactory.getLogger(getClass()),"FORMAL_WRITE","PROTOCOL_FORMAL_SYNC_FAILED",instanceId,failure);
            return Map.of("syncStatus","FAILED","code","PROTOCOL_FORMAL_SYNC_FAILED");
        }
    }
    public void validateCandidate(Map<String,Object> session,Map<String,Object> instance,Map<String,Object> data) {
        if("PERSONAL".equals(session.get("source_type")))direct.validateGovernedPersonal(session,instance,data);
        else store.preview(()->{recordTaskFactsBody(session,(String)instance.get("id"),false,data);return null;});
    }
    private Map<String,Object> locklessInstance(String session,String instance){var rows=jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE id=? AND session_id=?",instance,session);require(rows.size()==1,"AGENT_RESOURCE_NOT_ACCESSIBLE");return rows.getFirst();}
    private Map<String,Object> lockInstance(String session,String instance){var rows=jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE id=? AND session_id=? FOR UPDATE",instance,session);require(rows.size()==1&&"ACCEPTED".equals(rows.getFirst().get("verification")),"PROTOCOL_RESULT_NOT_ACCEPTED");return rows.getFirst();}
    private void recheckReviewer(Map<String,Object> session,Map<String,Object> data) {access.requireAcceptedReviewer(session,data);}
    private void recordTaskFacts(Map<String,Object>s,String iid) {
        store.transaction(()->{recordTaskFactsBody(s,iid,true,null);return null;});
    }
    private void recordTaskFactsBody(Map<String,Object>s,String iid,boolean accepted,Map<String,Object> candidateData) {
            var i=accepted?lockInstance((String)s.get("id"),iid):locklessInstance((String)s.get("id"),iid);var data=candidateData==null?store.data(i):candidateData;if(accepted)recheckReviewer(s,data);require(!accepted||!Boolean.TRUE.equals(data.get("disputed")),"PROTOCOL_CORRECTION_REQUIRED");
            require(Set.of("PASS","FAIL").contains(i.get("result")),"PROTOCOL_FORMAL_RESULT_UNSUPPORTED");
            access.requireTaskSourceWrite((String)s.get("owner_id"),(String)s.get("project_id"),(String)s.get("organization_id"),(String)s.get("environment_id"));
            jdbc.queryForList("SELECT id FROM ai_execution_task WHERE id=? AND current_execution_id=? FOR UPDATE",s.get("source_id"),s.get("source_execution_id"));
            var task=mapper.selectTaskById((String)s.get("source_id"));require(task!=null&&s.get("source_execution_id").equals(task.getCurrentExecutionId()),"QUALITY_ATTEMPT_INVALID");
            var c=mapper.selectCasesByTaskId(task.getId()).stream().filter(item->item.getId().equals(i.get("source_case_id"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
            var sourceSteps=mapper.selectStepsByTaskId(task.getId()).stream().filter(step->step.getExecutionCaseId().equals(i.get("source_case_id"))).toList();
            var imageMapping=materializer.materialize(s,i,data);
            var declared=objects(object(data.get("contract")).get("steps"));var actual=effectiveSteps(data);
            for(var source:sourceSteps) {
                var declaration=declared.stream().filter(d->((Number)d.getOrDefault("sourcePosition",declared.indexOf(d)+1)).intValue()==source.getPos()).findFirst().orElseThrow(AgentBusinessAccess::denied);
                var fact=actual.stream().filter(a->declaration.get("id").equals(a.get("stepId"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
                require(Set.of("SATISFIED","VIOLATED").contains(fact.get("outcome")),"QUALITY_FINAL_FACTS_INCOMPLETE");
                jdbc.update("UPDATE ai_execution_step SET status=?,actual_result=?,finished_at=?,version=version+1 WHERE id=?", "SATISFIED".equals(fact.get("outcome"))?"SUCCESS":"FAILED",text(fact,"observation"),System.currentTimeMillis(),source.getId());
                String status="SATISFIED".equals(fact.get("outcome"))?"SUCCESS":"FAILED";
                var ids=new TreeSet<String>();for(var ref:objects(fact.getOrDefault("evidence",List.of()))){String mapped="PERSONAL".equals(ref.get("kind"))?imageMapping.get(id(ref,"id")):id(ref,"id");require(mapped!=null,"PROTOCOL_EVIDENCE_UNAVAILABLE");ids.add(mapped);}
                boolean v2="v2".equals(AgentProtocolData.decode(task.getExecutionContract()).get("contractVersion"));
                String assertions=AgentStepFacts.assertions(encode(fact.get("legacyAssertions")),v2),request="governed:"+iid+":"+i.get("revision")+":"+source.getId();long time=System.currentTimeMillis();
                Integer attempt=jdbc.queryForObject("SELECT attempt_no FROM ai_execution_attempt WHERE id=? AND task_id=?",Integer.class,task.getCurrentExecutionId(),task.getId());
                jdbc.update("INSERT INTO ai_execution_step_result(id,task_id,execution_id,lease_id,step_id,attempt,status,assertion_result,artifact_ids,request_id,trace_id,finished_at,create_time,create_user,payload_hash,facts_version,protocol_instance_id,protocol_generation) VALUES (?,?,?,NULL,?,?,?,?,?,?,?,?,?,?,?,'governed-facts-v1',?,?) ON DUPLICATE KEY UPDATE id=id",uuid(),task.getId(),task.getCurrentExecutionId(),source.getId(),attempt,status,cipher.protectAll(assertions),encode(ids),request,uuid(),time,time,s.get("owner_id"),hash(fact),iid,i.get("generation"));
            }
            jdbc.update("UPDATE ai_execution_case SET status=?,version=version+1 WHERE id=?","PASS".equals(i.get("result"))?"SUCCESS":"FAILED",c.getId());
            c=mapper.selectCasesByTaskId(task.getId()).stream().filter(item->item.getId().equals(i.get("source_case_id"))).findFirst().orElseThrow(AgentBusinessAccess::denied);
            var frozenSteps=mapper.selectStepsByTaskId(task.getId()).stream().filter(step->step.getExecutionCaseId().equals(i.get("source_case_id"))).toList();
            gate.requireCase(task,c,frozenSteps);
            String snapshot=encode(Map.of("executionCase",c,"steps",frozenSteps));long now=System.currentTimeMillis();
            jdbc.update("INSERT INTO ai_execution_case_sync(execution_id,execution_case_id,task_id,project_id,facts_json,facts_hash,business_status,quality_status,status,attempt_count,next_retry_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'PASS','PENDING',0,?,?,?) ON DUPLICATE KEY UPDATE status=CASE WHEN facts_hash=VALUES(facts_hash) AND status='SUCCESS' THEN status ELSE 'PENDING' END,facts_json=VALUES(facts_json),facts_hash=VALUES(facts_hash),business_status=VALUES(business_status),quality_status=VALUES(quality_status),updated_at=VALUES(updated_at)",task.getCurrentExecutionId(),c.getId(),task.getId(),task.getProjectId(),snapshot,org.apache.commons.codec.digest.DigestUtils.sha256Hex(snapshot),c.getStatus(),now,now,now);
    }
}
