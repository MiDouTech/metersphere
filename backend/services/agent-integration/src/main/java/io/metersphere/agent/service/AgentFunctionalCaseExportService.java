package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentFunctionalCaseExportService {
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private AgentCaseSchemaMapper agentCaseSchemaMapper;

    public void enrichWithSteps(List<AgentCaseDTO> cases, List<String> warnings) {
        if (CollectionUtils.isEmpty(cases)) {
            return;
        }
        for (int start = 0; start < cases.size(); start += 200) {
            List<AgentCaseDTO> chunk = cases.subList(start, Math.min(start + 200, cases.size()));
            String projectId = chunk.getFirst().getProjectId();
            if (projectId == null || chunk.stream().anyMatch(item -> !projectId.equals(item.getProjectId())))
                throw new io.metersphere.sdk.exception.MSException("单批读取必须属于同一个已授权项目");
            var details = functionalCaseService.getAgentCaseDetails(chunk.stream().map(AgentCaseDTO::getCaseId).toList(), projectId, true);
            for (AgentCaseDTO item : chunk) {
                FunctionalCaseDetailDTO detail = details.get(item.getCaseId());
                if (detail == null || (item.getProjectId() != null && !item.getProjectId().equals(detail.getProjectId())))
                    throw new io.metersphere.sdk.exception.MSException("用例已变更或无权访问，请重新检索");
                agentCaseSchemaMapper.enrichDetail(item, detail, warnings);
            }
        }
    }
}
