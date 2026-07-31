package io.metersphere.agent.security;

import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentTokenMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AgentTokenService {
    private static final BCryptPasswordEncoder TOKEN_SECRET_ENCODER = new BCryptPasswordEncoder(12);

    @Resource
    private AgentTokenMapper agentTokenMapper;

    public AgentToken validateBearerToken(String authorization) {
        String token = extractBearerToken(authorization);
        return validateRawToken(token);
    }

    public AgentToken validateApiKey(String apiKey) {
        return validateRawToken(apiKey);
    }

    public AgentToken validateRequest(HttpServletRequest request) {
        AgentToken bearerToken = validateBearerToken(request.getHeader("Authorization"));
        if (bearerToken != null) {
            return bearerToken;
        }
        return validateApiKey(request.getHeader("X-API-Key"));
    }

    public AgentToken validateRawToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        AgentToken agentToken = validateV2Token(token);
        if (agentToken == null) {
            agentToken = agentTokenMapper.selectByTokenHash(DigestUtils.sha256Hex(token));
        }
        if (agentToken == null) {
            return null;
        }
        if (agentToken.getExpireTime() != null && agentToken.getExpireTime() < System.currentTimeMillis()) {
            return null;
        }
        return agentToken;
    }

    public boolean hasScope(AgentToken token, String requiredScope) {
        if (token == null || StringUtils.isBlank(requiredScope)) {
            return false;
        }
        return AgentScopeAssert.hasScope(StringUtils.defaultString(token.getScopes()), requiredScope);
    }

    private String extractBearerToken(String authorization) {
        if (StringUtils.isBlank(authorization)) {
            return null;
        }
        String prefix = "Bearer ";
        if (!StringUtils.startsWithIgnoreCase(authorization, prefix)) {
            return null;
        }
        return StringUtils.trim(authorization.substring(prefix.length()));
    }

    private AgentToken validateV2Token(String rawToken) {
        if (!StringUtils.startsWith(rawToken, "msat_")) {
            return null;
        }
        String[] parts = StringUtils.split(rawToken, '_');
        if (parts == null || parts.length != 3 || !"msat".equals(parts[0])) {
            return null;
        }
        AgentToken token = agentTokenMapper.selectByPublicId(parts[1]);
        if (token == null || !matchesV2Secret(parts[2], token.getSecretHash())) {
            return null;
        }
        return token;
    }

    private boolean matchesV2Secret(String rawSecret, String storedHash) {
        if (StringUtils.isAnyBlank(rawSecret, storedHash)) {
            return false;
        }
        if (StringUtils.startsWith(storedHash, "$2a$")
                || StringUtils.startsWith(storedHash, "$2b$")
                || StringUtils.startsWith(storedHash, "$2y$")) {
            return TOKEN_SECRET_ENCODER.matches(rawSecret, storedHash);
        }
        return StringUtils.equals(storedHash, DigestUtils.sha256Hex(rawSecret));
    }
}
