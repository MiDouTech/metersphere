package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.EncryptUtils;
import io.metersphere.system.dto.request.ai.AiOAuthAuthorizeRequest;
import io.metersphere.system.dto.request.ai.AiOAuthCallbackRequest;
import io.metersphere.system.dto.request.ai.AiOAuthConnectionRequest;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.service.ai.AiAuditService;
import io.metersphere.system.service.PermissionCheckService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.constants.UserRoleType;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AiOAuthService {
    private static final long STATE_TTL_MS = 10 * 60 * 1000L;
    private static final long REFRESH_SKEW_MS = 60_000L;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RestClient restClient;

    public AiOAuthService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource private AiAuditService aiAuditService;
    @Resource private PermissionCheckService permissionCheckService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(AiOAuthConnectionRequest request, String userId) {
        assertScopeAccess(request.getProjectId(), request.getOrganizationId(), userId);
        validateRemoteUri(request.getAuthorizationUri());
        validateRemoteUri(request.getTokenUri());
        if (StringUtils.isNotBlank(request.getRevokeUri())) validateRemoteUri(request.getRevokeUri());
        if (StringUtils.isNotBlank(request.getId())) {
            requireConnection(request.getId(), userId);
        }
        String id = StringUtils.defaultIfBlank(request.getId(), IDGenerator.nextStr());
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO ai_oauth_connection
                (id, provider_id, organization_id, project_id, user_id, authorization_uri, token_uri,
                 revoke_uri, client_id, client_secret_cipher, scopes, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIGURED', ?, ?)
                ON DUPLICATE KEY UPDATE authorization_uri=VALUES(authorization_uri), token_uri=VALUES(token_uri),
                  revoke_uri=VALUES(revoke_uri), client_id=VALUES(client_id), client_secret_cipher=VALUES(client_secret_cipher),
                  provider_id=VALUES(provider_id), organization_id=VALUES(organization_id), project_id=VALUES(project_id),
                  user_id=VALUES(user_id), scopes=VALUES(scopes), access_token_cipher=NULL, refresh_token_cipher=NULL,
                  expires_at=NULL, state_hash=NULL, state_expires_at=NULL, redirect_uri=NULL,
                  code_verifier_cipher=NULL, status='CONFIGURED', update_time=VALUES(update_time)
                """, id, request.getProviderId(), request.getOrganizationId(), request.getProjectId(), userId,
                request.getAuthorizationUri(), request.getTokenUri(), request.getRevokeUri(), request.getClientId(),
                EncryptUtils.aesEncrypt(request.getClientSecret()), request.getScopes(), now, now);
        aiAuditService.record(request.getProjectId(), request.getOrganizationId(), userId, id, "UPDATE",
                "AI_OAUTH_CONNECTION_CONFIGURE", "/ai/oauth/connection", "POST",
                Map.of("providerId", request.getProviderId()));
        return status(id, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> authorize(AiOAuthAuthorizeRequest request, String userId) {
        Map<String, Object> connection = requireConnection(request.getConnectionId(), userId);
        validateRedirectUri(request.getRedirectUri());
        String state = randomState();
        String codeVerifier = randomState();
        String codeChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DigestUtils.sha256(codeVerifier));
        jdbcTemplate.update("UPDATE ai_oauth_connection SET state_hash=?, state_expires_at=?, redirect_uri=?, code_verifier_cipher=?, update_time=? WHERE id=?",
                DigestUtils.sha256Hex(state), System.currentTimeMillis() + STATE_TTL_MS,
                request.getRedirectUri(), EncryptUtils.aesEncrypt(codeVerifier), System.currentTimeMillis(), request.getConnectionId());
        String url = UriComponentsBuilder.fromUriString((String) connection.get("authorization_uri"))
                .queryParam("response_type", "code")
                .queryParam("client_id", connection.get("client_id"))
                .queryParam("redirect_uri", request.getRedirectUri())
                .queryParam("scope", StringUtils.defaultString((String) connection.get("scopes")))
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
        return Map.of("authorizationUrl", url, "state", state);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> callback(AiOAuthCallbackRequest request) {
        String stateHash = DigestUtils.sha256Hex(request.getState());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_oauth_connection WHERE state_hash=? AND state_expires_at>=? FOR UPDATE",
                stateHash, System.currentTimeMillis());
        if (rows.size() != 1) throw new MSException("OAuth state 无效或已过期");
        Map<String, Object> connection = rows.get(0);
        if (!StringUtils.equals(request.getRedirectUri(), (String) connection.get("redirect_uri"))) {
            throw new MSException("OAuth redirect_uri 与授权请求不一致");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", request.getCode());
        form.add("redirect_uri", request.getRedirectUri());
        form.add("client_id", (String) connection.get("client_id"));
        form.add("client_secret", EncryptUtils.aesDecrypt(connection.get("client_secret_cipher")));
        String verifierCipher = (String) connection.get("code_verifier_cipher");
        if (StringUtils.isBlank(verifierCipher)) throw new MSException("OAuth PKCE verifier 缺失，请重新发起授权");
        form.add("code_verifier", EncryptUtils.aesDecrypt(verifierCipher));
        Map<?, ?> token = postTokenForm((String) connection.get("token_uri"), form);
        persistToken((String) connection.get("id"), token);
        aiAuditService.record((String) connection.get("project_id"), (String) connection.get("organization_id"),
                (String) connection.get("user_id"), (String) connection.get("id"), "UPDATE",
                "AI_OAUTH_AUTHORIZED", "/ai/oauth/callback", "POST", Map.of("providerId", connection.get("provider_id")));
        return status((String) connection.get("id"), (String) connection.get("user_id"));
    }

    @Transactional(rollbackFor = Exception.class)
    public String accessToken(String connectionId, String userId) {
        Map<String, Object> connection = requireConnection(connectionId, userId);
        Number expiresAt = (Number) connection.get("expires_at");
        if (expiresAt != null && expiresAt.longValue() <= System.currentTimeMillis() + REFRESH_SKEW_MS) {
            refresh(connection);
            connection = requireConnection(connectionId, userId);
        }
        String cipher = (String) connection.get("access_token_cipher");
        if (StringUtils.isBlank(cipher)) throw new MSException("OAuth 连接尚未授权");
        return EncryptUtils.aesDecrypt(cipher);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> forceRefresh(String connectionId, String userId) {
        Map<String, Object> connection = requireConnection(connectionId, userId);
        refresh(connection);
        aiAuditService.record((String) connection.get("project_id"), (String) connection.get("organization_id"),
                userId, connectionId, "UPDATE", "AI_OAUTH_REFRESHED", "/ai/oauth/" + connectionId + "/refresh",
                "POST", Map.of("providerId", connection.get("provider_id")));
        return status(connectionId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(String connectionId, String userId) {
        Map<String, Object> connection = requireConnection(connectionId, userId);
        String accessToken = StringUtils.isBlank((String) connection.get("access_token_cipher"))
                ? null : EncryptUtils.aesDecrypt(connection.get("access_token_cipher"));
        String revokeUri = (String) connection.get("revoke_uri");
        if (StringUtils.isNotBlank(revokeUri) && StringUtils.isNotBlank(accessToken)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", accessToken);
            form.add("client_id", (String) connection.get("client_id"));
            form.add("client_secret", EncryptUtils.aesDecrypt(connection.get("client_secret_cipher")));
            postRevokeForm(revokeUri, form);
        }
        jdbcTemplate.update("""
                UPDATE ai_oauth_connection SET access_token_cipher=NULL, refresh_token_cipher=NULL,
                  expires_at=NULL, status='REVOKED', state_hash=NULL, state_expires_at=NULL,
                  redirect_uri=NULL, code_verifier_cipher=NULL, update_time=? WHERE id=?
                """, System.currentTimeMillis(), connectionId);
        aiAuditService.record((String) connection.get("project_id"), (String) connection.get("organization_id"),
                userId, connectionId, "DELETE", "AI_OAUTH_REVOKED", "/ai/oauth/" + connectionId + "/revoke",
                "POST", Map.of("providerId", connection.get("provider_id")));
    }

    public Map<String, Object> status(String id, String userId) {
        Map<String, Object> row = requireConnection(id, userId);
        return Map.of(
                "id", row.get("id"), "providerId", row.get("provider_id"),
                "status", row.get("status"), "expiresAt", row.get("expires_at") == null ? 0L : row.get("expires_at"),
                "authorized", row.get("access_token_cipher") != null,
                "organizationId", StringUtils.defaultString((String) row.get("organization_id")),
                "projectId", StringUtils.defaultString((String) row.get("project_id")));
    }

    private void refresh(Map<String, Object> connection) {
        String refreshCipher = (String) connection.get("refresh_token_cipher");
        if (StringUtils.isBlank(refreshCipher)) throw new MSException("OAuth refresh token 不存在，请重新授权");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", EncryptUtils.aesDecrypt(refreshCipher));
        form.add("client_id", (String) connection.get("client_id"));
        form.add("client_secret", EncryptUtils.aesDecrypt(connection.get("client_secret_cipher")));
        persistToken((String) connection.get("id"), postTokenForm((String) connection.get("token_uri"), form));
    }

    private Map<?, ?> postTokenForm(String uri, MultiValueMap<String, String> form) {
        try {
            Map<?, ?> response = restClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            if (response == null || response.get("access_token") == null) throw new MSException("OAuth Token 响应缺少 access_token");
            return response;
        } catch (Exception ex) {
            throw new MSException("OAuth Provider 调用失败：" + sanitize(ex.getMessage()), ex);
        }
    }

    private void postRevokeForm(String uri, MultiValueMap<String, String> form) {
        try {
            restClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            throw new MSException("OAuth Provider 撤销失败：" + sanitize(ex.getMessage()), ex);
        }
    }

    private void persistToken(String id, Map<?, ?> token) {
        long expiresIn = token.get("expires_in") instanceof Number number ? number.longValue() : 3600L;
        String refresh = token.get("refresh_token") == null ? null : EncryptUtils.aesEncrypt(token.get("refresh_token"));
        jdbcTemplate.update("""
                UPDATE ai_oauth_connection SET access_token_cipher=?,
                  refresh_token_cipher=COALESCE(?, refresh_token_cipher), token_type=?, expires_at=?,
                  status='AUTHORIZED', state_hash=NULL, state_expires_at=NULL, redirect_uri=NULL,
                  code_verifier_cipher=NULL, update_time=? WHERE id=?
                """, EncryptUtils.aesEncrypt(token.get("access_token")), refresh,
                StringUtils.defaultIfBlank((String) token.get("token_type"), "Bearer"),
                System.currentTimeMillis() + expiresIn * 1000L, System.currentTimeMillis(), id);
    }

    private Map<String, Object> requireConnection(String id, String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_oauth_connection WHERE id=?", id);
        if (rows.size() != 1 || !canAccess(rows.get(0), userId)) {
            throw new MSException("OAuth 连接不存在或无权访问");
        }
        return rows.get(0);
    }

    private boolean canAccess(Map<String, Object> connection, String userId) {
        String projectId = (String) connection.get("project_id");
        if (StringUtils.isNotBlank(projectId)) {
            return permissionCheckService.userHasSourcePermission(userId, projectId,
                    PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG, UserRoleType.PROJECT.name());
        }
        String organizationId = (String) connection.get("organization_id");
        if (StringUtils.isNotBlank(organizationId)) {
            return permissionCheckService.userHasSourcePermission(userId, organizationId,
                    PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG, UserRoleType.ORGANIZATION.name());
        }
        return StringUtils.equals(userId, (String) connection.get("user_id"));
    }

    private void assertScopeAccess(String projectId, String organizationId, String userId) {
        if (StringUtils.isNotBlank(projectId)
                && !permissionCheckService.userHasSourcePermission(userId, projectId,
                PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG, UserRoleType.PROJECT.name())) {
            throw new MSException("无权配置该项目的 OAuth 连接");
        }
        if (StringUtils.isBlank(projectId) && StringUtils.isNotBlank(organizationId)
                && !permissionCheckService.userHasSourcePermission(userId, organizationId,
                PermissionConstants.FUNCTIONAL_CASE_AI_CONFIG, UserRoleType.ORGANIZATION.name())) {
            throw new MSException("无权配置该组织的 OAuth 连接");
        }
    }

    private void validateRemoteUri(String value) {
        AiRemoteEndpointValidator.validateHttps(value, "OAuth URI", false);
    }

    private void validateRedirectUri(String value) {
        try {
            URI uri = URI.create(value);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            boolean loopback = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost())
                    || (uri.getHost() != null && java.net.InetAddress.getByName(uri.getHost()).isLoopbackAddress()));
            if ((!secure && !loopback) || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            throw new MSException("OAuth redirect_uri 必须是 HTTPS 地址或本机回环地址");
        }
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sanitize(String message) {
        return StringUtils.defaultIfBlank(message, "unknown")
                .replaceAll("(?i)(token|secret|authorization|code)\\s*[:=]\\s*[^\\s,;]+", "$1=******");
    }
}
