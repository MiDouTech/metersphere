package io.metersphere.functional.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Scheduling belongs with its consumers, including modular application contexts. */
@Configuration
public class FunctionalImportSchedulingConfiguration {
    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    ThreadPoolTaskScheduler taskScheduler(@Value("${spring.task.scheduling.pool.size:1}") int size) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(size);
        scheduler.setThreadNamePrefix("scheduled-task-");
        return scheduler;
    }

    @Bean(name = "functionalImportEffectsScheduler")
    @ConditionalOnMissingBean(name = "functionalImportEffectsScheduler")
    ThreadPoolTaskScheduler functionalImportEffectsScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("import-effects-");
        return scheduler;
    }
}
