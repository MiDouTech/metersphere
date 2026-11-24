package io.metersphere.agent.tool.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.metersphere.agent.dto.SharedConfigurationSave;
import io.metersphere.agent.service.BusinessGovernanceException;
import io.metersphere.agent.service.SharedConfigurationService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import jakarta.validation.Validator;
import java.util.*;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Thin MCP adapters. Authentication, current user RBAC and encryption remain in the service. */
@Configuration
public class SharedConfigurationMcpTools {
    private final SharedConfigurationService service;
    private final Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    public SharedConfigurationMcpTools(SharedConfigurationService service, Validator validator) {
        this.service = service; this.validator = validator;
    }

    @Bean public AgentMcpToolHandler configurationList() {
        return tool("list", true, fields(false), List.of("projectId"), args -> service.list(text(args, "projectId")));
    }
    @Bean public AgentMcpToolHandler configurationVersions() {
        return tool("versions", true, fields(true), List.of("projectId", "id"),
                args -> service.versions(text(args, "projectId"), text(args, "id")));
    }
    @Bean public AgentMcpToolHandler configurationAudits() {
        var fields = fields(true);
        fields.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 1000000));
        fields.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100));
        fields.put("version", version(1));
        fields.put("eventType", AgentMcpToolSchemas.enumString(List.of("SAVE", "SELECT_VERSION", "EXECUTOR_RESOLVE", "RETRY_PARSE")));
        return tool("audits", true, fields, List.of("projectId", "id"), args -> service.audits(
                text(args, "projectId"), text(args, "id"),
                args.containsKey("page") ? number(args, "page") : 1,
                args.containsKey("pageSize") ? number(args, "pageSize") : 20,
                text(args, "eventType"), args.containsKey("version") ? number(args, "version") : null));
    }
    @Bean public AgentMcpToolHandler configurationRead() {
        var fields = fields(true); fields.put("version", version(1)); fields.put("document", Map.of("type", "boolean"));
        return tool("read", true, fields, List.of("projectId", "id", "version"), args -> service.read(
                text(args, "projectId"), text(args, "id"), number(args, "version"), Boolean.TRUE.equals(args.get("document"))));
    }
    @Bean public AgentMcpToolHandler configurationResolve() {
        var fields = fields(true); fields.put("sessionId", AgentMcpToolSchemas.string(1, 64));
        return tool("resolve", true, fields, List.of("projectId", "id", "sessionId"), args -> service.resolveForExecutor(
                text(args, "projectId"), text(args, "sessionId"), text(args, "id")));
    }
    @Bean public AgentMcpToolHandler configurationSave() {
        return tool("save", false, saveFields(), saveRequired(), args -> save(convert(args)));
    }
    @Bean public AgentMcpToolHandler configurationBatch() {
        var entries = Map.of("type", "array", "minItems", 1, "maxItems", 100,
                "items", AgentMcpToolSchemas.object(saveFields(), saveRequired()));
        return tool("batch", false, Map.of("entries", entries), List.of("entries"), args -> {
            var requests = ((List<?>) args.get("entries")).stream().map(this::convert).toList();
            return service.batch(requests);
        });
    }
    @Bean public AgentMcpToolHandler configurationSelectVersion() {
        var fields = fields(true); fields.put("version", version(1)); fields.put("expectedVersion", version(1));
        fields.put("reason", AgentMcpToolSchemas.string(1, 500));
        return tool("select_version", false, fields, List.of("projectId", "id", "version", "expectedVersion", "reason"), args -> {
            service.select(text(args, "projectId"), text(args, "id"), number(args, "version"), number(args, "expectedVersion"), text(args, "reason"));
            return Map.of("selected", true);
        });
    }
    @Bean public AgentMcpToolHandler configurationRetryParse() {
        var fields = fields(true); fields.put("version", version(1));
        return tool("retry_parse", false, fields, List.of("projectId", "id", "version"), args -> Map.of("parseStatus",
                service.retryParse(text(args, "projectId"), text(args, "id"), number(args, "version"))));
    }

    private Object save(SharedConfigurationSave r) {
        return service.saveStructured(r.projectId(), r.id(), r.systemKey(), r.environmentKey(), r.name(),
                r.kind(), r.value(), r.fileName(), r.expectedVersion(), r.secretType(), r.keepSecret());
    }
    private SharedConfigurationSave convert(Object raw) {
        final SharedConfigurationSave request;
        try { request = mapper.convertValue(raw, SharedConfigurationSave.class); }
        catch (IllegalArgumentException invalid) { throw invalid(); }
        if (!validator.validate(request).isEmpty()) throw invalid();
        return request;
    }
    private Map<String, Object> fields(boolean withId) {
        var result = new LinkedHashMap<String, Object>();
        result.put("projectId", AgentMcpToolSchemas.string(1, 64));
        if (withId) result.put("id", AgentMcpToolSchemas.string(1, 64));
        return result;
    }
    private Map<String, Object> saveFields() {
        var result = fields(true);
        for (String field : List.of("systemKey", "environmentKey", "name")) result.put(field, AgentMcpToolSchemas.string(1, 128));
        result.put("kind", AgentMcpToolSchemas.enumString(List.of("PLAIN", "SECRET", "DOCUMENT")));
        result.put("value", AgentMcpToolSchemas.string(0, 2800000));
        result.put("fileName", AgentMcpToolSchemas.string(0, 255));
        result.put("expectedVersion", version(0));
        result.put("secretType", AgentMcpToolSchemas.enumString(List.of("ACCOUNT_PASSWORD", "OTHER")));
        result.put("keepSecret", Map.of("type", "boolean"));
        return result;
    }
    private List<String> saveRequired() { return List.of("projectId", "systemKey", "environmentKey", "name", "kind", "expectedVersion"); }
    private Map<String, Object> version(int minimum) { return Map.of("type", "integer", "minimum", minimum, "maximum", Integer.MAX_VALUE); }
    private String text(Map<String, Object> args, String key) { return (String) args.get(key); }
    private int number(Map<String, Object> args, String key) { return ((Number) args.get(key)).intValue(); }
    private BusinessGovernanceException invalid() { return new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置工具参数无效，请检查必填项、类型和版本"); }

    private AgentMcpToolHandler tool(String action, boolean read, Map<String, Object> fields, List<String> required,
                                   Function<Map<String, Object>, Object> execute) {
        var schema = AgentMcpToolSchemas.object(fields, required);
        final com.github.fge.jsonschema.main.JsonSchema compiled;
        try { compiled = JsonSchemaFactory.byDefault().getJsonSchema(mapper.valueToTree(schema)); }
        catch (com.github.fge.jsonschema.core.exceptions.ProcessingException failure) { throw new IllegalStateException("Invalid configuration tool schema"); }
        return new AgentMcpToolHandler() {
            public String name() { return "metersphere.shared_configuration." + action; }
            public String requiredScope() { return "USER_RBAC"; }
            public String description() {
                return "Shared configuration " + action + ". Uses linked user roles and project access. "
                        + "Resolve requires the same Token's RUNNING executor session and uses its pinned version. "
                        + "Never log or persist actual values. Save requires expectedVersion; after an uncertain write read metadata before retrying.";
            }
            public Map<String, Object> inputSchema() { return schema; }
            public Map<String, Object> annotations() { return Map.of("authorization", "USER_RBAC", "readOnlyHint", read,
                    "destructiveHint", false, "idempotentHint", read, "openWorldHint", false); }
            public Object execute(Map<String, Object> args) {
                if (!compiled.validateUnchecked(mapper.valueToTree(args)).isSuccess()) throw invalid();
                return execute.apply(args);
            }
        };
    }
}
