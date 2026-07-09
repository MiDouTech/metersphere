package io.metersphere.system.service.department;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.sdk.util.LogUtils;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import io.metersphere.system.job.WecomOrgSyncJob;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import io.metersphere.system.schedule.ScheduleManager;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WecomOrgSyncScheduleService implements ApplicationRunner {

    @Resource
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;
    @Resource
    private ScheduleManager scheduleManager;

    @Override
    public void run(ApplicationArguments args) {
        initEnabledSchedules();
    }

    public void initEnabledSchedules() {
        OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
        example.createCriteria().andScheduleEnabledEqualTo(1);
        List<OrgWecomSyncConfig> configs = orgWecomSyncConfigMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(configs)) {
            return;
        }
        for (OrgWecomSyncConfig config : configs) {
            try {
                refreshSchedule(config);
            } catch (Exception ex) {
                LogUtils.error("init wecom org sync schedule failed: " + config.getOrganizationId(), ex);
            }
        }
    }

    public void refreshSchedule(OrgWecomSyncConfig config) {
        if (config == null || StringUtils.isBlank(config.getOrganizationId())) {
            return;
        }
        JobKey jobKey = WecomOrgSyncJob.getJobKey(config.getOrganizationId());
        TriggerKey triggerKey = WecomOrgSyncJob.getTriggerKey(config.getOrganizationId());
        if (Integer.valueOf(1).equals(config.getScheduleEnabled()) && StringUtils.isNotBlank(config.getScheduleCron())) {
            try {
                scheduleManager.addOrUpdateCronJob(jobKey, triggerKey, WecomOrgSyncJob.class,
                        config.getScheduleCron(), buildJobDataMap(config));
            } catch (SchedulerException e) {
                throw new MSException(Translator.getWithArgs("org.wecom.sync.schedule.refresh_failed", e.getMessage()));
            }
        } else {
            scheduleManager.removeJob(jobKey, triggerKey);
        }
    }

    private JobDataMap buildJobDataMap(OrgWecomSyncConfig config) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("resourceId", config.getOrganizationId());
        jobDataMap.put("expression", config.getScheduleCron());
        jobDataMap.put("userId", "system");
        return jobDataMap;
    }
}
