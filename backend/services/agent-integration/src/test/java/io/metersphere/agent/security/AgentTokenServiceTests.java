package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentTokenMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTokenServiceTests {

    private static final String RAW_TOKEN = "msat_demo_token_for_local_testing_01";

    @InjectMocks
    private AgentTokenService agentTokenService;

    @Mock
    private AgentTokenMapper agentTokenMapper;

    @Test
    void validBearerTokenShouldPassValidation() {
        AgentToken token = buildToken(AgentTokenScope.FUNCTIONAL_ALL, null);
        when(agentTokenMapper.selectByTokenHash(eq(DigestUtils.sha256Hex(RAW_TOKEN)))).thenReturn(token);

        AgentToken validated = agentTokenService.validateBearerToken("Bearer " + RAW_TOKEN);
        Assertions.assertNotNull(validated);
        Assertions.assertTrue(agentTokenService.hasScope(validated, AgentTokenScope.FUNCTIONAL_SUBMIT));
    }

    @Test
    void readScopeShouldNotAllowSubmit() {
        AgentToken token = buildToken(AgentTokenScope.FUNCTIONAL_READ, null);

        Assertions.assertFalse(agentTokenService.hasScope(token, AgentTokenScope.FUNCTIONAL_SUBMIT));
        Assertions.assertTrue(agentTokenService.hasScope(token, AgentTokenScope.FUNCTIONAL_READ));
    }

    @Test
    void expiredTokenShouldBeRejected() {
        AgentToken token = buildToken(AgentTokenScope.FUNCTIONAL_ALL, System.currentTimeMillis() - 1000);
        when(agentTokenMapper.selectByTokenHash(eq(DigestUtils.sha256Hex(RAW_TOKEN)))).thenReturn(token);

        Assertions.assertNull(agentTokenService.validateBearerToken("Bearer " + RAW_TOKEN));
    }

    @Test
    void invalidTokenShouldBeRejected() {
        when(agentTokenMapper.selectByTokenHash(eq(DigestUtils.sha256Hex("msat_invalid")))).thenReturn(null);

        Assertions.assertNull(agentTokenService.validateBearerToken("Bearer msat_invalid"));
        Assertions.assertNull(agentTokenService.validateBearerToken(null));
    }

    private AgentToken buildToken(String scopes, Long expireTime) {
        AgentToken token = new AgentToken();
        token.setId("token-001");
        token.setUserId("admin");
        token.setScopes(scopes);
        token.setExpireTime(expireTime);
        token.setEnable(true);
        return token;
    }
}
