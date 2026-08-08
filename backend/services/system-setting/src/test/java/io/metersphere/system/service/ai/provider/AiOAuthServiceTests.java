package io.metersphere.system.service.ai.provider;

import io.metersphere.sdk.util.EncryptUtils;
import io.metersphere.system.dto.request.ai.AiOAuthCallbackRequest;
import io.metersphere.system.dto.request.ai.AiOAuthAuthorizeRequest;
import io.metersphere.system.service.PermissionCheckService;
import io.metersphere.system.service.ai.AiAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.metersphere.sdk.exception.MSException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiOAuthServiceTests {
    private AiOAuthService service;
    private JdbcTemplate jdbc;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiOAuthService(builder);
        jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "aiAuditService", mock(AiAuditService.class));
        ReflectionTestUtils.setField(service, "permissionCheckService", mock(PermissionCheckService.class));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @Test
    void authorizeBindsStateRedirectAndPkceS256Challenge() {
        Map<String, Object> configured = connection("CONFIGURED");
        configured.put("authorization_uri", "https://provider.example.com/authorize");
        configured.put("scopes", "openid profile");
        when(jdbc.queryForList("SELECT * FROM ai_oauth_connection WHERE id=?", "connection-1"))
                .thenReturn(List.of(configured));
        AiOAuthAuthorizeRequest request = new AiOAuthAuthorizeRequest();
        request.setConnectionId("connection-1");
        request.setRedirectUri("https://metersphere.example.com/oauth/callback");

        Map<String, String> result = service.authorize(request, "user-1");

        assertTrue(result.get("authorizationUrl").contains("code_challenge_method=S256"));
        assertTrue(result.get("authorizationUrl").contains("code_challenge="));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("code_verifier_cipher"), any(Object[].class));
    }

    @Test
    void callbackExchangesCodePersistsEncryptedTokensAndConsumesState() {
        Map<String, Object> pending = connection("CONFIGURED");
        pending.put("redirect_uri", "https://metersphere.example.com/oauth/callback");
        pending.put("code_verifier_cipher", EncryptUtils.aesEncrypt("pkce-verifier"));
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(pending), List.of(authorizedConnection()));
        server.expect(requestTo("https://provider.example.com/token"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=authorization_code"),
                        org.hamcrest.Matchers.containsString("code=authorization-code"),
                        org.hamcrest.Matchers.containsString("code_verifier=pkce-verifier"))))
                .andRespond(withSuccess("""
                        {"access_token":"access-secret","refresh_token":"refresh-secret",
                         "token_type":"Bearer","expires_in":3600}
                        """, MediaType.APPLICATION_JSON));

        AiOAuthCallbackRequest request = new AiOAuthCallbackRequest();
        request.setState("secure-state");
        request.setCode("authorization-code");
        request.setRedirectUri("https://metersphere.example.com/oauth/callback");
        Map<String, Object> status = service.callback(request);

        assertTrue((Boolean) status.get("authorized"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("access_token_cipher"), any(Object[].class));
        server.verify();
    }

    @Test
    void forceRefreshUsesRefreshGrantAndKeepsRotatedTokenEncrypted() {
        Map<String, Object> current = authorizedConnection();
        when(jdbc.queryForList("SELECT * FROM ai_oauth_connection WHERE id=?", "connection-1"))
                .thenReturn(List.of(current), List.of(current));
        server.expect(requestTo("https://provider.example.com/token"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("grant_type=refresh_token"),
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-secret"))))
                .andRespond(withSuccess("{\"access_token\":\"rotated-access\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> status = service.forceRefresh("connection-1", "user-1");

        assertEquals("AUTHORIZED", status.get("status"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("access_token_cipher"), any(Object[].class));
        server.verify();
    }

    @Test
    void revokeAcceptsStandardEmptySuccessResponseAndClearsLocalTokens() {
        Map<String, Object> current = authorizedConnection();
        when(jdbc.queryForList("SELECT * FROM ai_oauth_connection WHERE id=?", "connection-1"))
                .thenReturn(List.of(current));
        server.expect(requestTo("https://provider.example.com/revoke"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("token=access-secret")))
                .andRespond(withNoContent());

        service.revoke("connection-1", "user-1");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status='REVOKED'"), any(Object[].class));
        server.verify();
    }

    @Test
    void personalConnectionCannotBeReadByAnotherUser() {
        when(jdbc.queryForList("SELECT * FROM ai_oauth_connection WHERE id=?", "connection-1"))
                .thenReturn(List.of(authorizedConnection()));

        assertThrows(MSException.class, () -> service.status("connection-1", "user-2"));
    }

    private Map<String, Object> connection(String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "connection-1");
        row.put("provider_id", "provider-1");
        row.put("user_id", "user-1");
        row.put("token_uri", "https://provider.example.com/token");
        row.put("revoke_uri", "https://provider.example.com/revoke");
        row.put("client_id", "client-1");
        row.put("client_secret_cipher", EncryptUtils.aesEncrypt("client-secret"));
        row.put("status", status);
        return row;
    }

    private Map<String, Object> authorizedConnection() {
        Map<String, Object> row = connection("AUTHORIZED");
        row.put("access_token_cipher", EncryptUtils.aesEncrypt("access-secret"));
        row.put("refresh_token_cipher", EncryptUtils.aesEncrypt("refresh-secret"));
        row.put("expires_at", System.currentTimeMillis() + 3_600_000L);
        return row;
    }
}
