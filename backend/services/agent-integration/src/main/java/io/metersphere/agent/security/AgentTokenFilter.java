package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.sdk.constants.SessionConstants;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.utils.SessionUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.web.filter.authc.AnonymousFilter;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.stereotype.Component;

@Component
public class AgentTokenFilter extends AnonymousFilter {
    private final AgentTokenService agentTokenService;

    public AgentTokenFilter(AgentTokenService agentTokenService) {
        this.agentTokenService = agentTokenService;
    }

    public static boolean isAgentTokenCall(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return StringUtils.isNotBlank(authorization)
                && StringUtils.startsWithIgnoreCase(authorization, "Bearer ")
                && StringUtils.contains(authorization, AgentConstants.TOKEN_PREFIX);
    }

    @Override
    protected boolean onPreHandle(ServletRequest request, ServletResponse response, Object mappedValue) {
        HttpServletRequest httpRequest = WebUtils.toHttp(request);
        if (!isAgentTokenCall(httpRequest)) {
            return true;
        }
        AgentToken token = agentTokenService.validateBearerToken(httpRequest.getHeader("Authorization"));
        if (token != null && StringUtils.isNotBlank(token.getUserId())) {
            if (!SecurityUtils.getSubject().isAuthenticated()) {
                SecurityUtils.getSubject().login(new UsernamePasswordToken(token.getUserId(), "no_pass"));
            }
            AgentTokenContext.set(token);
            String projectId = resolveProjectId(httpRequest, token);
            if (StringUtils.isNotBlank(projectId)) {
                SessionUtils.setCurrentProjectId(projectId);
            }
            return true;
        }
        ((HttpServletResponse) response).setHeader(SessionConstants.AUTHENTICATION_STATUS, SessionConstants.AUTHENTICATION_INVALID);
        return true;
    }

    @Override
    protected void postHandle(ServletRequest request, ServletResponse response) {
        if (isAgentTokenCall(WebUtils.toHttp(request)) && SecurityUtils.getSubject().isAuthenticated()) {
            SecurityUtils.getSubject().logout();
        }
        AgentTokenContext.clear();
        SessionUtils.clearCurrentProjectId();
    }

    private String resolveProjectId(HttpServletRequest request, AgentToken token) {
        String projectId = request.getHeader(AgentConstants.HEADER_PROJECT);
        if (StringUtils.isBlank(projectId)) {
            projectId = request.getHeader(AgentConstants.HEADER_PROJECT_LEGACY);
        }
        if (StringUtils.isBlank(projectId)) {
            projectId = token.getProjectId();
        }
        return projectId;
    }
}
