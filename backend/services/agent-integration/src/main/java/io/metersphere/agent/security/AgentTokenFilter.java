package io.metersphere.agent.security;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.sdk.constants.SessionConstants;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.utils.SessionUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.web.filter.authc.AnonymousFilter;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AgentTokenFilter extends AnonymousFilter {
    private final AgentTokenService agentTokenService;
    private final AgentTokenRateLimiter agentTokenRateLimiter;

    public AgentTokenFilter(AgentTokenService agentTokenService, AgentTokenRateLimiter agentTokenRateLimiter) {
        this.agentTokenService = agentTokenService;
        this.agentTokenRateLimiter = agentTokenRateLimiter;
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
            boolean searchApi = AgentTokenRateLimiter.isSearchApi(httpRequest.getRequestURI());
            if (!agentTokenRateLimiter.tryAcquire(token.getId(), searchApi)) {
                writeJsonError(WebUtils.toHttp(response), 429, searchRateLimitMessage(searchApi));
                return false;
            }
            String projectId;
            try {
                projectId = resolveProjectId(httpRequest, token);
            } catch (ProjectAccessDeniedException ex) {
                writeJsonError(WebUtils.toHttp(response), 403, ex.getMessage());
                return false;
            }
            if (!SecurityUtils.getSubject().isAuthenticated()) {
                SecurityUtils.getSubject().login(new UsernamePasswordToken(token.getUserId(), "no_pass"));
            }
            AgentTokenContext.set(token);
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

    /**
     * 解析请求项目：Header 优先；单项目白名单可省略 Header；非空白名单外项目返回 403。
     */
    String resolveProjectId(HttpServletRequest request, AgentToken token) {
        String projectId = request.getHeader(AgentConstants.HEADER_PROJECT);
        if (StringUtils.isBlank(projectId)) {
            projectId = request.getHeader(AgentConstants.HEADER_PROJECT_LEGACY);
        }
        List<String> allowed = AgentTokenProjectAccess.parseProjectIds(token);
        if (StringUtils.isBlank(projectId)) {
            if (CollectionUtils.isNotEmpty(allowed) && allowed.size() == 1) {
                return allowed.get(0);
            }
            return AgentTokenProjectAccess.primaryProjectId(allowed);
        }
        if (!AgentTokenProjectAccess.allows(token, projectId)) {
            throw new ProjectAccessDeniedException("Agent Token 无权访问项目: " + projectId);
        }
        return projectId;
    }

    private String searchRateLimitMessage(boolean searchApi) {
        return searchApi
                ? "Agent 检索过于频繁，请降低轮询频率（检索限 "
                + AgentConstants.SEARCH_RATE_LIMIT_PER_MINUTE + " 次/分钟，间隔≥"
                + AgentConstants.SEARCH_MIN_INTERVAL_MS + "ms；pageSize≤"
                + AgentConstants.MAX_PAGE_SIZE + "）"
                : "Agent Token 请求过于频繁，请稍后重试（限 "
                + AgentConstants.RATE_LIMIT_PER_MINUTE + " 次/分钟）";
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            String safe = StringUtils.defaultString(message).replace("\"", "'");
            response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + safe + "\"}");
            response.getWriter().flush();
        } catch (Exception ignored) {
            // ignore write failure
        }
    }

    static final class ProjectAccessDeniedException extends RuntimeException {
        ProjectAccessDeniedException(String message) {
            super(message);
        }
    }
}
