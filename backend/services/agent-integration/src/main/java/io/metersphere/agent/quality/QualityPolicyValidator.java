package io.metersphere.agent.quality;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/** Strict, local policy schema. Never loads a schema supplied by a caller. */
@Component
public class QualityPolicyValidator {
    public static final int MAX_BYTES = 16384;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(MAX_BYTES).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final JsonNode schemaDocument;
    private final JsonSchema schema;

    public QualityPolicyValidator() { this(5242880L); }

    @org.springframework.beans.factory.annotation.Autowired
    public QualityPolicyValidator(@org.springframework.beans.factory.annotation.Value("${agent.execution.artifact-max-bytes:5242880}") long artifactMaxBytes) {
        try (var stream = getClass().getResourceAsStream("/quality/quality-policy.v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing policy schema");
            schemaDocument = mapper.readTree(stream);
            if (artifactMaxBytes < 1) throw new IllegalStateException("Invalid artifact size limit");
            ((ObjectNode) schemaDocument.at("/properties/rules/items/oneOf/0/properties/parameters/properties/maxArtifactBytes"))
                    .put("maximum", Math.min(10485760L, artifactMaxBytes));
            schema = JsonSchemaFactory.byDefault().getJsonSchema(schemaDocument);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize policy schema", error);
        }
    }

    public record Issue(String path, String message, String code) {
        public Issue(String path, String message) { this(path, message, "POLICY_INVALID"); }
    }
    public record Validation(boolean valid, List<Issue> errors, String normalizedJson, String contentHash) { }

    public JsonNode schema() { return schemaDocument.deepCopy(); }

    public Validation validate(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return invalid("", "策略不能为空且不能超过 16 KiB");
        }
        JsonNode node;
        try {
            node = mapper.readTree(value);
        } catch (Exception error) {
            return invalid("", "请输入合法 JSON，不允许重复字段、注释或过深嵌套");
        }
        if (node == null) return invalid("", "策略不能为空");
        List<Issue> errors = new ArrayList<>();
        try {
            for (ProcessingMessage message : schema.validate(node, true)) {
                if (!"oneOf".equals(message.asJson().path("keyword").asText())) addIssues(message.asJson(), "", errors);
            }
            if (node.path("rules").isArray()) {
                for (int i = 0; i < node.path("rules").size(); i++) {
                    JsonNode rule = node.path("rules").get(i);
                    int branch = switch (rule.path("ruleId").asText()) {
                        case "Q-EVIDENCE-01" -> 0;
                        case "Q-ASSERT-01" -> 1;
                        default -> -1;
                    };
                    if (branch < 0) {
                        errors.add(new Issue("/rules/" + i + "/ruleId", "请选择支持的规则 ID", "RULE_ID_INVALID"));
                    } else {
                        JsonSchema ruleSchema = JsonSchemaFactory.byDefault().getJsonSchema(schemaDocument,
                                "/properties/rules/items/oneOf/" + branch);
                        for (ProcessingMessage message : ruleSchema.validate(rule, true)) {
                            addIssues(message.asJson(), "/rules/" + i, errors);
                        }
                    }
                }
            }
        } catch (com.github.fge.jsonschema.core.exceptions.ProcessingException error) {
            throw new IllegalStateException("Policy schema validation failed", error);
        }
        if (!errors.isEmpty()) return new Validation(false, errors.stream().distinct().limit(20).toList(), null, null);
        var ids = new HashSet<String>();
        for (JsonNode rule : node.path("rules")) {
            if (!ids.add(rule.path("ruleId").asText())) return invalid("/rules", "规则 ID 不允许重复");
        }
        String normalized = canonical(node).toString();
        return new Validation(true, List.of(), normalized, DigestUtils.sha256Hex(normalized));
    }

    private Validation invalid(String path, String message) {
        return new Validation(false, List.of(new Issue(path, message)), null, null);
    }

    private void addIssues(JsonNode error, String prefix, List<Issue> issues) {
        String path = prefix + error.path("instance").path("pointer").asText("");
        String keyword = error.path("keyword").asText();
        if ("required".equals(keyword) || "additionalProperties".equals(keyword)) {
            JsonNode fields = error.path("required".equals(keyword) ? "missing" : "unwanted");
            for (JsonNode field : fields) {
                String escaped = field.asText().replace("~", "~0").replace("/", "~1");
                issues.add(new Issue(path + "/" + escaped,
                        "required".equals(keyword) ? "缺少必填字段" : "不允许此字段",
                        "required".equals(keyword) ? "FIELD_REQUIRED" : "FIELD_UNKNOWN"));
            }
            if (!fields.isEmpty()) return;
        }
        String description = switch (keyword) {
            case "type" -> "字段类型不正确，请使用规定的类型";
            case "enum" -> "取值不在允许列表中，请参考策略表单选项";
            case "minimum", "maximum" -> "数值超出允许范围，请参考策略表单上下限";
            case "minItems", "maxItems" -> "选项数量不符合要求";
            case "uniqueItems" -> "选项不能重复";
            case "minLength", "maxLength" -> "文本长度不符合要求";
            case "pattern" -> "文本格式不符合要求，名称不能全为空白";
            default -> "字段不符合策略规范";
        };
        issues.add(new Issue(path, description, "SCHEMA_" + keyword.toUpperCase(java.util.Locale.ROOT)));
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            var keys = new TreeSet<String>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> out.set(key, canonical(node.get(key))));
            return out;
        }
        if (node.isArray()) {
            var out = mapper.createArrayNode();
            node.forEach(item -> out.add(canonical(item)));
            return out;
        }
        return node;
    }
}
