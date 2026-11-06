package io.metersphere.agent.tool.project;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentProjectFileService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class AgentProjectFileTools {
    @Bean public AgentMcpToolHandler projectFilePrepare(AgentProjectFileService service) { return tool(service, "upload.prepare"); }
    @Bean public AgentMcpToolHandler projectFileChunk(AgentProjectFileService service) { return tool(service, "upload.chunk"); }
    @Bean public AgentMcpToolHandler projectFileComplete(AgentProjectFileService service) { return tool(service, "upload.complete"); }
    @Bean public AgentMcpToolHandler projectFileCancel(AgentProjectFileService service) { return tool(service, "upload.cancel"); }
    @Bean public AgentMcpToolHandler projectFileUploadGet(AgentProjectFileService service) { return tool(service, "upload.get"); }
    @Bean public AgentMcpToolHandler projectFileSearch(AgentProjectFileService service) { return tool(service, "search"); }
    @Bean public AgentMcpToolHandler projectFileGet(AgentProjectFileService service) { return tool(service, "get"); }
    @Bean public AgentMcpToolHandler projectFileVersions(AgentProjectFileService service) { return tool(service, "versions"); }
    @Bean public AgentMcpToolHandler projectFileDownload(AgentProjectFileService service) { return tool(service, "download"); }

    private AgentMcpToolHandler tool(AgentProjectFileService service, String action) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectId", id());
        List<String> required = new ArrayList<>(List.of("projectId"));
        boolean write = action.startsWith("upload.") && !action.equals("upload.get");
        String scope = write ? AgentTokenScope.PROJECT_FILE_WRITE : action.equals("download")
                ? AgentTokenScope.PROJECT_FILE_DOWNLOAD : AgentTokenScope.PROJECT_FILE_READ;
        if (write) fields.put("requestId", AgentMcpToolSchemas.requestId());
        switch (action) {
            case "upload.prepare" -> {
                fields.put("requestId", AgentMcpToolSchemas.requestId());
                fields.put("fileName", AgentMcpToolSchemas.string(1, 255));
                fields.put("size", Map.of("type", "integer", "minimum", 1, "maximum", 50 * 1024 * 1024));
                fields.put("sha256", digest());
                fields.put("moduleId", id());
                fields.put("sourceLabel", AgentMcpToolSchemas.string(0, 255));
                fields.put("existingFileId", id());
                fields.put("expectedVersion", Map.of("type", "string", "minLength", 1, "maxLength", 128,
                        "not", Map.of("pattern", "^[Ll][Aa][Tt][Ee][Ss][Tt]$")));
                fields.put("confirmNewVersion", Map.of("type", "boolean"));
                fields.put("reuseVersionId", id());
                fields.put("saveSeparateOriginal", Map.of("type", "boolean"));
                required.addAll(List.of("requestId", "fileName", "size", "sha256"));
            }
            case "upload.chunk" -> {
                fields.put("uploadId", id());
                fields.put("offset", Map.of("type", "integer", "minimum", 0));
                fields.put("base64", AgentMcpToolSchemas.string(1, 1398104));
                fields.put("chunkHash", digest());
                required.addAll(List.of("uploadId", "offset", "base64", "chunkHash"));
            }
            case "upload.complete", "upload.cancel" -> {
                fields.put("uploadId", id());
                required.add("uploadId");
            }
            case "upload.get" -> {
                fields.put("requestId", AgentMcpToolSchemas.requestId());
                required.add("requestId");
            }
            case "search" -> {
                fields.put("keyword", AgentMcpToolSchemas.string(0, 255));
                fields.put("current", Map.of("type", "integer", "minimum", 1, "maximum", Integer.MAX_VALUE));
                fields.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100));
            }
            default -> {
                fields.put("fileId", id());
                fields.put("versionId", id());
                required.add("fileId");
                if (action.equals("download")) {
                    fields.put("offset", Map.of("type", "integer", "minimum", 0));
                    fields.put("length", Map.of("type", "integer", "minimum", 1, "maximum", AgentProjectFileService.MAX_CHUNK_BYTES));
                    required.addAll(List.of("versionId", "offset", "length"));
                }
            }
        }
        Map<String, Object> schema = AgentMcpToolSchemas.object(fields, required);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.github.fge.jsonschema.main.JsonSchema validator;
        try {
            validator = com.github.fge.jsonschema.main.JsonSchemaFactory.byDefault().getJsonSchema(mapper.valueToTree(schema));
        } catch (com.github.fge.jsonschema.core.exceptions.ProcessingException invalid) {
            throw new IllegalStateException("Invalid project file tool schema", invalid);
        }
        return new AgentMcpToolHandler() {
            public String name() { return "metersphere.project.file." + action; }
            public String requiredScope() { return scope; }
            public String description() {
                return "Project original files: " + action + ". Explicit PROJECT_FILE scopes and current project permissions required. "
                        + "Prepare xls/xlsx/xmind, then upload raw chunks <=1MiB using offsets/hashes within 24h; complete verifies full SHA256. "
                        + "Query upload.get by the original requestId after a lost response. Replays never create another version. "
                        + "New versions require existingFileId, exact expectedVersion and confirmNewVersion. Candidate reuse must be explicit. "
                        + "File IDs identify a version family; versionId is immutable. latest resolves to a concrete version. "
                        + "Download reauthorizes each chunk; no public URLs or physical paths. Cancel never deletes a stored version. "
                        + "Stored originals are not imported cases; XMind content is never executed.";
            }
            public Map<String, Object> inputSchema() { return schema; }
            public Map<String, Object> annotations() { return AgentMcpToolSchemas.annotations(scope, !write, false, true); }
            public Object execute(Map<String, Object> args) {
                String previousTrace = org.slf4j.MDC.get("traceId");
                String trace = previousTrace == null ? UUID.randomUUID().toString() : previousTrace;
                org.slf4j.MDC.put("traceId", trace);
                try {
                    if (!validator.validate(mapper.valueToTree(args)).isSuccess()) throw new io.metersphere.agent.service.ProjectFileException("VALIDATION_ERROR");
                    if (action.equals("upload.prepare")) return service.prepare(args);
                    if (action.startsWith("upload.")) return service.upload(action.substring(7), args);
                    return service.read(action, args);
                } catch (com.github.fge.jsonschema.core.exceptions.ProcessingException invalid) {
                    var rejected = new io.metersphere.agent.service.ProjectFileException("VALIDATION_ERROR").withTraceId(trace);
                    service.recordRejection(rejected, action, args, trace);
                    throw rejected;
                } catch (io.metersphere.sdk.exception.MSException failure) {
                    service.recordRejection(failure, action, args, trace);
                    var rejected = failure instanceof io.metersphere.agent.service.ProjectFileException file ? file
                            : new io.metersphere.agent.service.ProjectFileException(failure);
                    throw rejected.withTraceId(trace);
                } finally {
                    if (previousTrace == null) org.slf4j.MDC.remove("traceId");
                    else org.slf4j.MDC.put("traceId", previousTrace);
                }
            }
        };
    }
    private Map<String, Object> id() { return AgentMcpToolSchemas.string(1, 128); }
    private Map<String, Object> digest() { return Map.of("type", "string", "pattern", "^[a-fA-F0-9]{64}$"); }
}
