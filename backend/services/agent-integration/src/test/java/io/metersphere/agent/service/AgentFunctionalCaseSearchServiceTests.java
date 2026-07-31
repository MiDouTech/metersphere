package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentWarningCode;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.dto.AgentSearchFilters;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.agent.resolver.AgentQueryResolver;
import io.metersphere.agent.resolver.ModuleTreeMatcher;
import io.metersphere.agent.resolver.ResolvedSearchCondition;
import io.metersphere.functional.dto.FunctionalCasePageDTO;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.plan.dto.response.TestPlanCasePageResponse;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFunctionalCaseSearchServiceTests {

    @InjectMocks
    private AgentFunctionalCaseSearchService searchService;

    @Mock
    private AgentQueryResolver agentQueryResolver;
    @Mock
    private FunctionalCaseService functionalCaseService;
    @Mock
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Mock
    private AgentFunctionalCaseExportService agentFunctionalCaseExportService;
    @Mock
    private AgentCaseSchemaMapper agentCaseSchemaMapper;
    @Mock
    private ModuleTreeMatcher moduleTreeMatcher;
    @Mock
    private AgentProjectService agentProjectService;

    @AfterEach
    void tearDown() {
        SessionUtils.clearCurrentProjectId();
    }

    @Test
    void searchShouldReturnMatchedModulesWhenModuleHits() {
        SessionUtils.setCurrentProjectId("proj-001");
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");

        ResolvedSearchCondition condition = new ResolvedSearchCondition();
        condition.setModuleIds(new LinkedHashSet<>(Set.of("mod-order")));
        condition.setMatchedModules(List.of("订单", "订单/下单流程"));
        condition.getMatchedBy().add("module");

        FunctionalCasePageDTO pageItem = new FunctionalCasePageDTO();
        pageItem.setId("fc-001");
        pageItem.setModuleId("mod-order");
        AgentCaseDTO mapped = new AgentCaseDTO();
        mapped.setCaseId("fc-001");
        mapped.setPriority("P0");

        when(agentProjectService.resolveProjectId(null)).thenReturn("proj-001");
        when(agentQueryResolver.resolve(request, "proj-001")).thenReturn(condition);
        ModuleTreeMatcher.AgentModuleNode moduleNode = new ModuleTreeMatcher.AgentModuleNode();
        moduleNode.setId("mod-order");
        moduleNode.setName("订单");
        moduleNode.setPath("订单");
        when(moduleTreeMatcher.flatten("proj-001")).thenReturn(List.of(moduleNode));
        when(functionalCaseService.getFunctionalCasePage(any(), eq(false), eq(false)))
                .thenReturn(List.of(pageItem));
        when(agentCaseSchemaMapper.fromFunctionalCasePage(pageItem, "订单")).thenReturn(mapped);
        when(agentCaseSchemaMapper.matchesPriority(any(), any())).thenReturn(true);
        when(agentCaseSchemaMapper.matchesTags(any(), any())).thenReturn(true);

        AgentCaseSearchResponse response = searchService.search(request);

        Assertions.assertTrue(response.getMatchedBy().contains("module"));
        Assertions.assertEquals(List.of("订单", "订单/下单流程"), response.getMatchedModules());
        Assertions.assertEquals(1, response.getCases().size());
        verify(agentFunctionalCaseExportService).enrichWithSteps(any(), any());
    }

    @Test
    void searchWithTestPlanIdShouldExposeTestPlanCaseId() {
        SessionUtils.setCurrentProjectId("proj-001");
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");
        request.setTestPlanId("plan-001");

        ResolvedSearchCondition condition = new ResolvedSearchCondition();
        condition.getMatchedBy().add("module");
        TestPlanCasePageResponse planCase = new TestPlanCasePageResponse();
        planCase.setId("relate-001");
        planCase.setCaseId("fc-001");
        planCase.setModuleId("mod-order");
        AgentCaseDTO mapped = new AgentCaseDTO();
        mapped.setCaseId("fc-001");
        mapped.setTestPlanCaseId("relate-001");

        when(agentProjectService.resolveProjectId(null)).thenReturn("proj-001");
        when(agentQueryResolver.resolve(request, "proj-001")).thenReturn(condition);
        when(moduleTreeMatcher.flatten("proj-001")).thenReturn(List.of());
        when(testPlanFunctionalCaseService.getFunctionalCasePage(any(), eq(false), eq("proj-001")))
                .thenReturn(List.of(planCase));
        when(agentCaseSchemaMapper.fromTestPlanCase(planCase, null)).thenReturn(mapped);
        when(agentCaseSchemaMapper.matchesPriority(any(), any())).thenReturn(true);
        when(agentCaseSchemaMapper.matchesTags(any(), any())).thenReturn(true);

        AgentCaseSearchResponse response = searchService.search(request);

        Assertions.assertEquals("relate-001", response.getCases().get(0).getTestPlanCaseId());
        Assertions.assertEquals("plan-001", response.getCases().get(0).getTestPlanId());
    }

    @Test
    void searchWithPriorityFilterShouldApplyMatchedByFilter() {
        SessionUtils.setCurrentProjectId("proj-001");
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");
        AgentSearchFilters filters = new AgentSearchFilters();
        filters.setPriority(List.of("P0"));
        request.setFilters(filters);

        ResolvedSearchCondition condition = new ResolvedSearchCondition();
        condition.setPriorities(List.of("P0"));
        condition.getMatchedBy().add("filter");

        when(agentProjectService.resolveProjectId(null)).thenReturn("proj-001");
        when(agentQueryResolver.resolve(request, "proj-001")).thenReturn(condition);
        when(moduleTreeMatcher.flatten("proj-001")).thenReturn(List.of());
        when(functionalCaseService.getFunctionalCasePage(any(), eq(false), eq(false))).thenReturn(List.of());

        AgentCaseSearchResponse response = searchService.search(request);

        Assertions.assertTrue(response.getMatchedBy().contains("filter"));
    }

    @Test
    void keywordFallbackShouldSurfaceWarning() {
        SessionUtils.setCurrentProjectId("proj-001");
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("登录");

        ResolvedSearchCondition condition = new ResolvedSearchCondition();
        condition.setKeyword("登录");
        condition.getMatchedBy().add("keyword");
        condition.getWarnings().add(AgentWarningCode.MODULE_NOT_MATCHED_KEYWORD_FALLBACK);

        when(agentProjectService.resolveProjectId(null)).thenReturn("proj-001");
        when(agentQueryResolver.resolve(request, "proj-001")).thenReturn(condition);
        when(moduleTreeMatcher.flatten("proj-001")).thenReturn(List.of());
        when(functionalCaseService.getFunctionalCasePage(any(), eq(false), eq(false))).thenReturn(List.of());

        AgentCaseSearchResponse response = searchService.search(request);

        Assertions.assertTrue(response.getWarnings().contains(AgentWarningCode.MODULE_NOT_MATCHED_KEYWORD_FALLBACK));
    }

    @Test
    void modulesShouldReturnFlattenedPaths() {
        ModuleTreeMatcher.AgentModuleNode node = new ModuleTreeMatcher.AgentModuleNode();
        node.setId("mod-order");
        node.setPath("订单/下单流程");
        AgentModuleDTO dto = new AgentModuleDTO();
        dto.setId("mod-order");
        dto.setPath("订单/下单流程");

        when(agentProjectService.resolveProjectId("proj-001")).thenReturn("proj-001");
        when(moduleTreeMatcher.flatten("proj-001")).thenReturn(List.of(node));
        when(agentCaseSchemaMapper.toModuleDto(node)).thenReturn(dto);

        List<AgentModuleDTO> modules = searchService.listModules("proj-001");

        Assertions.assertEquals(1, modules.size());
        Assertions.assertEquals("订单/下单流程", modules.get(0).getPath());
    }

    @Test
    void searchWithoutProjectContextShouldFail() {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");

        Assertions.assertThrows(Exception.class, () -> searchService.search(request));
    }

    @Test
    void includeStepsFalseShouldSkipEnrichment() {
        SessionUtils.setCurrentProjectId("proj-001");
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setQuery("订单");
        request.setIncludeSteps(false);

        ResolvedSearchCondition condition = new ResolvedSearchCondition();
        when(agentProjectService.resolveProjectId(null)).thenReturn("proj-001");
        when(agentQueryResolver.resolve(request, "proj-001")).thenReturn(condition);
        when(moduleTreeMatcher.flatten("proj-001")).thenReturn(List.of());
        when(functionalCaseService.getFunctionalCasePage(any(), eq(false), eq(false))).thenReturn(List.of());

        searchService.search(request);

        verify(agentFunctionalCaseExportService, org.mockito.Mockito.never())
                .enrichWithSteps(any(), any());
    }
}
