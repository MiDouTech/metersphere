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
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

@Service
public class AgentExecutionCheckpointService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AgentExecutionPreflightService preflightService;
    @Resource private AgentProjectService projectService;

    private static final Set<String> CHECKPOINTABLE_STATUSES = Set.of("RUNNING", "WAITING_LOGIN", "WAITING_HUMAN");

    @Transactional(rollbackFor=Exception.class)
    public AgentExecutionCheckpointDTO create(String taskId,String leaseId,AgentCheckpointCreateRequest request){
        String requestId=StringUtils.trimToNull(request.getRequestId());
        if(requestId!=null){
            var prior=jdbcTemplate.queryForList("SELECT id,execution_id,checkpoint_version,status,reason,created_at FROM ai_execution_checkpoint WHERE task_id=? AND request_id=?",taskId,requestId);
            if(!prior.isEmpty()){Map<String,Object> row=prior.getFirst();return new AgentExecutionCheckpointDTO((String)row.get("id"),taskId,(String)row.get("execution_id"),((Number)row.get("checkpoint_version")).intValue(),(String)row.get("status"),(String)row.get("reason"),null,((Number)row.get("created_at")).longValue());}
        }
        Map<String,Object> task=jdbcTemplate.queryForMap("""
                SELECT t.project_id,t.current_execution_id,t.runner_lease_id,t.status,t.timeout_at,
                       l.status lease_status,l.expire_time
                FROM ai_execution_task t
                JOIN ai_runner_lease l ON l.id=t.runner_lease_id AND l.task_id=t.id
                WHERE t.id=? AND l.id=? FOR UPDATE
                """,taskId,leaseId);
        long now=System.currentTimeMillis();
        if(!request.getExecutionId().equals(task.get("current_execution_id")))throw new MSException("CHECKPOINT_EXECUTION_MISMATCH");
        if(!CHECKPOINTABLE_STATUSES.contains(task.get("status")))throw new MSException("CHECKPOINT_TASK_STATUS_INVALID");
        if(!"ACTIVE".equals(task.get("lease_status"))||((Number)task.get("expire_time")).longValue()<=now)
            throw new MSException("CHECKPOINT_LEASE_INVALID_OR_EXPIRED");
        Integer active=jdbcTemplate.queryForObject("SELECT COUNT(1) FROM ai_execution_checkpoint WHERE task_id=? AND status='ACTIVE'",Integer.class,taskId);
        if(active!=null&&active>0)throw new MSException("CHECKPOINT_ALREADY_ACTIVE");
        Integer version=jdbcTemplate.queryForObject("SELECT COALESCE(MAX(checkpoint_version),0)+1 FROM ai_execution_checkpoint WHERE task_id=?",Integer.class,taskId);
        String token=token();String id=IDGenerator.nextStr();String stateHash=sha(request.getStateSnapshot());
        jdbcTemplate.update("INSERT INTO ai_execution_checkpoint(id,task_id,execution_id,request_id,checkpoint_version,state_snapshot,state_hash,reason,resume_token_hash,status,created_at) VALUES (?,?,?,?,?,?,?,?,?, 'ACTIVE',?)",id,taskId,request.getExecutionId(),requestId,version,request.getStateSnapshot(),stateHash,request.getReason(),sha(token),now);
        jdbcTemplate.update("UPDATE ai_execution_human_request SET checkpoint_id=? WHERE task_id=? AND status='PENDING'",id,taskId);
        int leaseChanged=jdbcTemplate.update("UPDATE ai_runner_lease SET status='RELEASED',released_reason='HUMAN_WAIT',closed_at=?,update_time=?,version=version+1 WHERE id=? AND task_id=? AND execution_id=? AND status='ACTIVE'",now,now,leaseId,taskId,request.getExecutionId());
        int taskChanged=jdbcTemplate.update("UPDATE ai_execution_task SET status='WAITING_HUMAN',runner_id=NULL,runner_lease_id=NULL,update_time=?,version=version+1 WHERE id=? AND runner_lease_id=? AND current_execution_id=? AND status=?",now,taskId,leaseId,request.getExecutionId(),task.get("status"));
        if(leaseChanged!=1||taskChanged!=1)throw new MSException("CHECKPOINT_CREATE_CONFLICT");
        return new AgentExecutionCheckpointDTO(id,taskId,request.getExecutionId(),version,"ACTIVE",request.getReason(),token,now);
    }

    @Transactional(rollbackFor=Exception.class)
    public AgentExecutionCheckpointDTO resume(String taskId,String checkpointId,AgentCheckpointResumeRequest request){
        Map<String,Object> cp=jdbcTemplate.queryForMap("SELECT * FROM ai_execution_checkpoint WHERE id=? AND task_id=? FOR UPDATE",checkpointId,taskId);
        if(!"ACTIVE".equals(cp.get("status"))||!MessageDigest.isEqual(sha(request.getResumeToken()).getBytes(StandardCharsets.UTF_8),String.valueOf(cp.get("resume_token_hash")).getBytes(StandardCharsets.UTF_8)))throw new MSException("CHECKPOINT_RESUME_TOKEN_INVALID");
        String actualHash=sha((String)cp.get("state_snapshot"));if(!actualHash.equals(cp.get("state_hash")))throw new MSException("CHECKPOINT_HASH_MISMATCH");
        Map<String,Object> task=jdbcTemplate.queryForMap("SELECT project_id,create_user,task_origin,status,current_execution_id,timeout_at FROM ai_execution_task WHERE id=? FOR UPDATE",taskId);
        projectService.resolveProjectId((String)task.get("project_id"));
        if(!"WAITING_HUMAN".equals(task.get("status"))||!StringUtils.equals((String)cp.get("execution_id"),(String)task.get("current_execution_id")))throw new MSException("CHECKPOINT_RESUME_STATE_CONFLICT");
        if(task.get("timeout_at")!=null&&((Number)task.get("timeout_at")).longValue()<=System.currentTimeMillis())throw new MSException("CHECKPOINT_EXPIRED");
        String actor=StringUtils.defaultIfBlank(SessionUtils.getUserId(),AgentExecutionActorContext.get());
        if(StringUtils.isBlank(actor))throw new MSException("AUTHENTICATION_REQUIRED");
        AgentExecutionPreflightDTO preflight=preflightService.get(request.getPreflightId());if(!"PASSED".equals(preflight.getStatus())||!task.get("project_id").equals(preflight.getProjectId()))throw new MSException("PREFLIGHT_REQUIRED_FOR_RESUME");
        preflightService.consume(request.getPreflightId(),(String)task.get("project_id"),actor,(String)task.get("task_origin"),taskId);
        long now=System.currentTimeMillis();int cpChanged=jdbcTemplate.update("UPDATE ai_execution_checkpoint SET status='RESUMED',resumed_at=?,resumed_by=? WHERE id=? AND status='ACTIVE'",now,actor,checkpointId);
        int taskChanged=jdbcTemplate.update("UPDATE ai_execution_task SET status='QUEUED',preflight_id=?,current_execution_id=NULL,update_user=?,update_time=?,version=version+1 WHERE id=? AND status='WAITING_HUMAN' AND current_execution_id=?",request.getPreflightId(),actor,now,taskId,cp.get("execution_id"));
        if(cpChanged!=1||taskChanged!=1)throw new MSException("CHECKPOINT_RESUME_CONFLICT");
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
        projectService.resolveProjectId((String)task.get("project_id"));
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
