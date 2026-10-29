package io.metersphere.system.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class RedissonRetryConfigurationTests {
    @Test void exactFailureDelayCannotProduceAZeroTimerTick() {
        var safe = RedissonRetryConfiguration.timerSafeDelay(attempt -> Duration.ofMillis(1501));
        assertEquals(Duration.ofMillis(1600), safe.calcDelay(0));
    }

    @Test void preservesRetryAttemptAndBoundsRoundingToOneTimerInterval() {
        for (int attempt=0; attempt<10; attempt++) {
            var safe = RedissonRetryConfiguration.timerSafeDelay(n -> Duration.ofMillis(1001L + 100L*n));
            long millis = safe.calcDelay(attempt).toMillis();
            assertEquals(0, millis % 100);
            assertTrue(millis >= 1001L + 100L*attempt);
            assertTrue(millis - (1001L + 100L*attempt) < 100);
        }
    }

    @Test void defaultSingleServerJitterRemainsSafeAcrossSamples() {
        var config = new Config();
        var server = config.useSingleServer();
        new RedissonRetryConfiguration().safeRetryTimerPrecision().customize(config);
        for (int i=0; i<10000; i++) {
            long millis = server.getRetryDelay().calcDelay(0).toMillis();
            assertTrue(millis >= 100);
            assertEquals(0, millis % 100);
        }
    }
}
