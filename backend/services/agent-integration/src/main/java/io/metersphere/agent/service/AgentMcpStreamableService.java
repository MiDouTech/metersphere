package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugSearchRequest;
import io.metersphere.agent.dto.AgentBugUpdateRequest;
import io.metersphere.agent.dto.AgentCaseBatchCreateRequest;
import io.metersphere.agent.dto.AgentCaseCreateRequest;
import io.metersphere.agent.dto.AgentCaseReviewAssociateRequest;
import io.metersphere.agent.dto.AgentCaseReviewCreateRequest;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.dto.AgentModuleCreateRequest;
import io.metersphere.agent.dto.AgentProjectAddMembersRequest;
import io.metersphere.agent.dto.AgentProjectCreateRequest;
import io.metersphere.agent.dto.AgentProjectSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanAssociateRequest;
import io.metersphere.agent.dto.AgentTestPlanCreateRequest;
import io.metersphere.agent.security.AgentScopeAssert;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenRateLimiter;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.AgentToken;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentMcpStreamableService {
    private static final long IDEMPOTENCY_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final Set<String> WRITE_TOOLS = Set.of(
            "metersphere.functional.submit",
            "metersphere.functional.module.create",
            "metersphere.functional.case.create",
            "metersphere.functional.case.batch_create",
            "metersphere.bug.create",
            "metersphere.bug.update",
            "metersphere.project.create",
            "metersphere.project.members.add",
            "metersphere.test_plan.create",
            "metersphere.test_plan.associate_cases",
            "metersphere.case_review.create",
            "metersphere.case_review.associate_cases"
    );

    private final ConcurrentHashMap<String, IdempotencyRecord> idempotencyCache = new ConcurrentHashMap<>();

    @Resource
    private AgentFunctionalCaseSearchService agentFunctionalCaseSearchService;
    @Resource
    private AgentFunctionalCaseSubmitService agentFunctionalCaseSubmitService;
    @Resource
    private AgentCaseWriteService agentCaseWriteService;
    @Resource
    private AgentBugWriteService agentBugWriteService;
    @Resource
    private AgentProjectService agentProjectService;
    @Resource
    private AgentTestPlanWriteService agentTestPlanWriteService;
    @Resource
    private AgentCaseReviewWriteService agentCaseReviewWriteService;
    @Resource
    private AgentTokenRateLimiter agentTokenRateLimiter;
    @Autowired(required = false)
    private List<AgentMcpToolHandler> toolHandlers = List.of();

    public Map<String, Object> handle(Map<String, Object> request) {
        return handle(request, null);
    }

    public Map<String, Object> handle(Map<String, Object> request, String idempotencyKey) {
        Object id = request.get("id");
        String method = StringUtils.defaultString((String) request.get("method"));
        try {
            return switch (method) {
                case "initialize" -> response(id, initializeResult());
                case "notifications/initialized" -> response(id, Map.of("ok", true));
                case "ping" -> response(id, Map.of("ok", true));
                case "tools/list" -> response(id, Map.of("tools", tools()));
                case "tools/call" -> response(id, callTool(asMap(request.get("params")), idempotencyKey));
                default -> error(id, -32601, "Unsupported MCP method: " + method);
            };
        } catch (MSException ex) {
            return error(id, -32001, ex.getMessage());
        } catch (Exception ex) {
            return error(id, -32603, ex.getMessage());
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
        List<Map<String, Object>> tools = new ArrayList<>(List.of(
                tool("metersphere.functional.search",
                        "Search functional test cases. Optional projectId accepts internal project id, UI project number, or exact project name; when omitted it uses Agent Token current/default project.",
                        AgentTokenScope.FUNCTIONAL_READ,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.functional.get", "Get functional test case detail", AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("caseId", stringSchema(), "includeSteps", Map.of("type", "boolean"), "testPlanId", stringSchema()), List.of("caseId"))),
                tool("metersphere.functional.modules", "List functional case modules by project. projectId accepts internal project id, UI project number, or exact project name.", AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("projectId", stringSchema()), List.of("projectId"))),
                tool("metersphere.functional.submit", "Submit functional case execution result. projectId accepts internal project id, UI project number, or exact project name.", AgentTokenScope.FUNCTIONAL_SUBMIT,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.functional.module.create", "Create functional case module. projectId accepts internal project id, UI project number, or exact project name.", AgentTokenScope.CASE_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.functional.case.create", "Create functional case. projectId accepts internal project id, UI project number, or exact project name.", AgentTokenScope.CASE_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.functional.case.batch_create", "Batch create functional cases. projectId accepts internal project id, UI project number, or exact project name.", AgentTokenScope.CASE_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.bug.search", "Search bugs", AgentTokenScope.BUG_READ,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.bug.get", "Get bug detail", AgentTokenScope.BUG_READ,
                        objectSchema(Map.of("bugId", stringSchema()), List.of("bugId"))),
                tool("metersphere.bug.create", "Create bug", AgentTokenScope.BUG_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.bug.update", "Update bug", AgentTokenScope.BUG_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.project.create", "Create project", AgentTokenScope.PROJECT_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.project.members.add", "Add project members", AgentTokenScope.PROJECT_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.project.search",
                        "Search projects by internal project id, project name, or project number shown as ID in the UI. Returns all matched projects, including projects with the same number.",
                        AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("keyword", stringSchema(), "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200)), List.of())),
                tool("metersphere.project.list",
                        "List projects accessible to the current Agent Token user. Optional keyword matches project number shown as ID in the UI or project name.",
                        AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("keyword", stringSchema(), "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200)), List.of())),
                tool("metersphere.project.get", "Get project detail", AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("projectId", stringSchema()), List.of("projectId"))),
                tool("metersphere.test_plan.create", "Create test plan", AgentTokenScope.PLAN_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.test_plan.associate_cases", "Associate cases to test plan", AgentTokenScope.PLAN_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.test_plan.get", "Get test plan detail", AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("testPlanId", stringSchema()), List.of("testPlanId"))),
                tool("metersphere.case_review.create", "Create case review", AgentTokenScope.REVIEW_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.case_review.associate_cases", "Associate cases to review", AgentTokenScope.REVIEW_WRITE,
                        Map.of("type", "object", "additionalProperties", true)),
                tool("metersphere.case_review.get", "Get case review detail", AgentTokenScope.FUNCTIONAL_READ,
                        objectSchema(Map.of("reviewId", stringSchema()), List.of("reviewId")))
        ));
        for (AgentMcpToolHandler handler : toolHandlers) {
            tools.add(tool(handler.name(), handler.description(), handler.requiredScope(), handler.inputSchema(), handler.annotations()));
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
        if (StringUtils.isNotBlank(effectiveIdempotencyKey) && isWriteTool(name)) {
            String cacheKey = idempotencyCacheKey(token, name, effectiveIdempotencyKey);
            synchronized (idempotencyCache) {
                cleanExpiredIdempotencyRecords();
                IdempotencyRecord cached = idempotencyCache.get(cacheKey);
                if (cached != null && !cached.expired()) {
                    return cached.response();
                }
                Map<String, Object> response = callToolInternal(name, arguments);
                idempotencyCache.put(cacheKey, new IdempotencyRecord(System.currentTimeMillis(), response));
                return response;
            }
        }
        return callToolInternal(name, arguments);
    }

    private Map<String, Object> callToolInternal(String name, Map<String, Object> arguments) {
        AgentMcpToolHandler handler = findToolHandler(name);
        if (handler != null) {
            AgentScopeAssert.assertScope(handler.requiredScope());
            return toolResponse(handler.execute(arguments));
        }
        Object result = switch (name) {
            case "metersphere.functional.search" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentFunctionalCaseSearchService.search(convert(arguments, AgentCaseSearchRequest.class));
            }
            case "metersphere.functional.get" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                String caseId = requiredString(arguments, "caseId");
                boolean includeSteps = !arguments.containsKey("includeSteps") || BooleanUtils.toBoolean(arguments.get("includeSteps").toString());
                String testPlanId = (String) arguments.get("testPlanId");
                yield agentFunctionalCaseSearchService.getById(caseId, includeSteps, testPlanId);
            }
            case "metersphere.functional.modules" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentFunctionalCaseSearchService.listModules(requiredString(arguments, "projectId"));
            }
            case "metersphere.functional.submit" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_SUBMIT);
                agentFunctionalCaseSubmitService.submit(convert(arguments, AgentCaseSubmitRequest.class));
                yield Map.of("ok", true);
            }
            case "metersphere.functional.module.create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE);
                yield agentCaseWriteService.createModule(convert(arguments, AgentModuleCreateRequest.class));
            }
            case "metersphere.functional.case.create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE);
                yield agentCaseWriteService.createCase(convert(arguments, AgentCaseCreateRequest.class));
            }
            case "metersphere.functional.case.batch_create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.CASE_WRITE);
                yield agentCaseWriteService.batchCreate(convert(arguments, AgentCaseBatchCreateRequest.class));
            }
            case "metersphere.bug.search" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ);
                yield agentBugWriteService.search(convert(arguments, AgentBugSearchRequest.class));
            }
            case "metersphere.bug.get" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.BUG_READ);
                yield agentBugWriteService.get(requiredString(arguments, "bugId"));
            }
            case "metersphere.bug.create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
                yield agentBugWriteService.create(convert(arguments, AgentBugCreateRequest.class));
            }
            case "metersphere.bug.update" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.BUG_WRITE);
                yield agentBugWriteService.update(convert(arguments, AgentBugUpdateRequest.class));
            }
            case "metersphere.project.create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE);
                yield agentProjectService.create(convert(arguments, AgentProjectCreateRequest.class));
            }
            case "metersphere.project.members.add" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.PROJECT_WRITE);
                agentProjectService.addMembers(convert(arguments, AgentProjectAddMembersRequest.class));
                yield Map.of("ok", true);
            }
            case "metersphere.project.search" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentProjectService.search(convert(arguments, AgentProjectSearchRequest.class));
            }
            case "metersphere.project.list" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentProjectService.search(convert(arguments, AgentProjectSearchRequest.class));
            }
            case "metersphere.project.get" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentProjectService.get(requiredString(arguments, "projectId"));
            }
            case "metersphere.test_plan.create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.PLAN_WRITE);
                yield agentTestPlanWriteService.create(convert(arguments, AgentTestPlanCreateRequest.class));
            }
            case "metersphere.test_plan.associate_cases" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.PLAN_WRITE);
                agentTestPlanWriteService.associate(convert(arguments, AgentTestPlanAssociateRequest.class));
                yield Map.of("ok", true);
            }
            case "metersphere.test_plan.get" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentTestPlanWriteService.get(requiredString(arguments, "testPlanId"));
            }
            case "metersphere.case_review.create" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
                yield agentCaseReviewWriteService.create(convert(arguments, AgentCaseReviewCreateRequest.class));
            }
            case "metersphere.case_review.associate_cases" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.REVIEW_WRITE);
                agentCaseReviewWriteService.associate(convert(arguments, AgentCaseReviewAssociateRequest.class));
                yield Map.of("ok", true);
            }
            case "metersphere.case_review.get" -> {
                AgentScopeAssert.assertScope(AgentTokenScope.FUNCTIONAL_READ);
                yield agentCaseReviewWriteService.get(requiredString(arguments, "reviewId"));
            }
            default -> throw new MSException("Unsupported MCP tool: " + name);
        };
        return toolResponse(result);
    }

    private AgentMcpToolHandler findToolHandler(String name) {
        return toolHandlers.stream()
                .filter(handler -> StringUtils.equals(handler.name(), name))
                .findFirst()
                .orElse(null);
    }

    private boolean isWriteTool(String name) {
        if (WRITE_TOOLS.contains(name)) {
            return true;
        }
        AgentMcpToolHandler handler = findToolHandler(name);
        return handler != null && !Boolean.TRUE.equals(handler.annotations().get("readOnlyHint"));
    }

    private Map<String, Object> toolResponse(Object result) {
        return Map.of("content", List.of(Map.of("type", "text", "text", JSON.toJSONString(result))));
    }

    private String idempotencyCacheKey(AgentToken token, String toolName, String idempotencyKey) {
        String userId = token == null ? "anonymous" : StringUtils.defaultIfBlank(token.getUserId(), token.getId());
        return userId + ":" + toolName + ":" + StringUtils.trim(idempotencyKey);
    }

    private void cleanExpiredIdempotencyRecords() {
        idempotencyCache.entrySet().removeIf(entry -> entry.getValue().expired());
    }

    private Map<String, Object> tool(String name, String description, String scope, Map<String, Object> inputSchema) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("scope", scope);
        annotations.put("readOnlyHint", !StringUtils.contains(scope, "WRITE") && !StringUtils.contains(scope, "SUBMIT"));

        return tool(name, description, scope, inputSchema, annotations);
    }

    private Map<String, Object> tool(String name, String description, String scope, Map<String, Object> inputSchema, Map<String, Object> annotations) {
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

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private <T> T convert(Map<String, Object> arguments, Class<T> clazz) {
        return JSON.parseObject(JSON.toJSONString(arguments), clazz);
    }

    private String requiredString(Map<String, Object> arguments, String key) {
        String value = (String) arguments.get(key);
        if (StringUtils.isBlank(value)) {
            throw new MSException("Missing required argument: " + key);
        }
        return value;
    }

    private Map<String, Object> response(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", StringUtils.defaultString(message)));
        return response;
    }

    private record IdempotencyRecord(long createTime, Map<String, Object> response) {
        boolean expired() {
            return System.currentTimeMillis() - createTime > IDEMPOTENCY_CACHE_TTL_MS;
        }
    }
}
