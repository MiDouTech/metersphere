package io.metersphere.system.service.ai.agent;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.ai.agent.AiAgentDeviceAuthenticateRequest;
import io.metersphere.system.dto.ai.agent.AiAgentConnectionStatusRequest;
import io.metersphere.system.dto.ai.agent.AiAgentDeviceDTO;
import io.metersphere.system.dto.ai.agent.AiAgentPairingConsumeRequest;
import io.metersphere.system.dto.ai.agent.AiAgentPairingCreateRequest;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionCreateRequest;
import io.metersphere.system.dto.ai.agent.AiUserAgentConnectionDTO;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.uid.IDGenerator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional(rollbackFor = Exception.class)
public class AiUserAgentService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long PAIRING_TTL_MS = 5 * 60_000L;
    private static final long CHALLENGE_TTL_MS = 60_000L;
    private static final long ACCESS_TOKEN_TTL_MS = 10 * 60_000L;
    private static final Set<String> CONNECTION_STATUSES = Set.of("CONNECTED", "OFFLINE", "AUTH_EXPIRED");
    private static final Set<String> CAPABILITY_KEYS = Set.of(
            "stream", "tools", "files", "cancel", "vision", "sessionResume",
            "providerVersion", "cliVersion", "outputFormat");
    private static final Pattern CREDENTIAL_SHAPED_VALUE = Pattern.compile(
            "(?i)(bearer\\s+|sk-[a-z0-9_-]{12,}|api[_-]?key\\s*[:=]|token\\s*[:=])");
    private final SecureRandom secureRandom = new SecureRandom();
    private final AiUserAgentRepository repository;
    private final AiUserAgentFeatureService featureService;
    private final AiAuditService auditService;
    @Value("${ms.ai.user-agent.offline-after-ms:45000}")
    private long offlineAfterMs;

    public AiUserAgentService(AiUserAgentRepository repository, AiUserAgentFeatureService featureService,
                              AiAuditService auditService) {
        this.repository = repository;
        this.featureService = featureService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AiUserAgentConnectionDTO> listConnections(String userId) {
        if (!featureService.enabled()) {
            return List.of();
        }
        return repository.listConnections(userId).stream()
                .filter(item -> featureService.providerEnabled(item.getProvider()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiUserAgentConnectionDTO requireAvailable(String connectionId, String userId) {
        if (!featureService.enabled()) {
            throw new MSException("AI_RESOURCE_NOT_ALLOWED：用户 Agent 功能未启用");
        }
        AiUserAgentConnectionDTO connection = repository.findConnection(connectionId, userId);
        if (connection == null || !featureService.providerEnabled(connection.getProvider())) {
            throw new MSException("AI_RESOURCE_NOT_ALLOWED：Agent 连接不存在或无权限");
        }
        if (!StringUtils.equals(connection.getStatus(), "CONNECTED")
                || !StringUtils.equals(connection.getDeviceStatus(), "ONLINE")) {
            throw new MSException("AGENT_OFFLINE：Agent 或 Bridge 当前不可用");
        }
        if (connection.getExpiresAt() != null && connection.getExpiresAt() <= System.currentTimeMillis()) {
            throw new MSException("AGENT_AUTH_EXPIRED：Agent 授权已过期");
        }
        return connection;
    }

    @Transactional(readOnly = true)
    public List<AiAgentDeviceDTO> listDevices(String userId) {
        return featureService.enabled() ? repository.listDevices(userId) : List.of();
    }

    public Map<String, Object> createPairing(AiAgentPairingCreateRequest request, String userId) {
        requireFeature(request.getProvider());
        String rawCode = pairingCode();
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        repository.insertPairing(id, userId, normalizeProvider(request.getProvider()),
                StringUtils.trimToNull(request.getExpectedDeviceName()), DigestUtils.sha256Hex(rawCode),
                now + PAIRING_TTL_MS, now);
        auditService.record(null, null, userId, id, "CREATE", "AI_AGENT_BRIDGE_PAIRING_CREATE",
                "/ai/agent-bridge/pairing", "POST", Map.of("provider", normalizeProvider(request.getProvider())));
        return Map.of("pairingId", id, "pairingCode", rawCode, "expiresAt", now + PAIRING_TTL_MS);
    }

    public Map<String, Object> consumePairing(AiAgentPairingConsumeRequest request) {
        featureService.assertBridgeVersionSupported(request.getBridgeVersion());
        long now = System.currentTimeMillis();
        String normalizedCode = StringUtils.upperCase(StringUtils.trim(request.getPairingCode()));
        Map<String, Object> pairing = repository.findUsablePairing(DigestUtils.sha256Hex(normalizedCode), now);
        if (pairing == null) {
            throw invalidPairing();
        }
        String provider = (String) pairing.get("provider");
        if (StringUtils.isNotBlank(provider) && !featureService.providerEnabled(provider)) {
            throw invalidPairing();
        }
        String deviceId = IDGenerator.nextStr();
        if (repository.consumePairing((String) pairing.get("id"), deviceId, now) != 1) {
            throw invalidPairing();
        }
        String token = secureToken();
        repository.insertDevice(deviceId, (String) pairing.get("user_id"), request.getDeviceName(),
                request.getPublicKey(), StringUtils.lowerCase(request.getCertificateFingerprint()),
                request.getBridgeVersion(), request.getProtocolVersion(), request.getOsType(),
                DigestUtils.sha256Hex(token), now + ACCESS_TOKEN_TTL_MS, now);
        auditService.record(null, null, (String) pairing.get("user_id"), deviceId, "CREATE",
                "AI_AGENT_BRIDGE_DEVICE_PAIRED", "/ai/agent-bridge/pairing/consume", "POST",
                Map.of("provider", StringUtils.defaultString(provider), "bridgeVersion", request.getBridgeVersion(),
                        "osType", request.getOsType()));
        return Map.of("deviceId", deviceId, "accessToken", token,
                "accessTokenExpiresAt", now + ACCESS_TOKEN_TTL_MS);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pairingStatus(String id, String userId) {
        Map<String, Object> pairing = repository.findPairing(id, userId);
        if (pairing == null) {
            throw new MSException("配对请求不存在或无权限");
        }
        long expiresAt = ((Number) pairing.get("expires_at")).longValue();
        String storedStatus = (String) pairing.get("status");
        String status = "PENDING".equals(storedStatus) && expiresAt < System.currentTimeMillis()
                ? "EXPIRED" : storedStatus;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pairingId", pairing.get("id"));
        result.put("provider", pairing.get("provider"));
        result.put("status", status);
        result.put("expiresAt", expiresAt);
        result.put("deviceId", pairing.get("device_id"));
        result.put("consumedAt", pairing.get("consumed_at"));
        return result;
    }

    public Map<String, Object> createChallenge(String deviceId) {
        if (!featureService.enabled() || repository.findActiveDevice(deviceId) == null) {
            throw new MSException("设备认证失败");
        }
        String nonce = secureToken();
        String challengeId = IDGenerator.nextStr();
        long now = System.currentTimeMillis();
        repository.insertChallenge(challengeId, deviceId, DigestUtils.sha256Hex(nonce),
                now + CHALLENGE_TTL_MS, now);
        return Map.of("challengeId", challengeId, "nonce", nonce, "expiresAt", now + CHALLENGE_TTL_MS);
    }

    public Map<String, Object> authenticate(AiAgentDeviceAuthenticateRequest request) {
        long now = System.currentTimeMillis();
        Map<String, Object> device = repository.findActiveDevice(request.getDeviceId());
        Map<String, Object> challenge = repository.findChallenge(request.getChallengeId(), request.getDeviceId(), now);
        if (device == null || challenge == null
                || !constantTimeEquals((String) challenge.get("nonce_hash"), DigestUtils.sha256Hex(request.getNonce()))
                || !verifySignature((String) device.get("public_key"), request.getNonce(), request.getSignature())
                || repository.consumeChallenge(request.getChallengeId(), request.getDeviceId(), now) != 1) {
            throw new MSException("设备认证失败");
        }
        String token = secureToken();
        repository.rotateDeviceToken(request.getDeviceId(), DigestUtils.sha256Hex(token),
                now + ACCESS_TOKEN_TTL_MS, now);
        return Map.of("deviceId", request.getDeviceId(), "accessToken", token,
                "accessTokenExpiresAt", now + ACCESS_TOKEN_TTL_MS);
    }

    public Map<String, Object> authenticateToken(String deviceId, String rawToken) {
        Map<String, Object> device = repository.findActiveDevice(deviceId);
        if (device == null || StringUtils.isBlank(rawToken)) {
            throw new MSException("设备认证失败");
        }
        Number expiresAt = (Number) device.get("access_token_expires_at");
        if (expiresAt == null || expiresAt.longValue() < System.currentTimeMillis()
                || !constantTimeEquals((String) device.get("access_token_hash"), DigestUtils.sha256Hex(rawToken))) {
            throw new MSException("设备认证失败");
        }
        return device;
    }

    public AiUserAgentConnectionDTO createConnection(AiUserAgentConnectionCreateRequest request, String userId) {
        String provider = normalizeProvider(request.getProvider());
        requireFeature(provider);
        if (!repository.ownsOnlineDevice(request.getDeviceId(), userId)) {
            throw new MSException("设备不存在、已撤销或无权限");
        }
        long now = System.currentTimeMillis();
        String id = IDGenerator.nextStr();
        String displayName = StringUtils.defaultIfBlank(StringUtils.trim(request.getDisplayName()),
                provider + " Agent");
        repository.insertConnection(id, userId, provider, StringUtils.left(displayName, 255),
                request.getDeviceId(), now);
        auditService.record(null, null, userId, id, "CREATE", "AI_USER_AGENT_CONNECTION_CREATE",
                "/ai/user-agent/connections", "POST",
                Map.of("provider", provider, "deviceId", request.getDeviceId()));
        return repository.findConnection(id, userId);
    }

    public AiUserAgentConnectionDTO prepareAuthorization(String id, String userId) {
        requireFeatureEnabled();
        AiUserAgentConnectionDTO connection = repository.findConnection(id, userId);
        if (connection == null || StringUtils.equals(connection.getStatus(), "REVOKED")
                || !featureService.providerEnabled(connection.getProvider())) {
            throw new MSException("Agent 连接不存在、已撤销或无权限");
        }
        if (!repository.ownsOnlineDevice(connection.getDeviceId(), userId)) {
            throw new MSException("AGENT_OFFLINE：Bridge 当前不可用");
        }
        auditService.record(null, null, userId, id, "UPDATE", "AI_USER_AGENT_AUTHORIZATION_REQUEST",
                "/ai/user-agent/connections/{id}/authorize", "POST",
                Map.of("provider", connection.getProvider(), "deviceId", connection.getDeviceId()));
        return connection;
    }

    public void revokeConnection(String id, String userId) {
        if (repository.revokeConnection(id, userId, System.currentTimeMillis()) == 0) {
            throw new MSException("Agent 连接不存在、已撤销或无权限");
        }
        auditService.record(null, null, userId, id, "DELETE", "AI_USER_AGENT_CONNECTION_REVOKE",
                "/ai/user-agent/connections/{id}/revoke", "POST", Map.of());
    }

    @Transactional(readOnly = true)
    public AiUserAgentConnectionDTO getConnection(String id, String userId) {
        AiUserAgentConnectionDTO connection = repository.findConnection(id, userId);
        if (connection == null) throw new MSException("Agent 连接不存在或无权限");
        return connection;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> connectionImpact(String id, String userId) {
        getConnection(id, userId);
        return repository.connectionImpact(id, userId);
    }

    public void deleteConnection(String id, String userId) {
        AiUserAgentConnectionDTO connection = getConnection(id, userId);
        if (!"REVOKED".equals(connection.getStatus())) {
            throw new MSException("请先撤销 Agent 连接，再删除记录");
        }
        Map<String, Object> impact = repository.connectionImpact(id, userId);
        if (((Number) impact.get("activeExecutionCount")).intValue() > 0
                || repository.deleteRevokedConnection(id, userId, System.currentTimeMillis()) != 1) {
            throw new MSException("Agent 连接仍被活动执行使用，不能删除");
        }
        auditService.record(null, null, userId, id, "DELETE", "AI_USER_AGENT_CONNECTION_DELETE",
                "/ai/user-agent/connections/{id}", "DELETE", impact);
    }

    public void revokeDevice(String id, String userId) {
        if (repository.revokeDevice(id, userId, System.currentTimeMillis()) == 0) {
            throw new MSException("Bridge 设备不存在、已撤销或无权限");
        }
        auditService.record(null, null, userId, id, "DELETE", "AI_AGENT_BRIDGE_DEVICE_REVOKE",
                "/ai/agent-bridge/devices/{id}/revoke", "POST", Map.of());
    }

    public void heartbeat(String deviceId, String rawToken) {
        authenticateToken(deviceId, rawToken);
        heartbeatAuthenticated(deviceId);
    }

    public void heartbeatAuthenticated(String deviceId) {
        if (repository.heartbeat(deviceId, System.currentTimeMillis()) != 1) {
            throw new MSException("设备认证失败");
        }
    }

    public void markDeviceOfflineAuthenticated(String deviceId) {
        repository.markDeviceOffline(deviceId, System.currentTimeMillis());
    }

    @Scheduled(fixedDelayString = "${ms.ai.user-agent.offline-scan-ms:15000}")
    public void markStaleDevicesOffline() {
        if (featureService.enabled()) {
            long now = System.currentTimeMillis();
            repository.markStaleDevicesOffline(now - Math.max(15_000L, offlineAfterMs), now);
        }
    }

    public void reportConnectionStatus(AiAgentConnectionStatusRequest request, String rawToken) {
        authenticateToken(request.getDeviceId(), rawToken);
        reportConnectionStatusAuthenticated(request, request.getDeviceId());
    }

    public void reportConnectionStatusAuthenticated(AiAgentConnectionStatusRequest request, String deviceId) {
        Map<String, Object> device = repository.findActiveDevice(deviceId);
        if (device == null || !CONNECTION_STATUSES.contains(request.getStatus())) {
            throw new MSException("Agent connection status is invalid");
        }
        String userId = (String) device.get("user_id");
        if (repository.updateConnectionStatus(request.getConnectionId(), deviceId, userId,
                request.getStatus(), sanitizeMaskedAccount(request.getMaskedAccount()),
                sanitizeCapabilities(request.getCapabilities()), request.getExpiresAt(),
                System.currentTimeMillis()) != 1) {
            throw new MSException("Agent 连接不存在、已撤销或不属于当前设备");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> connectionRoutesForDevice(String deviceId) {
        if (!featureService.enabled() || repository.findActiveDevice(deviceId) == null) {
            return List.of();
        }
        return repository.listConnectionRoutes(deviceId).stream()
                .filter(item -> featureService.providerEnabled((String) item.get("provider")))
                .toList();
    }

    private void requireFeature(String provider) {
        if (!featureService.providerEnabled(normalizeProvider(provider))) {
            throw new MSException("AI_RESOURCE_NOT_ALLOWED：Agent Provider 未启用");
        }
    }

    private void requireFeatureEnabled() {
        if (!featureService.enabled()) {
            throw new MSException("AI_RESOURCE_NOT_ALLOWED：用户 Agent 功能未启用");
        }
    }

    private String normalizeProvider(String provider) {
        return StringUtils.upperCase(StringUtils.trim(provider));
    }

    private String pairingCode() {
        StringBuilder value = new StringBuilder(14);
        for (int group = 0; group < 3; group++) {
            if (group > 0) {
                value.append('-');
            }
            for (int i = 0; i < 4; i++) {
                value.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }
        }
        return value.toString();
    }

    private String secureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private MSException invalidPairing() {
        return new MSException("配对码无效、已过期或已使用");
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean verifySignature(String pem, String nonce, String encodedSignature) {
        try {
            String normalized = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            PublicKey publicKey = null;
            for (String algorithm : List.of("EC", "RSA")) {
                try {
                    publicKey = KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(keyBytes));
                    break;
                } catch (Exception ignored) {
                    // Try the next supported public key format.
                }
            }
            if (publicKey == null) {
                return false;
            }
            String signatureAlgorithm = publicKey.getAlgorithm().toUpperCase(Locale.ROOT).contains("EC")
                    ? "SHA256withECDSA" : "SHA256withRSA";
            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(publicKey);
            verifier.update(nonce.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(encodedSignature));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String sanitizeMaskedAccount(String value) {
        String masked = StringUtils.left(StringUtils.trimToNull(value), 255);
        if (masked != null && CREDENTIAL_SHAPED_VALUE.matcher(masked).find()) {
            throw new MSException("Agent account metadata must not contain credentials");
        }
        return masked;
    }

    private String sanitizeCapabilities(String value) {
        if (StringUtils.isBlank(value)) {
            return "{}";
        }
        try {
            Map<?, ?> source = JSON.parseObject(value, Map.class);
            Map<String, Object> sanitized = new LinkedHashMap<>();
            source.forEach((key, capabilityValue) -> {
                String name = String.valueOf(key);
                if (CAPABILITY_KEYS.contains(name)
                        && (capabilityValue instanceof Boolean || capabilityValue instanceof Number
                        || capabilityValue instanceof String && ((String) capabilityValue).length() <= 100)) {
                    sanitized.put(name, capabilityValue);
                }
            });
            return JSON.toJSONString(sanitized);
        } catch (RuntimeException error) {
            throw new MSException("Agent capabilities must be a valid JSON object");
        }
    }
}
