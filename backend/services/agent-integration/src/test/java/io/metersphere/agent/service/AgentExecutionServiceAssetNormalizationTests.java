package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.TestAssetRefDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AgentExecutionServiceAssetNormalizationTests {

    private final AgentExecutionService service = new AgentExecutionService();

    @Test
    void boundEnvironmentBecomesAssetReferenceWithoutChangingCredentialScope() {
        AgentExecutionCreateRequest request = new AgentExecutionCreateRequest();
        request.setEnvironmentId("credential-scope");

        service.normalizeEnvironmentAssetRef(request, "env-1");

        Assertions.assertEquals(1, request.getAssetRefs().size());
        Assertions.assertEquals("ENVIRONMENT", request.getAssetRefs().getFirst().getAssetType());
        Assertions.assertEquals("env-1", request.getAssetRefs().getFirst().getAssetId());
        Assertions.assertEquals("credential-scope", request.getEnvironmentId());
    }

    @Test
    void frozenEnvironmentReferenceRetainsItsVersionAndDoesNotPopulateCredentialScope() {
        AgentExecutionCreateRequest request = new AgentExecutionCreateRequest();
        request.setAssetRefs(List.of(ref("CASE", "case-1"), ref("ENVIRONMENT", "env-1")));
        request.getAssetRefs().get(1).setVersionId("frozen-version");

        service.normalizeEnvironmentAssetRef(request, "env-1");

        Assertions.assertNull(request.getEnvironmentId());
        Assertions.assertEquals("frozen-version", request.getAssetRefs().get(1).getVersionId());
        Assertions.assertEquals(2, request.getAssetRefs().size());
    }

    @Test
    void conflictingOrMultipleEnvironmentsShouldBeRejected() {
        AgentExecutionCreateRequest conflict = new AgentExecutionCreateRequest();
        conflict.setEnvironmentId("env-1");
        conflict.setAssetRefs(List.of(ref("ENVIRONMENT", "env-2")));
        Assertions.assertThrows(MSException.class, () -> service.normalizeEnvironmentAssetRef(conflict, "env-1"));

        AgentExecutionCreateRequest multiple = new AgentExecutionCreateRequest();
        multiple.setAssetRefs(List.of(ref("ENVIRONMENT", "env-1"), ref("ENVIRONMENT", "env-2")));
        Assertions.assertThrows(MSException.class, () -> service.normalizeEnvironmentAssetRef(multiple, "env-1"));
    }

    @Test void credentialScopeAloneCannotSatisfyManagedAssetRequirement() {
        AgentExecutionCreateRequest request = new AgentExecutionCreateRequest();
        request.setEnvironmentId("credential-scope");
        Assertions.assertThrows(MSException.class, () -> service.normalizeEnvironmentAssetRef(request, null));
    }

    private TestAssetRefDTO ref(String type, String id) {
        TestAssetRefDTO ref = new TestAssetRefDTO();
        ref.setAssetType(type);
        ref.setAssetId(id);
        return ref;
    }
}
