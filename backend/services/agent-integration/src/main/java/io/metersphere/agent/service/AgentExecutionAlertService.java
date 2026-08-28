package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentExecutionAlertService {
    @Resource private JdbcTemplate jdbc;
    @Resource private AgentProjectService projects;

    @Scheduled(fixedDelayString = "${agent.execution.alert-scan-ms:60000}")
    public void scan() {
        long now = System.currentTimeMillis();long offlineBefore = now - 120_000;long expiry = now + 7L * 24 * 60 * 60 * 1000;
        emitRows("RUNNER_OFFLINE", "CRITICAL", jdbc.queryForList("SELECT id resource_id,organization_id,NULL project_id,NULL task_id,NULL trace_id,CONCAT('Runner ',name,' is offline') message,create_user receiver FROM ai_runner WHERE last_heartbeat_time IS NULL OR last_heartbeat_time<?", offlineBefore));
        emitRows("CREDENTIAL_EXPIRING", "WARN", jdbc.queryForList("SELECT id resource_id,organization_id,project_id,NULL task_id,NULL trace_id,CONCAT('Credential ',name,' expires within 7 days') message,update_user receiver FROM ai_credential_reference WHERE enabled=b'1' AND status='ACTIVE' AND expires_at BETWEEN ? AND ?", now, expiry));
        emitRows("TASK_BLOCKED", "WARN", jdbc.queryForList("SELECT id resource_id,organization_id,project_id,id task_id,trace_id,CONCAT('AI execution blocked: ',COALESCE(blocked_reason,'WAITING_HUMAN')) message,executed_by receiver FROM ai_execution_task WHERE status IN ('WAITING_HUMAN','WAITING_LOGIN')"));
        emitRows("HUMAN_TIMEOUT", "WARN", jdbc.queryForList("SELECT h.id resource_id,t.organization_id,t.project_id,h.task_id,t.trace_id,'Human request timed out' message,t.executed_by receiver FROM ai_execution_human_request h JOIN ai_execution_task t ON t.id=h.task_id WHERE h.status='PENDING' AND h.expires_at<?", now));
        emitRows("CLEANUP_BACKLOG", "CRITICAL", jdbc.queryForList("SELECT c.id resource_id,t.organization_id,t.project_id,t.id task_id,t.trace_id,'Test data cleanup failed or is backlogged' message,t.executed_by receiver FROM ai_test_data_cleanup c JOIN ai_test_data_lease l ON l.id=c.lease_id JOIN ai_execution_task t ON t.id=l.task_id WHERE c.status='FAILED' OR c.attempt_count>=3"));
        emitRows("WRITEBACK_FAILED", "CRITICAL", jdbc.queryForList("SELECT id resource_id,organization_id,project_id,id task_id,trace_id,'AI execution result writeback failed' message,executed_by receiver FROM ai_execution_task WHERE writeback_status='FAILED'"));
        emitRows("GATEWAY_UNHEALTHY", "CRITICAL", jdbc.queryForList("SELECT id resource_id,organization_id,project_id,NULL task_id,NULL trace_id,CONCAT('MAP Gateway model profile unavailable: ',name) message,update_user receiver FROM ai_model_profile WHERE enabled=b'1' AND last_verify_status='FAILED'"));
        emitRows("MODEL_BUDGET_EXCEEDED", "CRITICAL", jdbc.queryForList("SELECT id resource_id,organization_id,project_id,id task_id,trace_id,'AI execution model budget exceeded' message,executed_by receiver FROM ai_execution_task WHERE blocked_detail='MODEL_BUDGET_EXCEEDED'"));
    }

    public List<Map<String, Object>> list(String projectId, String status) {
        String resolvedProjectId = projects.resolveProjectId(projectId);
        String organizationId = organizationId(resolvedProjectId);
        String normalizedStatus = StringUtils.upperCase(StringUtils.trimToNull(status));
        if (normalizedStatus != null && !List.of("OPEN", "ACKNOWLEDGED").contains(normalizedStatus)) throw new MSException("ALERT_STATUS_INVALID");
        return jdbc.queryForList("SELECT id,project_id projectId,task_id taskId,alert_type alertType,severity,message,trace_id traceId,status,acknowledged_by acknowledgedBy,acknowledged_at acknowledgedAt,create_time createTime FROM ai_execution_alert WHERE organization_id=? AND (project_id=? OR project_id IS NULL) AND (? IS NULL OR status=?) ORDER BY create_time DESC LIMIT 500",
                organizationId, resolvedProjectId, normalizedStatus, normalizedStatus);
    }

    public void acknowledge(String projectId, String id) {
        String resolvedProjectId = projects.resolveProjectId(projectId);long now = System.currentTimeMillis();
        int changed = jdbc.update("UPDATE ai_execution_alert SET status='ACKNOWLEDGED',acknowledged_by=?,acknowledged_at=?,update_time=? WHERE id=? AND organization_id=? AND (project_id=? OR project_id IS NULL) AND status='OPEN'",
                SessionUtils.getUserId(), now, now, id, organizationId(resolvedProjectId), resolvedProjectId);
        if (changed != 1) throw new MSException("ALERT_NOT_OPEN_OR_NOT_FOUND");
    }

    private void emitRows(String type, String severity, List<Map<String, Object>> rows) { rows.forEach(row -> emit(type, severity, row)); }

    private void emit(String type, String severity, Map<String, Object> row) {
        long now = System.currentTimeMillis();String resource = String.valueOf(row.get("resource_id"));String fingerprint = type + ":" + resource + ":" + (now / 86_400_000L);
        String organizationId = (String) row.get("organization_id");if (StringUtils.isBlank(organizationId)) return;
        try {
            jdbc.update("INSERT INTO ai_execution_alert(id,fingerprint,organization_id,project_id,task_id,alert_type,severity,message,trace_id,status,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,'OPEN',?,?)",
                    IDGenerator.nextStr(), fingerprint, organizationId, row.get("project_id"), row.get("task_id"), type, severity,
                    StringUtils.abbreviate(String.valueOf(row.get("message")), 1000), row.get("trace_id"), now, now);
            String receiver = (String) row.get("receiver");
            if (StringUtils.isNotBlank(receiver)) jdbc.update("INSERT INTO notification(type,receiver,subject,status,create_time,operator,operation,resource_id,project_id,resource_type,resource_name,content) VALUES ('AI_EXECUTION',?,'AI execution operations alert','UNREAD',?,'system:ai-alert','AI_ALERT',?,?,'AI_EXECUTION_ALERT',?,?)",
                    receiver, now, resource, row.get("project_id"), type, row.get("message"));
        } catch (DuplicateKeyException ignored) {
            // Daily fingerprint makes repeated scheduled scans idempotent.
        }
    }

    private String organizationId(String projectId) {
        return jdbc.queryForObject("SELECT organization_id FROM project WHERE id=?", String.class, projectId);
    }
}
