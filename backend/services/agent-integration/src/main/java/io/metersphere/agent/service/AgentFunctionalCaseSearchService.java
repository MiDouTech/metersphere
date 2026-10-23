package io.metersphere.agent.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentWarningCode;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.agent.resolver.AgentQueryResolver;
import io.metersphere.agent.resolver.ModuleTreeMatcher;
import io.metersphere.agent.resolver.ResolvedSearchCondition;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.dto.FunctionalCasePageDTO;
import io.metersphere.functional.request.FunctionalCasePageRequest;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.plan.dto.request.TestPlanCaseRequest;
import io.metersphere.plan.dto.response.TestPlanCasePageResponse;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
public class AgentFunctionalCaseSearchService {
    @Resource
    private AgentQueryResolver agentQueryResolver;
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Resource
    private AgentFunctionalCaseExportService agentFunctionalCaseExportService;
    @Resource
    private AgentCaseSchemaMapper agentCaseSchemaMapper;
    @Resource
    private ModuleTreeMatcher moduleTreeMatcher;
    @Resource
    private AgentProjectService agentProjectService;

    @Resource
    private AgentBusinessAccess access;

    public AgentCaseSearchResponse search(AgentCaseSearchRequest request) {
        validateSearchRequest(request);
        String projectId = request.getIdentityType() == null ? agentProjectService.resolveProjectId(request.getProjectId())
                : agentProjectService.resolveProjectId(request.getProjectId(), request.getIdentityType());
        access.project(projectId, PermissionConstants.FUNCTIONAL_CASE_READ, false, AgentTokenScope.FUNCTIONAL_READ);
        if (StringUtils.isNotBlank(request.getTestPlanId())) access.plan(projectId, request.getTestPlanId());
        ResolvedSearchCondition condition = agentQueryResolver.resolve(request, projectId);
        Map<String, String> modulePathMap = buildModulePathMap(projectId);

        SearchPage page = StringUtils.isNotBlank(request.getTestPlanId())
                ? searchInTestPlan(request, projectId, condition, modulePathMap)
                : searchInRepository(request, projectId, condition, modulePathMap);
        List<AgentCaseDTO> cases = page.cases();
        cases.forEach(item -> {
            if (StringUtils.isBlank(item.getProjectId())) item.setProjectId(projectId);
            else if (!projectId.equals(item.getProjectId())) throw new MSException("用例已变更或无权访问，请重新检索");
        });
        boolean includeSteps = request.getIncludeSteps() == null || request.getIncludeSteps();
        AgentCaseSearchResponse response = buildResponse(condition, cases, page.total());
        response.setProjectId(projectId);
        response.setCurrent(request.getCurrent());
        response.setPageSize(request.getPageSize());
        response.setHasMore((long) request.getCurrent() * request.getPageSize() < page.total());
        if (includeSteps) {
            agentFunctionalCaseExportService.enrichWithSteps(cases, response.getWarnings());
        }
        if (StringUtils.isNotBlank(request.getTestPlanId())) {
            for (AgentCaseDTO item : cases) {
                item.setTestPlanId(request.getTestPlanId());
                if (StringUtils.isBlank(item.getTestPlanCaseId())) {
                    response.getWarnings().add(AgentWarningCode.TEST_PLAN_CASE_ID_MISSING);
                }
            }
        }
        return response;
    }

    public AgentCaseDTO getById(String caseId, boolean includeSteps, String testPlanId) {
        String projectId = access.caseProject(caseId, null, PermissionConstants.FUNCTIONAL_CASE_READ, false, AgentTokenScope.FUNCTIONAL_READ);
        if (StringUtils.isNotBlank(testPlanId)) access.plan(projectId, testPlanId);
        FunctionalCaseDetailDTO detail = functionalCaseService.getAgentCaseDetails(List.of(caseId), projectId, includeSteps).get(caseId);
        if (detail == null || !projectId.equals(detail.getProjectId())) throw new MSException("用例已变更或无权访问，请重新检索");
        AgentCaseDTO agentCase = new AgentCaseDTO();
        agentCase.setCaseId(detail.getId());
        agentCase.setNum(detail.getNum());
        agentCase.setName(detail.getName());
        agentCase.setModulePath(resolveModulePath(projectId, detail.getModuleId()));
        agentCase.setTags(detail.getTags());
        List<String> warnings = new ArrayList<>();
        agentCaseSchemaMapper.enrichDetail(agentCase, detail, warnings);
        if (StringUtils.isNotBlank(testPlanId)) {
            fillTestPlanCaseId(agentCase, testPlanId, projectId);
        }
        if (!includeSteps) {
            agentCase.setSteps(new ArrayList<>());
        }
        return agentCase;
    }

    public List<AgentModuleDTO> listModules(String projectId) {
        String resolvedProjectId = agentProjectService.resolveProjectId(projectId);
        access.project(resolvedProjectId, PermissionConstants.FUNCTIONAL_CASE_READ, false, AgentTokenScope.FUNCTIONAL_READ);
        return moduleTreeMatcher.flatten(resolvedProjectId).stream()
                .map(agentCaseSchemaMapper::toModuleDto)
                .collect(Collectors.toList());
    }

    private SearchPage searchInRepository(AgentCaseSearchRequest request, String projectId,
                                                    ResolvedSearchCondition condition, Map<String, String> modulePathMap) {
        FunctionalCasePageRequest pageRequest = new FunctionalCasePageRequest();
        pageRequest.setProjectId(projectId);
        pageRequest.setVersionId(request.getVersionId());
        pageRequest.setRefId(request.getRefId());
        pageRequest.setCurrent(request.getCurrent());
        pageRequest.setPageSize(request.getPageSize());
        if (CollectionUtils.isNotEmpty(condition.getModuleIds())) {
            pageRequest.setModuleIds(new ArrayList<>(condition.getModuleIds()));
        }
        if (StringUtils.isNotBlank(condition.getKeyword())) {
            pageRequest.initKeyword(condition.getKeyword());
        }
        Map<String, List<String>> filter = buildRepositoryFilter(condition);
        addFilters(filter, request, condition);
        if (!filter.isEmpty()) {
            pageRequest.setFilter(filter);
        }
        try (Page<?> pagination = PageHelper.startPage(request.getCurrent(), request.getPageSize(), "functional_case.pos desc, functional_case.id asc")) {
            List<FunctionalCasePageDTO> rows = functionalCaseService.getFunctionalCasePage(pageRequest, false, true);
            return new SearchPage(rows.stream()
                    .map(item -> agentCaseSchemaMapper.fromFunctionalCasePage(item, modulePathMap.get(item.getModuleId())))
                    .collect(Collectors.toList()), Math.max(0, pagination.getTotal()));
        } finally {
            PageHelper.clearPage();
        }
    }

    private SearchPage searchInTestPlan(AgentCaseSearchRequest request, String projectId,
                                                 ResolvedSearchCondition condition, Map<String, String> modulePathMap) {
        TestPlanCaseRequest pageRequest = new TestPlanCaseRequest();
        pageRequest.setTestPlanId(request.getTestPlanId());
        pageRequest.setProjectId(projectId);
        pageRequest.setVersionId(request.getVersionId());
        pageRequest.setRefId(request.getRefId());
        pageRequest.setCurrent(request.getCurrent());
        pageRequest.setPageSize(request.getPageSize());
        if (CollectionUtils.isNotEmpty(condition.getModuleIds())) {
            pageRequest.setModuleIds(new ArrayList<>(condition.getModuleIds()));
        }
        if (StringUtils.isNotBlank(condition.getKeyword())) {
            pageRequest.initKeyword(condition.getKeyword());
        }
        Map<String, List<String>> filter = buildTestPlanFilter(condition);
        addFilters(filter, request, condition);
        if (!filter.isEmpty()) {
            pageRequest.setFilter(filter);
        }
        try (Page<?> pagination = PageHelper.startPage(request.getCurrent(), request.getPageSize(), "test_plan_functional_case.pos desc, test_plan_functional_case.id asc")) {
            List<TestPlanCasePageResponse> rows = testPlanFunctionalCaseService.getFunctionalCasePage(pageRequest, false, projectId);
            return new SearchPage(rows.stream()
                    .map(item -> agentCaseSchemaMapper.fromTestPlanCase(item, modulePathMap.get(item.getModuleId())))
                    .peek(item -> item.setTestPlanId(request.getTestPlanId()))
                    .collect(Collectors.toList()), Math.max(0, pagination.getTotal()));
        } finally {
            PageHelper.clearPage();
        }
    }

    private record SearchPage(List<AgentCaseDTO> cases, long total) {}

    private void fillTestPlanCaseId(AgentCaseDTO agentCase, String testPlanId, String projectId) {
        TestPlanCaseRequest pageRequest = new TestPlanCaseRequest();
        pageRequest.setTestPlanId(testPlanId);
        pageRequest.setProjectId(projectId);
        pageRequest.setCurrent(1);
        pageRequest.setPageSize(AgentConstants.MAX_PAGE_SIZE);
        pageRequest.initKeyword(agentCase.getNum() == null ? agentCase.getName() : String.valueOf(agentCase.getNum()));
        List<TestPlanCasePageResponse> page = testPlanFunctionalCaseService.getFunctionalCasePage(pageRequest, false, projectId);
        page.stream()
                .filter(item -> StringUtils.equals(item.getCaseId(), agentCase.getCaseId()))
                .findFirst()
                .ifPresent(item -> {
                    agentCase.setTestPlanCaseId(item.getId());
                    agentCase.setTestPlanId(testPlanId);
                    agentCase.setLastExecuteResult(item.getLastExecResult());
                    agentCase.setLastExecuteUser(item.getLastExecuteUser());
                    agentCase.setLastExecuteUserName(item.getLastExecuteUserName());
                });
    }

    private Map<String, List<String>> buildRepositoryFilter(ResolvedSearchCondition condition) {
        Map<String, List<String>> filter = new HashMap<>();
        if (CollectionUtils.isNotEmpty(condition.getLastExecuteResults())) {
            filter.put(AgentConstants.FILTER_LAST_EXECUTE_RESULT, condition.getLastExecuteResults());
        }
        if (CollectionUtils.isNotEmpty(condition.getPriorities())) {
            filter.put(AgentConstants.FILTER_CASE_LEVEL, condition.getPriorities());
        }
        return filter;
    }

    private Map<String, List<String>> buildTestPlanFilter(ResolvedSearchCondition condition) {
        Map<String, List<String>> filter = new HashMap<>();
        if (CollectionUtils.isNotEmpty(condition.getLastExecuteResults())) {
            filter.put(AgentConstants.FILTER_LAST_EXEC_RESULT, condition.getLastExecuteResults());
        }
        if (CollectionUtils.isNotEmpty(condition.getPriorities())) {
            filter.put(AgentConstants.FILTER_CASE_LEVEL, condition.getPriorities());
        }
        return filter;
    }

    private AgentCaseSearchResponse buildResponse(ResolvedSearchCondition condition, List<AgentCaseDTO> cases, long total) {
        AgentCaseSearchResponse response = new AgentCaseSearchResponse();
        response.setMatchedBy(new ArrayList<>(new LinkedHashSet<>(condition.getMatchedBy())));
        response.setMatchedModules(condition.getMatchedModules());
        response.setMatchedModuleIds(new ArrayList<>(condition.getModuleIds()));
        response.setWarnings(new ArrayList<>(new LinkedHashSet<>(condition.getWarnings())));
        response.setCases(cases);
        response.setTotal(total);
        return response;
    }

    private Map<String, String> buildModulePathMap(String projectId) {
        return moduleTreeMatcher.flatten(projectId).stream()
                .collect(Collectors.toMap(ModuleTreeMatcher.AgentModuleNode::getId, ModuleTreeMatcher.AgentModuleNode::getPath, (a, b) -> a));
    }

    private String resolveModulePath(String projectId, String moduleId) {
        return buildModulePathMap(projectId).getOrDefault(moduleId, "");
    }

    private void addFilters(Map<String, List<String>> filter, AgentCaseSearchRequest request, ResolvedSearchCondition condition) {
        if (CollectionUtils.isNotEmpty(condition.getPriorities())) {
            filter.remove(AgentConstants.FILTER_CASE_LEVEL);
            filter.put("agentPriority", condition.getPriorities());
        }
        if (CollectionUtils.isNotEmpty(condition.getTags())) {
            filter.put(request.getFilters() != null && "ALL".equals(request.getFilters().getTagMatch()) ? "agentTagsAll" : "agentTagsAny", condition.getTags());
        }
        if (request.getFilters() != null && CollectionUtils.isNotEmpty(request.getFilters().getReviewStatus())) {
            filter.put("reviewStatus", request.getFilters().getReviewStatus());
        }
    }

    private void validateSearchRequest(AgentCaseSearchRequest request) {
        if (request == null || request.getCurrent() < 1 || request.getPageSize() < 1 || request.getPageSize() > AgentConstants.MAX_PAGE_SIZE) {
            throw new MSException("AGENT_VALIDATION_ERROR");
        }
        if (StringUtils.isNotBlank(request.getVersionId()) && StringUtils.isNotBlank(request.getRefId())) throw new MSException("AGENT_VALIDATION_ERROR");
        if (request.getFilters() != null) {
            var filters = request.getFilters();
            if (!Set.of("ANY", "ALL").contains(StringUtils.defaultString(filters.getTagMatch(), "ANY"))
                    || Boolean.TRUE.equals(filters.getExcludeRiskActions())) throw new MSException("AGENT_VALIDATION_ERROR");
            if (filters.getLimit() != null) {
                if (filters.getLimit() < 1 || filters.getLimit() > AgentConstants.MAX_PAGE_SIZE) throw new MSException("AGENT_VALIDATION_ERROR");
                request.setPageSize(Math.min(request.getPageSize(), filters.getLimit()));
            }
            for (var values : java.util.Arrays.asList(filters.getTags(), filters.getPriority(), filters.getLastExecuteResult(), filters.getModuleIds(), filters.getReviewStatus())) {
                if (values != null && (values.size() > 100 || values.stream().anyMatch(v -> StringUtils.isBlank(v) || v.length() > 255))) throw new MSException("AGENT_VALIDATION_ERROR");
            }
        }
    }
}
