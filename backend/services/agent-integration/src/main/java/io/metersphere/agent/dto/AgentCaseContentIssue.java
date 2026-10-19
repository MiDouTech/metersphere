package io.metersphere.agent.dto;

public record AgentCaseContentIssue(String code, String stage, String caseId, String caseVersionId,
        String stepId, Integer stepIndex, String fieldPath, String message, String suggestion,
        boolean retryable, String traceId) {}
