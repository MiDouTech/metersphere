package io.metersphere.system.job;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import io.metersphere.system.dto.department.SyncResult;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import io.metersphere.system.schedule.BaseScheduleJob;
import io.metersphere.system.service.department.WecomOrgSyncApplicationService;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

import java.util.List;

public class WecomOrgSyncJob extends BaseScheduleJob {

    @Override
    protected void businessExecute(JobExecutionContext context) {
        WecomOrgSyncApplicationService syncApplicationService =
                CommonBeanFactory.getBean(WecomOrgSyncApplicationService.class);
        OrgWecomSyncConfigMapper configMapper = CommonBeanFactory.getBean(OrgWecomSyncConfigMapper.class);
        if (syncApplicationService == null || configMapper == null) {
            LogUtils.error("WecomOrgSyncJob dependencies not found");
            return;
        }
        String organizationId = resourceId;
        OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
        example.createCriteria().andOrganizationIdEqualTo(organizationId);
        List<OrgWecomSyncConfig> configs = configMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(configs)) {
            LogUtils.warn("WecomOrgSyncJob skipped, config not found: " + organizationId);
            return;
        }
        OrgWecomSyncConfig config = configs.getFirst();
        int maxRetry = config.getRetryTimes() == null || config.getRetryTimes() < 0 ? 3 : config.getRetryTimes();
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                SyncResult result = syncApplicationService.syncSchedule(organizationId);
                LogUtils.info("WecomOrgSyncJob finished: org={}, status={}", organizationId, result.getSyncStatus());
                return;
            } catch (MSException ex) {
                if (ex.getErrorCode() == MsHttpResultCode.CONFLICT) {
                    LogUtils.info("WecomOrgSyncJob skipped, sync already running: {}", organizationId);
                    return;
                }
                if (attempt >= maxRetry) {
                    LogUtils.error("WecomOrgSyncJob failed after retries: " + organizationId, ex);
                    throw ex;
                }
                sleepBackoff(attempt + 1);
            } catch (Exception ex) {
                if (attempt >= maxRetry) {
                    LogUtils.error("WecomOrgSyncJob failed after retries: " + organizationId, ex);
                    throw new MSException("WecomOrgSyncJob failed: " + ex.getMessage());
                }
                sleepBackoff(attempt + 1);
            }
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new MSException("WecomOrgSyncJob retry interrupted");
        }
    }

    public static JobKey getJobKey(String organizationId) {
        return new JobKey(organizationId, WecomOrgSyncJob.class.getName());
    }

    public static TriggerKey getTriggerKey(String organizationId) {
        return new TriggerKey(organizationId, WecomOrgSyncJob.class.getName());
    }
}
