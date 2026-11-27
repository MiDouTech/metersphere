package io.metersphere.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.sdk.exception.MSException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class DataAssetService {
  private final JdbcTemplate jdbc;
  private final GlobalAssetAccess access;
  private final ObjectMapper json = new ObjectMapper();
  @jakarta.annotation.Resource private AgentBusinessAccess projectAccess;

  /** Caller must keep this transaction open through creating the project reference. */
  public Map<String, Object> resolveForProject(String project, String id, String version) {
    var scope = scope(project, false);
    if (project == null
        || project.isBlank()
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM project WHERE id=? AND organization_id=? AND deleted=0 AND"
                    + " enable=1",
                Long.class,
                project,
                scope.organizationId())
            != 1
        || !projectAccess.userHasPermission(
            scope.actorId(),
            project,
            io.metersphere.sdk.constants.PermissionConstants.TEST_PLAN_READ))
      throw AgentBusinessAccess.denied();
    jdbc.queryForList(
        "SELECT id FROM data_asset WHERE id=? AND organization_id=? AND deleted=0 FOR UPDATE",
        id,
        scope.organizationId());
    var metadata = detail(project, id);
    String selected =
        version == null || version.isBlank()
            ? metadata.get("currentVersionId").toString()
            : version;
    var result = new HashMap<String, Object>(metadata);
    result.put("versionId", selected);
    var frozen = content(project, id, selected);
    var snapshot = (Map<?, ?>) frozen.get("metadata");
    for (String field : List.of("name", "description", "categoryId", "format"))
      result.put(field, snapshot.get(field));
    result.put("metadata", snapshot);
    result.put("metadataAvailable", frozen.get("metadataAvailable"));
    result.put("metadataSource", frozen.get("metadataSource"));
    result.put("content", frozen.get("content"));
    return result;
  }

  /** Organization-filtered existence only; never use this as authorization for source files. */
  public boolean hasExisting(String project, String id) {
    var scope = scope(project, false);
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM data_asset WHERE id=? AND organization_id=?",
            Long.class,
            id,
            scope.organizationId())
        > 0;
  }

  public DataAssetService(JdbcTemplate jdbc, GlobalAssetAccess access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  private GlobalAssetAccess.Scope scope(String project, boolean write) {
    return access.authorize(
        project, write ? "TEST_DATA_ASSET:READ+UPDATE" : "TEST_DATA_ASSET:READ");
  }

  private String encode(Object value) {
    try {
      String s = json.writeValueAsString(value);
      if (s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
          > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
        throw new IllegalArgumentException();
      return s;
    } catch (Exception e) {
      throw new MSException("数据内容无效或超过2MB限制");
    }
  }

  public List<Map<String, Object>> categories() {
    var scope = scope(null, false);
    return jdbc.queryForList(
        "SELECT c.id,c.parent_id AS parentId,c.name,c.path,c.level,c.sort,(SELECT COUNT(*) FROM"
            + " data_asset d WHERE CAST(d.organization_id AS BINARY(256))=CAST(c.organization_id AS"
            + " BINARY(256)) AND CAST(d.category_id AS BINARY(256))=CAST(c.id AS BINARY(256)) AND"
            + " d.deleted=0) AS assetCount FROM test_asset_category c WHERE c.organization_id=? AND"
            + " c.deleted=0 ORDER BY c.level,c.sort,c.id",
        scope.organizationId());
  }

  private void validateContent(String format, Object value) {
    if ("TXT".equals(format) && !(value instanceof String)) throw new MSException("文本格式的数据内容必须是文本");
    if ("CSV".equals(format)) {
      if (!(value instanceof Map<?, ?> map)
          || !(map.get("rows") instanceof List<?> rows)
          || !validRows(rows, false)) throw new MSException("CSV数据需要完整的二维行数组");
    }
    if (Set.of("XLS", "XLSX").contains(format)) {
      if (!(value instanceof Map<?, ?> map)
          || !(map.get("sheets") instanceof List<?> sheets)
          || sheets.stream()
              .anyMatch(
                  sheet ->
                      !(sheet instanceof Map<?, ?> entry)
                          || !(entry.get("name") instanceof String)
                          || !(entry.get("rows") instanceof List<?>)))
        throw new MSException("工作簿数据需要包含名称和行数据的完整工作表");
      Set<String> names = new HashSet<>();
      for (var sheet : sheets) {
        var entry = (Map<?, ?>) sheet;
        String name = entry.get("name").toString();
        if (name.isBlank()
            || name.length() > 31
            || !names.add(name.toLowerCase(Locale.ROOT))
            || !validRows((List<?>) entry.get("rows"), true)) throw new MSException("工作表名称或行列数据无效");
      }
    }
  }

  private boolean validRows(List<?> rows, boolean dates) {
    if (rows.size() > DataAssetLimits.MAX_ROWS) return false;
    for (var row : rows) {
      if (!(row instanceof List<?> cells) || cells.size() > 16384) return false;
      for (var cell : cells) {
        if (dates && cell instanceof Map<?, ?> typed) {
          normalizeDate(typed);
          continue;
        }
        if (cell != null
            && !(cell instanceof String)
            && !(cell instanceof Boolean)
            && !(cell instanceof Number)) return false;
      }
    }
    return true;
  }

  private Map<String, Object> normalizeDate(Map<?, ?> cell) {
    try {
      if (!"DATE".equals(cell.get("type"))
          || !(cell.get("value") instanceof String value)
          || !(cell.get("date1904") instanceof Boolean date1904)
          || !(cell.get("format") instanceof String style)
          || style.length() > 255) throw new IllegalArgumentException();
      java.time.LocalDateTime date;
      try {
        date = java.time.LocalDateTime.parse(value);
      } catch (java.time.format.DateTimeParseException e) {
        date = java.time.LocalDate.parse(value).atStartOfDay();
      }
      double serial = org.apache.poi.ss.usermodel.DateUtil.getExcelDate(date, date1904);
      if (cell.get("serial") instanceof Number original
          && Double.isFinite(original.doubleValue())
          && org.apache.poi.ss.usermodel.DateUtil.isValidExcelDate(original.doubleValue())
          && org.apache.poi.ss.usermodel.DateUtil.getLocalDateTime(original.doubleValue(), date1904)
              .equals(date)) serial = original.doubleValue();
      if (!Double.isFinite(serial)
          || !org.apache.poi.ss.usermodel.DateUtil.isValidExcelDate(serial))
        throw new IllegalArgumentException();
      return Map.of(
          "type",
          "DATE",
          "value",
          date.toString(),
          "serial",
          serial,
          "date1904",
          date1904,
          "format",
          style);
    } catch (RuntimeException invalid) {
      throw new MSException("日期单元格格式无效，请使用ISO日期或日期时间");
    }
  }

  private Object normalizeContent(String format, Object value) {
    if (!Set.of("XLS", "XLSX").contains(format)) return value;
    var root = new LinkedHashMap<String, Object>((Map<String, Object>) value);
    List<Object> sheets = new ArrayList<>();
    for (var raw : (List<?>) root.get("sheets")) {
      var sheet = new LinkedHashMap<String, Object>((Map<String, Object>) raw);
      List<Object> rows = new ArrayList<>();
      for (var rawRow : (List<?>) sheet.get("rows")) {
        List<Object> cells = new ArrayList<>();
        for (var cell : (List<?>) rawRow)
          cells.add(cell instanceof Map<?, ?> typed ? normalizeDate(typed) : cell);
        rows.add(cells);
      }
      sheet.put("rows", rows);
      sheets.add(sheet);
    }
    root.put("sheets", sheets);
    return root;
  }

  public Map<String, Object> list(String keyword, String category, int page, int size) {
    return list(null, keyword, category, page, size);
  }

  public Map<String, Object> list(
      String project, String keyword, String category, int page, int size) {
    var s = scope(project, false);
    if (page < 1 || size < 1 || size > 100) throw new MSException("分页参数无效");
    String filter =
        " FROM data_asset WHERE organization_id=? AND deleted=0 AND name LIKE ? AND (?='' OR"
            + " category_id=? OR (?='__uncategorized__' AND (category_id IS NULL OR"
            + " category_id='')))";
    Object[] args = {
      s.organizationId(),
      "%" + Objects.toString(keyword, "") + "%",
      Objects.toString(category, ""),
      Objects.toString(category, "")
    };
    return Map.of(
        "total",
        jdbc.queryForObject(
            "SELECT COUNT(*)" + filter, Long.class, args[0], args[1], args[2], args[3], args[3]),
        "items",
        jdbc.queryForList(
            "SELECT id,name,format,(SELECT j.id FROM data_asset_parse_job j WHERE"
                + " j.version_id=data_asset.current_version_id LIMIT 1) AS parseJobId,(SELECT"
                + " v.parse_status FROM data_asset_version v WHERE"
                + " v.id=data_asset.current_version_id) AS parseStatus,(SELECT v.parse_error FROM"
                + " data_asset_version v WHERE v.id=data_asset.current_version_id) AS"
                + " parseError,EXISTS(SELECT 1 FROM data_asset_file f WHERE"
                + " f.version_id=data_asset.current_version_id) AS hasFile,description,create_user"
                + " AS owner,create_time AS createTime,update_time AS updateTime,category_id AS"
                + " categoryId,revision,current_version_id AS currentVersionId"
                + filter
                + " ORDER BY update_time DESC LIMIT ? OFFSET ?",
            s.organizationId(),
            args[1],
            args[2],
            args[3],
            args[3],
            size,
            (page - 1) * size));
  }

  public Map<String, Object> detail(String id) {
    return detail(null, id);
  }

  public Map<String, Object> detail(String project, String id) {
    var s = scope(project, false);
    var rows =
        jdbc.queryForList(
            "SELECT id,name,format,(SELECT j.id FROM data_asset_parse_job j WHERE"
                + " j.version_id=data_asset.current_version_id LIMIT 1) AS parseJobId,(SELECT"
                + " v.parse_status FROM data_asset_version v WHERE"
                + " v.id=data_asset.current_version_id) AS parseStatus,(SELECT v.parse_error FROM"
                + " data_asset_version v WHERE v.id=data_asset.current_version_id) AS"
                + " parseError,EXISTS(SELECT 1 FROM data_asset_file f WHERE"
                + " f.version_id=data_asset.current_version_id) AS hasFile,description,create_user"
                + " AS owner,create_time AS createTime,update_time AS updateTime,category_id AS"
                + " categoryId,revision,current_version_id AS currentVersionId FROM data_asset"
                + " WHERE id=? AND organization_id=? AND deleted=0",
            id,
            s.organizationId());
    if (rows.size() != 1) throw new MSException("数据资产不存在或无访问权限");
    var result = rows.getFirst();
    if (projectAccess != null) {
      var sources =
          jdbc.queryForList(
              "SELECT s.source_file_id AS sourceFileId,s.source_project_id AS"
                  + " sourceProjectId,s.source_version_id AS versionId,p.name AS sourceProjectName"
                  + " FROM data_asset_source s JOIN project p ON CAST(p.id AS"
                  + " BINARY(256))=CAST(s.source_project_id AS BINARY(256)) AND p.organization_id=?"
                  + " WHERE s.asset_id=?",
              s.organizationId(),
              id);
      result.put(
          "sources",
          sources.stream()
              .filter(
                  row ->
                      projectAccess.userHasPermission(
                          s.actorId(),
                          row.get("sourceProjectId").toString(),
                          io.metersphere.sdk.constants.PermissionConstants
                              .PROJECT_FILE_MANAGEMENT_READ))
              .toList());
      var references =
          jdbc.queryForList(
              "SELECT r.project_id AS projectId,p.name AS projectName,COUNT(*) AS referenceCount"
                  + " FROM test_asset_relation r JOIN project p ON CAST(p.id AS"
                  + " BINARY(256))=CAST(r.project_id AS BINARY(256)) AND p.organization_id=? WHERE"
                  + " r.source_asset_id=? OR r.target_asset_id=? GROUP BY r.project_id,p.name",
              s.organizationId(),
              id,
              id);
      result.put(
          "projectReferences",
          references.stream()
              .filter(
                  row ->
                      projectAccess.userHasPermission(
                          s.actorId(),
                          row.get("projectId").toString(),
                          io.metersphere.sdk.constants.PermissionConstants.TEST_PLAN_READ))
              .toList());
    }
    return result;
  }

  public List<Map<String, Object>> versions(String id) {
    return versions(null, id);
  }

  public List<Map<String, Object>> versions(String project, String id) {
    detail(project, id);
    var versions =
        jdbc.queryForList(
            "SELECT id,metadata_json AS metadataJson,metadata_source AS metadataSource,version_no"
                + " AS versionNo,create_time AS createTime,EXISTS(SELECT 1 FROM data_asset_file f"
                + " WHERE f.version_id=data_asset_version.id) AS hasFile FROM data_asset_version"
                + " WHERE asset_id=? ORDER BY version_no DESC",
            id);
    for (var item : versions) {
      Object raw = item.remove("metadataJson");
      item.put("metadata", DataAssetMetadata.decodeStored(raw));
      item.put("metadataAvailable", raw != null);
    }
    return versions;
  }

  public Map<String, Object> content(String id, String version) {
    return content(null, id, version);
  }

  public Map<String, Object> preview(String id, String version, int page, int size, int sheet) {
    if (page < 1 || size < 1 || size > 500 || sheet < 0) throw new MSException("预览分页参数无效");
    var fullResponse = content(id, version);
    Object full = fullResponse.get("content");
    Object rows = full;
    if (full instanceof Map<?, ?> map) {
      if (map.get("sheets") instanceof List<?> sheets) {
        if (sheet >= sheets.size()) throw new MSException("工作表不存在");
        rows = ((Map<?, ?>) sheets.get(sheet)).get("rows");
      } else if (map.get("rows") instanceof List<?>) rows = map.get("rows");
    }
    if (full instanceof String text) rows = Arrays.asList(text.split("\\R", -1));
    if (!(rows instanceof List<?> list)) return content(id, version);
    int from = (int) Math.min((long) (page - 1) * size, list.size()),
        to = Math.min(from + size, list.size());
    return Map.of(
        "content",
        list.subList(from, to),
        "total",
        list.size(),
        "page",
        page,
        "pageSize",
        size,
        "sheet",
        sheet,
        "complete",
        from == 0 && to == list.size(),
        "metadata",
        fullResponse.get("metadata"),
        "metadataAvailable",
        fullResponse.get("metadataAvailable"),
        "metadataSource",
        fullResponse.get("metadataSource"));
  }

  public Map<String, Object> content(String project, String id, String version) {
    var d = detail(project, id);
    String v = version == null ? (String) d.get("currentVersionId") : version;
    String parseStatus =
        jdbc.queryForObject(
            "SELECT parse_status FROM data_asset_version WHERE id=? AND asset_id=?",
            String.class,
            v,
            id);
    if (!"READY".equals(parseStatus))
      throw new MSException("FAILED".equals(parseStatus) ? "解析失败，请查看任务并重试" : "数据正在解析，请稍后刷新");
    var rows =
        jdbc.queryForList(
            "SELECT content_json FROM data_asset_version WHERE id=? AND asset_id=?", v, id);
    if (rows.size() != 1) throw new MSException("数据版本不存在");
    try {
      var result = new HashMap<String, Object>();
      result.put(
          "content",
          json.readValue(
              jdbc.queryForObject(
                  "SELECT content_json FROM data_asset_version WHERE id=? AND asset_id=?",
                  String.class,
                  v,
                  id),
              Object.class));
      String rawMetadata =
          jdbc.queryForObject(
              "SELECT metadata_json FROM data_asset_version WHERE id=? AND asset_id=?",
              String.class,
              v,
              id);
      result.put("metadata", DataAssetMetadata.decode(rawMetadata));
      result.put("metadataAvailable", rawMetadata != null);
      result.put(
          "metadataSource",
          jdbc.queryForObject(
              "SELECT metadata_source FROM data_asset_version WHERE id=?", String.class, v));
      result.put("complete", true);
      return result;
    } catch (Exception e) {
      throw new MSException("数据内容暂时无法读取");
    }
  }

  public Map<String, Object> save(String id, Map<String, Object> r) {
    return save(null, id, r);
  }

  public Map<String, Object> save(String project, String id, Map<String, Object> r) {
    if (Boolean.FALSE.equals(r.get("complete"))) throw new MSException("分页预览不能直接保存，请加载完整内容后编辑");
    var s = scope(project, true);
    boolean create = id == null;
    String requestId = Objects.toString(r.get("requestId"), "");
    if (requestId.length() > 128) throw new MSException("请求标识过长");
    String requestHash =
        org.apache.commons.codec.digest.DigestUtils.sha256Hex(encode(new TreeMap<>(r)));
    if (create && !requestId.isBlank()) {
      var prior =
          jdbc.queryForList(
              "SELECT asset_id,request_hash FROM data_asset_request WHERE organization_id=? AND"
                  + " actor_id=? AND request_id=?",
              s.organizationId(),
              s.actorId(),
              requestId);
      if (!prior.isEmpty()) {
        if (!requestHash.equals(prior.getFirst().get("request_hash")))
          throw new MSException("请求标识已用于不同内容");
        return detail(project, (String) prior.getFirst().get("asset_id"));
      }
    }
    String name = Objects.toString(r.get("name"), "").trim();
    String format = Objects.toString(r.get("format"), "JSON").toUpperCase(Locale.ROOT);
    if (create) {
      if (!r.containsKey("content") && !"JSON".equals(format)) throw new MSException("请提供完整的数据内容");
      if (name.isEmpty()
          || name.length() > 255
          || !Set.of("JSON", "TXT", "CSV", "XLS", "XLSX").contains(format))
        throw new MSException("名称或数据格式无效");
      id = UUID.randomUUID().toString();
    }
    Map<String, Object> old = create ? Map.of() : detail(project, id);
    if (!create) {
      if (!"READY".equals(old.get("parseStatus"))) throw new MSException("数据解析完成后才能编辑");
      if (r.containsKey("format")
          && !old.get("format").toString().equalsIgnoreCase(r.get("format").toString()))
        throw new MSException("编辑不能改变原数据格式");
      format = old.get("format").toString();
    }
    if (r.containsKey("content")) validateContent(format, r.get("content"));
    int revision = create ? 0 : ((Number) old.get("revision")).intValue();
    if (!create
        && (!(r.get("expectedRevision") instanceof Number)
            || ((Number) r.get("expectedRevision")).intValue() != revision))
      throw new MSException("数据已更新，请刷新后重试");
    if (r.get("categoryId") != null
        && !r.get("categoryId").toString().isBlank()
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM test_asset_category WHERE id=? AND organization_id=? AND"
                    + " deleted=0",
                Long.class,
                r.get("categoryId"),
                s.organizationId())
            != 1) throw new MSException("分类不存在或无访问权限");
    if (name.length() > 255) throw new MSException("名称不能超过255个字符");
    String body =
        encode(
            normalizeContent(
                format,
                r.containsKey("content")
                    ? r.get("content")
                    : create ? List.of() : content(project, id, null).get("content")));
    String version = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    if (create)
      jdbc.update(
          "INSERT INTO"
              + " data_asset(id,organization_id,name,format,category_id,revision,current_version_id,update_time)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          id,
          s.organizationId(),
          name,
          format,
          r.get("categoryId"),
          1,
          version,
          now);
    else {
      if (name.isEmpty()) name = (String) old.get("name");
      int n =
          jdbc.update(
              "UPDATE data_asset SET"
                  + " name=?,category_id=?,revision=revision+1,current_version_id=?,update_time=?"
                  + " WHERE id=? AND revision=? AND deleted=0",
              name,
              r.containsKey("categoryId") ? r.get("categoryId") : old.get("categoryId"),
              version,
              now,
              id,
              revision);
      if (n != 1) throw new MSException("数据已更新，请刷新后重试");
    }
    String description =
        Objects.toString(
            r.get("description"), create ? "" : Objects.toString(old.get("description"), ""));
    if (description.length() > 2000) throw new MSException("说明不能超过2000个字符");
    jdbc.update(
        "INSERT INTO"
            + " data_asset_version(id,asset_id,version_no,content_json,create_time,actor_id,metadata_json)"
            + " VALUES(?,?,?,?,?,?,?)",
        version,
        id,
        revision + 1,
        body,
        now,
        s.actorId(),
        DataAssetMetadata.encode(
            name,
            description,
            r.containsKey("categoryId") ? r.get("categoryId") : old.get("categoryId"),
            format));
    if (!create)
      jdbc.update(
          "INSERT INTO data_asset_file(version_id,storage_path,file_name) SELECT"
              + " ?,storage_path,file_name FROM data_asset_file WHERE version_id=?",
          version,
          old.get("currentVersionId"));
    jdbc.update("UPDATE data_asset SET description=? WHERE id=?", description, id);
    if (create)
      jdbc.update(
          "UPDATE data_asset SET create_user=?,create_time=? WHERE id=?", s.actorId(), now, id);
    if (create && !requestId.isBlank())
      jdbc.update(
          "INSERT INTO data_asset_request VALUES(?,?,?,?,?)",
          s.organizationId(),
          s.actorId(),
          requestId,
          requestHash,
          id);
    jdbc.update(
        "INSERT INTO data_asset_audit VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        s.organizationId(),
        id,
        version,
        s.actorId(),
        create ? "CREATE" : "UPDATE",
        now);
    return detail(project, id);
  }

  public void delete(String id, int revision) {
    delete(null, id, revision);
  }

  public void delete(String project, String id, int revision) {
    var actor = scope(project, true);
    detail(project, id);
    jdbc.queryForList("SELECT id FROM data_asset WHERE id=? FOR UPDATE", id);
    if (!jdbc.queryForList(
            "SELECT dataset_id FROM ai_test_data_lease WHERE dataset_id=? AND status='ACTIVE' AND"
                + " expires_at>? FOR UPDATE",
            id,
            System.currentTimeMillis())
        .isEmpty()) throw new MSException("数据正在被运行任务使用，请等待任务结束");
    if (!jdbc.queryForList(
            "SELECT t.id FROM test_asset_relation r JOIN ai_execution_task t ON"
                + " ((r.source_asset_type='TASK' AND CAST(r.source_asset_id AS"
                + " BINARY(256))=CAST(t.id AS BINARY(256)) AND r.target_asset_type='DATASET' AND"
                + " r.target_asset_id=?) OR (r.target_asset_type='TASK' AND CAST(r.target_asset_id"
                + " AS BINARY(256))=CAST(t.id AS BINARY(256)) AND r.source_asset_type='DATASET' AND"
                + " r.source_asset_id=?)) WHERE t.status IN"
                + " ('CREATED','WAITING_CONFIRMATION','PREPARING_BROWSER','WAITING_LOGIN','RUNNING','WRITING_BACK')"
                + " FOR UPDATE",
            id,
            id)
        .isEmpty()) throw new MSException("数据正在被运行任务引用，请等待任务结束");
    if (jdbc.update(
            "UPDATE data_asset SET deleted=1,revision=revision+1 WHERE id=? AND revision=? AND"
                + " deleted=0",
            id,
            revision)
        != 1) throw new MSException("数据已更新，请刷新后重试");
    jdbc.update(
        "INSERT INTO data_asset_audit VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        actor.organizationId(),
        id,
        null,
        actor.actorId(),
        "DELETE",
        System.currentTimeMillis());
  }

  public Map<String, Object> upload(byte[] bytes, String filename, Map<String, Object> request) {
    return upload(null, bytes, filename, request);
  }

  public Map<String, Object> upload(
      String project, byte[] bytes, String filename, Map<String, Object> request) {
    return uploadVersion(project, null, bytes, filename, request);
  }

  public Map<String, Object> uploadVersion(
      String project, String assetId, byte[] bytes, String filename, Map<String, Object> request) {
    var s = scope(project, true);
    String path = "data-assets/" + s.organizationId() + "/" + UUID.randomUUID();
    var file = new io.metersphere.sdk.file.FileRequest(path, "MINIO", "source");
    String diagnosticAsset = assetId,
        diagnosticVersion = null,
        storageStage = "version-source-upload";
    try {
      io.metersphere.sdk.file.FileCenter.getRepository("MINIO").saveFile(bytes, file);
      storageStage = "version-save";
      var d = save(project, assetId, request);
      diagnosticAsset = d.get("id").toString();
      diagnosticVersion = d.get("currentVersionId").toString();
      if (jdbc.queryForObject(
                  "SELECT COUNT(*) FROM data_asset_file WHERE version_id=?",
                  Long.class,
                  d.get("currentVersionId"))
              > 0
          && assetId == null) {
        storageStage = "version-source-replay-cleanup";
        io.metersphere.sdk.file.FileCenter.getRepository("MINIO").delete(file);
        return d;
      }
      if (assetId != null)
        jdbc.update("DELETE FROM data_asset_file WHERE version_id=?", d.get("currentVersionId"));
      jdbc.update(
          "INSERT INTO data_asset_file(version_id,storage_path,file_name) VALUES(?,?,?)",
          d.get("currentVersionId"),
          path,
          filename);
      String rollbackAsset = d.get("id").toString(),
          rollbackVersion = d.get("currentVersionId").toString();
      org.springframework.transaction.support.TransactionSynchronizationManager
          .registerSynchronization(
              new org.springframework.transaction.support.TransactionSynchronization() {
                public void afterCompletion(int status) {
                  if (status != STATUS_COMMITTED)
                    try {
                      io.metersphere.sdk.file.FileCenter.getRepository("MINIO").delete(file);
                    } catch (Exception cleanupFailure) {
                      DataAssetDiagnostics.storageFailure(
                          DataAssetService.class,
                          "DATA_ASSET_STORAGE_COMPENSATION_FAILURE",
                          rollbackAsset,
                          rollbackVersion,
                          "version-upload-rollback",
                          cleanupFailure);
                    }
                }
              });
      return d;
    } catch (Exception e) {
      try {
        io.metersphere.sdk.file.FileCenter.getRepository("MINIO").delete(file);
      } catch (Exception cleanupFailure) {
        DataAssetDiagnostics.storageFailure(
            DataAssetService.class,
            "DATA_ASSET_STORAGE_COMPENSATION_FAILURE",
            diagnosticAsset,
            diagnosticVersion,
            "version-upload-cleanup",
            cleanupFailure);
      }
      if (e instanceof MSException m && "version-save".equals(storageStage)) throw m;
      DataAssetDiagnostics.storageFailure(
          DataAssetService.class,
          "DATA_ASSET_VERSION_UPLOAD_FAILURE",
          diagnosticAsset,
          diagnosticVersion,
          storageStage,
          e);
      throw new MSException("文件存储失败，请稍后重试");
    }
  }

  public byte[] file(String id, String version) {
    return file(null, id, version);
  }

  public String fileName(String id, String version) {
    var d = detail(id);
    String v = version == null ? (String) d.get("currentVersionId") : version;
    var names =
        jdbc.queryForList(
            "SELECT f.file_name FROM data_asset_file f JOIN data_asset_version v ON"
                + " v.id=f.version_id WHERE v.asset_id=? AND v.id=?",
            String.class,
            id,
            v);
    if (names.size() != 1) throw new MSException("该版本没有原始文件");
    return names.getFirst().replaceAll("[\\r\\n\\x00-\\x1f/\\\\]", "_");
  }

  public byte[] file(String project, String id, String version) {
    var d = detail(project, id);
    String v = version == null ? (String) d.get("currentVersionId") : version;
    var rows =
        jdbc.queryForList(
            "SELECT f.storage_path FROM data_asset_file f JOIN data_asset_version v ON"
                + " v.id=f.version_id WHERE v.asset_id=? AND v.id=?",
            id,
            v);
    if (rows.size() != 1) throw new MSException("该版本没有原始文件");
    try {
      return io.metersphere.sdk.file.FileCenter.getRepository("MINIO")
          .getFile(
              new io.metersphere.sdk.file.FileRequest(
                  (String) rows.getFirst().get("storage_path"), "MINIO", "source"));
    } catch (Exception e) {
      DataAssetDiagnostics.storageFailure(
          DataAssetService.class,
          "DATA_ASSET_SOURCE_DOWNLOAD_FAILURE",
          id,
          v,
          "source-download",
          e);
      throw new MSException("原始文件暂时无法下载");
    }
  }
}
