package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.system.uid.IDGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.lang3.StringUtils;

/** Metadata only: survives rollback of a rejected result and never stores observed secrets. */
@Service
public class AgentQualityAuditService {
    private final JdbcTemplate jdbc;
    public AgentQualityAuditService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void rejected(AgentExecutionStepSubmitRequest request,String code,String traceId){
        rejected(request,code,traceId,java.util.Map.of());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void rejected(AgentExecutionStepSubmitRequest request,String code,String traceId,java.util.Map<String,String> hashes){
        if(request==null || request.getTaskId()==null)return;
        // INSERT ... SELECT would request a shared source-row lock under MySQL RR,
        // deadlocking with the suspended submission transaction's task write lock.
        var projects=jdbc.queryForList("SELECT project_id FROM ai_execution_task WHERE id=?",String.class,request.getTaskId());
        if(projects.size()!=1)return;
        jdbc.update("INSERT INTO ai_quality_evaluation(id,project_id,task_id,execution_id,step_id,stage,outcome,rule_code,request_id,trace_id,created_at,contract_hash,policy_hash,payload_hash) VALUES (?,?,?,?,?,'STEP_SUBMISSION','REJECTED',?,?,?,?,?,?,?)",
                IDGenerator.nextStr(),projects.getFirst(),request.getTaskId(),StringUtils.abbreviate(request.getExecutionId(),64),StringUtils.abbreviate(request.getStepId(),64),
                StringUtils.abbreviate(code,128),StringUtils.abbreviate(request.getRequestId(),64),traceId,System.currentTimeMillis(),hashes.get("contract"),hashes.get("policy"),hashes.get("payload"));
    }
}
