package io.metersphere.agent.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentTokenRateLimiterTests {

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
