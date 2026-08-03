package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentErrorCode;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenRateLimiter;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolRegistry;
import io.metersphere.sdk.exception.IResultCode;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentToken;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AgentMcpStreamableService {
    @Resource
    private AgentTokenRateLimiter agentTokenRateLimiter;
    @Resource
    private AgentIdempotencyService agentIdempotencyService;
    @Resource
    private AgentMcpToolRegistry agentMcpToolRegistry;

    public Map<String, Object> handle(Map<String, Object> request) {
        return handle(request, null);
    }

    /**
     * JSON-RPC Notification（无 id）：不返回响应体语义，由 Controller 映射为 202/空 body。
     */
    public boolean isNotification(Map<String, Object> request) {
        if (request == null) {
            return false;
        }
        if (request.containsKey("id") && request.get("id") != null) {
            return false;
        }
        String method = StringUtils.defaultString((String) request.get("method"));
        return method.startsWith("notifications/");
    }

    public void handleNotification(Map<String, Object> request) {
        String method = StringUtils.defaultString((String) request.get("method"));
        if ("notifications/initialized".equals(method) || method.startsWith("notifications/")) {
            return;
        }
        throw new MSException("Unsupported MCP notification: " + method);
    }

    public Map<String, Object> handle(Map<String, Object> request, String idempotencyKey) {
        Object id = request.get("id");
        String method = StringUtils.defaultString((String) request.get("method"));
        try {
            return switch (method) {
                case "initialize" -> response(id, initializeResult());
                case "notifications/initialized" -> {
                    // 带 id 的非标准客户端：返回空 result；无 id 应由 isNotification 短路
                    yield response(id, Map.of());
                }
                case "ping" -> response(id, Map.of("ok", true));
                case "tools/list" -> response(id, Map.of("tools", tools()));
                case "tools/call" -> response(id, callTool(asMap(request.get("params")), idempotencyKey));
                default -> error(id, -32601, "Unsupported MCP method: " + method, null);
            };
        } catch (MSException ex) {
            return error(id, -32001, ex.getMessage(), ex.getErrorCode());
        } catch (Exception ex) {
            return error(id, -32603, ex.getMessage(), null);
        }
    }

    private Map<String, Object> initializeResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "2025-03-26");
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of("name", "metersphere-agent", "version", "1.0.0"));
        return result;
    }

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (AgentMcpToolHandler handler : agentMcpToolRegistry.all()) {
            tools.add(tool(handler.name(), handler.description(), handler.requiredScope(),
                    handler.inputSchema(), handler.annotations()));
        }
        return tools;
    }

    private Map<String, Object> callTool(Map<String, Object> params, String idempotencyKey) {
        String name = StringUtils.defaultString((String) params.get("name"));
        Map<String, Object> arguments = asMap(params.get("arguments"));
        AgentToken token = AgentTokenContext.get();
        if (token != null && !agentTokenRateLimiter.tryAcquireTool(token.getId(), name)) {
            throw new MSException("Agent MCP tool requests are too frequent. Please retry later.");
        }
        String effectiveIdempotencyKey = StringUtils.defaultIfBlank(idempotencyKey, (String) arguments.get("requestId"));
        if (StringUtils.isNotBlank(effectiveIdempotencyKey) && agentMcpToolRegistry.isWriteTool(name)) {
            Optional<Map<String, Object>> cached = agentIdempotencyService.findCachedResponse(name, effectiveIdempotencyKey, arguments);
            if (cached.isPresent()) {
                return cached.get();
            }
            Map<String, Object> response = callToolInternal(name, arguments);
            agentIdempotencyService.save(name, effectiveIdempotencyKey, arguments, response);
            return response;
        }
        return callToolInternal(name, arguments);
    }

    private Map<String, Object> callToolInternal(String name, Map<String, Object> arguments) {
        AgentMcpToolHandler handler = agentMcpToolRegistry.find(name)
                .orElseThrow(() -> new MSException("Unsupported MCP tool: " + name));
        AgentScopeAssert.assertScope(handler.requiredScope());
        return toolResponse(handler.execute(arguments));
    }

    private Map<String, Object> toolResponse(Object result) {
        return Map.of("content", List.of(Map.of("type", "text", "text", JSON.toJSONString(result))));
    }

    private Map<String, Object> tool(String name, String description, String scope,
                                     Map<String, Object> inputSchema, Map<String, Object> annotations) {
        Map<String, Object> mergedAnnotations = new LinkedHashMap<>();
        if (annotations != null) {
            mergedAnnotations.putAll(annotations);
        }
        mergedAnnotations.put("scope", scope);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        tool.put("annotations", mergedAnnotations);
        return tool;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message, IResultCode errorCode) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", StringUtils.defaultString(message));
        if (errorCode != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", errorCode instanceof AgentErrorCode agentCode ? agentCode.name() : errorCode.getMessage());
            data.put("httpCode", errorCode.getCode());
            if (StringUtils.isNotBlank(message)) {
                data.put("detail", message);
            }
            error.put("data", data);
        }
        response.put("error", error);
        return response;
    }
}
