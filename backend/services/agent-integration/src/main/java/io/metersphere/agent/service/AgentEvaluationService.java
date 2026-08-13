package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.dto.AgentEvaluationRequest;
import io.metersphere.agent.dto.AgentEvaluationSummaryDTO;
import io.metersphere.agent.dto.AgentExecutionEvaluationDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentEvaluationMapper;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentEvaluationService {
    @Resource
    private AgentEvaluationMapper mapper;
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentProjectService projectService;
    @Resource
    private AgentExecutionService executionService;
    @Resource
    private AgentExecLogService execLogService;

    public AgentExecutionEvaluationDTO calculate(String taskId) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(taskId);
        if (task == null || !AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            throw new MSException("仅终态任务可生成执行评价");
        }
        int completedCases = value(task.getSuccessCount()) + value(task.getFailedCount())
                + value(task.getBlockedCount()) + value(task.getSkippedCount());
        int terminalSteps = executionMapper.countTerminalSteps(taskId);
        int stepsWithEvidence = executionMapper.countTerminalStepsWithArtifacts(taskId);
        long now = System.currentTimeMillis();
        AgentExecutionEvaluationDTO evaluation = new AgentExecutionEvaluationDTO();
        evaluation.setId(IDGenerator.nextStr());
        evaluation.setTaskId(taskId);
        evaluation.setProjectId(task.getProjectId());
        evaluation.setExecutorType(StringUtils.defaultIfBlank(task.getExecutionMode(), "UNKNOWN"));
        evaluation.setExecutorId(StringUtils.defaultIfBlank(task.getRunnerId(), task.getAgentType()));
        evaluation.setOperationalStatus(task.getStatus());
        evaluation.setBusinessVerdict(task.getVerdict());
        evaluation.setCompletionRate(rate(completedCases, value(task.getTotalCount())));
        evaluation.setEvidenceRate(rate(stepsWithEvidence, terminalSteps));
        evaluation.setHealingCount(executionMapper.sumHealingCount(taskId));
        evaluation.setRetryCount(executionMapper.sumRetryCount(taskId));
        evaluation.setDurationMs(task.getFinishedAt() == null || task.getCreateTime() == null
                ? null : Math.max(0, task.getFinishedAt() - task.getCreateTime()));
        evaluation.setCreatedAt(now);
        evaluation.setUpdatedAt(now);
        mapper.upsert(evaluation);
        return mapper.selectByTaskId(taskId);
    }

    public AgentExecutionEvaluationDTO get(String taskId) {
        AgentExecutionTaskDTO task = executionService.get(taskId);
        AgentExecutionEvaluationDTO evaluation = mapper.selectByTaskId(taskId);
        if (evaluation == null && AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            evaluation = calculate(taskId);
        }
        return evaluation;
    }

    public Pager<List<AgentExecutionEvaluationDTO>> page(String projectId, Integer current, Integer pageSize) {
        String resolved = projectService.resolveProjectId(projectId);
        int page = Math.max(current == null ? 1 : current, 1);
        int size = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        long total = mapper.countByProject(resolved);
        return new Pager<>(mapper.selectByProject(resolved, (page - 1) * size, size), total, size, page);
    }

    public List<AgentEvaluationSummaryDTO> summary(String projectId, Long fromTime, Long toTime) {
        String resolved = projectService.resolveProjectId(projectId);
        if (fromTime != null && toTime != null && fromTime > toTime) {
            throw new MSException("fromTime 不能大于 toTime");
        }
        return mapper.summarize(resolved, fromTime, toTime);
    }

    public AgentExecutionEvaluationDTO manualEvaluate(String taskId, AgentEvaluationRequest request) {
        executionService.get(taskId);
        AgentExecutionEvaluationDTO evaluation = mapper.selectByTaskId(taskId);
        if (evaluation == null) {
            evaluation = calculate(taskId);
        }
        String userId = SessionUtils.getUserId();
        int updated = mapper.updateManual(taskId, request.getScore(),
                StringUtils.abbreviate(request.getComment(), 2000), userId, System.currentTimeMillis());
        if (updated != 1) {
            throw new MSException("执行评价更新失败");
        }
        execLogService.audit("AI_EXECUTION_MANUAL_EVALUATION", taskId,
                "score=" + request.getScore());
        return mapper.selectByTaskId(taskId);
    }

    @Scheduled(fixedDelay = 60_000L)
    public void backfillTerminalEvaluations() {
        for (String taskId : mapper.selectTerminalTasksWithoutEvaluation(100)) {
            try {
                calculate(taskId);
            } catch (Exception ex) {
                execLogService.audit("AI_EXECUTION_EVALUATION_FAILED", taskId,
                        StringUtils.abbreviate(ex.getMessage(), 1000));
            }
        }
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
