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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentMcpStreamableService.class);
    private static final String TRIGGER_TOOL_PREFIX = "metersphere.execution.trigger.";
    @Resource
    private AgentTokenRateLimiter agentTokenRateLimiter;
    @Resource
    private AgentIdempotencyService agentIdempotencyService;
    @Resource private AgentGenericMcpWriteService genericWrites;
    @Resource
    private AgentMcpToolRegistry agentMcpToolRegistry;
    @Resource
    private AgentSafeErrorMapper safeErrorMapper;
    @Resource
    private AgentExecLogService execLogService;

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
                case "initialize" -> response(id, initializeResult(asMap(request.get("params"))));
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
            var safe = safeErrorMapper.toApiError(ex, null);
            LOG.warn("MCP operation failed traceId={} code={}",safe.getTraceId(),safe.getCode(),ex);
            return safeError(id, -32001, safe.getMessage(), safe.getCode(), safe.getTraceId());
        } catch (Exception ex) {
            var safe = safeErrorMapper.toApiError(ex, null);
            LOG.warn("MCP operation failed traceId={} code={}",safe.getTraceId(),safe.getCode(),ex);
            return safeError(id, -32603, safe.getMessage(), safe.getCode(), safe.getTraceId());
        }
    }

    private Map<String, Object> initializeResult(Map<String, Object> params) {
        String requested = String.valueOf(params.getOrDefault("protocolVersion", "2025-03-26"));
        if (!List.of("2025-03-26", "2024-11-05").contains(requested)) requested = "2025-03-26";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", requested);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of("name", "metersphere-agent", "version", "1.0.0"));
        return result;
    }

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        AgentToken token = AgentTokenContext.get();
        for (AgentMcpToolHandler handler : agentMcpToolRegistry.all()) {
            if (!io.metersphere.agent.tool.AgentMcpToolExposure.discoverable(handler.name())) continue;
            if (token == null || !AgentScopeAssert.hasScope(token.getScopes(), handler.requiredScope())) continue;
            tools.add(tool(handler.name(), handler.description(), handler.requiredScope(),
                    schemaWithIdempotency(handler), handler.outputSchema(), handler.annotations()));
        }
        return tools;
    }

    private static boolean bypassesGenericIdempotency(String name) {
        return java.util.Set.of(
                "metersphere.functional.export.start",
                "metersphere.functional.export.stop",
                "metersphere.functional.export.retry",
                "metersphere.test_plan.create",
                "metersphere.test_plan.associate_cases",
                "metersphere.bug.create",
                "metersphere.bug.import.commit",
                "metersphere.bug.export",
                "metersphere.functional.execution.evidence.upload",
                "metersphere.functional.execution.submit",
                "metersphere.case_review.create",
                "metersphere.case_review.associate_cases",
                "metersphere.functional.comment.create",
                "metersphere.functional.comment.update",
                "metersphere.functional.case.update",
                "metersphere.functional.module.update",
                "metersphere.functional.module.move",
                "metersphere.functional.attachment.upload.prepare",
                "metersphere.functional.attachment.upload.complete",
                "metersphere.functional.attachment.import_url",
                "metersphere.bug.attachment.import_url",
                "metersphere.functional.execution.evidence.import_url",
                "metersphere.functional.import.precheck",
                "metersphere.functional.import.commit",
                "metersphere.functional.import.followup.retry",
                "metersphere.functional.import.stop",
                "metersphere.functional.import.retry",
                "metersphere.functional.snapshot.create",
                "metersphere.functional.snapshot.release",
                "metersphere.functional.case.delete_preview",
                "metersphere.functional.case.delete",
                "metersphere.functional.case.batch_delete",
                "metersphere.functional.case.restore",
                "metersphere.functional.comment.delete",
                "metersphere.functional.archive.prepare",
                "metersphere.functional.archive.retry",
                "metersphere.functional.archive.data").contains(name);
    }

    private Map<String, Object> schemaWithIdempotency(AgentMcpToolHandler handler) {
        if (!agentMcpToolRegistry.isWriteTool(handler.name()) || bypassesGenericIdempotency(handler.name())) return handler.inputSchema();
        Map<String, Object> schema = new LinkedHashMap<>(handler.inputSchema());
        Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) schema.getOrDefault("properties", Map.of()));
        properties.putIfAbsent("requestId", io.metersphere.agent.tool.AgentMcpToolSchemas.requestId());
        List<String> required = new ArrayList<>((List<String>) schema.getOrDefault("required", List.of()));
        if (!required.contains("requestId")) required.add("requestId");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> callTool(Map<String, Object> params, String idempotencyKey) {
        if(!(params.get("name") instanceof String) || (params.get("arguments")!=null && !(params.get("arguments") instanceof Map)) || (idempotencyKey!=null && !idempotencyKey.matches("[A-Za-z0-9_.:-]{1,128}")))throw new MSException("AGENT_VALIDATION_ERROR");
        String name = (String)params.get("name");
        Map<String, Object> arguments = asMap(params.get("arguments"));
        if (io.metersphere.agent.tool.AgentMcpToolExposure.legacyResultWrite(name)) throw new MSException("QUALITY_LEGACY_SUBMIT_FORBIDDEN");
        if (name.startsWith(TRIGGER_TOOL_PREFIX)) {
            if(execLogService!=null)execLogService.audit("MCP_TRIGGER_TOOL_FORBIDDEN",name,"Direct personal MCP trigger call rejected");
            throw new MSException("MCP_TOOL_FORBIDDEN");
        }
        AgentToken token = AgentTokenContext.get();
        if (token != null && !agentTokenRateLimiter.tryAcquireTool(token.getId(), name)) {
            throw new MSException("Agent MCP tool requests are too frequent. Please retry later.");
        }
        Object rawKey=arguments.get("requestId");
        if(rawKey!=null && (!(rawKey instanceof String) || !((String)rawKey).matches("[A-Za-z0-9_.:-]{1,128}")))throw new MSException("AGENT_VALIDATION_ERROR");
        String argumentKey=(String)rawKey;
        if(StringUtils.isNotBlank(idempotencyKey) && StringUtils.isNotBlank(argumentKey) && !idempotencyKey.equals(argumentKey))throw new MSException("IDEMPOTENCY_CONFLICT");
        String effectiveIdempotencyKey = StringUtils.defaultIfBlank(idempotencyKey, argumentKey);
        if(StringUtils.isNotBlank(effectiveIdempotencyKey)) {arguments=new LinkedHashMap<>(arguments);arguments.put("requestId",effectiveIdempotencyKey);}
        // These protocols reauthorize their resource on every call. Upload/job/preview/run IDs
        // govern transitions and replay in their own transactions; a generic response cache
        // would bypass current ownership, expiry and state checks. Snapshot/preview creation
        // intentionally creates a new bounded temporary read/confirmation resource.
        if (bypassesGenericIdempotency(name)) return callToolInternal(name, arguments);
        // Runtime protocols validate current lease/state on every request. Never persist lease
        // credentials or replay an expired capability through the generic response cache.
        if (java.util.Set.of("metersphere.task.claim", "metersphere.task.lease.heartbeat",
                "metersphere.task.release", "metersphere.execution.events.batch",
                "metersphere.execution.step.submit", "metersphere.execution.complete",
                "metersphere.execution.fail", "metersphere.execution.checkpoint.create",
                "metersphere.human_request.create", "metersphere.artifact.prepare",
                "metersphere.artifact.commit").contains(name)) {
            return callToolInternal(name, arguments);
        }
        // Recheck scope even when a previous result is cached for this token.
        agentMcpToolRegistry.find(name).ifPresent(handler -> AgentScopeAssert.assertScope(handler.requiredScope()));
        if (agentMcpToolRegistry.isWriteTool(name) && StringUtils.isBlank(effectiveIdempotencyKey)) {
            throw new MSException("IDEMPOTENCY_KEY_REQUIRED");
        }
        if(AgentGenericMcpWriteService.handles(name)) {
            Map<String,Object> original=arguments;
            return genericWrites.execute(name,original,()->callToolInternal(name,original));
        }
        // New business writers must opt into an atomic/resource-owned protocol; never fall back to a scope-only cache.
        if(agentMcpToolRegistry.isWriteTool(name) && !(name.startsWith("metersphere.execution.")||name.startsWith("metersphere.task.")||name.startsWith("metersphere.human_request.")||name.startsWith("metersphere.artifact.")))throw new MSException("MCP_TOOL_PROTOCOL_NOT_CONFIGURED");
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
        if (name.startsWith(TRIGGER_TOOL_PREFIX)) {
            throw new MSException("MCP_TOOL_FORBIDDEN");
        }
        AgentMcpToolHandler handler = agentMcpToolRegistry.find(name)
                .orElseThrow(() -> new MSException("MCP_TOOL_NOT_FOUND"));
        AgentScopeAssert.assertScope(handler.requiredScope());
        return toolResponse(handler.execute(arguments));
    }

    private Map<String, Object> toolResponse(Object result) {
        if(result instanceof io.metersphere.agent.tool.AgentMcpImageResult image) {
            return Map.of("content",List.of(Map.of("type","image","data",image.data(),"mimeType",image.mimeType()),
                    Map.of("type","text","text",JSON.toJSONString(image.metadata()))),"structuredContent",Map.of("result",image.metadata()));
        }
        Object safeResult = result == null ? Map.of() : result;
        return Map.of("content", List.of(Map.of("type", "text", "text", JSON.toJSONString(safeResult))),
                "structuredContent", Map.of("result", safeResult));
    }

    private Map<String, Object> tool(String name, String description, String scope,
                                     Map<String, Object> inputSchema, Map<String, Object> outputSchema,
                                     Map<String, Object> annotations) {
        Map<String, Object> mergedAnnotations = new LinkedHashMap<>();
        if (annotations != null) {
            mergedAnnotations.putAll(annotations);
        }
        mergedAnnotations.put("scope", scope);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        tool.put("outputSchema", outputSchema);
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

    private Map<String, Object> safeError(Object id, int rpcCode, String message, String code, String traceId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0"); response.put("id", id);
        response.put("error", Map.of("code", rpcCode, "message", message,
                "data", Map.of("code", code, "traceId", traceId)));
        return response;
    }
}
