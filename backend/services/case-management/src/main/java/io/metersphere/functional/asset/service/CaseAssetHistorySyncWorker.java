package io.metersphere.functional.asset.service;

import jakarta.annotation.Resource;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CaseAssetHistorySyncWorker {
    @Resource private JdbcTemplate jdbcTemplate;
    @Lazy
    @Resource private CaseAssetService caseAssetService;
    @Resource private CaseAssetHistoryCaseSyncService caseSyncService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        try {
            List<String> interruptedJobIds = jdbcTemplate.queryForList(
                    "SELECT id FROM case_asset_history_sync_job WHERE status='RUNNING'", String.class);
            for (String jobId : interruptedJobIds) {
                jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status='FAILED',failure_reason=?,update_time=? "
                                + "WHERE job_id=? AND status='PENDING'",
                        "服务重启导致同步中断，可重试该项目", System.currentTimeMillis(), jobId);
                refresh(jobId, true);
            }
        } catch (Exception e) {
            LogUtils.error("Recover interrupted case asset history jobs failed", e);
        }
    }

    @Async
    public void execute(String jobId, String organizationId, String operator) {
        int acquired = jdbcTemplate.update("UPDATE case_asset_history_sync_job SET status='RUNNING',update_time=? " +
                "WHERE id=? AND status IN ('PENDING','FAILED','PARTIAL_SUCCESS')", System.currentTimeMillis(), jobId);
        if (acquired == 0) return;
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT id,project_id projectId FROM case_asset_history_sync_item " +
                "WHERE job_id=? AND status IN ('PENDING','FAILED') ORDER BY create_time,project_id", jobId);
        for (Map<String, Object> item : items) {
            String itemId = String.valueOf(item.get("id"));
            String projectId = String.valueOf(item.get("projectId"));
            try {
                Map<String, Object> context = caseAssetService.prepareHistoricalProject(projectId, organizationId, operator);
                if (Boolean.TRUE.equals(context.get("skipped"))) {
                    jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status='SKIPPED',case_created_count=0," +
                                    "case_updated_count=0,case_skipped_count=0,failure_reason=NULL,update_time=? WHERE id=?",
                            System.currentTimeMillis(), itemId);
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> caseIds = (List<String>) context.getOrDefault("caseIds", List.of());
                    String hubModuleId = String.valueOf(context.get("hubModuleId"));
                    int created = 0;
                    int updated = 0;
                    int skipped = 0;
                    List<String> failures = new ArrayList<>();
                    for (String caseId : caseIds) {
                        try {
                            String outcome = syncCaseWithRetry(projectId, organizationId, operator, hubModuleId, caseId);
                            if (StringUtils.equals(outcome, "CREATED")) created++;
                            else if (StringUtils.equals(outcome, "UPDATED")) updated++;
                            else skipped++;
                        } catch (Exception caseError) {
                            String reference = itemId + ":" + caseId;
                            LogUtils.error("Historical case asset sync failed, reference=" + reference
                                    + ", project=" + projectId + ", case=" + caseId, caseError);
                            failures.add("用例 " + caseId + "：" + safeFailureMessage(caseError, reference));
                        }
                    }
                    String status = failures.isEmpty() ? "SUCCESS" : "FAILED";
                    String failureReason = failures.isEmpty() ? null
                            : StringUtils.left(String.join("；", failures), 2000);
                    jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status=?," +
                                    "case_created_count=case_created_count+?,case_updated_count=case_updated_count+?," +
                                    "case_skipped_count=case_skipped_count+?,failure_reason=?,update_time=? WHERE id=?",
                            status, created, updated, skipped, failureReason, System.currentTimeMillis(), itemId);
                }
            } catch (Exception e) {
                LogUtils.error("Historical case asset project preparation failed, item=" + itemId
                        + ", project=" + projectId, e);
                jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status='FAILED',failure_reason=?,update_time=? WHERE id=?",
                        safeFailureMessage(e, itemId),
                        System.currentTimeMillis(), itemId);
            }
            refresh(jobId, false);
        }
        refresh(jobId, true);
    }

    private String syncCaseWithRetry(String projectId, String organizationId, String operator,
                                     String hubModuleId, String caseId) {
        int attempt = 0;
        while (true) {
            try {
                return caseSyncService.sync(projectId, organizationId, operator, hubModuleId, caseId);
            } catch (TransientDataAccessException transientFailure) {
                attempt++;
                if (attempt >= 3) throw transientFailure;
                LogUtils.warn("Retry transient historical case sync, project=" + projectId
                        + ", case=" + caseId + ", attempt=" + (attempt + 1));
            }
        }
    }

    private String safeFailureMessage(Exception error, String reference) {
        if (error instanceof MSException && StringUtils.isNotBlank(error.getMessage())) {
            return StringUtils.left(error.getMessage(), 1500);
        }
        return "同步失败，请根据错误编号 " + reference + " 查看服务日志";
    }

    private void refresh(String jobId, boolean finish) {
        Map<String, Object> counts = jdbcTemplate.queryForMap("SELECT COUNT(*) total," +
                "COALESCE(SUM(status='SUCCESS'),0) success,COALESCE(SUM(status='FAILED'),0) failed," +
                "COALESCE(SUM(status='SKIPPED'),0) skipped," +
                "COALESCE(SUM(case_created_count),0) created,COALESCE(SUM(case_updated_count),0) updated," +
                "COALESCE(SUM(case_skipped_count),0) caseSkipped FROM case_asset_history_sync_item WHERE job_id=?", jobId);
        long failed = ((Number) counts.get("failed")).longValue();
        long success = ((Number) counts.get("success")).longValue();
        String status = finish ? failed == 0 ? "SUCCESS" : success == 0 ? "FAILED" : "PARTIAL_SUCCESS" : "RUNNING";
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE case_asset_history_sync_job SET status=?,total_count=?,success_count=?,skipped_count=?," +
                        "failed_count=?,case_created_count=?,case_updated_count=?,case_skipped_count=?,update_time=?,finish_time=? WHERE id=?",
                status, counts.get("total"), counts.get("success"), counts.get("skipped"), counts.get("failed"),
                counts.get("created"), counts.get("updated"), counts.get("caseSkipped"), now, finish ? now : null, jobId);
    }
}
