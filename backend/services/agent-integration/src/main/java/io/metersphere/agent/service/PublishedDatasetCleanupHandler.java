package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublishedDatasetCleanupHandler implements AgentTestDataCleanupHandler {
    @Resource private JdbcTemplate jdbc;
    @Override public boolean supports(String cleanupType){return "DATASET".equals(cleanupType);}
    @Override public void cleanup(String leaseId,String taskId,String executionId,String datasetId,String dataKey,String namespace){Integer active=jdbc.queryForObject("SELECT COUNT(1) FROM ai_test_data_lease WHERE id=? AND task_id=? AND execution_id=? AND status='ACTIVE'",Integer.class,leaseId,taskId,executionId);if(active!=null&&active>0)throw new MSException("TEST_DATA_LEASE_STILL_ACTIVE");long now=System.currentTimeMillis();int changed=jdbc.update("UPDATE ai_test_data_lease SET content_snapshot=NULL,content_sha256=NULL,cleaned_at=COALESCE(cleaned_at,?),update_time=? WHERE id=? AND task_id=? AND execution_id=? AND dataset_id=? AND data_key=? AND namespace=? AND status<>'ACTIVE'",now,now,leaseId,taskId,executionId,datasetId,dataKey,namespace);if(changed!=1)throw new MSException("TEST_DATA_CLEANUP_TARGET_MISMATCH");}
}
