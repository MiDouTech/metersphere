package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.CommonBeanFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

public class WecomNotificationCronJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        String ruleId = context.getMergedJobDataMap().getString("ruleId");
        CommonBeanFactory.getBean(WecomNotificationCronExecutor.class)
                .execute(ruleId, context.getScheduledFireTime().getTime());
    }
}
