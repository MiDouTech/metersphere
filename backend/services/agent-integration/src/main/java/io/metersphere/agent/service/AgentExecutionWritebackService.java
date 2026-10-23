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
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AgentExecutionWritebackService.class);
    @Resource private AgentExecutionFinalizationService finalization;
    @Resource private AgentRecoveryScanHealth recoveryHealth;
    @Resource private AgentExecutionAlertService recoveryAlerts;

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000)
    public void recoverPendingSync(){
        try {
            if (!recoveryHealth.begin()) return;
            for(String taskId:finalization.recoverableTasks()) {
                try { writeback(taskId); }
                catch(Exception error) { LOG.warn("Quality sync task recovery failed task={}",taskId,error); }
            }
            recoveryHealth.success();
        } catch(Exception error) {
            boolean report = recoveryHealth.failure(error);
            if (report) {
                LOG.error("Writeback recovery scan unavailable; category={}, retries are bounded",AgentRecoveryScanHealth.category(error),error);
                try { recoveryAlerts.recoveryFailure(AgentRecoveryScanHealth.category(error)); }
                catch(Exception alertFailure) { LOG.warn("Recovery alert store unavailable; local cooldown remains active"); }
            }
        }
    }
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentExecutionCaseWritebackService caseWritebackService;
    @Resource
    private AgentEvaluationService evaluationService;
    @Resource
    private AgentFailureClassifier failureClassifier;
    @Resource
    private AgentBugDraftService bugDraftService;

    public void writeback(String taskId) {
        AgentExecutionTaskDTO task = executionMapper.selectTaskById(taskId);
        var frozen=finalization.prepare(taskId);
        List<AgentExecutionCaseDTO> cases=frozen.stream().map(AgentExecutionFinalizationService.FrozenCase::executionCase).toList();
        Map<String,List<AgentExecutionStepDTO>> steps=frozen.stream().collect(Collectors.toMap(item->item.executionCase().getId(),AgentExecutionFinalizationService.FrozenCase::steps));
        boolean qualityRejected=frozen.stream().anyMatch(item->!"PASS".equals(item.qualityStatus()));
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
                try {
                int caseArtifactCount=executionMapper.countAvailableArtifactsByExecutionCase(taskId,executionCase.getId());
                AgentFailureClassifier.Classification classification=failureClassifier.classify(
                        steps.getOrDefault(executionCase.getId(),List.of()),caseArtifactCount,false,false);
                if("PRODUCT_FAILED".equals(classification.verdict())&&classification.evidenceSufficient()){
                    String bugId=bugDraftService.createIfAbsent(task,executionCase,caseArtifactCount);
                    appendEvent(task,executionCase.getCaseId(),"INFO","BUG_DRAFT_CREATED","已创建待人工复核的缺陷草稿",Map.of("bugId",bugId,"evidenceCount",caseArtifactCount));
                }
                    appendEvent(task, executionCase.getCaseId(), "INFO", "CASE_WRITEBACK_SUCCESS",
                            "执行结果已幂等回写", Map.of("result", StringUtils.defaultString(executionCase.getResult())));
                } catch(Exception diagnosticFailure){LOG.warn("Post-sync diagnostics failed task={} case={}",taskId,executionCase.getCaseId(),diagnosticFailure);}
            } catch (Exception ex) {
                writebackFailed++;
                caseWritebackService.markFailed(taskId,task.getCurrentExecutionId(), executionCase.getCaseId(), ex.getMessage());
                appendEvent(task, executionCase.getCaseId(), "ERROR", "CASE_WRITEBACK_FAILED",
                        "执行结果回写失败", Map.of("category", "WRITEBACK_FAILED"));
            }
        }
        finalizeTask(task, cases, steps.values().stream().flatMap(List::stream).toList(),
                writebackSuccess, writebackFailed,qualityRejected);
    }

    private void finalizeTask(AgentExecutionTaskDTO task, List<AgentExecutionCaseDTO> cases,
                              List<AgentExecutionStepDTO> steps,
                              int writebackSuccess, int writebackFailed,boolean qualityRejected) {
        int success = (int) cases.stream().filter(item -> AgentExecutionStatus.SUCCESS.equals(item.getStatus())).count();
        int failed = (int) cases.stream().filter(item -> List.of(AgentExecutionStatus.FAILED,
                AgentExecutionStatus.CASE_ERROR, AgentExecutionStatus.CASE_NEEDS_REVIEW).contains(item.getStatus())).count();
        int blocked = (int) cases.stream().filter(item -> AgentExecutionStatus.CASE_BLOCKED.equals(item.getStatus())).count();
        int skipped = (int) cases.stream().filter(item -> AgentExecutionStatus.CASE_SKIPPED.equals(item.getStatus())).count();
        int unexecuted = Math.max(0, cases.size() - success - failed - blocked - skipped);
        int artifactCount = executionMapper.countAvailableArtifacts(task.getId());
        int cleanupOutstanding = countCleanupOutstanding(task.getId());

        boolean technicalIssue = unexecuted > 0 || writebackFailed > 0 || writebackSuccess == 0 || artifactCount == 0 || cleanupOutstanding > 0;
        int completed = success + failed + blocked + skipped;
        String status = technicalIssue
                ? (completed > 0 ? AgentExecutionStatus.PARTIAL_SUCCESS : AgentExecutionStatus.FAILED)
                : AgentExecutionStatus.SUCCESS;
        String verdict = qualityRejected ? AgentExecutionVerdict.INCONCLUSIVE : determineVerdict(failed, blocked, skipped, unexecuted, writebackFailed,
                artifactCount, cleanupOutstanding > 0, steps);
        String verdictReason = "success=" + success + ",failed=" + failed + ",blocked=" + blocked
                + ",skipped=" + skipped + ",unexecuted=" + unexecuted + ",writebackFailed=" + writebackFailed
                + ",artifacts=" + artifactCount + ",cleanupOutstanding=" + cleanupOutstanding;
        String writebackStatus = writebackFailed == 0 ? "SUCCESS" : writebackSuccess > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        String artifactStatus = artifactCount > 0 ? "AVAILABLE" : "MISSING";
        int updated = executionMapper.finalizeExecutionTask(task.getId(), task.getCurrentExecutionId(),status, verdict, verdictReason,
                success, failed, blocked, skipped,
                unexecuted, writebackStatus, artifactStatus, "system:ai-webui-writeback", System.currentTimeMillis());
        if (updated != 1) {
            var current=executionMapper.selectTaskById(task.getId());
            if(current!=null && java.util.Objects.equals(current.getCurrentExecutionId(),task.getCurrentExecutionId())
                    && java.util.Objects.equals(current.getWritebackStatus(),writebackStatus))return;
            throw new IllegalStateException("WRITEBACK_TASK_STATE_CONFLICT");
        }
        appendEvent(task, null, "SUCCESS".equals(status) ? "INFO" : "WARN", "TASK_WRITEBACK_COMPLETED",
                "结果回写与证据对账完成", Map.of("status", status, "writebackStatus", writebackStatus,
                        "artifactStatus", artifactStatus, "verdict", verdict));
        evaluationService.calculate(task.getId());
    }

    private String determineVerdict(int failed, int blocked, int skipped, int unexecuted,
                                    int writebackFailed, int artifactCount, boolean cleanupIncomplete,
                                    List<AgentExecutionStepDTO> steps) {
        if (artifactCount == 0 || cleanupIncomplete) {
            return AgentExecutionVerdict.INCONCLUSIVE;
        }
        if (unexecuted > 0) {
            return AgentExecutionVerdict.AGENT_FAILED;
        }
        if (blocked > 0) {
            return AgentExecutionVerdict.BLOCKED;
        }
        if (failed > 0) {
            return failureClassifier.classify(steps,artifactCount,cleanupIncomplete,false).verdict();
        }
        if (skipped > 0) {
            return AgentExecutionVerdict.INCONCLUSIVE;
        }
        return AgentExecutionVerdict.PASSED;
    }

    private int countCleanupOutstanding(String taskId){
        return executionMapper.countOutstandingDataCleanup(taskId);
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
