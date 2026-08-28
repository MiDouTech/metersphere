package io.metersphere.agent.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionReconciler {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentRunnerService runnerService;
    @Resource private AgentTestDataCleanupService cleanupService;
    @Resource private AgentExecutionAuditService auditService;
    @Value("${agent.execution.reconcile-batch-size:200}") private int batchSize;

    @Scheduled(fixedDelayString="${agent.execution.reconcile-scan-ms:60000}")
    public void reconcile(){reconcileStaleTasks();reconcileExpiredLeases();reconcilePendingArtifacts();reconcileCleanupBacklog();}

    @Transactional(rollbackFor=Exception.class)
    public int reconcileStaleTasks(){long now=System.currentTimeMillis();var rows=jdbc.queryForList("SELECT id,status,trace_id FROM ai_execution_task WHERE status NOT IN ('SUCCESS','FAILED','PARTIAL_SUCCESS','BLOCKED','CANCELED','EXPIRED') AND timeout_at IS NOT NULL AND timeout_at<=? ORDER BY timeout_at LIMIT ? FOR UPDATE",now,batchSize);for(var row:rows){int n=jdbc.update("UPDATE ai_execution_task SET status='EXPIRED',blocked_reason='BLOCKED_POLICY',blocked_detail='TASK_TIMEOUT',runner_id=NULL,runner_lease_id=NULL,update_time=?,version=version+1 WHERE id=? AND status=?",now,row.get("id"),row.get("status"));if(n==1)auditService.record("SYSTEM:reconciler","TASK_TIMEOUT_RECONCILED","TASK:"+row.get("id"),row,java.util.Map.of("status","EXPIRED"),String.valueOf(row.get("trace_id")));}return rows.size();}
    public void reconcileExpiredLeases(){runnerService.expireLeases();}
    @Transactional(rollbackFor=Exception.class)
    public int reconcilePendingArtifacts(){long now=System.currentTimeMillis();return jdbc.update("UPDATE ai_execution_artifact SET status='EXPIRED',upload_status='EXPIRED' WHERE status='PREPARED' AND retention_until IS NOT NULL AND retention_until<=?",now);}
    public void reconcileCleanupBacklog(){cleanupService.retryDue();}
}
