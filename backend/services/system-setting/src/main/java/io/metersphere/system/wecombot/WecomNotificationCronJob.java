package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.CommonBeanFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
public class WecomNotificationCronJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        String ruleId = context.getMergedJobDataMap().getString("ruleId");
        String scheduleId = context.getMergedJobDataMap().getString("scheduleId");
        WecomNotificationCronExecutor executor = CommonBeanFactory.getBean(WecomNotificationCronExecutor.class);
        if (scheduleId != null) executor.executeSchedule(scheduleId, context.getScheduledFireTime().getTime());
        else executor.execute(ruleId, context.getScheduledFireTime().getTime());
    }
}
