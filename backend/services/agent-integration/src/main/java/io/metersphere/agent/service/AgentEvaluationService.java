package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.dto.AgentEvaluationRequest;
import io.metersphere.agent.dto.AgentEvaluationHistoryDTO;
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
import java.util.Set;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentEvaluationService {
    @Resource
    private AgentEvaluationMapper mapper;
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentProjectService agentProjectService;
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
        AgentExecutionTaskDTO task = requireTask(taskId);
        AgentExecutionEvaluationDTO evaluation = mapper.selectByTaskId(taskId);
        if (evaluation == null && AgentExecutionStatus.TERMINAL.contains(task.getStatus())) {
            evaluation = calculate(taskId);
        }
        return evaluation;
    }

    public Pager<List<AgentExecutionEvaluationDTO>> page(String projectId, String operationalStatus,
                                                         String businessVerdict, String executorType,
                                                         Integer current, Integer pageSize) {
        String resolved = agentProjectService.resolveProjectId(projectId);
        String status = normalizeFilter(operationalStatus, Set.of(
                AgentExecutionStatus.CREATED, AgentExecutionStatus.RESOLVING_SCOPE,
                AgentExecutionStatus.WAITING_CONFIRMATION, AgentExecutionStatus.QUEUED,
                AgentExecutionStatus.PREPARING_BROWSER, AgentExecutionStatus.WAITING_LOGIN,
                AgentExecutionStatus.WAITING_HUMAN, AgentExecutionStatus.RUNNING,
                AgentExecutionStatus.PAUSED, AgentExecutionStatus.WRITING_BACK,
                AgentExecutionStatus.SUCCESS, AgentExecutionStatus.PARTIAL_SUCCESS,
                AgentExecutionStatus.FAILED, AgentExecutionStatus.CANCELED, AgentExecutionStatus.EXPIRED),
                "operationalStatus");
        String verdict = normalizeFilter(businessVerdict,
                Set.of("PASSED", "PRODUCT_FAILED", "ENV_FAILED", "DATA_FAILED", "AGENT_FAILED", "BLOCKED", "CANCELED"),
                "businessVerdict");
        String executor = normalizeFilter(executorType, Set.of("RUNNER", "AGENT"), "executorType");
        int page = Math.max(current == null ? 1 : current, 1);
        int size = Math.min(Math.max(pageSize == null ? 20 : pageSize, 1), 100);
        long total = mapper.countByProject(resolved, status, verdict, executor);
        return new Pager<>(mapper.selectByProject(resolved, status, verdict, executor,
                (page - 1) * size, size), total, size, page);
    }

    public List<AgentEvaluationSummaryDTO> summary(String projectId, Long fromTime, Long toTime) {
        String resolved = agentProjectService.resolveProjectId(projectId);
        if (fromTime != null && toTime != null && fromTime > toTime) {
            throw new MSException("fromTime 不能大于 toTime");
        }
        return mapper.summarize(resolved, fromTime, toTime);
    }

    public AgentExecutionEvaluationDTO manualEvaluate(String taskId, AgentEvaluationRequest request) {
        requireTask(taskId);
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
        AgentEvaluationHistoryDTO history = new AgentEvaluationHistoryDTO();
        history.setId(IDGenerator.nextStr());
        history.setTaskId(taskId);
        history.setProjectId(evaluation.getProjectId());
        history.setScore(request.getScore());
        history.setComment(StringUtils.abbreviate(request.getComment(), 2000));
        history.setEvaluatedBy(userId);
        history.setEvaluatedAt(System.currentTimeMillis());
        mapper.insertHistory(history);
        execLogService.audit("AI_EXECUTION_MANUAL_EVALUATION", taskId,
                "score=" + request.getScore());
        return mapper.selectByTaskId(taskId);
    }

    public List<AgentEvaluationHistoryDTO> history(String taskId, Integer limit) {
        requireTask(taskId);
        return mapper.selectHistory(taskId, Math.min(Math.max(limit == null ? 50 : limit, 1), 200));
    }

    private AgentExecutionTaskDTO requireTask(String taskId) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(taskId);
        if (task == null) {
            throw new MSException("AI 执行任务不存在：" + taskId);
        }
        String resolvedProjectId = agentProjectService.resolveProjectId(task.getProjectId());
        if (!StringUtils.equals(resolvedProjectId, task.getProjectId())) {
            throw new MSException("AI 执行任务项目上下文校验失败：" + taskId);
        }
        return task;
    }

    private String normalizeFilter(String value, Set<String> allowed, String field) {
        String normalized = StringUtils.upperCase(StringUtils.trimToNull(value));
        if (normalized != null && !allowed.contains(normalized)) {
            throw new MSException(field + " 不支持: " + normalized);
        }
        return normalized;
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
