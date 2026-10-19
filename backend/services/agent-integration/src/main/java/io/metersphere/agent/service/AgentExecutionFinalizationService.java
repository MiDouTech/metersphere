package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;
import java.util.stream.Collectors;

/** Final facts and sync intent commit before any formal plan write. */
@Service
public class AgentExecutionFinalizationService {
    private final JdbcTemplate jdbc;
    private final AgentExecutionMapper mapper;
    private final GlobalQualityGate gate;
    public AgentExecutionFinalizationService(JdbcTemplate jdbc,AgentExecutionMapper mapper,GlobalQualityGate gate){this.jdbc=jdbc;this.mapper=mapper;this.gate=gate;}
    public record FrozenCase(AgentExecutionCaseDTO executionCase,List<AgentExecutionStepDTO> steps,String qualityStatus,String errorCode) {}

    public void requireMutable(String taskId,String executionId){
        lockCurrent(taskId,executionId);
        String status=jdbc.queryForObject("SELECT status FROM ai_execution_attempt WHERE id=? AND task_id=? FOR UPDATE",String.class,executionId,taskId);
        if(!List.of("CLAIMED","RUNNING").contains(status))throw new MSException("EXECUTION_FACTS_FINALIZED_CONFLICT");
    }
    private void lockCurrent(String taskId,String executionId){
        var current=jdbc.queryForList("SELECT current_execution_id FROM ai_execution_task WHERE id=? FOR UPDATE",String.class,taskId);
        if(current.size()!=1 || !Objects.equals(current.getFirst(),executionId))throw new MSException("QUALITY_ATTEMPT_INVALID");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public List<FrozenCase> prepare(String taskId){
        var locked=jdbc.queryForList("SELECT current_execution_id FROM ai_execution_task WHERE id=? FOR UPDATE",String.class,taskId);
        if(locked.size()!=1)throw new MSException("QUALITY_ATTEMPT_INVALID");
        AgentExecutionTaskDTO task=mapper.selectTaskById(taskId);
        if(task==null || task.getCurrentExecutionId()==null)throw new MSException("QUALITY_ATTEMPT_INVALID");
        lockCurrent(taskId,task.getCurrentExecutionId());
        var existing=read(taskId,task.getCurrentExecutionId());
        if(!existing.isEmpty())return existing;
        if(!"WRITING_BACK".equals(task.getStatus()))throw new MSException("RUNNER_TASK_NOT_READY_FOR_COMPLETION");
        requireMutable(taskId,task.getCurrentExecutionId());
        var steps=mapper.selectStepsByTaskId(taskId).stream().collect(Collectors.groupingBy(AgentExecutionStepDTO::getExecutionCaseId));
        var cases=mapper.selectCasesByTaskId(taskId);
        if(cases.isEmpty())throw new MSException("QUALITY_ASSERTION_INVALID");
        long now=System.currentTimeMillis();
        for(var c:cases){
            var caseSteps=steps.getOrDefault(c.getId(),List.of());String quality="PASS";String error=null;
            try {
                if(!List.of("SUCCESS","FAILED").contains(c.getStatus()))throw new MSException("QUALITY_FINAL_FACTS_INCOMPLETE");
                gate.requireCase(task,c,caseSteps);
            } catch(MSException rejected){quality="REJECTED";error=rejected.getMessage();}
            String snapshot=JSON.toJSONString(Map.of("executionCase",c,"steps",caseSteps));
            jdbc.update("INSERT INTO ai_execution_case_sync(execution_id,execution_case_id,task_id,project_id,facts_json,facts_hash,business_status,quality_status,status,error_code,attempt_count,next_retry_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?)",
                    task.getCurrentExecutionId(),c.getId(),taskId,task.getProjectId(),snapshot,DigestUtils.sha256Hex(snapshot),c.getStatus(),quality,
                    "PASS".equals(quality)?"PENDING":"REJECTED",error,now,now,now);
        }
        jdbc.update("UPDATE ai_execution_attempt SET status='FINALIZING',update_time=?,version=version+1 WHERE id=?",now,task.getCurrentExecutionId());
        return read(taskId,task.getCurrentExecutionId());
    }
    private List<FrozenCase> read(String taskId,String executionId){
        var result=new ArrayList<FrozenCase>();
        for(var row:jdbc.queryForList("SELECT facts_json,facts_hash,quality_status,error_code FROM ai_execution_case_sync WHERE task_id=? AND execution_id=? ORDER BY execution_case_id",taskId,executionId)){
            String snapshot=(String)row.get("facts_json");
            if(!DigestUtils.sha256Hex(snapshot).equals(row.get("facts_hash")))throw new MSException("QUALITY_FINAL_FACTS_HASH_INVALID");
            Map<String,Object> content=JSON.parseObject(snapshot,Map.class);
            result.add(new FrozenCase(JSON.parseObject(JSON.toJSONString(content.get("executionCase")),AgentExecutionCaseDTO.class),
                    JSON.parseArray(JSON.toJSONString(content.get("steps")),AgentExecutionStepDTO.class),(String)row.get("quality_status"),(String)row.get("error_code")));
        }
        return result;
    }
    public void beginSync(AgentExecutionTaskDTO task,AgentExecutionCaseDTO c){
        lockCurrent(task.getId(),task.getCurrentExecutionId());
        var rows=jdbc.queryForList("SELECT quality_status FROM ai_execution_case_sync WHERE execution_id=? AND execution_case_id=? FOR UPDATE",task.getCurrentExecutionId(),c.getId());
        if(rows.size()!=1 || !"PASS".equals(rows.getFirst().get("quality_status")))throw new MSException("QUALITY_FINAL_FACTS_INCOMPLETE");
    }
    public void synced(AgentExecutionTaskDTO task,AgentExecutionCaseDTO c){
        jdbc.update("UPDATE ai_execution_case_sync SET status='SUCCESS',error_code=NULL,updated_at=? WHERE execution_id=? AND execution_case_id=?",System.currentTimeMillis(),task.getCurrentExecutionId(),c.getId());
    }
    public void failed(String taskId,String executionId,String caseId){
        lockCurrent(taskId,executionId);
        jdbc.update("UPDATE ai_execution_case_sync SET status='FAILED',error_code='WRITEBACK_FAILED',attempt_count=attempt_count+1,next_retry_at=?,updated_at=? WHERE execution_id=? AND execution_case_id IN (SELECT id FROM ai_execution_case WHERE task_id=? AND case_id=?) AND status IN ('PENDING','FAILED')",
                System.currentTimeMillis()+30000,System.currentTimeMillis(),executionId,taskId,caseId);
    }
    @Transactional(rollbackFor=Exception.class)
    public void retrySync(String taskId,String executionId){
        lockCurrent(taskId,executionId);
        jdbc.update("UPDATE ai_execution_case_sync SET attempt_count=0,next_retry_at=?,updated_at=? WHERE task_id=? AND execution_id=? AND status='FAILED'",
                System.currentTimeMillis(),System.currentTimeMillis(),taskId,executionId);
    }
    public boolean hasFrozen(String taskId,String executionId){
        return jdbc.queryForObject("SELECT COUNT(1) FROM ai_execution_case_sync WHERE task_id=? AND execution_id=?",Integer.class,taskId,executionId)>0;
    }
    /** A frozen execution belongs to sync recovery, not the execution retry queue. */
    @Transactional(rollbackFor=Exception.class)
    public boolean reconcileExpiredFinalization(AgentRunnerLeaseDTO lease) {
        if (!hasFrozen(lease.getTaskId(), lease.getExecutionId())) return false;
        var current=jdbc.queryForList("SELECT current_execution_id FROM ai_execution_task WHERE id=? FOR UPDATE",String.class,lease.getTaskId());
        if(current.size()!=1 || !Objects.equals(current.getFirst(),lease.getExecutionId())) return false;
        var task=mapper.selectTaskById(lease.getTaskId());
        if ("WRITING_BACK".equals(task.getStatus())) return true;
        if (io.metersphere.agent.constants.AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            long now=System.currentTimeMillis();
            mapper.closeRunnerLease(lease.getId(),lease.getRunnerId(),lease.getVersion()==null?0:lease.getVersion(),"COMPLETED",now);
            boolean passed="PASSED".equals(task.getVerdict());
            mapper.finishExecutionAttempt(lease.getExecutionId(),passed?"SUCCEEDED":"NEEDS_REVIEW",
                    passed?null:"QUALITY_GATE_NOT_PASSED",passed?null:"Platform checks require review",now);
        }
        return true;
    }
    public List<String> recoverableTasks(){
        return jdbc.queryForList("SELECT DISTINCT t.id FROM ai_execution_task t LEFT JOIN ai_execution_case_sync s ON t.id=s.task_id AND t.current_execution_id=s.execution_id WHERE t.status='WRITING_BACK' OR (s.status IN ('PENDING','FAILED') AND s.attempt_count<20 AND s.next_retry_at<=?) ORDER BY t.id LIMIT 20",String.class,System.currentTimeMillis());
    }
}
