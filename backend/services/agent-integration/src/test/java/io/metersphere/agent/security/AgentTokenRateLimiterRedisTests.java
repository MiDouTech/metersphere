package io.metersphere.agent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "quality.redis", matches = "true")
class AgentTokenRateLimiterRedisTests {
    @Test
    void twoWorkersShareOne720RequestWindowAndExpireIdleKeys() {
        var config = new org.redisson.config.Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:16479");
        var client = org.redisson.Redisson.create(config);
        var factory = new RedissonConnectionFactory(client);
        var redis = new StringRedisTemplate(factory);
        String namespace = "rate-test-" + java.util.UUID.randomUUID();
        try {
            var first = limiter(redis, namespace);
            var second = limiter(redis, namespace);
            long accepted = java.util.stream.IntStream.range(0, 900).parallel()
                    .filter(i -> (i % 2 == 0 ? first : second).tryAcquire("token", false)).count();
            assertEquals(720, accepted);
            assertFalse(second.tryAcquire("token", false));
            assertTrue(second.tryAcquire("other-token", false));
            var keys = redis.keys(namespace + ":*");
            assertNotNull(keys);
            assertEquals(2, keys.size());
            keys.forEach(key -> assertTrue(redis.getExpire(key) > 0));
        } finally {
            var keys = redis.keys(namespace + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
            client.shutdown();
        }
    }

    @Test
    void unavailableRedisDoesNotAdmitCalls() {
        var redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        var limiter = limiter(redis, "unavailable");
        // A missing script result must fail closed, never silently use local unlimited admission.
        assertThrows(org.springframework.dao.DataAccessException.class, () -> limiter.tryAcquire("token", false));
    }

    private AgentTokenRateLimiter limiter(StringRedisTemplate redis, String namespace) {
        var limiter = new AgentTokenRateLimiter();
        ReflectionTestUtils.setField(limiter, "redis", redis);
        ReflectionTestUtils.setField(limiter, "namespace", namespace);
        return limiter;
    }
}
