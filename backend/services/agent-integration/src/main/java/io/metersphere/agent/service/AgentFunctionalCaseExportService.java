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
        String userId = SessionUtils.getUserId();
        for (AgentCaseDTO item : cases) {
            FunctionalCaseDetailDTO detail = functionalCaseService.getFunctionalCaseDetail(item.getCaseId(), userId, false);
            agentCaseSchemaMapper.enrichDetail(item, detail, warnings);
        }
    }
}
