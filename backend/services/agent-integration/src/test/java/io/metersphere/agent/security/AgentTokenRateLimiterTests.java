package io.metersphere.agent.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentTokenRateLimiterTests {

    @Test
    void requirement720PerSlidingMinuteAndIndependentTokens() {
        var now = new java.util.concurrent.atomic.AtomicLong(100000);
        AgentTokenRateLimiter limiter = new AgentTokenRateLimiter(now::get);
        for (int i = 0; i < 720; i++) Assertions.assertTrue(limiter.tryAcquire("same", false));
        Assertions.assertFalse(limiter.tryAcquire("same", false));
        Assertions.assertTrue(limiter.tryAcquire("other", false));
        now.addAndGet(59999);
        Assertions.assertFalse(limiter.tryAcquire("same", false));
        now.incrementAndGet();
        Assertions.assertTrue(limiter.tryAcquire("same", false));
    }

    @Test
    void concurrentRequestsCannotExceedSharedTokenBudget() {
        AgentTokenRateLimiter limiter = new AgentTokenRateLimiter(() -> 100000);
        long admitted = java.util.stream.IntStream.range(0, 1500).parallel()
                .filter(i -> limiter.tryAcquire("same", false)).count();
        Assertions.assertEquals(720, admitted);
    }

    @Test
    void searchMinIntervalShouldBlockBurst() {
        AgentTokenRateLimiter limiter = new AgentTokenRateLimiter();
        Assertions.assertTrue(limiter.tryAcquire("t1", true));
        Assertions.assertFalse(limiter.tryAcquire("t1", true));
    }

    @Test
    void generalLimitShouldAllowNonSearchBetweenSearches() {
        AgentTokenRateLimiter limiter = new AgentTokenRateLimiter();
        Assertions.assertTrue(limiter.tryAcquire("t2", true));
        Assertions.assertTrue(limiter.tryAcquire("t2", false));
    }

    @Test
    void isSearchApiShouldMatchBugAndFunctionalSearch() {
        Assertions.assertTrue(AgentTokenRateLimiter.isSearchApi("/api/agent/v1/bug/search"));
        Assertions.assertTrue(AgentTokenRateLimiter.isSearchApi("/agent/v1/functional/search"));
        Assertions.assertFalse(AgentTokenRateLimiter.isSearchApi("/api/agent/v1/bug/create"));
        Assertions.assertFalse(AgentTokenRateLimiter.isSearchApi("/api/agent/v1/functional/modules"));
    }
}
