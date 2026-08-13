package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.constants.AgentExecutionVerdict;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionEventDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentExecutionWritebackService {
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentExecutionCaseWritebackService caseWritebackService;
    @Resource
    private AgentEvaluationService evaluationService;

    public void writeback(String taskId) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(taskId);
        List<AgentExecutionCaseDTO> cases = executionMapper.selectCasesByTaskId(taskId);
        Map<String, List<AgentExecutionStepDTO>> steps = executionMapper.selectStepsByTaskId(taskId).stream()
                .collect(Collectors.groupingBy(AgentExecutionStepDTO::getExecutionCaseId));
        int writebackSuccess = 0;
        int writebackFailed = 0;
        for (AgentExecutionCaseDTO executionCase : cases) {
            try {
                if (!AgentExecutionStatus.CASE_TERMINAL.contains(executionCase.getStatus())) {
                    throw new IllegalStateException("WRITEBACK_CASE_NOT_TERMINAL: " + executionCase.getStatus());
                }
                caseWritebackService.writeback(task, executionCase,
                        steps.getOrDefault(executionCase.getId(), List.of()));
                writebackSuccess++;
                appendEvent(task, executionCase.getCaseId(), "INFO", "CASE_WRITEBACK_SUCCESS",
                        "执行结果已幂等回写", Map.of("result", StringUtils.defaultString(executionCase.getResult())));
            } catch (Exception ex) {
                writebackFailed++;
                caseWritebackService.markFailed(taskId, executionCase.getCaseId(), ex.getMessage());
                appendEvent(task, executionCase.getCaseId(), "ERROR", "CASE_WRITEBACK_FAILED",
                        "执行结果回写失败", Map.of("category", "WRITEBACK_FAILED"));
            }
        }
        finalizeTask(task, cases, steps.values().stream().flatMap(List::stream).toList(),
                writebackSuccess, writebackFailed);
    }

    private void finalizeTask(AgentExecutionTaskDTO task, List<AgentExecutionCaseDTO> cases,
                              List<AgentExecutionStepDTO> steps,
                              int writebackSuccess, int writebackFailed) {
        int success = (int) cases.stream().filter(item -> AgentExecutionStatus.SUCCESS.equals(item.getStatus())).count();
        int failed = (int) cases.stream().filter(item -> List.of(AgentExecutionStatus.FAILED,
                AgentExecutionStatus.CASE_ERROR, AgentExecutionStatus.CASE_NEEDS_REVIEW).contains(item.getStatus())).count();
        int blocked = (int) cases.stream().filter(item -> AgentExecutionStatus.CASE_BLOCKED.equals(item.getStatus())).count();
        int skipped = (int) cases.stream().filter(item -> AgentExecutionStatus.CASE_SKIPPED.equals(item.getStatus())).count();
        int unexecuted = Math.max(0, cases.size() - success - failed - blocked - skipped);
        int artifactCount = executionMapper.countAvailableArtifacts(task.getId());

        boolean technicalIssue = unexecuted > 0 || writebackFailed > 0 || writebackSuccess == 0 || artifactCount == 0;
        int completed = success + failed + blocked + skipped;
        String status = technicalIssue
                ? (completed > 0 ? AgentExecutionStatus.PARTIAL_SUCCESS : AgentExecutionStatus.FAILED)
                : AgentExecutionStatus.SUCCESS;
        String verdict = determineVerdict(failed, blocked, skipped, unexecuted, writebackFailed,
                artifactCount, steps);
        String verdictReason = "success=" + success + ",failed=" + failed + ",blocked=" + blocked
                + ",skipped=" + skipped + ",unexecuted=" + unexecuted + ",writebackFailed=" + writebackFailed
                + ",artifacts=" + artifactCount;
        String writebackStatus = writebackFailed == 0 ? "SUCCESS" : writebackSuccess > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        String artifactStatus = artifactCount > 0 ? "AVAILABLE" : "MISSING";
        int updated = executionMapper.finalizeExecutionTask(task.getId(), status, verdict, verdictReason,
                success, failed, blocked, skipped,
                unexecuted, writebackStatus, artifactStatus, "system:ai-webui-writeback", System.currentTimeMillis());
        if (updated != 1) {
            throw new IllegalStateException("WRITEBACK_TASK_STATE_CONFLICT");
        }
        appendEvent(task, null, "SUCCESS".equals(status) ? "INFO" : "WARN", "TASK_WRITEBACK_COMPLETED",
                "结果回写与证据对账完成", Map.of("status", status, "writebackStatus", writebackStatus,
                        "artifactStatus", artifactStatus, "verdict", verdict));
        evaluationService.calculate(task.getId());
    }

    private String determineVerdict(int failed, int blocked, int skipped, int unexecuted,
                                    int writebackFailed, int artifactCount,
                                    List<AgentExecutionStepDTO> steps) {
        if (unexecuted > 0 || writebackFailed > 0 || artifactCount == 0) {
            return AgentExecutionVerdict.AGENT_FAILED;
        }
        if (blocked > 0) {
            return AgentExecutionVerdict.BLOCKED;
        }
        if (failed > 0) {
            List<String> categories = steps.stream().map(AgentExecutionStepDTO::getFailureCategory)
                    .filter(StringUtils::isNotBlank).map(String::toUpperCase).toList();
            if (categories.stream().anyMatch(category -> category.startsWith("ENV_"))) {
                return AgentExecutionVerdict.ENV_FAILED;
            }
            if (categories.stream().anyMatch(category -> category.startsWith("DATA_"))) {
                return AgentExecutionVerdict.DATA_FAILED;
            }
            if (categories.stream().anyMatch(category -> category.startsWith("AGENT_")
                    || category.startsWith("RUNNER_") || category.startsWith("SCOPE_"))) {
                return AgentExecutionVerdict.AGENT_FAILED;
            }
            return AgentExecutionVerdict.PRODUCT_FAILED;
        }
        if (skipped > 0) {
            return AgentExecutionVerdict.INCONCLUSIVE;
        }
        return AgentExecutionVerdict.PASSED;
    }

    private void appendEvent(AgentExecutionTaskDTO task, String caseId, String level, String eventType,
                             String message, Map<String, Object> metadata) {
        Long max = executionMapper.selectMaxEventSequence(task.getId());
        AgentExecutionEventDTO event = new AgentExecutionEventDTO();
        event.setId(IDGenerator.nextStr());
        event.setContractVersion("v1");
        event.setTaskId(task.getId());
        event.setCaseId(caseId);
        event.setAttempt(0);
        event.setSequence((max == null ? 0 : max) + 1);
        event.setEventTime(System.currentTimeMillis());
        event.setLevel(level);
        event.setEventType(eventType);
        event.setMessage(message);
        event.setSanitizedMetadata(JSON.toJSONString(metadata));
        event.setCreateUser("system:ai-webui-writeback");
        executionMapper.insertEvent(event);
    }
}
