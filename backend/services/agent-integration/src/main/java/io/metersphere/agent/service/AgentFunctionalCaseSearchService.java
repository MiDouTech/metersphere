package io.metersphere.agent.service;

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
@Transactional(rollbackFor = Exception.class)
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

    public AgentCaseSearchResponse search(AgentCaseSearchRequest request) {
        validateSearchRequest(request);
        String projectId = requireProjectId();
        ResolvedSearchCondition condition = agentQueryResolver.resolve(request, projectId);
        Map<String, String> modulePathMap = buildModulePathMap(projectId);

        List<AgentCaseDTO> cases;
        long total;
        if (StringUtils.isNotBlank(request.getTestPlanId())) {
            cases = searchInTestPlan(request, projectId, condition, modulePathMap);
            total = cases.size();
        } else {
            cases = searchInRepository(request, projectId, condition, modulePathMap);
            total = cases.size();
            if (StringUtils.isBlank(request.getTestPlanId())) {
                // no testPlanCaseId without test plan
            }
        }

        boolean includeSteps = request.getIncludeSteps() == null || request.getIncludeSteps();
        AgentCaseSearchResponse response = buildResponse(condition, cases, total);
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
        String projectId = requireProjectId();
        String userId = SessionUtils.getUserId();
        FunctionalCaseDetailDTO detail = functionalCaseService.getFunctionalCaseDetail(caseId, userId, false);
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
        return moduleTreeMatcher.flatten(projectId).stream()
                .map(agentCaseSchemaMapper::toModuleDto)
                .collect(Collectors.toList());
    }

    private List<AgentCaseDTO> searchInRepository(AgentCaseSearchRequest request, String projectId,
                                                    ResolvedSearchCondition condition, Map<String, String> modulePathMap) {
        FunctionalCasePageRequest pageRequest = new FunctionalCasePageRequest();
        pageRequest.setProjectId(projectId);
        pageRequest.setCurrent(request.getCurrent());
        pageRequest.setPageSize(request.getPageSize());
        if (CollectionUtils.isNotEmpty(condition.getModuleIds())) {
            pageRequest.setModuleIds(new ArrayList<>(condition.getModuleIds()));
        }
        if (StringUtils.isNotBlank(condition.getKeyword())) {
            pageRequest.initKeyword(condition.getKeyword());
        }
        Map<String, List<String>> filter = buildRepositoryFilter(condition);
        if (!filter.isEmpty()) {
            pageRequest.setFilter(filter);
        }
        List<FunctionalCasePageDTO> page = functionalCaseService.getFunctionalCasePage(pageRequest, false, false);
        return page.stream()
                .map(item -> agentCaseSchemaMapper.fromFunctionalCasePage(item, modulePathMap.get(item.getModuleId())))
                .filter(item -> agentCaseSchemaMapper.matchesPriority(item.getPriority(), condition.getPriorities()))
                .filter(item -> agentCaseSchemaMapper.matchesTags(item.getTags(), condition.getTags()))
                .collect(Collectors.toList());
    }

    private List<AgentCaseDTO> searchInTestPlan(AgentCaseSearchRequest request, String projectId,
                                                 ResolvedSearchCondition condition, Map<String, String> modulePathMap) {
        TestPlanCaseRequest pageRequest = new TestPlanCaseRequest();
        pageRequest.setTestPlanId(request.getTestPlanId());
        pageRequest.setProjectId(projectId);
        pageRequest.setCurrent(request.getCurrent());
        pageRequest.setPageSize(request.getPageSize());
        if (CollectionUtils.isNotEmpty(condition.getModuleIds())) {
            pageRequest.setModuleIds(new ArrayList<>(condition.getModuleIds()));
        }
        if (StringUtils.isNotBlank(condition.getKeyword())) {
            pageRequest.initKeyword(condition.getKeyword());
        }
        Map<String, List<String>> filter = buildTestPlanFilter(condition);
        if (!filter.isEmpty()) {
            pageRequest.setFilter(filter);
        }
        List<TestPlanCasePageResponse> page = testPlanFunctionalCaseService.getFunctionalCasePage(pageRequest, false, projectId);
        return page.stream()
                .map(item -> agentCaseSchemaMapper.fromTestPlanCase(item, modulePathMap.get(item.getModuleId())))
                .filter(item -> agentCaseSchemaMapper.matchesPriority(item.getPriority(), condition.getPriorities()))
                .filter(item -> agentCaseSchemaMapper.matchesTags(item.getTags(), condition.getTags()))
                .peek(item -> item.setTestPlanId(request.getTestPlanId()))
                .collect(Collectors.toList());
    }

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

    private void validateSearchRequest(AgentCaseSearchRequest request) {
        boolean hasQuery = StringUtils.isNotBlank(request.getQuery());
        boolean hasFilters = request.getFilters() != null && (
                CollectionUtils.isNotEmpty(request.getFilters().getModuleIds())
                        || CollectionUtils.isNotEmpty(request.getFilters().getPriority())
                        || CollectionUtils.isNotEmpty(request.getFilters().getLastExecuteResult())
                        || CollectionUtils.isNotEmpty(request.getFilters().getTags())
        );
        boolean hasPlan = StringUtils.isNotBlank(request.getTestPlanId());
        if (!hasQuery && !hasFilters && !hasPlan) {
            throw new IllegalArgumentException("query 与 filters 至少一项非空");
        }
        if (request.getPageSize() > AgentConstants.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 最大 500");
        }
    }

    private String requireProjectId() {
        String projectId = SessionUtils.getCurrentProjectId();
        if (StringUtils.isBlank(projectId)) {
            throw new IllegalArgumentException("缺少项目上下文，请设置 X-MS-PROJECT 或 PROJECT");
        }
        return projectId;
    }
}
