package io.metersphere.agent.tool.data;

import io.metersphere.agent.service.*;
import io.metersphere.agent.tool.*;
import java.util.*;
import org.springframework.context.annotation.*;

@Configuration
public class DataAssetMcpTools {
  @Bean
  public AgentMcpToolHandler dataAssetManage(
      DataAssetService service,
      DataAssetBatchService batches,
      DataAssetParseJobService parseJobs,
      DataAssetLegacyService legacy) {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("sourceProjectId", AgentMcpToolSchemas.string(1, 64));
    fields.put(
        "action",
        AgentMcpToolSchemas.enumString(
            List.of(
                "list",
                "detail",
                "content",
                "versions",
                "create",
                "update",
                "delete",
                "batch",
                "job",
                "retry_job",
                "upload",
                "replace_upload",
                "download",
                "legacy_import",
                "legacy_inventory")));
    fields.put("fileName", AgentMcpToolSchemas.string(1, 255));
    fields.put("name", AgentMcpToolSchemas.string(1, 255));
    fields.put("requestId", AgentMcpToolSchemas.string(1, 128));
    fields.put("base64", AgentMcpToolSchemas.string(0, 2666672));
    fields.put(
        "offset", Map.of("type", "integer", "minimum", 0, "maximum", DataAssetLimits.MAX_BYTES));
    fields.put("chunkSize", Map.of("type", "integer", "minimum", 1, "maximum", 65536));
    fields.put("id", AgentMcpToolSchemas.string(1, 64));
    fields.put("versionId", AgentMcpToolSchemas.string(1, 64));
    fields.put("keyword", AgentMcpToolSchemas.string(0, 255));
    fields.put("categoryId", AgentMcpToolSchemas.string(0, 64));
    fields.put("page", Map.of("type", "integer", "minimum", 1));
    fields.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100));
    fields.put("data", Map.of("type", "object"));
    fields.put(
        "items",
        Map.of("type", "array", "minItems", 1, "maxItems", 100, "items", Map.of("type", "object")));
    fields.put("expectedRevision", Map.of("type", "integer", "minimum", 1));
    var schema = AgentMcpToolSchemas.object(fields, List.of("sourceProjectId", "action"));
    return new AgentMcpToolHandler() {
      public String name() {
        return "metersphere.data_asset.manage";
      }

      public String requiredScope() {
        return "USER_RBAC";
      }

      public String description() {
        return "Manage organization-global test data using linked user organization role"
            + " permissions. sourceProjectId selects Token-whitelisted organization context,"
            + " never filters global contents. update/delete require expectedRevision."
            + " create supports requestId idempotency. Full content editing is bounded to"
            + " 2MB; use immutable versionId when reading historical data. No additional"
            + " Token scopes.";
      }

      public Map<String, Object> inputSchema() {
        return schema;
      }

      public Map<String, Object> annotations() {
        return Map.of(
            "authorization",
            "USER_RBAC",
            "readOnlyHint",
            false,
            "destructiveHint",
            true,
            "idempotentHint",
            false,
            "openWorldHint",
            false);
      }

      public Object execute(Map<String, Object> args) {
        return DataAssetErrors.nativeBoundary(() -> executeInternal(args));
      }

      private Object executeInternal(Map<String, Object> args) {
        try {
          var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
          var compiled =
              com.github.fge.jsonschema.main.JsonSchemaFactory.byDefault()
                  .getJsonSchema(mapper.valueToTree(schema));
          if (!compiled.validateUnchecked(mapper.valueToTree(args)).isSuccess())
            throw new IllegalArgumentException();
        } catch (Exception e) {
          throw new io.metersphere.sdk.exception.MSException("测试数据工具参数无效");
        }
        String project = (String) args.get("sourceProjectId"),
            id = (String) args.get("id"),
            action = (String) args.get("action");
        if (!Set.of("list", "create", "batch", "upload", "legacy_inventory").contains(action)
            && (id == null || id.isBlank()))
          throw new io.metersphere.sdk.exception.MSException("请选择数据资产");
        return switch (action) {
          case "list" ->
              service.list(
                  project,
                  Objects.toString(args.get("keyword"), ""),
                  Objects.toString(args.get("categoryId"), ""),
                  ((Number) args.getOrDefault("page", 1)).intValue(),
                  ((Number) args.getOrDefault("pageSize", 20)).intValue());
          case "batch" -> batches.submit(project, (List<Map<String, Object>>) args.get("items"));
          case "job" ->
              parseJobs.exists(id) ? parseJobs.get(project, id) : batches.get(project, id);
          case "retry_job" ->
              parseJobs.exists(id) ? parseJobs.retry(project, id) : batches.retry(project, id);
          case "upload", "replace_upload" -> {
            if (!(args.get("base64") instanceof String encoded)
                || !(args.get("fileName") instanceof String filename)
                || !(args.get("requestId") instanceof String requestId))
              throw new io.metersphere.sdk.exception.MSException("上传需要文件名、幂等标识及Base64内容");
            byte[] bytes;
            try {
              bytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException e) {
              throw new io.metersphere.sdk.exception.MSException("文件编码无效");
            }
            if ("replace_upload".equals(action)) {
              if (!(args.get("expectedRevision") instanceof Number revision))
                throw new io.metersphere.sdk.exception.MSException("替换文件需要当前版本号");
              yield parseJobs.replace(project, id, revision.intValue(), bytes, filename, requestId);
            }
            yield parseJobs.upload(
                project,
                bytes,
                filename,
                (String) args.get("name"),
                (String) args.get("categoryId"),
                requestId);
          }
          case "download" -> {
            byte[] bytes = service.file(project, id, (String) args.get("versionId"));
            int offset = ((Number) args.getOrDefault("offset", 0)).intValue(),
                size = ((Number) args.getOrDefault("chunkSize", 65536)).intValue();
            if (offset > bytes.length)
              throw new io.metersphere.sdk.exception.MSException("下载偏移超过文件长度");
            int end = Math.min(offset + size, bytes.length);
            yield Map.of(
                "base64",
                Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, offset, end)),
                "offset",
                offset,
                "nextOffset",
                end,
                "total",
                bytes.length,
                "complete",
                end == bytes.length,
                "sha256",
                org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes));
          }
          case "legacy_import" -> legacy.importFile(project, id);
          case "legacy_inventory" -> legacy.inventory(project);
          case "detail" -> service.detail(project, id);
          case "content" -> service.content(project, id, (String) args.get("versionId"));
          case "versions" -> service.versions(project, id);
          case "create", "update" -> {
            if (!(args.get("data") instanceof Map<?, ?>))
              throw new io.metersphere.sdk.exception.MSException("请提供完整数据参数");
            var data = new LinkedHashMap<String, Object>((Map<String, Object>) args.get("data"));
            for (String field : List.of("expectedRevision", "requestId"))
              if (args.containsKey(field)) {
                Object outer = args.get(field), inner = data.get(field);
                boolean same =
                    outer instanceof Number left && inner instanceof Number right
                        ? new java.math.BigDecimal(left.toString())
                                .compareTo(new java.math.BigDecimal(right.toString()))
                            == 0
                        : Objects.equals(outer, inner);
                if (data.containsKey(field) && !same)
                  throw new io.metersphere.sdk.exception.MSException("外层与数据内的版本号或请求标识不一致");
                data.put(field, outer);
              }
            yield service.save(project, "create".equals(action) ? null : id, data);
          }
          case "delete" -> {
            if (!(args.get("expectedRevision") instanceof Number n))
              throw new io.metersphere.sdk.exception.MSException("删除需要当前版本号");
            service.delete(project, id, n.intValue());
            yield Map.of("deleted", true);
          }
          default -> throw new io.metersphere.sdk.exception.MSException("未知操作");
        };
      }
    };
  }
}
