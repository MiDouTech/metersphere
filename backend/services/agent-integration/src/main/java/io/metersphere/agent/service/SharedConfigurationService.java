package io.metersphere.agent.service;

import io.metersphere.agent.security.SharedConfigurationCipher;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class SharedConfigurationService {
  private final JdbcTemplate jdbc;
  private final BusinessAssetService authorization;
  private final SharedConfigurationCipher cipher;
  private final GlobalAssetAccess global;

  public SharedConfigurationService(
      JdbcTemplate jdbc, BusinessAssetService authorization, SharedConfigurationCipher cipher) {
    this.jdbc = jdbc;
    this.authorization = authorization;
    this.cipher = cipher;
    this.global = authorization.globalAccess();
  }

  private String authorize(String project, boolean write) {
    return global
        .authorize(project, write ? "SHARED_CONFIGURATION:WRITE" : "SHARED_CONFIGURATION:READ")
        .actorId();
  }

  public List<Map<String, Object>> list(String project) {
    authorize(project, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT c.id,c.system_key AS systemKey,c.environment_key AS"
                + " environmentKey,c.name,c.current_version AS currentVersion,v.kind,v.file_name AS"
                + " fileName,v.parse_status AS parseStatus,v.secret_type AS secretType FROM"
                + " shared_configuration c JOIN shared_configuration_version v ON"
                + " v.configuration_id=c.id AND v.version_no=c.current_version WHERE"
                + " c.organization_id=? ORDER BY c.system_key,c.environment_key,c.name",
            organization(project)),
        "id",
        "systemKey",
        "environmentKey",
        "name",
        "currentVersion",
        "kind",
        "fileName",
        "parseStatus",
        "secretType");
  }

  public List<Map<String, Object>> batch(
      List<io.metersphere.agent.dto.SharedConfigurationSave> entries) {
    if (entries.isEmpty()
        || entries.size() > 100
        || entries.stream().map(entry -> organization(entry.projectId())).distinct().count() != 1
        || entries.stream().mapToLong(r -> Objects.toString(r.value(), "").length()).sum()
            > 2800000)
      throw new BusinessGovernanceException(
          "CONFIGURATION_BATCH_INVALID", "每批最多 100 条且只允许同一组织，配置内容总量不能超过 2 MB");
    var result = new ArrayList<Map<String, Object>>();
    for (var r : entries)
      result.add(
          saveStructured(
              r.projectId(),
              r.id(),
              r.systemKey(),
              r.environmentKey(),
              r.name(),
              r.kind(),
              r.value(),
              r.fileName(),
              r.expectedVersion(),
              r.secretType(),
              r.keepSecret()));
    return result;
  }

  public Map<String, Object> save(
      String project,
      String id,
      String system,
      String environment,
      String name,
      String kind,
      String value,
      String filename,
      int expectedVersion) {
    return saveStructured(
        project,
        id,
        system,
        environment,
        name,
        kind,
        value,
        filename,
        expectedVersion,
        null,
        false);
  }

  public Map<String, Object> saveStructured(
      String project,
      String id,
      String system,
      String environment,
      String name,
      String kind,
      String value,
      String filename,
      int expectedVersion,
      String secretType,
      boolean keepSecret) {
    String actor = authorize(project, true);
    if (!Set.of("PLAIN", "SECRET", "DOCUMENT").contains(kind))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置类型无效");
    jdbc.queryForList("SELECT id FROM organization WHERE id=? FOR UPDATE", organization(project));
    if (keepSecret) {
      if (!"SECRET".equals(kind)
          || id == null
          || id.isBlank()
          || (value != null && !value.isEmpty()))
        throw new BusinessGovernanceException(
            "GOVERNANCE_VALIDATION_ERROR", "仅已有敏感配置允许保留原值，且不能同时提交替换值");
      var existing = configuration(project, id, true);
      if (((Number) existing.get("current_version")).intValue() != expectedVersion)
        throw new BusinessGovernanceException("CONFIGURATION_VERSION_CONFLICT", "配置已变化，请刷新后重新编辑");
      var stored =
          jdbc.queryForMap(
              "SELECT kind,secret_type,encrypted_value FROM shared_configuration_version WHERE"
                  + " configuration_id=? AND version_no=?",
              id,
              expectedVersion);
      if (!"SECRET".equals(stored.get("kind")))
        throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "原配置不是敏感配置，请输入新内容");
      secretType = (String) stored.get("secret_type");
      value =
          new String(
              cipher.decrypt(
                  (String) stored.get("encrypted_value"),
                  aadProject(project, id) + ":" + id + ":" + expectedVersion),
              StandardCharsets.UTF_8);
    }
    if (value == null || value.isBlank())
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置内容不能为空");
    if (secretType != null && !secretType.isEmpty()) {
      if (!"SECRET".equals(kind))
        throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "仅敏感配置可指定敏感分类");
      validateSecret(secretType, value);
    }
    byte[] bytes;
    try {
      bytes =
          "DOCUMENT".equals(kind)
              ? Base64.getDecoder().decode(value)
              : value.getBytes(StandardCharsets.UTF_8);
    } catch (IllegalArgumentException invalid) {
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置文档编码无效");
    }
    if (bytes.length > 2 * 1024 * 1024 || bytes.length == 0)
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置内容不能为空，且不能超过 2 MB");
    if ("PLAIN".equals(kind)
        && (io.metersphere.agent.security.BusinessAssetSafety.containsSecret(value)
            || name.matches(
                "(?i).*(password|passwd|token|secret|api.?key|authorization|cookie|username|account|账号|账户|密码|口令|密钥).*")))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "检测到敏感内容，请使用敏感配置类型");
    int next;
    if (id == null || id.isBlank()) {
      if (expectedVersion != 0)
        throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "新配置版本必须为零");
      Long duplicate =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM shared_configuration WHERE organization_id=? AND system_key=?"
                  + " AND environment_key=? AND name=?",
              Long.class,
              organization(project),
              system,
              environment,
              name);
      if (duplicate != null && duplicate > 0)
        throw new BusinessGovernanceException(
            "GOVERNANCE_VALIDATION_ERROR", "同一系统和环境下配置名称不能重复，请编辑已有配置");
      id = UUID.randomUUID().toString();
      next = 1;
      jdbc.update(
          "INSERT INTO"
              + " shared_configuration(id,project_id,organization_id,system_key,environment_key,name,current_version)"
              + " VALUES(?,?,?,?,?,?,?)",
          id,
          Objects.toString(project, ""),
          organization(project),
          system,
          environment,
          name,
          next);
    } else {
      var old = configuration(project, id, true);
      if (((Number) old.get("current_version")).intValue() != expectedVersion)
        throw new BusinessGovernanceException("CONFIGURATION_VERSION_CONFLICT", "配置已变化，请刷新后重新编辑");
      Long duplicate =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM shared_configuration WHERE organization_id=? AND system_key=?"
                  + " AND environment_key=? AND name=? AND id<>?",
              Long.class,
              organization(project),
              system,
              environment,
              name,
              id);
      if (duplicate != null && duplicate > 0)
        throw new BusinessGovernanceException(
            "GOVERNANCE_VALIDATION_ERROR", "同一系统和环境下配置名称不能重复，请编辑已有配置");
      next =
          jdbc.queryForObject(
              "SELECT MAX(version_no)+1 FROM shared_configuration_version WHERE configuration_id=?",
              Integer.class,
              id);
      jdbc.update(
          "UPDATE shared_configuration SET system_key=?,environment_key=?,name=?,current_version=?"
              + " WHERE id=?",
          system,
          environment,
          name,
          next,
          id);
    }
    String parse =
        "DOCUMENT".equals(kind) ? parse(bytes, filename, id + ":" + next) : "NOT_APPLICABLE";
    jdbc.update(
        "INSERT INTO"
            + " shared_configuration_version(configuration_id,version_no,kind,encrypted_value,file_name,parse_status,actor_id,create_time,secret_type)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        next,
        kind,
        cipher.encrypt(bytes, aadProject(project, id) + ":" + id + ":" + next),
        "DOCUMENT".equals(kind) ? Objects.toString(filename, "") : "",
        parse,
        actor,
        System.currentTimeMillis(),
        Objects.toString(secretType, ""));
    audit(id, next, actor, "SAVE");
    return Map.of("id", id, "version", next, "parseStatus", parse);
  }

  public List<Map<String, Object>> versions(String project, String id) {
    authorize(project, false);
    configuration(project, id, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT version_no AS versionNo,kind,file_name AS fileName,parse_status AS"
                + " parseStatus,actor_id AS actorId,create_time AS createTime,secret_type AS"
                + " secretType FROM shared_configuration_version WHERE configuration_id=? ORDER BY"
                + " version_no DESC",
            id),
        "versionNo",
        "kind",
        "fileName",
        "parseStatus",
        "actorId",
        "createTime",
        "secretType");
  }

  /** Read-only audit metadata. Configuration contents and ciphertext are never selected. */
  @Transactional(readOnly = true)
  public Map<String, Object> audits(
      String project, String id, int page, int pageSize, String eventType, Integer version) {
    authorize(project, false);
    if ((project != null && project.length() > 64)
        || id == null
        || id.isBlank()
        || id.length() > 64
        || page < 1
        || page > 1000000
        || pageSize < 1
        || pageSize > 100
        || (version != null && version < 1)
        || (eventType != null
            && !Set.of("SAVE", "SELECT_VERSION", "EXECUTOR_RESOLVE", "RETRY_PARSE")
                .contains(eventType))) {
      throw new BusinessGovernanceException(
          "GOVERNANCE_VALIDATION_ERROR", "审计查询参数无效，请检查页码、每页条数、事件类型和版本");
    }
    configuration(project, id, false);
    var params = new ArrayList<Object>();
    params.add(id);
    params.add(organization(project));
    String where =
        " FROM shared_configuration_audit a JOIN shared_configuration c ON c.id=a.configuration_id"
            + " WHERE a.configuration_id=? AND c.organization_id=?";
    if (eventType != null) {
      where += " AND a.event_type=?";
      params.add(eventType);
    }
    if (version != null) {
      where += " AND a.version_no=?";
      params.add(version);
    }
    Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, params.toArray());
    params.add(pageSize);
    params.add(((long) page - 1) * pageSize);
    var items =
        BusinessAssetRows.canonical(
            jdbc.queryForList(
                "SELECT a.id,a.configuration_id AS configurationId,a.version_no AS"
                    + " versionNo,a.actor_id AS actorId,a.event_type AS eventType,a.create_time AS"
                    + " createTime,a.reason"
                    + where
                    + " ORDER BY a.create_time DESC,a.id DESC LIMIT ? OFFSET ?",
                params.toArray()),
            "id",
            "configurationId",
            "versionNo",
            "actorId",
            "eventType",
            "createTime",
            "reason");
    return Map.of(
        "items", items, "total", total == null ? 0L : total, "page", page, "pageSize", pageSize);
  }

  public Map<String, Object> read(String project, String id, int version, boolean document) {
    authorize(project, false);
    configuration(project, id, false);
    var rows =
        jdbc.queryForList(
            "SELECT kind,encrypted_value,file_name FROM shared_configuration_version WHERE"
                + " configuration_id=? AND version_no=?",
            id,
            version);
    if (rows.isEmpty())
      throw new BusinessGovernanceException("CONFIGURATION_VERSION_NOT_FOUND", "配置版本不存在");
    var row = rows.getFirst();
    String kind = (String) row.get("kind");
    if ("SECRET".equals(kind)) return Map.of("kind", kind, "value", "******");
    if ("DOCUMENT".equals(kind) && !document) return Map.of("kind", kind, "value", "配置文档需授权下载");
    // Documents may contain passwords: download requires write permission and never exposes them in
    // list/version JSON.
    if (document) authorize(project, true);
    byte[] value =
        cipher.decrypt(
            (String) row.get("encrypted_value"),
            aadProject(project, id) + ":" + id + ":" + version);
    return Map.of(
        "kind",
        kind,
        "value",
        document
            ? Base64.getEncoder().encodeToString(value)
            : new String(value, StandardCharsets.UTF_8),
        "fileName",
        row.get("file_name"));
  }

  public void select(String project, String id, int version, int expected) {
    select(project, id, version, expected, null);
  }

  public void select(String project, String id, int version, int expected, String reason) {
    String actor = authorize(project, true);
    var row = configuration(project, id, true);
    if (((Number) row.get("current_version")).intValue() != expected)
      throw new BusinessGovernanceException("CONFIGURATION_VERSION_CONFLICT", "配置已变化，请刷新后重试");
    if (((Number) row.get("current_version")).intValue() == version) return;
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM shared_configuration_version WHERE configuration_id=? AND"
                + " version_no=?",
            Long.class,
            id,
            version);
    if (count == null || count != 1)
      throw new BusinessGovernanceException("CONFIGURATION_VERSION_NOT_FOUND", "配置版本不存在");
    if (reason != null && (reason.isBlank() || reason.length() > 500))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "切换原因需为1至500字符");
    jdbc.update("UPDATE shared_configuration SET current_version=? WHERE id=?", version, id);
    audit(id, version, actor, "SELECT_VERSION", reason == null ? "历史客户端切换版本" : reason.trim());
  }

  public Map<String, Object> resolveForExecutor(String project, String session, String id) {
    String actor =
        authorization.authorize(
            project, true, "SHARED_CONFIGURATION:RESOLVE", "SHARED_CONFIGURATION_RESOLVE");
    authorization.requireProjectAccess(actor, project, true);
    var token = io.metersphere.agent.security.AgentTokenContext.get();
    if (token == null) throw AgentBusinessAccess.denied();
    var rows =
        jdbc.queryForList(
            """
SELECT v.version_no,v.kind,v.encrypted_value,v.file_name,v.secret_type
FROM external_executor_session s JOIN external_executor_configuration pin ON pin.session_id=s.id
JOIN shared_configuration c ON c.id=pin.configuration_id AND c.organization_id=(SELECT organization_id FROM project WHERE id=s.project_id)
JOIN shared_configuration_version v ON v.configuration_id=c.id AND v.version_no=pin.version_no
WHERE s.id=? AND s.project_id=? AND s.token_id=? AND s.actor_id=? AND c.id=? AND s.status='RUNNING'
""",
            session,
            project,
            token.getId(),
            actor,
            id);
    if (rows.size() != 1) throw AgentBusinessAccess.denied();
    var row = rows.getFirst();
    int version = ((Number) row.get("version_no")).intValue();
    byte[] bytes =
        cipher.decrypt(
            (String) row.get("encrypted_value"),
            aadProject(project, id) + ":" + id + ":" + version);
    audit(id, version, actor, "EXECUTOR_RESOLVE");
    return Map.of(
        "version",
        version,
        "kind",
        row.get("kind"),
        "fileName",
        row.get("file_name"),
        "secretType",
        Objects.toString(row.get("secret_type"), ""),
        "value",
        "DOCUMENT".equals(row.get("kind"))
            ? Base64.getEncoder().encodeToString(bytes)
            : new String(bytes, StandardCharsets.UTF_8));
  }

  public String retryParse(String project, String id, int version) {
    String actor = authorize(project, true);
    configuration(project, id, true);
    var rows =
        jdbc.queryForList(
            "SELECT encrypted_value,file_name FROM shared_configuration_version WHERE"
                + " configuration_id=? AND version_no=? AND kind='DOCUMENT'",
            id,
            version);
    if (rows.isEmpty())
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置文档版本不存在");
    var row = rows.getFirst();
    String status =
        parse(
            cipher.decrypt(
                (String) row.get("encrypted_value"),
                aadProject(project, id) + ":" + id + ":" + version),
            (String) row.get("file_name"),
            id + ":" + version);
    jdbc.update(
        "UPDATE shared_configuration_version SET parse_status=? WHERE configuration_id=? AND"
            + " version_no=?",
        status,
        id,
        version);
    audit(id, version, actor, "RETRY_PARSE");
    return status;
  }

  private void validateSecret(String type, String value) {
    if (!Set.of("ACCOUNT_PASSWORD", "OTHER").contains(type))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "敏感配置分类无效");
    try {
      var payload =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
              .readTree(value);
      var fields = payload.get("fields");
      if (!payload.isObject()
          || payload.size() != 1
          || fields == null
          || !fields.isArray()
          || fields.isEmpty()
          || fields.size() > 20) throw new IllegalArgumentException();
      Set<String> names = new HashSet<>();
      for (var field : fields) {
        if (!field.isObject()
            || field.size() != 2
            || !field.path("name").isTextual()
            || !field.path("value").isTextual()) throw new IllegalArgumentException();
        String name = field.get("name").asText();
        String content = field.get("value").asText();
        if (name.isBlank()
            || name.length() > 64
            || !name.equals(name.trim())
            || !names.add(name)
            || content.isBlank()
            || content.length() > 100000) throw new IllegalArgumentException();
      }
      if ("ACCOUNT_PASSWORD".equals(type) && !names.containsAll(Set.of("username", "password")))
        throw new IllegalArgumentException();
    } catch (Exception invalid) {
      io.metersphere.agent.security.AgentSafeDiagnostics.failure(
          org.slf4j.LoggerFactory.getLogger(SharedConfigurationService.class),
          "CONFIGURATION_FIELDS_INVALID",
          "VALIDATE",
          "structured-secret",
          "GOVERNANCE_VALIDATION_ERROR",
          invalid);
      throw new BusinessGovernanceException(
          "GOVERNANCE_VALIDATION_ERROR",
          "敏感字段需为1至20行，名称不可重复且最多64字符，值不能为空且最多100000字符；账号密码类必须包含账号和密码");
    }
  }

  private String parse(byte[] bytes, String filename, String reference) {
    if (filename == null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(md|txt|json)$"))
      return "UNSUPPORTED";
    try {
      var text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      if (filename.toLowerCase(Locale.ROOT).endsWith(".json"))
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
      return "PARSED";
    } catch (Exception invalid) {
      io.metersphere.agent.security.AgentSafeDiagnostics.failure(
          org.slf4j.LoggerFactory.getLogger(SharedConfigurationService.class),
          "CONFIGURATION_PARSE_FAILED",
          "PARSE",
          reference,
          "CONFIGURATION_DOCUMENT_PARSE_FAILED",
          invalid);
      return "FAILED";
    }
  }

  private String organization(String project) {
    return global.authorize(project, "SHARED_CONFIGURATION:READ").organizationId();
  }

  private String aadProject(String project, String id) {
    return Objects.toString(configuration(project, id, false).get("project_id"), "");
  }

  private Map<String, Object> configuration(String project, String id, boolean lock) {
    var rows =
        jdbc.queryForList(
            "SELECT current_version,project_id FROM shared_configuration WHERE id=? AND"
                + " organization_id=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            organization(project));
    if (rows.isEmpty())
      throw new BusinessGovernanceException("CONFIGURATION_NOT_ACCESSIBLE", "公共配置不存在或无权访问");
    return rows.getFirst();
  }

  private void audit(String id, int version, String actor, String event) {
    audit(id, version, actor, event, "");
  }

  private void audit(String id, int version, String actor, String event, String reason) {
    jdbc.update(
        "INSERT INTO"
            + " shared_configuration_audit(id,configuration_id,version_no,actor_id,event_type,create_time,reason)"
            + " VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        version,
        actor,
        event,
        System.currentTimeMillis(),
        reason);
  }
}
