package io.metersphere.functional.asset.service;

import jakarta.annotation.Resource;
import io.metersphere.sdk.util.LogUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CaseAssetHistorySyncWorker {
    @Resource private JdbcTemplate jdbcTemplate;
    @Lazy
    @Resource private CaseAssetService caseAssetService;

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
                Map<String, Integer> counts = caseAssetService.syncHistoricalProject(projectId, organizationId, operator);
                jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status='SUCCESS',case_created_count=?," +
                                "case_updated_count=?,case_skipped_count=?,failure_reason=NULL,update_time=? WHERE id=?",
                        counts.getOrDefault("created", 0), counts.getOrDefault("updated", 0),
                        counts.getOrDefault("skipped", 0), System.currentTimeMillis(), itemId);
            } catch (Exception e) {
                jdbcTemplate.update("UPDATE case_asset_history_sync_item SET status='FAILED',failure_reason=?,update_time=? WHERE id=?",
                        StringUtils.left(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()), 2000),
                        System.currentTimeMillis(), itemId);
            }
            refresh(jobId, false);
        }
        refresh(jobId, true);
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
