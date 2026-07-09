package io.metersphere.agent.resolver;

import io.metersphere.agent.constants.AgentWarningCode;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentSearchFilters;
import io.metersphere.agent.service.AgentModuleAliasService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class AgentQueryResolver {
    @Resource
    private ModuleTreeMatcher moduleTreeMatcher;
    @Resource
    private AgentModuleAliasService agentModuleAliasService;

    public ResolvedSearchCondition resolve(AgentCaseSearchRequest request, String projectId) {
        ResolvedSearchCondition condition = new ResolvedSearchCondition();
        AgentSearchFilters filters = request.getFilters() == null ? new AgentSearchFilters() : request.getFilters();

        if (CollectionUtils.isNotEmpty(filters.getModuleIds())) {
            condition.getModuleIds().addAll(filters.getModuleIds());
            condition.getMatchedBy().add("moduleIds");
        } else if (StringUtils.isNotBlank(request.getQuery())) {
            Set<String> aliasModuleIds = agentModuleAliasService.resolveModuleIds(projectId, request.getQuery());
            if (CollectionUtils.isNotEmpty(aliasModuleIds)) {
                condition.getModuleIds().addAll(aliasModuleIds);
                condition.getMatchedBy().add("moduleAlias");
            } else {
                ModuleTreeMatcher.ModuleMatchResult match = moduleTreeMatcher.match(projectId, request.getQuery());
                if (match.isHit()) {
                    condition.getModuleIds().addAll(match.getModuleIds());
                    condition.getMatchedModules().addAll(match.getPaths());
                    condition.getMatchedBy().add("module");
                }
            }
        }

        if (condition.getModuleIds().isEmpty() && StringUtils.isNotBlank(request.getQuery())) {
            condition.setKeyword(request.getQuery());
            condition.getMatchedBy().add("keyword");
            condition.getWarnings().add(AgentWarningCode.MODULE_NOT_MATCHED_KEYWORD_FALLBACK);
        }

        if (CollectionUtils.isNotEmpty(filters.getPriority())) {
            condition.setPriorities(new ArrayList<>(filters.getPriority()));
            condition.getMatchedBy().add("filter");
        }
        if (CollectionUtils.isNotEmpty(filters.getLastExecuteResult())) {
            condition.setLastExecuteResults(new ArrayList<>(filters.getLastExecuteResult()));
            condition.getMatchedBy().add("filter");
        }
        if (CollectionUtils.isNotEmpty(filters.getTags())) {
            condition.setTags(new ArrayList<>(filters.getTags()));
            condition.getMatchedBy().add("filter");
        }
        return condition;
    }
}
