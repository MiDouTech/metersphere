package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentExecutionStatus;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.dto.AgentExecutionCaseDTO;
import io.metersphere.agent.dto.AgentExecutionStepDTO;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentExecutionSnapshotService {
    private static final List<String> HIGH_RISK_WORDS = List.of(
            "删除", "支付", "退款", "发布", "权限", "批量修改", "转账", "清空",
            "delete", "payment", "refund", "publish", "permission", "transfer"
    );

    @Resource
    private AgentFunctionalCaseSearchService functionalCaseSearchService;

    public List<AgentExecutionStepDTO> prepareSnapshot(AgentExecutionCaseDTO executionCase, long now) {
        AgentCaseDTO source = functionalCaseSearchService.getById(
                executionCase.getCaseId(), true, executionCase.getTestPlanId());
        executionCase.setCaseSnapshot(JSON.toJSONString(source));
        if (CollectionUtils.isEmpty(source.getSteps())) {
            return List.of();
        }
        List<AgentExecutionStepDTO> steps = new ArrayList<>();
        int fallbackPos = 0;
        for (AgentCaseStepDTO sourceStep : source.getSteps()) {
            AgentExecutionStepDTO step = new AgentExecutionStepDTO();
            step.setId(IDGenerator.nextStr());
            step.setTaskId(executionCase.getTaskId());
            step.setExecutionCaseId(executionCase.getId());
            step.setCaseId(executionCase.getCaseId());
            step.setSourceStepId(sourceStep.getId());
            step.setPos(sourceStep.getNum() == null ? fallbackPos : sourceStep.getNum());
            step.setInstruction(StringUtils.trimToNull(sourceStep.getDesc()));
            step.setExpected(StringUtils.trimToNull(sourceStep.getExpected()));
            boolean ambiguous = StringUtils.isAnyBlank(step.getInstruction(), step.getExpected());
            boolean highRisk = containsHighRisk(step.getInstruction());
            step.setRiskLevel(highRisk ? "HIGH" : "LOW");
            step.setRetryable(!highRisk);
            step.setStatus(ambiguous ? AgentExecutionStatus.CASE_NEEDS_REVIEW : AgentExecutionStatus.CASE_PENDING);
            step.setFailureCategory(ambiguous ? "SCOPE_STEP_AMBIGUOUS" : null);
            step.setErrorMessage(ambiguous ? "步骤描述或预期结果为空，需要人工补充后执行" : null);
            step.setAttempt(0);
            step.setRetryCount(0);
            step.setHealed(false);
            step.setCreateTime(now);
            step.setUpdateTime(now);
            step.setVersion(0);
            steps.add(step);
            fallbackPos++;
        }
        return steps;
    }

    private boolean containsHighRisk(String instruction) {
        if (StringUtils.isBlank(instruction)) {
            return false;
        }
        String lower = instruction.toLowerCase();
        return HIGH_RISK_WORDS.stream().anyMatch(word -> lower.contains(word.toLowerCase()));
    }
}
