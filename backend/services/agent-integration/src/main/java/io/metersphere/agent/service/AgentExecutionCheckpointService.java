package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
public class AgentExecutionCheckpointService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AgentExecutionPreflightService preflightService;

    @Transactional(rollbackFor=Exception.class)
    public AgentExecutionCheckpointDTO create(String taskId,AgentCheckpointCreateRequest request){
        Map<String,Object> task=jdbcTemplate.queryForMap("SELECT project_id,current_execution_id,runner_lease_id,status FROM ai_execution_task WHERE id=?",taskId);
        if(!request.getExecutionId().equals(task.get("current_execution_id")))throw new MSException("CHECKPOINT_EXECUTION_MISMATCH");
        Integer version=jdbcTemplate.queryForObject("SELECT COALESCE(MAX(checkpoint_version),0)+1 FROM ai_execution_checkpoint WHERE task_id=?",Integer.class,taskId);
        String token=token();String id=IDGenerator.nextStr();long now=System.currentTimeMillis();String stateHash=sha(request.getStateSnapshot());
        jdbcTemplate.update("INSERT INTO ai_execution_checkpoint(id,task_id,execution_id,checkpoint_version,state_snapshot,state_hash,reason,resume_token_hash,status,created_at) VALUES (?,?,?,?,?,?,?,?, 'ACTIVE',?)",id,taskId,request.getExecutionId(),version,request.getStateSnapshot(),stateHash,request.getReason(),sha(token),now);
        jdbcTemplate.update("UPDATE ai_execution_human_request SET checkpoint_id=? WHERE task_id=? AND status='PENDING'",id,taskId);
        String leaseId=(String)task.get("runner_lease_id");if(leaseId!=null){jdbcTemplate.update("UPDATE ai_runner_lease SET status='RELEASED',released_reason='HUMAN_WAIT',closed_at=?,update_time=?,version=version+1 WHERE id=? AND status='ACTIVE'",now,now,leaseId);}
        jdbcTemplate.update("UPDATE ai_execution_task SET status='WAITING_HUMAN',runner_id=NULL,runner_lease_id=NULL,update_time=?,version=version+1 WHERE id=?",now,taskId);
        return new AgentExecutionCheckpointDTO(id,taskId,request.getExecutionId(),version,"ACTIVE",request.getReason(),token,now);
    }

    @Transactional(rollbackFor=Exception.class)
    public AgentExecutionCheckpointDTO resume(String taskId,String checkpointId,AgentCheckpointResumeRequest request){
        Map<String,Object> cp=jdbcTemplate.queryForMap("SELECT * FROM ai_execution_checkpoint WHERE id=? AND task_id=?",checkpointId,taskId);
        if(!"ACTIVE".equals(cp.get("status"))||!sha(request.getResumeToken()).equals(cp.get("resume_token_hash")))throw new MSException("CHECKPOINT_RESUME_TOKEN_INVALID");
        String actualHash=sha((String)cp.get("state_snapshot"));if(!actualHash.equals(cp.get("state_hash")))throw new MSException("CHECKPOINT_HASH_MISMATCH");
        Map<String,Object> task=jdbcTemplate.queryForMap("SELECT project_id,create_user,task_origin FROM ai_execution_task WHERE id=?",taskId);String actor=SessionUtils.getUserId();
        AgentExecutionPreflightDTO preflight=preflightService.get(request.getPreflightId());if(!"PASSED".equals(preflight.getStatus())||!task.get("project_id").equals(preflight.getProjectId()))throw new MSException("PREFLIGHT_REQUIRED_FOR_RESUME");
        preflightService.consume(request.getPreflightId(),(String)task.get("project_id"),actor,(String)task.get("task_origin"),taskId);
        long now=System.currentTimeMillis();jdbcTemplate.update("UPDATE ai_execution_checkpoint SET status='RESUMED',resumed_at=?,resumed_by=? WHERE id=? AND status='ACTIVE'",now,actor,checkpointId);
        jdbcTemplate.update("UPDATE ai_execution_task SET status='QUEUED',preflight_id=?,current_execution_id=NULL,update_user=?,update_time=?,version=version+1 WHERE id=? AND status='WAITING_HUMAN'",request.getPreflightId(),actor,now,taskId);
        return new AgentExecutionCheckpointDTO(checkpointId,taskId,(String)cp.get("execution_id"),((Number)cp.get("checkpoint_version")).intValue(),"RESUMED",(String)cp.get("reason"),null,((Number)cp.get("created_at")).longValue());
    }

    /**
     * Resume a platform task after one of the three configured recipients has
     * resolved the human request.  The original one-time resume token is only
     * intended for the runner that created the checkpoint; it must never be
     * copied into a browser response or notification.  A human resume therefore
     * performs a fresh preflight from the persisted, non-secret request and
     * queues a new attempt instead of changing a lease-less task to RUNNING.
     */
    @Transactional(rollbackFor=Exception.class)
    public AgentExecutionCheckpointDTO resumeAfterHuman(String taskId,String checkpointId){
        Map<String,Object> cp=jdbcTemplate.queryForMap("SELECT * FROM ai_execution_checkpoint WHERE id=? AND task_id=? FOR UPDATE",checkpointId,taskId);
        if(!"ACTIVE".equals(cp.get("status")))throw new MSException("CHECKPOINT_NOT_ACTIVE");
        String actualHash=sha((String)cp.get("state_snapshot"));
        if(!actualHash.equals(cp.get("state_hash")))throw new MSException("CHECKPOINT_HASH_MISMATCH");
        Map<String,Object> task=jdbcTemplate.queryForMap("SELECT t.project_id,t.task_origin,p.request_json FROM ai_execution_task t JOIN ai_execution_preflight p ON p.id=t.preflight_id WHERE t.id=?",taskId);
        AgentExecutionPreflightRequest freshRequest=io.metersphere.sdk.util.JSON.parseObject((String)task.get("request_json"),AgentExecutionPreflightRequest.class);
        freshRequest.setProjectId((String)task.get("project_id"));
        freshRequest.setTaskOrigin((String)task.get("task_origin"));
        AgentExecutionPreflightDTO fresh=preflightService.preflight(freshRequest);
        if(!"PASSED".equals(fresh.getStatus()))throw new MSException("PREFLIGHT_REQUIRED_FOR_RESUME: "+fresh.getBlockedReason());
        String actor=SessionUtils.getUserId();
        preflightService.consume(fresh.getId(),(String)task.get("project_id"),actor,(String)task.get("task_origin"),taskId);
        long now=System.currentTimeMillis();
        int checkpointUpdated=jdbcTemplate.update("UPDATE ai_execution_checkpoint SET status='RESUMED',resumed_at=?,resumed_by=? WHERE id=? AND status='ACTIVE'",now,actor,checkpointId);
        int taskUpdated=jdbcTemplate.update("UPDATE ai_execution_task SET status='QUEUED',preflight_id=?,current_execution_id=NULL,update_user=?,update_time=?,version=version+1 WHERE id=? AND status IN ('WAITING_HUMAN','WAITING_LOGIN')",fresh.getId(),actor,now,taskId);
        if(checkpointUpdated!=1||taskUpdated!=1)throw new MSException("CHECKPOINT_RESUME_CONFLICT");
        return new AgentExecutionCheckpointDTO(checkpointId,taskId,(String)cp.get("execution_id"),((Number)cp.get("checkpoint_version")).intValue(),"RESUMED",(String)cp.get("reason"),null,((Number)cp.get("created_at")).longValue());
    }
    private String token(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private String sha(String v){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
