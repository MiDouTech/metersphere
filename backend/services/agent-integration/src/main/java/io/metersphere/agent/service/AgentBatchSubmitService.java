package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBatchSubmitRequest;
import io.metersphere.agent.dto.AgentBatchSubmitResponse;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AgentBatchSubmitService {
    @Resource
    private AgentFunctionalCaseSubmitService agentFunctionalCaseSubmitService;
    @Resource
    private AgentExecutionService agentExecutionService;

    public AgentBatchSubmitResponse batchSubmit(AgentBatchSubmitRequest request) {
        AgentBatchSubmitResponse response = new AgentBatchSubmitResponse();
        response.setTotal(request.getResults().size());
        for (AgentCaseSubmitRequest item : request.getResults()) {
            mergeBatchContext(request, item);
            try {
                agentFunctionalCaseSubmitService.submit(item);
                response.setSuccess(response.getSuccess() + 1);
            } catch (Exception e) {
                if (StringUtils.isNotBlank(item.getExecutionTaskId())) {
                    agentExecutionService.markCaseWritebackFailed(item.getExecutionTaskId(), item.getCaseId(), e.getMessage());
                }
                response.setFailed(response.getFailed() + 1);
                response.getErrors().add(new AgentBatchSubmitResponse.AgentBatchSubmitError(
                        item.getCaseId(), e.getMessage()));
                if (request.isFailFast()) {
                    break;
                }
            }
        }
        return response;
    }

    private void mergeBatchContext(AgentBatchSubmitRequest request, AgentCaseSubmitRequest item) {
        if (StringUtils.isBlank(item.getProjectId())) {
            item.setProjectId(request.getProjectId());
        }
        if (StringUtils.isBlank(item.getTestPlanId())) {
            item.setTestPlanId(request.getTestPlanId());
        }
        if (StringUtils.isBlank(item.getExecutedBy())) {
            item.setExecutedBy(request.getExecutedBy());
        }
        if (StringUtils.isBlank(item.getExecutionTaskId())) {
            item.setExecutionTaskId(request.getExecutionTaskId());
        }
    }
}
