package io.metersphere.functional.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.*;

class FunctionalImportSchedulingConfigurationTests {
    @Configuration
    @org.springframework.context.annotation.Profile("scheduling-contract-only")
    @EnableScheduling
    static class Consumer {
        @Scheduled(initialDelay = 3600000, fixedDelay = 3600000, scheduler = "functionalImportEffectsScheduler")
        void imports() { }

        @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
        void ordinaryJob() { }
    }

    @Test
    void modularContextResolvesQualifiedAndDefaultSchedulersWithoutAppConfiguration() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("scheduling-contract-only");
            context.register(FunctionalImportSchedulingConfiguration.class, Consumer.class);
            context.refresh();
            assertNotNull(context.getBean(Consumer.class));
            var ordinary = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
            var imports = context.getBean("functionalImportEffectsScheduler", ThreadPoolTaskScheduler.class);
            assertNotSame(ordinary, imports);
            assertEquals("scheduled-task-", ordinary.getThreadNamePrefix());
            assertEquals("import-effects-", imports.getThreadNamePrefix());
            assertTrue(ordinary.isRunning());
            assertTrue(imports.isRunning());
        }
    }
}
