package io.metersphere.agent.resolver;

import io.metersphere.agent.constants.AgentWarningCode;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentSearchFilters;
import io.metersphere.agent.service.AgentModuleAliasService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentQueryResolverTests {

    @InjectMocks
    private AgentQueryResolver resolver;

    @Mock
    private ModuleTreeMatcher moduleTreeMatcher;

    @Mock
    private AgentModuleAliasService agentModuleAliasService;

    @Test
    void moduleHitShouldExpandSubtree() {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");

        ModuleTreeMatcher.ModuleMatchResult match = ModuleTreeMatcher.ModuleMatchResult.hit(
                new LinkedHashSet<>(List.of("mod-order", "mod-order-child")),
                List.of("订单", "订单/下单流程"));
        when(moduleTreeMatcher.match(eq("proj-001"), eq("订单"))).thenReturn(match);
        when(agentModuleAliasService.resolveModuleIds("proj-001", "订单")).thenReturn(Set.of());

        ResolvedSearchCondition condition = resolver.resolve(request, "proj-001");

        Assertions.assertTrue(condition.getModuleIds().contains("mod-order"));
        Assertions.assertTrue(condition.getMatchedBy().contains("module"));
        Assertions.assertFalse(condition.getWarnings().contains(AgentWarningCode.MODULE_NOT_MATCHED_KEYWORD_FALLBACK));
    }

    @Test
    void moduleMissShouldFallbackToKeywordWithWarning() {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("登录");

        when(agentModuleAliasService.resolveModuleIds("proj-001", "登录")).thenReturn(Set.of());
        when(moduleTreeMatcher.match("proj-001", "登录")).thenReturn(ModuleTreeMatcher.ModuleMatchResult.miss());

        ResolvedSearchCondition condition = resolver.resolve(request, "proj-001");

        Assertions.assertEquals("登录", condition.getKeyword());
        Assertions.assertTrue(condition.getMatchedBy().contains("keyword"));
        Assertions.assertTrue(condition.getWarnings().contains(AgentWarningCode.MODULE_NOT_MATCHED_KEYWORD_FALLBACK));
    }

    @Test
    void aliasHitShouldSkipModuleMatcher() {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("CW");

        when(agentModuleAliasService.resolveModuleIds("proj-001", "CW"))
                .thenReturn(new LinkedHashSet<>(List.of("mod-finance")));

        ResolvedSearchCondition condition = resolver.resolve(request, "proj-001");

        Assertions.assertTrue(condition.getModuleIds().contains("mod-finance"));
        Assertions.assertTrue(condition.getMatchedBy().contains("moduleAlias"));
        Assertions.assertFalse(condition.getWarnings().contains(AgentWarningCode.MODULE_NOT_MATCHED_KEYWORD_FALLBACK));
    }

    @Test
    void explicitModuleIdsShouldSkipQueryParsing() {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");
        AgentSearchFilters filters = new AgentSearchFilters();
        filters.setModuleIds(List.of("mod-explicit"));
        request.setFilters(filters);

        ResolvedSearchCondition condition = resolver.resolve(request, "proj-001");

        Assertions.assertEquals(Set.of("mod-explicit"), condition.getModuleIds());
        Assertions.assertTrue(condition.getMatchedBy().contains("moduleIds"));
    }

    @Test
    void priorityFilterShouldBeApplied() {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        AgentSearchFilters filters = new AgentSearchFilters();
        filters.setPriority(List.of("P0"));
        request.setFilters(filters);

        ResolvedSearchCondition condition = resolver.resolve(request, "proj-001");

        Assertions.assertEquals(List.of("P0"), condition.getPriorities());
        Assertions.assertTrue(condition.getMatchedBy().contains("filter"));
    }
}
