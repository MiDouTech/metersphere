package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSearchResponse;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanSearchResponse;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTestPlanQueryService {
    @Resource
    private AgentExecutionMapper agentExecutionMapper;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentFunctionalCaseSearchService agentFunctionalCaseSearchService;

    public AgentTestPlanSearchResponse search(AgentTestPlanSearchRequest request) {
        AgentTestPlanSearchRequest actual = request == null ? new AgentTestPlanSearchRequest() : request;
        String projectId = agentProjectService.resolveProjectId(actual.getProjectId());
        int page = actual.getPage() == null || actual.getPage() < 1 ? 1 : actual.getPage();
        int pageSize = actual.getPageSize() == null || actual.getPageSize() < 1 ? 20 : Math.min(actual.getPageSize(), AgentConstants.MAX_PAGE_SIZE);
        String keyword = StringUtils.trimToEmpty(actual.getKeyword());
        String likeKeyword = AgentProjectService.escapeLike(keyword.toLowerCase());
        boolean includeArchived = BooleanUtils.isTrue(actual.getIncludeArchived());

        AgentTestPlanSearchResponse response = new AgentTestPlanSearchResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        long total = agentExecutionMapper.countPlans(projectId, keyword, likeKeyword, actual.getStatus(), includeArchived);
        response.setTotal(total);
        response.setHasMore((long) page * pageSize < total);
        if (total == 0) {
            response.setItems(Collections.emptyList());
            return response;
        }
        response.setItems(agentExecutionMapper.searchPlans(projectId, keyword, likeKeyword, actual.getStatus(),
                includeArchived, (page - 1) * pageSize, pageSize));
        return response;
    }

    public AgentCaseSearchResponse cases(String projectId, String testPlanId, int current, int pageSize, boolean includeSteps) {
        AgentCaseSearchRequest request = new AgentCaseSearchRequest();
        request.setProjectId(projectId);
        request.setTestPlanId(testPlanId);
        request.setCurrent(current < 1 ? 1 : current);
        request.setPageSize(Math.min(Math.max(pageSize, 1), AgentConstants.MAX_PAGE_SIZE));
        request.setIncludeSteps(includeSteps);
        return agentFunctionalCaseSearchService.search(request);
    }

    public AgentTestPlanDTO get(String id) {
        AgentTestPlanSearchRequest request = new AgentTestPlanSearchRequest();
        request.setKeyword(id);
        request.setIncludeArchived(true);
        request.setPageSize(AgentConstants.MAX_PAGE_SIZE);
        return search(request).getItems().stream()
                .filter(item -> StringUtils.equals(item.getId(), id))
                .findFirst()
                .orElse(null);
    }
}
