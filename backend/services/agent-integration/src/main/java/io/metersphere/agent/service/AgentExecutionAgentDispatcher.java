package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.system.service.ai.provider.AiAgentGatewayService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentExecutionAgentDispatcher {
    @Resource
    private AiAgentGatewayService agentGatewayService;
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentExecutionService executionService;

    @Async("threadPoolTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AgentExecutionDispatchEvent event) {
        try {
            AgentExecutionTaskDTO task = executionMapper.selectTaskById(event.taskId());
            if (task == null) {
                throw new IllegalStateException("AI execution task does not exist: " + event.taskId());
            }
            List<AgentExecutionCaseDTO> cases = executionMapper.selectCasesByTaskId(event.taskId());
            Map<String, List<AgentExecutionStepDTO>> steps = executionMapper.selectStepsByTaskId(event.taskId())
                    .stream().collect(Collectors.groupingBy(AgentExecutionStepDTO::getExecutionCaseId));
            cases.forEach(item -> item.setSteps(steps.getOrDefault(item.getId(), List.of())));

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("executionTaskId", task.getId());
            context.put("projectId", task.getProjectId());
            context.put("agentType", event.agentType());
            context.put("targetUrl", task.getTargetUrl());
            context.put("environmentId", task.getEnvironmentId());
            context.put("browserType", task.getBrowserType());
            context.put("loginMode", task.getLoginMode());
            context.put("cases", cases);
            agentGatewayService.invokeExecutionAgent(event.gatewayId(), event.projectId(), event.taskId(),
                    context, event.userId());
            executionService.markAgentDispatchAccepted(event.taskId(), event.agentType(), event.gatewayId());
        } catch (Exception ex) {
            log.error("AI execution Agent dispatch failed, taskId={}, agentType={}",
                    event.taskId(), event.agentType(), ex);
            executionService.markAgentDispatchFailed(event.taskId(), event.agentType(), ex.getMessage());
        }
    }
}
