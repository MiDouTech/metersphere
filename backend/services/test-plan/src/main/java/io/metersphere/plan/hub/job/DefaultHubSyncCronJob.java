package io.metersphere.plan.hub.job;

import io.metersphere.plan.hub.service.DefaultHubSyncJobService;
import io.metersphere.sdk.util.CommonBeanFactory;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.schedule.BaseScheduleJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.TriggerKey;

/**
 * 每日 0 点枢纽对账（Cron 由 DefaultHubSyncScheduleService 注册）
 */
public class DefaultHubSyncCronJob extends BaseScheduleJob {

    public static final String RESOURCE_ID = "default_hub_global";

    @Override
    protected void businessExecute(JobExecutionContext context) {
        DefaultHubSyncJobService jobService = CommonBeanFactory.getBean(DefaultHubSyncJobService.class);
        if (jobService == null) {
            LogUtils.error("DefaultHubSyncCronJob: DefaultHubSyncJobService not found");
            return;
        }
        jobService.runCronReconcile();
    }

    public static JobKey getJobKey() {
        return new JobKey(RESOURCE_ID, DefaultHubSyncCronJob.class.getName());
    }

    public static TriggerKey getTriggerKey() {
        return new TriggerKey(RESOURCE_ID, DefaultHubSyncCronJob.class.getName());
    }
}
