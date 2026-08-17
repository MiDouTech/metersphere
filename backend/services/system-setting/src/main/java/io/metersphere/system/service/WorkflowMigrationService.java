package io.metersphere.system.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowMigrationService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private TransactionTemplate transactionTemplate;

    /**
     * Async invocations do not survive a process restart. Keep interrupted batches
     * recoverable so an administrator can resume them instead of seeing RUNNING forever.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedBatches() {
        try {
            long now = System.currentTimeMillis();
            jdbcTemplate.update("UPDATE workflow_migration_batch SET status='FAILED',finish_time=?,update_time=? "
                    + "WHERE status='RUNNING'", now, now);
        } catch (Exception e) {
            LogUtils.error("Recover interrupted workflow migration batches failed", e);
        }
    }

    @Async
    public void execute(String batchId, String targetFlowId, int targetVersion, Map<String, String> mappings) {
        int acquired = jdbcTemplate.update("UPDATE workflow_migration_batch SET status='RUNNING', update_time=? "
                + "WHERE id=? AND status IN ('PENDING','FAILED','PARTIAL_SUCCESS')", System.currentTimeMillis(), batchId);
        if (acquired == 0) return;
        List<Map<String, Object>> bugs = jdbcTemplate.queryForList("SELECT bug_id id,source_status_id status,source_workflow_id workflow_id,"
                + "source_workflow_version workflow_version,target_status_id FROM workflow_migration_item "
                + "WHERE batch_id=? AND status='PENDING' ORDER BY create_time,bug_id", batchId);
        long success = countItems(batchId, "SUCCESS");
        long failed = 0;
        for (Map<String, Object> bug : bugs) {
            String bugId = String.valueOf(bug.get("id"));
            String sourceStatus = String.valueOf(bug.get("status"));
            String targetStatus = String.valueOf(bug.get("target_status_id"));
            if (StringUtils.isBlank(targetStatus)) {
                recordFailure(batchId, bugId, sourceStatus, "STATUS_UNMAPPED", "状态未映射");
                jdbcTemplate.update("UPDATE workflow_migration_item SET status='FAILED',update_time=? WHERE batch_id=? AND bug_id=?",
                        System.currentTimeMillis(), batchId, bugId);
                failed++;
                continue;
            }
            try {
                Boolean migrated = transactionTemplate.execute(status -> {
                    int updated = jdbcTemplate.update("UPDATE bug SET status=?, workflow_id=?, workflow_version=?, update_time=? "
                                    + "WHERE id=? AND deleted=b'0' AND workflow_id IS NULL AND status=?",
                            targetStatus, targetFlowId, targetVersion, System.currentTimeMillis(), bugId, sourceStatus);
                    if (updated != 1) {
                        jdbcTemplate.update("UPDATE workflow_migration_item SET status='SKIPPED',update_time=? "
                                        + "WHERE batch_id=? AND bug_id=?",
                                System.currentTimeMillis(), batchId, bugId);
                        return false;
                    }
                    jdbcTemplate.update("UPDATE workflow_migration_item SET status='SUCCESS',update_time=? WHERE batch_id=? AND bug_id=?",
                            System.currentTimeMillis(), batchId, bugId);
                    return true;
                });
                if (Boolean.TRUE.equals(migrated)) success++;
            } catch (Exception e) {
                failed++;
                jdbcTemplate.update("UPDATE workflow_migration_item SET status='FAILED',update_time=? WHERE batch_id=? AND bug_id=?",
                        System.currentTimeMillis(), batchId, bugId);
                recordFailure(batchId, bugId, sourceStatus, "MIGRATION_FAILED", StringUtils.defaultString(e.getMessage()));
            }
            updateProgress(batchId, success, failed);
        }
        long total = countTotal(batchId);
        long skipped = Math.max(0, total - success - failed);
        String status = failed == 0 ? "COMPLETED" : success == 0 ? "FAILED" : "PARTIAL_SUCCESS";
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE workflow_migration_batch SET status=?,success_count=?,failed_count=?,skipped_count=?,"
                        + "update_time=?,finish_time=? WHERE id=?", status, success, failed, skipped, now, now, batchId);
    }

    public Map<String, Object> getBatch(String batchId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id,target_flow_id targetFlowId,dry_run dryRun,"
                + "status,total_count totalCount,success_count successCount,skipped_count skippedCount,failed_count failedCount,"
                + "create_user createUser,create_time createTime,update_time updateTime,finish_time finishTime "
                + "FROM workflow_migration_batch WHERE id=?", batchId);
        if (rows.isEmpty()) throw new MSException("迁移批次不存在");
        Map<String, Object> result = new java.util.LinkedHashMap<>(rows.getFirst());
        result.put("failures", jdbcTemplate.queryForList("SELECT bug_id bugId,source_status sourceStatus,failure_code failureCode,"
                + "failure_reason failureReason,create_time createTime FROM workflow_migration_exception WHERE batch_id=? ORDER BY create_time", batchId));
        return result;
    }

    public List<Map<String, Object>> listBatches(String targetFlowId) {
        return jdbcTemplate.queryForList("SELECT id,target_flow_id targetFlowId,dry_run dryRun,status,"
                + "total_count totalCount,success_count successCount,skipped_count skippedCount,failed_count failedCount,"
                + "create_user createUser,create_time createTime,update_time updateTime,finish_time finishTime "
                + "FROM workflow_migration_batch WHERE target_flow_id=? AND dry_run=b'0' "
                + "ORDER BY create_time DESC LIMIT 20", targetFlowId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(String batchId) {
        Map<String, Object> batch = getBatch(batchId);
        if (Boolean.TRUE.equals(batch.get("dryRun"))) throw new MSException("预检批次无需回滚");
        String currentStatus = String.valueOf(batch.get("status"));
        if (!StringUtils.equalsAny(currentStatus, "COMPLETED", "FAILED", "PARTIAL_SUCCESS")) {
            throw new MSException("仅已完成、失败或部分成功的批次可以回滚，当前状态: " + currentStatus);
        }
        int acquired = jdbcTemplate.update("UPDATE workflow_migration_batch SET status='ROLLING_BACK',update_time=? "
                        + "WHERE id=? AND status IN ('COMPLETED','FAILED','PARTIAL_SUCCESS')",
                System.currentTimeMillis(), batchId);
        if (acquired != 1) {
            throw new MSException("迁移批次状态已变化，请刷新后重试");
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT * FROM workflow_migration_item "
                + "WHERE batch_id=? AND status='SUCCESS' ORDER BY create_time DESC", batchId);
        long rolledBack = 0;
        long conflicts = 0;
        for (Map<String, Object> item : items) {
            int updated = jdbcTemplate.update("UPDATE bug SET status=?,workflow_id=?,workflow_version=?,update_time=? "
                            + "WHERE id=? AND status=? AND workflow_id=? AND workflow_version=?",
                    item.get("source_status_id"), item.get("source_workflow_id"), item.get("source_workflow_version"),
                    System.currentTimeMillis(), item.get("bug_id"), item.get("target_status_id"),
                    item.get("target_workflow_id"), item.get("target_workflow_version"));
            String itemStatus = updated == 1 ? "ROLLED_BACK" : "ROLLBACK_CONFLICT";
            jdbcTemplate.update("UPDATE workflow_migration_item SET status=?,update_time=? WHERE id=?",
                    itemStatus, System.currentTimeMillis(), item.get("id"));
            if (updated == 1) rolledBack++; else conflicts++;
        }
        jdbcTemplate.update("UPDATE workflow_migration_batch SET status=?,success_count=?,failed_count=?,update_time=?,finish_time=? WHERE id=?",
                conflicts == 0 ? "ROLLED_BACK" : "ROLLBACK_PARTIAL", rolledBack, conflicts,
                System.currentTimeMillis(), System.currentTimeMillis(), batchId);
    }

    private void recordFailure(String batchId, String bugId, String sourceStatus, String code, String reason) {
        jdbcTemplate.update("INSERT INTO workflow_migration_exception (id,batch_id,bug_id,source_status,failure_code,failure_reason,create_time) "
                        + "VALUES (?,?,?,?,?,?,?)", IDGenerator.nextStr(), batchId, bugId, sourceStatus, code,
                StringUtils.left(reason, 2000), System.currentTimeMillis());
    }

    private void updateProgress(String batchId, long success, long failed) {
        jdbcTemplate.update("UPDATE workflow_migration_batch SET success_count=?,failed_count=?,update_time=? WHERE id=?",
                success, failed, System.currentTimeMillis(), batchId);
    }

    private long countItems(String batchId, String status) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflow_migration_item WHERE batch_id=? AND status=?",
                Long.class, batchId, status);
        return value == null ? 0 : value;
    }

    private long countTotal(String batchId) {
        Long value = jdbcTemplate.queryForObject("SELECT total_count FROM workflow_migration_batch WHERE id=?", Long.class, batchId);
        return value == null ? 0 : value;
    }
}
