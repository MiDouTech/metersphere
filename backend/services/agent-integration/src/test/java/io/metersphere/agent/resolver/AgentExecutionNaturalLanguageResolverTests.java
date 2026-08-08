package io.metersphere.agent.resolver;

import io.metersphere.agent.dto.AgentSearchFilters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentExecutionNaturalLanguageResolverTests {
    private final AgentExecutionNaturalLanguageResolver resolver = new AgentExecutionNaturalLanguageResolver();

    @Test
    void shouldResolveWhitelistedChineseFilters() {
        AgentExecutionNaturalLanguageResolver.Resolution result = resolver.resolve(
                "执行用户管理模块 P0 最近失败的冒烟用例，排除删除，最多 20 条", null);

        Assertions.assertTrue(result.recognized());
        Assertions.assertTrue(result.filters().getPriority().contains("P0"));
        Assertions.assertTrue(result.filters().getLastExecuteResult().contains("FAILED"));
        Assertions.assertTrue(result.filters().getTags().contains("smoke"));
        Assertions.assertTrue(result.filters().getExcludeRiskActions());
        Assertions.assertEquals(20, result.filters().getLimit());
        Assertions.assertEquals("用户管理", result.filters().getKeyword());
    }

    @Test
    void shouldMergeExplicitFiltersAndClampLimit() {
        AgentSearchFilters explicit = new AgentSearchFilters();
        explicit.setKeyword("登录");
        explicit.setLimit(999);

        AgentExecutionNaturalLanguageResolver.Resolution result = resolver.resolve("执行 P1 用例", explicit);

        Assertions.assertEquals("登录", result.filters().getKeyword());
        Assertions.assertEquals(100, result.filters().getLimit());
        Assertions.assertTrue(result.filters().getPriority().contains("P1"));
    }

    @Test
    void shouldNotTreatArbitraryInputAsExecutableExpression() {
        AgentExecutionNaturalLanguageResolver.Resolution result = resolver.resolve(
                "'; drop table functional_case; --", null);

        Assertions.assertFalse(result.recognized());
        Assertions.assertNull(result.filters().getKeyword());
    }
}
