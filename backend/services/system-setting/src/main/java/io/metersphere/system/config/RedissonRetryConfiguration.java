package io.metersphere.system.config;

import org.redisson.config.BaseConfig;
import org.redisson.config.DelayStrategy;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Redisson 3.49 derives a timer tick from delay % 100 / 2; a remainder of 1 yields zero. */
@Configuration(proxyBeanMethods = false)
public class RedissonRetryConfiguration {
    @Bean
    RedissonAutoConfigurationCustomizer safeRetryTimerPrecision() {
        return config -> {
            if (config.isSingleConfig()) normalize(config.useSingleServer());
            else if (config.isClusterConfig()) normalize(config.useClusterServers());
            else if (config.isSentinelConfig()) normalize(config.useSentinelServers());
        };
    }

    private static void normalize(BaseConfig<?> server) {
        server.setRetryDelay(timerSafeDelay(server.getRetryDelay()));
    }

    static DelayStrategy timerSafeDelay(DelayStrategy original) {
        return attempt -> {
            long millis = original.calcDelay(attempt).toMillis();
            return Duration.ofMillis(Math.max(100L, Math.multiplyExact(Math.ceilDiv(millis, 100L), 100L)));
        };
    }
}
