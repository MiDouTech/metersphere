package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.plan.dto.request.TestPlanCaseRunRequest;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.constants.ResultStatus;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.LogInsertModule;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AgentExecutionCaseWritebackService {
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentCaseSchemaMapper caseSchemaMapper;
    @Resource
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private FunctionalCaseBlobMapper functionalCaseBlobMapper;
    @Resource
    private FunctionalCaseService functionalCaseService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void writeback(AgentExecutionTaskDTO task, AgentExecutionCaseDTO executionCase,
                          List<AgentExecutionStepDTO> steps) {
        String idempotencyKey = "ai-webui:" + task.getId() + ":" + executionCase.getCaseId()
                + ":attempt-" + (executionCase.getRetryCount() == null ? 0 : executionCase.getRetryCount()) + ":v1";
        String result = toPlatformResult(executionCase.getStatus());
        if (executionMapper.countWritebackIdempotency(task.getId(), executionCase.getCaseId(), idempotencyKey) > 0) {
            executionMapper.updateCaseWritebackStatus(task.getId(), executionCase.getCaseId(),
                    "SUCCESS", null, System.currentTimeMillis());
            return;
        }
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(executionCase.getCaseId());
        if (functionalCase == null || !StringUtils.equals(task.getProjectId(), functionalCase.getProjectId())) {
            throw new MSException("WRITEBACK_CASE_MISMATCH");
        }
        List<AgentCaseStepDTO> resultSteps = steps.stream().map(this::toResultStep).toList();
        String operator = StringUtils.defaultIfBlank(task.getCreateUser(), "system");
        if (StringUtils.isNotBlank(executionCase.getTestPlanCaseId())) {
            TestPlanCaseRunRequest request = new TestPlanCaseRunRequest();
            request.setProjectId(task.getProjectId());
            request.setId(executionCase.getTestPlanCaseId());
            request.setCaseId(executionCase.getCaseId());
            request.setTestPlanId(executionCase.getTestPlanId());
            request.setLastExecResult(result);
            request.setStepsExecResult(caseSchemaMapper.toStepsExecResultJson(resultSteps));
            request.setContent("[AI WebUI] task=" + task.getId());
            testPlanFunctionalCaseService.run(request,
                    new LogInsertModule(operator, "/internal/ai-runner/v1/writeback", HttpMethodConstants.POST.name()));
        } else {
            long now = System.currentTimeMillis();
            FunctionalCase update = new FunctionalCase();
            update.setId(executionCase.getCaseId());
            update.setLastExecuteResult(result);
            update.setLastExecuteUser(operator);
            update.setLastExecuteTime(now);
            functionalCaseMapper.updateByPrimaryKeySelective(update);
            FunctionalCaseBlob blob = new FunctionalCaseBlob();
            blob.setId(executionCase.getCaseId());
            blob.setSteps(caseSchemaMapper.toStepsExecResultJson(resultSteps).getBytes(StandardCharsets.UTF_8));
            functionalCaseBlobMapper.updateByPrimaryKeySelective(blob);
            functionalCaseService.syncAssociatedPlanCaseExec(List.of(executionCase.getCaseId()), result, operator);
        }
        executionMapper.insertWritebackIdempotency(IDGenerator.nextStr(), task.getId(), executionCase.getCaseId(),
                idempotencyKey, task.getProjectId(), result, operator, System.currentTimeMillis());
        executionMapper.updateCaseWritebackStatus(task.getId(), executionCase.getCaseId(),
                "SUCCESS", null, System.currentTimeMillis());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(String taskId, String caseId, String message) {
        executionMapper.updateCaseWritebackStatus(taskId, caseId, "FAILED",
                StringUtils.abbreviate(sanitize(message), 1000), System.currentTimeMillis());
    }

    private AgentCaseStepDTO toResultStep(AgentExecutionStepDTO step) {
        AgentCaseStepDTO result = new AgentCaseStepDTO();
        result.setId(step.getSourceStepId());
        result.setNum(step.getPos());
        result.setDesc(step.getInstruction());
        result.setExpected(step.getExpected());
        result.setActualResult(step.getActualResult());
        result.setExecuteResult("SUCCESS".equals(step.getStatus())
                ? ResultStatus.SUCCESS.name() : ResultStatus.ERROR.name());
        return result;
    }

    private String toPlatformResult(String status) {
        if ("SUCCESS".equals(status)) return ResultStatus.SUCCESS.name();
        if ("BLOCKED".equals(status)) return ResultStatus.BLOCKED.name();
        return ResultStatus.ERROR.name();
    }

    private String sanitize(String message) {
        return StringUtils.defaultString(message, "WRITEBACK_FAILED")
                .replaceAll("(?i)(password|passwd|token|secret|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
    }
}
