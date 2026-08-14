package io.metersphere.system.service.ai.agent;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.ai.agent.AiAgentPairingConsumeRequest;
import io.metersphere.system.dto.ai.agent.AiAgentPairingCreateRequest;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class AiUserAgentServiceTests {
    @Mock private AiUserAgentRepository repository;
    @Mock private AiAuditService auditService;
    private AiUserAgentService service;

    @BeforeEach
    void setup() {
        AiUserAgentFeatureService flags = new AiUserAgentFeatureService();
        ReflectionTestUtils.setField(flags, "enabled", true);
        ReflectionTestUtils.setField(flags, "workbuddyEnabled", true);
        ReflectionTestUtils.setField(flags, "codexEnabled", true);
        ReflectionTestUtils.setField(flags, "codexVerified", true);
        ReflectionTestUtils.setField(flags, "cursorEnabled", false);
        ReflectionTestUtils.setField(flags, "minimumBridgeVersion", "0.1.0");
        service = new AiUserAgentService(repository, flags, auditService);
    }

    @Test
    void pairingPersistsOnlyHashAndReturnsHighEntropyOneTimeCode() {
        AiAgentPairingCreateRequest request = new AiAgentPairingCreateRequest();
        request.setProvider("CODEX");
        Map<String, Object> result;
        try (var ids = mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("pairing-id");
            result = service.createPairing(request, "user-a");
        }
        String raw = (String) result.get("pairingCode");
        assertTrue(raw.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}"));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).insertPairing(any(), eq("user-a"), eq("CODEX"), any(), hash.capture(),
                anyLong(), anyLong());
        assertFalse(hash.getValue().contains(raw));
        assertTrue(hash.getValue().equals(DigestUtils.sha256Hex(raw)));
    }

    @Test
    void concurrentOrRepeatedPairingConsumptionUsesGenericFailure() {
        AiAgentPairingConsumeRequest request = consumeRequest();
        String hash = DigestUtils.sha256Hex(request.getPairingCode());
        when(repository.findUsablePairing(eq(hash), anyLong())).thenReturn(Map.of(
                "id", "pairing", "user_id", "user-a", "provider", "CODEX"));
        when(repository.consumePairing(eq("pairing"), any(), anyLong())).thenReturn(0);
        MSException error;
        try (var ids = mockStatic(IDGenerator.class)) {
            ids.when(IDGenerator::nextStr).thenReturn("device-id");
            error = assertThrows(MSException.class, () -> service.consumePairing(request));
        }
        assertTrue(error.getMessage().contains("无效、已过期或已使用"));
    }

    @Test
    void disabledProviderCannotCreatePairing() {
        AiAgentPairingCreateRequest request = new AiAgentPairingCreateRequest();
        request.setProvider("CURSOR");
        assertThrows(MSException.class, () -> service.createPairing(request, "user-a"));
    }

    @Test
    void outdatedBridgeCannotConsumePairing() {
        AiAgentPairingConsumeRequest request = consumeRequest();
        request.setBridgeVersion("0.0.9");
        MSException error = assertThrows(MSException.class, () -> service.consumePairing(request));
        assertTrue(error.getMessage().contains("版本不得低于"));
    }

    private AiAgentPairingConsumeRequest consumeRequest() {
        AiAgentPairingConsumeRequest request = new AiAgentPairingConsumeRequest();
        request.setPairingCode("ABCD-EFGH-JKLM");
        request.setDeviceName("test");
        request.setPublicKey("public-key");
        request.setCertificateFingerprint("fingerprint");
        request.setBridgeVersion("1.0");
        request.setProtocolVersion("1.0");
        request.setOsType("windows");
        return request;
    }
}
