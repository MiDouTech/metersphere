package io.metersphere.functional.handoff;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Webhook polling and maintenance never share the ordinary taskScheduler. Network threads are owned by the worker. */
@Configuration
public class HandoffSchedulingConfiguration {
    @Bean(name="handoffPollScheduler")
    ThreadPoolTaskScheduler polling(){return scheduler("handoff-poll-");}
    @Bean(name="handoffMaintenanceScheduler")
    ThreadPoolTaskScheduler maintenance(){return scheduler("handoff-maintenance-");}
    private ThreadPoolTaskScheduler scheduler(String prefix){
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);scheduler.setWaitForTasksToCompleteOnShutdown(true);scheduler.setAwaitTerminationSeconds(6);return scheduler;
    }
}
