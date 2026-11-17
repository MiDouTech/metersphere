package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.agent.dto.AgentExecutionTaskDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.functional.domain.FunctionalCase;
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

import java.util.List;

@Service
public class AgentExecutionCaseWritebackService {
    @Resource private io.metersphere.agent.quality.GlobalQualityGate qualityGate;
    @Resource private AgentExecutionFinalizationService finalization;
    @Resource
    private AgentExecutionMapper executionMapper;
    @Resource
    private AgentCaseSchemaMapper caseSchemaMapper;
    @Resource
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource private org.springframework.jdbc.core.JdbcTemplate protocolJdbc;
    @Resource private AgentProtocolStore protocolStore;
    @Resource private AgentProtocolAccess protocolAccess;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void writeback(AgentExecutionTaskDTO task, AgentExecutionCaseDTO executionCase,
                          List<AgentExecutionStepDTO> steps) {
        finalization.requireLegacyWriteback(task.getId());
        writebackVersion(task,executionCase,steps,"v1");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void writebackGoverned(AgentExecutionTaskDTO task,AgentExecutionCaseDTO executionCase,List<AgentExecutionStepDTO> steps,String instanceId,long resultVersion) {
        var accepted=protocolJdbc.queryForList("SELECT i.payload,s.organization_id,s.environment_id,s.owner_id,s.project_id FROM msp_protocol_instance i JOIN msp_protocol_session s ON s.id=i.session_id WHERE i.id=? AND i.result_version=? AND i.verification='ACCEPTED' AND i.source_case_id=? AND s.source_id=? AND s.source_execution_id=? FOR UPDATE",instanceId,resultVersion,executionCase.getId(),task.getId(),task.getCurrentExecutionId());
        if(accepted.size()!=1)throw new MSException("PROTOCOL_RESULT_NOT_ACCEPTED");
        var context=accepted.getFirst();var facts=protocolStore.data(context);
        protocolAccess.requireAcceptedReviewer(context,facts);
        if(Boolean.TRUE.equals(facts.get("disputed")))throw new MSException("PROTOCOL_CORRECTION_REQUIRED");
        protocolAccess.requireTaskSourceWrite((String)context.get("owner_id"),(String)context.get("project_id"),(String)context.get("organization_id"),(String)context.get("environment_id"));
        writebackVersion(task,executionCase,steps,"governed-"+org.apache.commons.codec.digest.DigestUtils.sha256Hex(instanceId+":"+resultVersion).substring(0,16));
    }
    private void writebackVersion(AgentExecutionTaskDTO task,AgentExecutionCaseDTO executionCase,List<AgentExecutionStepDTO> steps,String suffix) {
        finalization.beginSync(task,executionCase);
        String idempotencyKey = "ai-webui:" + task.getId() + ":" + task.getCurrentExecutionId() + ":" + executionCase.getCaseId()
                + ":attempt-" + (executionCase.getRetryCount() == null ? 0 : executionCase.getRetryCount()) + ":"+suffix;
        String result = toPlatformResult(executionCase.getStatus());
        if (executionMapper.countWritebackIdempotency(task.getId(), executionCase.getCaseId(), idempotencyKey) > 0) {
            executionMapper.updateCaseWritebackStatus(task.getId(), executionCase.getCaseId(),
                    "SUCCESS", null, System.currentTimeMillis());
            finalization.synced(task,executionCase);
            return;
        }
        qualityGate.requireCase(task, executionCase, steps);
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(executionCase.getCaseId());
        if (functionalCase == null || Boolean.TRUE.equals(functionalCase.getDeleted()) || !StringUtils.equals(task.getProjectId(), functionalCase.getProjectId())) {
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
            testPlanFunctionalCaseService.runFrozenResult(request,
                    new LogInsertModule(operator, "/internal/ai-runner/v1/writeback", HttpMethodConstants.POST.name()));
        } else {
            long now = System.currentTimeMillis();
            FunctionalCase update = new FunctionalCase();
            update.setId(executionCase.getCaseId());
            update.setLastExecuteResult(result);
            update.setLastExecuteUser(operator);
            update.setLastExecuteTime(now);
            functionalCaseMapper.updateByPrimaryKeySelective(update);
            functionalCaseService.syncAssociatedPlanCaseExec(List.of(executionCase.getCaseId()), result, operator);
        }
        executionMapper.insertWritebackIdempotency(IDGenerator.nextStr(), task.getId(), executionCase.getCaseId(),
                idempotencyKey, task.getProjectId(), result, operator, System.currentTimeMillis());
        executionMapper.updateCaseWritebackStatus(task.getId(), executionCase.getCaseId(),
                "SUCCESS", null, System.currentTimeMillis());
        finalization.synced(task,executionCase);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(String taskId, String executionId,String caseId, String message) {
        finalization.failed(taskId,executionId,caseId);
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
