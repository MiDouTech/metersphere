package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentTokenMapper;
import jakarta.annotation.Resource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AgentTokenService {
    @Resource
    private AgentTokenMapper agentTokenMapper;

    public AgentToken validateBearerToken(String authorization) {
        String token = extractBearerToken(authorization);
        if (StringUtils.isBlank(token)) {
            return null;
        }
        AgentToken agentToken = agentTokenMapper.selectByTokenHash(DigestUtils.sha256Hex(token));
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
        String scopes = StringUtils.defaultString(token.getScopes());
        return StringUtils.contains(scopes, AgentTokenScope.FUNCTIONAL_ALL)
                || StringUtils.contains(scopes, requiredScope);
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
}
