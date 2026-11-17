package io.metersphere.agent.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static io.metersphere.agent.service.AgentProtocolData.*;

/** Only aggregates already persisted protocol verdicts. Never manufactures legacy case results or an attempt. */
@Service
public class AgentProtocolTaskLifecycleService {
    private final JdbcTemplate jdbc;private final AgentProtocolStore store;
    public AgentProtocolTaskLifecycleService(JdbcTemplate jdbc,AgentProtocolStore store){this.jdbc=jdbc;this.store=store;}
    void requireCurrent(Map<String,Object> session){if(!"TASK".equals(session.get("source_type")))return;
        var rows=jdbc.queryForList("SELECT current_execution_id,status FROM ai_execution_task WHERE id=? AND project_id=? FOR UPDATE",session.get("source_id"),session.get("project_id"));
        require(rows.size()==1&&Objects.equals(rows.getFirst().get("current_execution_id"),session.get("source_execution_id"))&&Set.of("RUNNING","PREPARING_BROWSER","WRITING_BACK").contains(rows.getFirst().get("status")),"QUALITY_ATTEMPT_INVALID");
    }
    List<String> blockers(String session){var result=new ArrayList<String>();for(var row:jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? ORDER BY id FOR UPDATE",session)){
        var data=store.data(row);boolean sync="ACCEPTED".equals(row.get("verification"))&&Set.of("PASS","FAIL").contains(row.get("result"))&&!"SYNCED".equals(data.get("syncStatus"));
        if(!"ENDED".equals(row.get("phase"))||AgentGovernedExecutionService.unresolved(data)||sync||Boolean.TRUE.equals(data.get("disputed")))result.add((String)row.get("id"));
    }return result;}
    void reconcile(String sessionId){store.transaction(()->{
        var rows=jdbc.queryForList("SELECT * FROM msp_protocol_session WHERE id=? FOR UPDATE",sessionId);if(rows.size()!=1)return null;var session=rows.getFirst();
        if(!Set.of("CLOSING","ENDED").contains(session.get("state"))||!blockers(sessionId).isEmpty())return null;
        if("CLOSING".equals(session.get("state")))jdbc.update("UPDATE msp_protocol_session SET state='ENDED',version=version+1 WHERE id=?",sessionId);
        if(!"TASK".equals(session.get("source_type")))return null;
        var tasks=jdbc.queryForList("SELECT current_execution_id FROM ai_execution_task WHERE id=? AND project_id=? FOR UPDATE",session.get("source_id"),session.get("project_id"));
        require(tasks.size()==1&&Objects.equals(tasks.getFirst().get("current_execution_id"),session.get("source_execution_id")),"QUALITY_ATTEMPT_INVALID");
        var instances=jdbc.queryForList("SELECT * FROM msp_protocol_instance WHERE session_id=? ORDER BY id",sessionId);String aggregate=hash(instances.stream().map(instance->List.of(instance.get("id"),instance.get("result_version"),instance.get("verification"),Objects.toString(instance.get("result"),""))).toList());var sessionData=store.data(session);if(aggregate.equals(sessionData.get("taskAggregateHash")))return null;
        int success=0,failed=0,blocked=0,skipped=0,unaccepted=0,errors=0,cancelled=0;for(var instance:instances){
            var facts=store.data(instance);if(!"ACCEPTED".equals(instance.get("verification"))){unaccepted++;if(Boolean.TRUE.equals(facts.get("cancelRequested")))cancelled++;continue;}
            String result=Objects.toString(instance.get("result"),"");switch(result){case "PASS"->success++;case "FAIL"->failed++;case "BLOCKED"->blocked++;case "NOT_RUN"->skipped++;case "EXECUTION_ERROR"->errors++;default->unaccepted++;}
            if(!Set.of("PASS","FAIL").contains(result)){String state=switch(result){case "BLOCKED"->"BLOCKED";case "NOT_RUN"->"SKIPPED";default->"ERROR";};jdbc.update("UPDATE ai_execution_case SET status=?,writeback_status='NOT_APPLICABLE',update_time=?,version=version+1 WHERE id=? AND task_id=?",state,System.currentTimeMillis(),instance.get("source_case_id"),session.get("source_id"));}
        }
        String status=errors+unaccepted>0?(success+failed+blocked+skipped>0?"PARTIAL_SUCCESS":cancelled==instances.size()?"CANCELED":"FAILED"):"SUCCESS";
        String verdict=unaccepted>0?"INCONCLUSIVE":errors>0?"AGENT_FAILED":failed>0?"PRODUCT_FAILED":blocked>0?"BLOCKED":skipped>0?"INCONCLUSIVE":"PASSED";long now=System.currentTimeMillis();
        jdbc.update("UPDATE ai_execution_task SET status=?,verdict=?,verdict_reason='GOVERNED_PROTOCOL_AGGREGATE',success_count=?,failed_count=?,blocked_count=?,skipped_count=?,unexecuted_count=?,writeback_status=?,finished_at=COALESCE(finished_at,?),update_time=?,version=version+1 WHERE id=? AND current_execution_id=?",status,verdict,success,failed+errors,blocked,skipped,unaccepted,unaccepted>0?"PARTIAL_SUCCESS":"SUCCESS",now,now,session.get("source_id"),session.get("source_execution_id"));
        sessionData.put("taskAggregateHash",aggregate);jdbc.update("UPDATE msp_protocol_session SET payload=? WHERE id=?",store.payload(sessionData),sessionId);
        jdbc.update("UPDATE ai_execution_attempt SET status=?,finish_time=?,update_time=?,version=version+1 WHERE id=? AND task_id=? AND status IN ('CLAIMED','RUNNING','FINALIZING')","PASSED".equals(verdict)?"SUCCEEDED":"NEEDS_REVIEW",now,now,session.get("source_execution_id"),session.get("source_id"));
        store.event(sessionId,null,uuid(),"SOURCE_TASK_RECONCILED","SYSTEM",Map.of("status",status,"verdict",verdict,"unaccepted",unaccepted));return null;
    });}
}
