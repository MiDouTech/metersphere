package io.metersphere.system.security;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.controller.handler.ResultHolder;
import io.metersphere.system.controller.handler.result.MsHttpResultCode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Returns JSON 401 for unauthenticated API requests instead of forwarding to index.html.
 */
public class MsAuthenticationFilter extends FormAuthenticationFilter {

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        if (isApiRequest(WebUtils.toHttp(request))) {
            writeUnauthorized(WebUtils.toHttp(response));
            return false;
        }
        return super.onAccessDenied(request, response);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (StringUtils.equalsAny(uri, "/", "/login", "/403")) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (StringUtils.isNotBlank(accept) && accept.contains("application/json")) {
            return true;
        }
        String contentType = request.getContentType();
        if (StringUtils.isNotBlank(contentType) && contentType.contains("application/json")) {
            return true;
        }
        return !StringUtils.containsAny(uri, ".html", ".js", ".css", ".ico", ".png", ".svg", ".woff", ".woff2");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        ResultHolder result = ResultHolder.error(
                MsHttpResultCode.UNAUTHORIZED.getCode(),
                MsHttpResultCode.UNAUTHORIZED.getMessage()
        );
        response.getWriter().write(JSON.toJSONString(result));
    }
}
