package io.metersphere.plan.hub.service;

import io.metersphere.plan.hub.job.DefaultHubSyncCronJob;
import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.schedule.ScheduleManager;
import jakarta.annotation.Resource;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * 启动时注册每日 0 点枢纽对账 Cron
 */
@Service
public class DefaultHubSyncScheduleService implements ApplicationRunner {

    @Resource
    private ScheduleManager scheduleManager;

    @Override
    public void run(ApplicationArguments args) {
        try {
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("resourceId", DefaultHubSyncCronJob.RESOURCE_ID);
            jobDataMap.put("expression", DefaultHubConstants.SYNC_CRON_DAILY);
            jobDataMap.put("userId", "system");
            scheduleManager.addOrUpdateCronJob(
                    DefaultHubSyncCronJob.getJobKey(),
                    DefaultHubSyncCronJob.getTriggerKey(),
                    DefaultHubSyncCronJob.class,
                    DefaultHubConstants.SYNC_CRON_DAILY,
                    jobDataMap);
        } catch (SchedulerException e) {
            LogUtils.error("register default hub sync cron failed", e);
        }
    }
}
