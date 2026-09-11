package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBatchSubmitRequest;
import io.metersphere.agent.dto.AgentBatchSubmitResponse;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentBatchSubmitService {
    @Resource
    private AgentSafeErrorMapper safeErrorMapper;
    @Resource
    private AgentMcpBatchItemService mcpBatchItemService;
    @Resource
    private AgentFunctionalCaseSubmitService agentFunctionalCaseSubmitService;
    @Resource
    private AgentExecutionService agentExecutionService;

    public AgentBatchSubmitResponse batchSubmit(AgentBatchSubmitRequest request) {
        return batchSubmit(request, null);
    }

    public AgentBatchSubmitResponse batchSubmit(AgentBatchSubmitRequest request, String requestId) {
        AgentBatchSubmitResponse response = new AgentBatchSubmitResponse();
        response.setTotal(request.getResults().size());
        int index = 0;
        for (AgentCaseSubmitRequest item : request.getResults()) {
            String key = requestId == null ? null : org.apache.commons.codec.digest.DigestUtils.sha256Hex(requestId + ":" + index++);
            mergeBatchContext(request, item);
            if (key != null && StringUtils.isNotBlank(item.getExecutionTaskId()) && StringUtils.isBlank(item.getIdempotencyKey())) {
                item.setIdempotencyKey(key);
            }
            try {
                if (key == null) agentFunctionalCaseSubmitService.submit(item);
                else mcpBatchItemService.submit(item, key);
                response.setSuccess(response.getSuccess() + 1);
            } catch (Exception e) {
                var safe = safeErrorMapper.toApiError(e, null);
                log.error("Batch writeback failed, traceId={}", safe.getTraceId(), e);
                if (StringUtils.isNotBlank(item.getExecutionTaskId())) {
                    try {
                        agentExecutionService.markCaseWritebackFailed(item.getExecutionTaskId(), item.getCaseId(), safe.getMessage());
                    } catch (Exception markingError) {
                        log.error("Cannot mark failed writeback, traceId={}", safe.getTraceId(), markingError);
                    }
                }
                response.setFailed(response.getFailed() + 1);
                var error = new AgentBatchSubmitResponse.AgentBatchSubmitError(item.getCaseId(), safe.getMessage());
                error.setCode(safe.getCode());
                error.setTraceId(safe.getTraceId());
                response.getErrors().add(error);
                if (request.isFailFast()) {
                    break;
                }
            }
        }
        response.setSkipped(response.getTotal() - response.getSuccess() - response.getFailed());
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
