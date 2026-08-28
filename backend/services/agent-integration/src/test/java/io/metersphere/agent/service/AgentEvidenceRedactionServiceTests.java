package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentEvidenceRedactionServiceTests {
    private final AgentEvidenceRedactionService service=new AgentEvidenceRedactionService();
    @Test void redactsSensitiveHeadersAndRejectsRawSecretArtifacts(){
        assertEquals("***",service.redactHeaders(Map.of("Authorization","Bearer abc")).get("Authorization"));
        assertThrows(MSException.class,()->service.scanBeforePersist("password=abc".getBytes(StandardCharsets.UTF_8),"text/plain",false));
    }
    @Test void screenshotsRequireRunnerMasking(){
        byte[] png=new byte[8];assertThrows(MSException.class,()->service.scanBeforePersist(png,"image/png",false));
        assertDoesNotThrow(()->service.scanBeforePersist(png,"image/png",true));
    }
}
