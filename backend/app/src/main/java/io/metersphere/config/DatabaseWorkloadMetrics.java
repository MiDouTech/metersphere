package io.metersphere.config;

import io.metersphere.system.security.DatabaseWorkloadFilter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseWorkloadMetrics {
    /** Keep the default scheduler explicit: adding a qualified scheduler must not capture every existing job. */
    @Bean(name = "taskScheduler")
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler(
            @org.springframework.beans.factory.annotation.Value("${spring.task.scheduling.pool.size:1}") int size) {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(size);
        scheduler.setThreadNamePrefix("scheduled-task-");
        return scheduler;
    }

    @Bean(name = "functionalImportEffectsScheduler")
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler functionalImportEffectsScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("import-effects-");
        return scheduler;
    }

    @Bean
    MeterBinder databaseWorkloadBinder(DatabaseWorkloadFilter filter, io.metersphere.functional.service.FunctionalImportEffectOutbox outbox) {
        return registry -> {
            Gauge.builder("msp.database.workload.active", filter, DatabaseWorkloadFilter::activeRequests)
                    .description("Admitted database-heavy HTTP requests").register(registry);
            FunctionCounter.builder("msp.database.workload.rejected", filter, DatabaseWorkloadFilter::rejectedRequests)
                    .description("Requests rejected before acquiring database connections").register(registry);
            FunctionCounter.builder("msp.database.operations", filter, ignored -> io.metersphere.system.interceptor.DatabaseQueryTelemetry.operations())
                    .description("Mapped data operations, including connection acquisition and mapping").register(registry);
            FunctionCounter.builder("msp.database.slow.operations", filter, ignored -> io.metersphere.system.interceptor.DatabaseQueryTelemetry.slowOperations())
                    .description("Mapped operations taking at least one second").register(registry);
            FunctionCounter.builder("msp.import.effects.delivered", outbox, io.metersphere.functional.service.FunctionalImportEffectOutbox::deliveredCount)
                    .description("Committed import effects in this process").register(registry);
            FunctionCounter.builder("msp.import.effects.failures", outbox, io.metersphere.functional.service.FunctionalImportEffectOutbox::failureCount)
                    .description("Failed import effect attempts in this process").register(registry);
        };
    }
}
