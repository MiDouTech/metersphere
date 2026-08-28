package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentSafeErrorMapperTests {
    private final AgentSafeErrorMapper mapper = new AgentSafeErrorMapper();

    @Test
    void preservesStableBusinessCodeWithoutLeakingTechnicalDetails() {
        var result = mapper.toApiError(new MSException("MODEL_BUDGET_EXCEEDED"), "trace-1");
        assertEquals("MODEL_BUDGET_EXCEEDED", result.getCode());
        assertEquals("trace-1", result.getTraceId());
        assertFalse(result.getMessage().contains("Exception"));
    }

    @Test
    void replacesUnknownExceptionWithSafeError() {
        var result = mapper.toApiError(new IllegalStateException("jdbc:mysql://secret/db password=hidden"), "trace-2");
        assertEquals("AI_EXECUTION_INTERNAL_ERROR", result.getCode());
        assertFalse(result.toString().contains("jdbc:mysql"));
        assertFalse(result.toString().contains("hidden"));
    }
}
