package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentHealingDecisionDTO;
import io.metersphere.agent.dto.AgentWebActionDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AgentExecutionHealingPolicy {
    public static final int DEFAULT_STEP_BUDGET = 2;
    public static final int DEFAULT_CASE_BUDGET = 5;
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.80D;
    private static final Set<String> HEALABLE_FAILURES = Set.of(
            "LOCATOR_NOT_FOUND", "LOCATOR_NOT_UNIQUE", "ELEMENT_NOT_ACTIONABLE",
            "NAVIGATION_TIMEOUT", "TRANSIENT_OVERLAY");

    public AgentHealingDecisionDTO decide(String failureType, AgentWebActionDTO action, int stepAttempts,
                                          int caseAttempts, double confidence, int candidateCount) {
        if (!HEALABLE_FAILURES.contains(StringUtils.upperCase(failureType))) {
            return denied("failure type is not healable");
        }
        if (action == null || Boolean.FALSE.equals(action.getRetryable())
                || "HIGH".equalsIgnoreCase(action.getRiskLevel())) {
            return denied("action is not retryable");
        }
        if (stepAttempts >= DEFAULT_STEP_BUDGET) {
            return denied("step healing budget exhausted");
        }
        if (caseAttempts >= DEFAULT_CASE_BUDGET) {
            return denied("case healing budget exhausted");
        }
        if (candidateCount != 1) {
            return denied("healing target must be unique");
        }
        if (confidence < DEFAULT_CONFIDENCE_THRESHOLD) {
            return denied("healing confidence below threshold");
        }
        return new AgentHealingDecisionDTO(true, "healing allowed within policy budget");
    }

    private AgentHealingDecisionDTO denied(String reason) {
        return new AgentHealingDecisionDTO(false, reason);
    }
}
