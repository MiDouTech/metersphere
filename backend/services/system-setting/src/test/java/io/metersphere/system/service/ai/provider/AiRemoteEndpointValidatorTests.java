package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiRemoteEndpointValidatorTests {

    @Test
    void rejectsNonHttpsMetadataAndPrivateNetworks() {
        assertThrows(MSException.class,
                () -> AiRemoteEndpointValidator.validateHttps("http://example.com/token", "OAuth", false));
        assertThrows(MSException.class,
                () -> AiRemoteEndpointValidator.validateHttps("https://169.254.169.254/latest/meta-data", "OAuth", false));
        assertThrows(MSException.class,
                () -> AiRemoteEndpointValidator.validateHttps("https://127.0.0.1/token", "OAuth", false));
        assertThrows(MSException.class,
                () -> AiRemoteEndpointValidator.validateHttps("https://10.0.0.8/gateway", "Gateway", false));
        assertThrows(MSException.class,
                () -> AiRemoteEndpointValidator.validateHttps("https://user:secret@example.com/token", "OAuth", false));
    }

    @Test
    void permitsExplicitEnterprisePrivateGatewayOverride() {
        assertDoesNotThrow(
                () -> AiRemoteEndpointValidator.validateHttps("https://10.0.0.8/gateway", "Gateway", true));
    }
}
