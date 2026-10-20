package io.metersphere.agent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTokenFilterTests {
    @Test
    void embeddedPrefixIsNotAnAgentCredential() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt_msat_suffix");
        Assertions.assertFalse(AgentTokenFilter.isAgentTokenCall(request));
    }

    @Test
    void managementRejectsPersonalCredentialBeforeAuthentication() throws Exception {
        var filter = new AgentTokenFilter(null, null, null);
        for (String path : new String[]{"/personal/agent-tokens", "/api/admin/agent-tokens/one/revoke",
                "/api/personal/agent-package/manifest", "/admin/agent-policy"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader("Authorization", "Bearer msat_example_secret");
            var response = new MockHttpServletResponse();
            Assertions.assertFalse(filter.onPreHandle(request, response, null));
            Assertions.assertEquals(403, response.getStatus());
        }
    }

    @Test
    void missingAndInvalidMcpCredentialsCannotFallBackToSession() {
        var service = mock(AgentTokenService.class);
        var filter = new AgentTokenFilter(service, null, null);
        for (String value : new String[]{"", "Bearer msat_invalid"}) {
            var request = new MockHttpServletRequest("POST", "/api/mcp");
            if (!value.isEmpty()) request.addHeader("Authorization", value);
            var response = new MockHttpServletResponse();
            Assertions.assertFalse(filter.onPreHandle(request, response, null));
            Assertions.assertEquals(401, response.getStatus());
        }
    }

    @Test
    void dualHeadersAreRejectedBeforeSecretValidation() {
        var request = new MockHttpServletRequest("POST", "/api/mcp");
        request.addHeader("Authorization", "Bearer msat_example_secret");
        request.addHeader("X-API-Key", "msat_another_secret");
        var response = new MockHttpServletResponse();
        Assertions.assertFalse(new AgentTokenFilter(null, null, null).onPreHandle(request, response, null));
        Assertions.assertEquals(401, response.getStatus());
    }

    @Test
    void webManagementContinuesToExistingSessionAuthentication() {
        var request = new MockHttpServletRequest("GET", "/api/personal/agent-tokens");
        Assertions.assertTrue(new AgentTokenFilter(null, null, null)
                .onPreHandle(request, new MockHttpServletResponse(), null));
    }

    @Test
    void exceptionCompletionClearsAgentContextWithoutLoggingOutExistingSession() {
        var request = new MockHttpServletRequest("POST", "/api/mcp");
        request.setAttribute(AgentTokenFilter.ATTR_AGENT_LOGIN_ESTABLISHED, Boolean.FALSE);
        AgentTokenContext.set(new io.metersphere.system.domain.AgentToken());
        new AgentTokenFilter(null, null, null).afterCompletion(request, new MockHttpServletResponse(),
                new IllegalStateException("fixture failure"));
        Assertions.assertNull(AgentTokenContext.get());
    }

    @Test
    void isAgentTokenCallShouldDetectBearerMsatToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer msat_demo_token_for_local_testing_01");

        Assertions.assertTrue(AgentTokenFilter.isAgentTokenCall(request));
    }

    @Test
    void isAgentTokenCallShouldRejectMissingBearerPrefix() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("msat_demo_token_for_local_testing_01");

        Assertions.assertFalse(AgentTokenFilter.isAgentTokenCall(request));
    }

    @Test
    void isAgentTokenCallShouldRejectBlankAuthorization() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("");

        Assertions.assertFalse(AgentTokenFilter.isAgentTokenCall(request));
    }

    @Test
    void isAgentTokenCallShouldRejectNonMsatBearer() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer other_token_scheme");

        Assertions.assertFalse(AgentTokenFilter.isAgentTokenCall(request));
    }

    @Test
    void isMcpGetWithoutSseShouldMatchApiMcpGet() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/api/mcp");

        Assertions.assertTrue(AgentTokenFilter.isMcpStreamableEndpoint(request));
        Assertions.assertTrue(AgentTokenFilter.isMcpGetWithoutSse(request));
    }

    @Test
    void isMcpGetWithoutSseShouldMatchStrippedMcpGet() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/mcp");

        Assertions.assertTrue(AgentTokenFilter.isMcpGetWithoutSse(request));
    }

    @Test
    void isMcpGetWithoutSseShouldIgnorePost() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");

        Assertions.assertFalse(AgentTokenFilter.isMcpGetWithoutSse(request));
    }

    @Test
    void loginAttributeConstantShouldBeStable() {
        Assertions.assertEquals("AGENT_TOKEN_LOGIN_ESTABLISHED", AgentTokenFilter.ATTR_AGENT_LOGIN_ESTABLISHED);
    }
}
