package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentTestDataLeaseDTO;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.*;

@Service
public class AgentTestDataLeaseService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentTestDataCleanupService cleanup;
    @Resource private FileMetadataService fileMetadataService;
    private static final SecureRandom RANDOM=new SecureRandom();

    @Transactional(rollbackFor=Exception.class)
    public AgentTestDataLeaseDTO acquire(String taskId,String datasetId,String key,long ttl){
        if(StringUtils.isAnyBlank(taskId,datasetId,key)||ttl<1000||ttl>86_400_000)throw new MSException("TEST_DATA_LEASE_REQUEST_INVALID");
        Map<String,Object> task=jdbc.queryForMap("SELECT project_id,current_execution_id,status FROM ai_execution_task WHERE id=?",taskId);String execution=(String)task.get("current_execution_id");
        if(StringUtils.isBlank(execution)||!List.of("PREPARING_BROWSER","RUNNING").contains(task.get("status")))throw new MSException("TEST_DATA_LEASE_TASK_NOT_ACTIVE");
        Integer dataset=jdbc.queryForObject("SELECT COUNT(1) FROM test_asset_version WHERE project_id=? AND asset_type='DATASET' AND asset_id=? AND status='PUBLISHED'",Integer.class,task.get("project_id"),datasetId);
        if(dataset==null||dataset==0)throw new MSException("PUBLISHED_DATASET_NOT_FOUND");
        String token=token(),id=IDGenerator.nextStr(),namespace="ai/"+taskId+"/"+execution;long now=System.currentTimeMillis();
        try{jdbc.update("INSERT INTO ai_test_data_lease(id,task_id,execution_id,project_id,dataset_id,data_key,namespace,status,lease_token_hash,expires_at,version,create_time,update_time) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?,0,?,?)",id,taskId,execution,task.get("project_id"),datasetId,key,namespace,DigestUtils.sha256Hex(token),now+ttl,now,now);}catch(DuplicateKeyException ex){throw new MSException("TEST_DATA_ALREADY_LEASED");}
        return map(jdbc.queryForMap("SELECT * FROM ai_test_data_lease WHERE id=?",id),token);
    }
    @Transactional(rollbackFor=Exception.class)
    public AgentTestDataLeaseDTO heartbeat(String id,String token,long ttl){if(ttl<1000||ttl>86_400_000)throw new MSException("TEST_DATA_LEASE_TTL_INVALID");long now=System.currentTimeMillis();int n=jdbc.update("UPDATE ai_test_data_lease SET expires_at=?,update_time=?,version=version+1 WHERE id=? AND status='ACTIVE' AND expires_at>? AND lease_token_hash=?",now+ttl,now,id,now,DigestUtils.sha256Hex(StringUtils.defaultString(token)));if(n!=1)throw new MSException("TEST_DATA_LEASE_INVALID_OR_EXPIRED");return map(jdbc.queryForMap("SELECT * FROM ai_test_data_lease WHERE id=?",id),null);}
    public void assertExecution(String id,String executionId){Integer count=jdbc.queryForObject("SELECT COUNT(1) FROM ai_test_data_lease WHERE id=? AND execution_id=?",Integer.class,id,executionId);if(count==null||count!=1)throw new MSException("TEST_DATA_LEASE_NOT_ACCESSIBLE");}
    public ResponseEntity<byte[]> content(String id,String token,String executionId){Map<String,Object> lease=jdbc.queryForMap("SELECT * FROM ai_test_data_lease WHERE id=?",id);if(!executionId.equals(lease.get("execution_id"))||!"ACTIVE".equals(lease.get("status"))||((Number)lease.get("expires_at")).longValue()<=System.currentTimeMillis()||!DigestUtils.sha256Hex(StringUtils.defaultString(token)).equals(lease.get("lease_token_hash")))throw new MSException("TEST_DATA_LEASE_INVALID_OR_EXPIRED");List<String> files=jdbc.queryForList("SELECT id FROM file_metadata WHERE project_id=? AND (id=? OR ref_id=?) AND latest=b'1' AND (enable IS NULL OR enable=b'1') ORDER BY update_time DESC LIMIT 1",String.class,lease.get("project_id"),lease.get("dataset_id"),lease.get("dataset_id"));if(files.isEmpty())throw new MSException("TEST_DATASET_FILE_NOT_FOUND");return fileMetadataService.downloadById(files.getFirst());}
    @Transactional(rollbackFor=Exception.class)
    public void release(String id,String token){long now=System.currentTimeMillis();int n=jdbc.update("UPDATE ai_test_data_lease SET status='RELEASED',released_at=?,update_time=?,version=version+1 WHERE id=? AND status='ACTIVE' AND lease_token_hash=?",now,now,id,DigestUtils.sha256Hex(StringUtils.defaultString(token)));if(n!=1)throw new MSException("TEST_DATA_LEASE_INVALID");cleanup.enqueue(id,"DATASET");}
    @Transactional(rollbackFor=Exception.class)
    public int releaseForExecution(String executionId){List<String> ids=jdbc.queryForList("SELECT id FROM ai_test_data_lease WHERE execution_id=? AND status='ACTIVE' FOR UPDATE",String.class,executionId);long now=System.currentTimeMillis();for(String id:ids){jdbc.update("UPDATE ai_test_data_lease SET status='RELEASED',released_at=?,update_time=?,version=version+1 WHERE id=? AND status='ACTIVE'",now,now,id);String cleanupId=cleanup.enqueue(id,"DATASET");cleanup.execute(cleanupId);}return ids.size();}
    @Scheduled(fixedDelayString="${agent.execution.data-lease-reclaim-ms:30000}") @Transactional(rollbackFor=Exception.class)
    public int reclaimExpired(){long now=System.currentTimeMillis();List<String> ids=jdbc.queryForList("SELECT id FROM ai_test_data_lease WHERE status='ACTIVE' AND expires_at<=? FOR UPDATE",String.class,now);for(String id:ids){jdbc.update("UPDATE ai_test_data_lease SET status='EXPIRED',released_at=?,update_time=?,version=version+1 WHERE id=? AND status='ACTIVE'",now,now,id);cleanup.enqueue(id,"DATASET");}return ids.size();}
    private String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private AgentTestDataLeaseDTO map(Map<String,Object>r,String token){AgentTestDataLeaseDTO d=new AgentTestDataLeaseDTO();d.setId((String)r.get("id"));d.setTaskId((String)r.get("task_id"));d.setExecutionId((String)r.get("execution_id"));d.setProjectId((String)r.get("project_id"));d.setDatasetId((String)r.get("dataset_id"));d.setDataKey((String)r.get("data_key"));d.setNamespace((String)r.get("namespace"));d.setStatus((String)r.get("status"));d.setLeaseToken(token);d.setExpiresAt(((Number)r.get("expires_at")).longValue());d.setReleasedAt(r.get("released_at")==null?null:((Number)r.get("released_at")).longValue());d.setVersion(((Number)r.get("version")).intValue());return d;}
}
