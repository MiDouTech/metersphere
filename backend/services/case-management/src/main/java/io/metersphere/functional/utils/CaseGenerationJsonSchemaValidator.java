package io.metersphere.functional.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Validates AI structured generation JSON against the shared CaseGenerationResult schema.
 */
public final class CaseGenerationJsonSchemaValidator {
    private static final JsonSchema SCHEMA;

    static {
        try {
            JsonNode schemaNode = JsonLoader.fromResource("/schema/case-generation-result.json");
            SCHEMA = JsonSchemaFactory.byDefault().getJsonSchema(schemaNode);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private CaseGenerationJsonSchemaValidator() {
    }

    public static void validateOrThrow(String json) {
        if (StringUtils.isBlank(json)) {
            throw new MSException("AI 返回为空，JSON Schema 校验失败");
        }
        try {
            JsonNode data = JsonLoader.fromString(json);
            ProcessingReport report = SCHEMA.validate(data);
            if (report.isSuccess()) {
                return;
            }
            List<String> messages = new ArrayList<>();
            Iterator<ProcessingMessage> iterator = report.iterator();
            while (iterator.hasNext() && messages.size() < 5) {
                messages.add(iterator.next().getMessage());
            }
            throw new MSException("AI 返回不符合 JSON Schema：" + String.join("; ", messages));
        } catch (MSException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MSException("AI 返回 JSON Schema 校验异常：" + ex.getMessage(), ex);
        }
    }
}
