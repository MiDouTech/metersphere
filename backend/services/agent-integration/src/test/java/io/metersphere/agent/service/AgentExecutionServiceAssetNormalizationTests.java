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
    void legacyEnvironmentIdShouldBecomeVersionableAssetReference() {
        AgentExecutionCreateRequest request = new AgentExecutionCreateRequest();
        request.setEnvironmentId("env-1");

        service.normalizeEnvironmentAssetRef(request);

        Assertions.assertEquals(1, request.getAssetRefs().size());
        Assertions.assertEquals("ENVIRONMENT", request.getAssetRefs().getFirst().getAssetType());
        Assertions.assertEquals("env-1", request.getAssetRefs().getFirst().getAssetId());
    }

    @Test
    void environmentAssetReferenceShouldPopulateLegacyEnvironmentField() {
        AgentExecutionCreateRequest request = new AgentExecutionCreateRequest();
        request.setAssetRefs(List.of(ref("CASE", "case-1"), ref("ENVIRONMENT", "env-1")));

        service.normalizeEnvironmentAssetRef(request);

        Assertions.assertEquals("env-1", request.getEnvironmentId());
        Assertions.assertEquals(2, request.getAssetRefs().size());
    }

    @Test
    void conflictingOrMultipleEnvironmentsShouldBeRejected() {
        AgentExecutionCreateRequest conflict = new AgentExecutionCreateRequest();
        conflict.setEnvironmentId("env-1");
        conflict.setAssetRefs(List.of(ref("ENVIRONMENT", "env-2")));
        Assertions.assertThrows(MSException.class, () -> service.normalizeEnvironmentAssetRef(conflict));

        AgentExecutionCreateRequest multiple = new AgentExecutionCreateRequest();
        multiple.setAssetRefs(List.of(ref("ENVIRONMENT", "env-1"), ref("ENVIRONMENT", "env-2")));
        Assertions.assertThrows(MSException.class, () -> service.normalizeEnvironmentAssetRef(multiple));
    }

    private TestAssetRefDTO ref(String type, String id) {
        TestAssetRefDTO ref = new TestAssetRefDTO();
        ref.setAssetType(type);
        ref.setAssetId(id);
        return ref;
    }
}
