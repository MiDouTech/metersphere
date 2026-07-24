package io.metersphere.plan.hub.service;

import io.metersphere.functional.hub.dao.DefaultHubSyncJobDao;
import io.metersphere.functional.hub.dto.DefaultHubJobResponse;
import io.metersphere.functional.hub.dto.DefaultHubSyncJobRow;
import io.metersphere.functional.hub.service.DefaultHubCaseSyncService;
import io.metersphere.functional.hub.service.DefaultHubConfigService;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.service.DefaultHubProjectService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 枢纽同步任务：手动/定时对账，更新 progress
 */
@Service
public class DefaultHubSyncJobService {

    @Resource
    private DefaultHubConfigService defaultHubConfigService;
    @Resource
    private DefaultHubProjectService defaultHubProjectService;
    @Resource
    private DefaultHubSyncJobDao defaultHubSyncJobDao;
    @Resource
    private DefaultHubCaseSyncService defaultHubCaseSyncService;
    @Resource
    private DefaultHubPlanSyncService defaultHubPlanSyncService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private DefaultHubSyncJobService self;

    public String getDefaultProjectId() {
        return defaultHubProjectService.getDefaultProjectId();
    }

    public DefaultHubJobResponse startManualSync(String projectId, String operator) {
        String jobId = defaultHubSyncJobDao.createJob(DefaultHubConstants.JOB_TYPE_MANUAL, projectId, operator);
        self.executeSyncAsync(jobId, projectId, operator);
        DefaultHubJobResponse resp = new DefaultHubJobResponse();
        resp.setJobId(jobId);
        resp.setStatus(DefaultHubConstants.JOB_STATUS_PENDING);
        resp.setProgress(0);
        return resp;
    }

    public DefaultHubJobResponse getJob(String jobId) {
        DefaultHubSyncJobRow row = defaultHubSyncJobDao.findById(jobId);
        DefaultHubJobResponse resp = new DefaultHubJobResponse();
        if (row == null) {
            resp.setStatus(DefaultHubConstants.JOB_STATUS_FAILED);
            resp.setErrorMessage("job not found");
            return resp;
        }
        resp.setJobId(row.getId());
        resp.setStatus(row.getStatus());
        resp.setProgress(row.getProgress());
        resp.setSuccessCount(row.getSuccessCount());
        resp.setFailCount(row.getFailCount());
        resp.setErrorMessage(row.getErrorMessage());
        return resp;
    }

    @Async
    public void executeSyncAsync(String jobId, String scopeProjectId, String operator) {
        if (!defaultHubSyncJobDao.tryAcquire(jobId)) {
            return;
        }
        if (!defaultHubConfigService.isSyncEnabled()) {
            defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_SUCCESS, 100, 0, 0, "sync disabled");
            return;
        }
        try {
            List<String> projectIds = resolveProjectIds(scopeProjectId);
            int total = projectIds.size();
            int index = 0;
            int success = 0;
            for (String bizProjectId : projectIds) {
                try {
                    final int projectIndex = index;
                    defaultHubCaseSyncService.reconcileProjectCases(bizProjectId, operator,
                            (done, t, s, f) -> defaultHubSyncJobDao.updateProgress(jobId,
                                    calcProgress(projectIndex, total, done, t), s, f));
                    defaultHubPlanSyncService.reconcileProjectPlans(bizProjectId, operator);
                    success++;
                } catch (Exception e) {
                    LogUtils.error("hub sync project failed: " + bizProjectId, e);
                }
                index++;
                defaultHubSyncJobDao.updateProgress(jobId, index * 100 / Math.max(total, 1), success, total - success);
            }
            defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_SUCCESS, 100, success, total - success, null);
        } catch (Exception e) {
            LogUtils.error("default hub sync job failed: " + jobId, e);
            defaultHubSyncJobDao.finish(jobId, DefaultHubConstants.JOB_STATUS_FAILED, 0, 0, 1,
                    StringUtils.defaultString(e.getMessage()));
        }
    }

    /** 定时任务入口 */
    public void runCronReconcile() {
        if (!defaultHubConfigService.isSyncEnabled()) {
            return;
        }
        String jobId = defaultHubSyncJobDao.createJob(DefaultHubConstants.JOB_TYPE_CRON, null, "system");
        self.executeSyncAsync(jobId, null, "system");
    }

    private List<String> resolveProjectIds(String scopeProjectId) {
        if (StringUtils.isNotBlank(scopeProjectId)) {
            return List.of(scopeProjectId);
        }
        String hubId = defaultHubProjectService.getDefaultProjectId();
        if (StringUtils.isNotBlank(hubId)) {
            return jdbcTemplate.queryForList(
                    "SELECT id FROM project WHERE deleted = 0 AND enable = 1 AND (is_default = 0 OR is_default IS NULL) AND id <> ?",
                    String.class, hubId);
        }
        return jdbcTemplate.queryForList(
                "SELECT id FROM project WHERE deleted = 0 AND enable = 1 AND (is_default = 0 OR is_default IS NULL)",
                String.class);
    }

    private int calcProgress(int projectIndex, int totalProjects, int caseDone, int caseTotal) {
        if (totalProjects <= 0) {
            return 100;
        }
        int base = projectIndex * 100 / totalProjects;
        if (caseTotal <= 0) {
            return base;
        }
        return Math.min(99, base + caseDone * 100 / caseTotal / totalProjects);
    }
}
