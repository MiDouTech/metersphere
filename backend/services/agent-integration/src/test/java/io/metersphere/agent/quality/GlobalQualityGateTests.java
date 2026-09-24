package io.metersphere.agent.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GlobalQualityGateTests {
    private final ObjectMapper json=new ObjectMapper();
    private AgentExecutionArtifactDTO evidence() {
        var artifact=new AgentExecutionArtifactDTO();
        artifact.setPurpose("AFTER_STEP");artifact.setContentType("image/png");artifact.setSizeBytes(100L);
        return artifact;
    }
    private void check(String frozen,String actual,List<AgentExecutionArtifactDTO> evidence) throws Exception {
        GlobalQualityGate.evaluate(json.readTree(QualityPolicyValidatorTests.VALID),json.readTree(frozen),json.readTree(actual),evidence);
    }
    @Test void evaluatesActualInsteadOfTrustingSubmittedPassOrExpected() throws Exception {
        check("[{\"operator\":\"EQUALS\",\"expected\":\"ok\"}]","[{\"actual\":\"ok\"}]",List.of(evidence()));
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":\"ok\"}]",
                "[{\"actual\":\"wrong\",\"expected\":\"wrong\",\"passed\":true}]",List.of(evidence())));
    }
    @Test void missingEvidenceAndMissingAssertionsEachFailIndependently() {
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":\"ok\"}]","[{\"actual\":\"ok\"}]",List.of()));
        assertThrows(MSException.class,()->check("[]","[]",List.of(evidence())));
        assertThrows(MSException.class,()->check("[]","[]",List.of()));
    }
    @Test void evidenceMimeSizeAndRequiredRoleAreEnforced() {
        var artifact=evidence(); artifact.setContentType("text/plain");
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":1}]","[{\"actual\":1}]",List.of(artifact)));
        artifact.setContentType("image/png");artifact.setSizeBytes(20_000_000L);
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":1}]","[{\"actual\":1}]",List.of(artifact)));
        artifact.setSizeBytes(10L);artifact.setPurpose("BEFORE_STEP");
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":1}]","[{\"actual\":1}]",List.of(artifact)));
    }
    @Test void typedEqualityDoesNotCoerceNumbersOrAcceptMissingObservations() {
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":1}]","[{\"actual\":\"1\"}]",List.of(evidence())));
        assertThrows(MSException.class,()->check("[{\"operator\":\"EQUALS\",\"expected\":1}]","[{\"passed\":true}]",List.of(evidence())));
    }
    @Test void numericRangeUsesFrozenBoundsAndInclusiveEdges() throws Exception {
        var policy=json.readTree(QualityPolicyValidatorTests.VALID.replace("\"EQUALS\"","\"IN_RANGE\""));
        var frozen=json.readTree("[{\"operator\":\"IN_RANGE\",\"expected\":[1,5]}]");
        GlobalQualityGate.evaluate(policy,frozen,json.readTree("[{\"actual\":5}]"),List.of(evidence()));
        assertThrows(MSException.class,()->GlobalQualityGate.evaluate(policy,frozen,json.readTree("[{\"actual\":6}]"),List.of(evidence())));
    }
}
