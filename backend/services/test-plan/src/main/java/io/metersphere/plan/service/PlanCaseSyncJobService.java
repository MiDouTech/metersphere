package io.metersphere.plan.service;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.utils.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;

/** Durable, resumable reconciliation. At most one plan is processed per lease, never one project transaction. */
@Service
public class PlanCaseSyncJobService {
    private static final Logger LOG = LoggerFactory.getLogger(PlanCaseSyncJobService.class);
    private final JdbcTemplate jdbc;
    private final UserLoginService users;
    private final TestPlanFunctionalCaseService synchronization;
    private final TransactionTemplate tx;
    private volatile long retryScanAfter;

    public PlanCaseSyncJobService(JdbcTemplate jdbc, UserLoginService users,
                                  TestPlanFunctionalCaseService synchronization, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.users = users;
        this.synchronization = synchronization;
        this.tx = new TransactionTemplate(manager);
        this.tx.setTimeout(25);
    }

    private void authorize(String projectId, String userId) {
        var project = jdbc.queryForList("SELECT organization_id,module_setting FROM project WHERE id=? AND deleted=0 AND enable=1", projectId);
        var user = users.getUserDTO(userId);
        if (project.isEmpty() || user == null || !Boolean.TRUE.equals(user.getEnable())) throw denied();
        String settings = (String) project.getFirst().get("module_setting");
        if (settings == null || !io.metersphere.sdk.util.JSON.parseArray(settings, String.class)
                .contains(io.metersphere.plan.constants.TestPlanResourceConfig.CONFIG_TEST_PLAN_FUNCTIONAL_CASE)) throw denied();
        String orgId = (String) project.getFirst().get("organization_id");
        var actor = SessionUser.fromUser(user, "");
        if (!SessionUtils.hasPermission(actor, orgId, projectId, PermissionConstants.FUNCTIONAL_CASE_READ)
                || !SessionUtils.hasPermission(actor, orgId, projectId, PermissionConstants.TEST_PLAN_READ_ASSOCIATION)) throw denied();
    }

    public Map<String, Object> create(String projectId, String userId, String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) throw new MSException("同步请求标识不正确");
        authorize(projectId, userId);
        String id = tx.execute(status -> {
            String proposed = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            jdbc.update("""
                    INSERT INTO plan_case_sync_job(id,project_id,user_id,request_id,state,create_time,update_time)
                    VALUES (?,?,?,?,'QUEUED',?,?) ON DUPLICATE KEY UPDATE id=id
                    """, proposed, projectId, userId, requestId, now, now);
            String actual = jdbc.queryForObject("SELECT id FROM plan_case_sync_job WHERE project_id=? AND user_id=? AND request_id=?",
                    String.class, projectId, userId, requestId);
            if (proposed.equals(actual)) jdbc.update("""
                    INSERT INTO plan_case_sync_item(job_id,plan_id,state)
                    SELECT ?,id,'PENDING' FROM test_plan WHERE project_id=? AND type='TEST_PLAN' AND status<>'ARCHIVED'
                    """, actual, projectId);
            return actual;
        });
        return get(id, userId);
    }

    private Map<String, Object> owned(String id, String userId) {
        var rows = jdbc.queryForList("SELECT id,project_id,user_id,state,create_time,update_time FROM plan_case_sync_job WHERE id=? AND user_id=?", id, userId);
        if (rows.isEmpty()) throw denied();
        authorize((String) rows.getFirst().get("project_id"), userId);
        return rows.getFirst();
    }

    public Map<String, Object> get(String id, String userId) {
        var result = owned(id, userId);
        result.put("counts", jdbc.queryForList("SELECT state,COUNT(*) AS total,SUM(updated_count) AS updatedCount,SUM(defect_added_count) AS defectAddedCount FROM plan_case_sync_item WHERE job_id=? GROUP BY state", id));
        result.put("failures", jdbc.queryForList("SELECT plan_id AS planId,error_code AS code,trace_id AS traceId FROM plan_case_sync_item WHERE job_id=? AND state='FAILED' ORDER BY plan_id LIMIT 100", id));
        return result;
    }

    public Map<String, Object> cancel(String id, String userId) {
        owned(id, userId);
        // The current plan transaction may finish; cancellation prevents the next plan from starting.
        jdbc.update("UPDATE plan_case_sync_job SET state='CANCELLED',update_time=? WHERE id=? AND state IN ('QUEUED','RUNNING')", System.currentTimeMillis(), id);
        return get(id, userId);
    }

    public Map<String, Object> retry(String id, String userId) {
        owned(id, userId);
        tx.executeWithoutResult(status -> {
            var row = jdbc.queryForList("SELECT state,lease_until FROM plan_case_sync_job WHERE id=? FOR UPDATE", id).getFirst();
            if (!java.util.Set.of("PARTIAL_FAILED", "CANCELLED").contains(row.get("state"))) throw new MSException("当前任务不能重试");
            if (row.get("lease_until") instanceof Number until && until.longValue() > System.currentTimeMillis()) {
                throw new MSException("当前批次正在结束，请稍后重试");
            }
            jdbc.update("UPDATE plan_case_sync_item SET state='PENDING',error_code=NULL,trace_id=NULL WHERE job_id=? AND state='FAILED'", id);
            jdbc.update("UPDATE plan_case_sync_job SET state='QUEUED',lease_token=NULL,lease_until=NULL,update_time=? WHERE id=?", System.currentTimeMillis(), id);
        });
        return get(id, userId);
    }

    @Scheduled(fixedDelayString = "${ms.plan-sync.worker-delay-ms:2000}")
    public void processNext() {
        if (System.currentTimeMillis() < retryScanAfter) return;
        try {
            processNextLease();
        } catch (Exception error) {
            retryScanAfter = System.currentTimeMillis() + 60000;
            LOG.warn("Plan synchronization scan failed; retry after 60 seconds traceId={}", UUID.randomUUID(), error);
        }
    }

    private void processNextLease() {
        String lease = UUID.randomUUID().toString();
        Map<String, Object> job = tx.execute(status -> {
            long now = System.currentTimeMillis();
            var rows = jdbc.queryForList("""
                    SELECT * FROM plan_case_sync_job WHERE state IN ('QUEUED','RUNNING')
                     AND (lease_until IS NULL OR lease_until<?) ORDER BY create_time,id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, now);
            if (rows.isEmpty()) return null;
            var row = rows.getFirst();
            jdbc.update("UPDATE plan_case_sync_job SET state='RUNNING',lease_token=?,lease_until=?,update_time=? WHERE id=?",
                    lease, now + 120000, now, row.get("id"));
            return row;
        });
        if (job == null) return;
        String id = (String) job.get("id"), userId = (String) job.get("user_id");
        var items = jdbc.queryForList("SELECT plan_id FROM plan_case_sync_item WHERE job_id=? AND state='PENDING' ORDER BY plan_id LIMIT 1", id);
        if (items.isEmpty()) {
            jdbc.update("""
                    UPDATE plan_case_sync_job SET state=IF(EXISTS(SELECT 1 FROM plan_case_sync_item WHERE job_id=? AND state='FAILED'),'PARTIAL_FAILED','SUCCEEDED'),
                     lease_token=NULL,lease_until=NULL,update_time=? WHERE id=? AND lease_token=? AND state='RUNNING'
                    """, id, System.currentTimeMillis(), id, lease);
            return;
        }
        String planId = (String) items.getFirst().get("plan_id");
        try {
            authorize((String) job.get("project_id"), userId);
            var target = jdbc.queryForList("SELECT id FROM test_plan WHERE id=? AND project_id=?", planId, job.get("project_id"));
            if (target.isEmpty()) throw new MSException("目标计划已不存在");
            var result = tx.execute(status -> synchronization.syncPlanCases(planId, userId));
            jdbc.update("""
                    UPDATE plan_case_sync_item i JOIN plan_case_sync_job j ON j.id=i.job_id
                     SET i.state='SUCCESS',i.updated_count=?,i.defect_added_count=?
                     WHERE i.job_id=? AND i.plan_id=? AND j.lease_token=?
                    """, result.getUpdatedCount(), result.getDefectAddedCount(), id, planId, lease);
        } catch (Exception error) {
            String traceId = UUID.randomUUID().toString();
            LOG.warn("Plan reconciliation failed jobId={} planId={} traceId={}", id, planId, traceId, error);
            jdbc.update("""
                    UPDATE plan_case_sync_item i JOIN plan_case_sync_job j ON j.id=i.job_id
                     SET i.state='FAILED',i.error_code='PLAN_SYNC_FAILED',i.trace_id=?
                     WHERE i.job_id=? AND i.plan_id=? AND j.lease_token=?
                    """, traceId, id, planId, lease);
        } finally {
            jdbc.update("UPDATE plan_case_sync_job SET lease_token=NULL,lease_until=NULL,update_time=? WHERE id=? AND lease_token=?", System.currentTimeMillis(), id, lease);
        }
    }

    private static MSException denied() { return new MSException("同步任务不存在或当前账号无权操作"); }
}
