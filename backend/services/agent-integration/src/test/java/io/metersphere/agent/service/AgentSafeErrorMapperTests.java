package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentSafeErrorMapperTests {
    private final AgentSafeErrorMapper mapper = new AgentSafeErrorMapper();

    @Test
    void mapsTypedConflictIndependentlyOfLocalizedMessage() {
        var result = mapper.toApiError(new MSException(
                io.metersphere.system.controller.handler.result.MsHttpResultCode.CONFLICT,
                "technical conflict details must not escape"), "conflict-trace");
        assertEquals("VERSION_CONFLICT", result.getCode());
        assertEquals("conflict-trace", result.getTraceId());
        assertFalse(result.getMessage().contains("technical"));
    }

    @Test
    void preservesTypedPermissionErrorAndDiscardsRawMessage() {
        var result = mapper.toApiError(new MSException(io.metersphere.agent.constants.AgentErrorCode.SCOPE_DENIED, "SQL password=secret"), "typed-trace");
        assertEquals("SCOPE_DENIED", result.getCode());assertFalse(result.getMessage().contains("secret"));
    }

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

    @Test
    void returnsActionableMessagesForConfigurationErrors() {
        var credential = mapper.toApiError(new MSException("CREDENTIAL_SECRET_REF_INVALID"), "trace-3");
        var model = mapper.toApiError(new MSException("MODEL_SERVICE_KEY_REF_INVALID"), "trace-4");

        assertEquals("CREDENTIAL_SECRET_REF_INVALID", credential.getCode());
        assertEquals("Secret 引用格式无效，请使用 env://变量名 或 vault://mount/path#field", credential.getMessage());
        assertEquals("Gateway Service Key 引用必须使用 env:// 或 vault:// 格式", model.getMessage());
    }
    @Test
    void moduleConflictUsesTypedCodeAndNeverExposesLocalizedDetails() {
        var result = mapper.toApiError(new io.metersphere.sdk.exception.ModuleNameConflictException("localized SQL detail"), "m");
        assertEquals("MODULE_NAME_CONFLICT", result.getCode());
        assertFalse(result.getMessage().contains("SQL"));
    }

    @Test
    void caseEvidenceErrorsDescribeWholeCaseWithoutChangingRunnerErrors() {
        var result = mapper.toApiError(new MSException("QUALITY_CASE_EVIDENCE_REQUIRED"), "case");
        assertEquals("QUALITY_CASE_EVIDENCE_REQUIRED", result.getCode());
        org.junit.jupiter.api.Assertions.assertTrue(result.getMessage().contains("所有步骤"));
    }
}
