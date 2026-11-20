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

  public SharedConfigurationService(
      JdbcTemplate jdbc, BusinessAssetService authorization, SharedConfigurationCipher cipher) {
    this.jdbc = jdbc;
    this.authorization = authorization;
    this.cipher = cipher;
  }

  private String authorize(String project, boolean write) {
    return authorization.authorize(
        project,
        write,
        write ? "SHARED_CONFIGURATION:WRITE" : "SHARED_CONFIGURATION:READ",
        write ? "SHARED_CONFIGURATION_WRITE" : "SHARED_CONFIGURATION_READ");
  }

  public List<Map<String, Object>> list(String project) {
    authorize(project, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT c.id,c.system_key AS systemKey,c.environment_key AS"
                + " environmentKey,c.name,c.current_version AS currentVersion,v.kind,v.file_name AS"
                + " fileName,v.parse_status AS parseStatus FROM shared_configuration c JOIN"
                + " shared_configuration_version v ON v.configuration_id=c.id AND"
                + " v.version_no=c.current_version WHERE c.project_id=? ORDER BY"
                + " c.system_key,c.environment_key,c.name",
            project),
        "id",
        "systemKey",
        "environmentKey",
        "name",
        "currentVersion",
        "kind",
        "fileName",
        "parseStatus");
  }

  public List<Map<String, Object>> batch(
      List<io.metersphere.agent.dto.SharedConfigurationSave> entries) {
    if (entries.isEmpty()
        || entries.size() > 100
        || entries.stream()
                .map(io.metersphere.agent.dto.SharedConfigurationSave::projectId)
                .distinct()
                .count()
            != 1
        || entries.stream().mapToLong(r -> r.value().length()).sum() > 2800000)
      throw new BusinessGovernanceException(
          "CONFIGURATION_BATCH_INVALID", "每批最多 100 条且只允许同一项目，配置内容总量不能超过 2 MB");
    var result = new ArrayList<Map<String, Object>>();
    for (var r : entries)
      result.add(
          save(
              r.projectId(),
              r.id(),
              r.systemKey(),
              r.environmentKey(),
              r.name(),
              r.kind(),
              r.value(),
              r.fileName(),
              r.expectedVersion()));
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
    String actor = authorize(project, true);
    if (!Set.of("PLAIN", "SECRET", "DOCUMENT").contains(kind))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "配置类型无效");
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
    jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", project);
    int next;
    if (id == null || id.isBlank()) {
      if (expectedVersion != 0)
        throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "新配置版本必须为零");
      Long duplicate =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM shared_configuration WHERE project_id=? AND system_key=? AND"
                  + " environment_key=? AND name=?",
              Long.class,
              project,
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
              + " shared_configuration(id,project_id,system_key,environment_key,name,current_version)"
              + " VALUES(?,?,?,?,?,?)",
          id,
          project,
          system,
          environment,
          name,
          next);
    } else {
      var old = configuration(project, id, true);
      if (((Number) old.get("current_version")).intValue() != expectedVersion)
        throw new BusinessGovernanceException("CONFIGURATION_VERSION_CONFLICT", "配置已变化，请刷新后重新编辑");
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
            + " shared_configuration_version(configuration_id,version_no,kind,encrypted_value,file_name,parse_status,actor_id,create_time)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        next,
        kind,
        cipher.encrypt(bytes, project + ":" + id + ":" + next),
        Objects.toString(filename, ""),
        parse,
        actor,
        System.currentTimeMillis());
    audit(id, next, actor, "SAVE");
    return Map.of("id", id, "version", next, "parseStatus", parse);
  }

  public List<Map<String, Object>> versions(String project, String id) {
    authorize(project, false);
    configuration(project, id, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT version_no AS versionNo,kind,file_name AS fileName,parse_status AS"
                + " parseStatus,actor_id AS actorId,create_time AS createTime FROM"
                + " shared_configuration_version WHERE configuration_id=? ORDER BY version_no DESC",
            id),
        "versionNo",
        "kind",
        "fileName",
        "parseStatus",
        "actorId",
        "createTime");
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
        cipher.decrypt((String) row.get("encrypted_value"), project + ":" + id + ":" + version);
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
    String actor = authorize(project, true);
    var row = configuration(project, id, true);
    if (((Number) row.get("current_version")).intValue() != expected)
      throw new BusinessGovernanceException("CONFIGURATION_VERSION_CONFLICT", "配置已变化，请刷新后重试");
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM shared_configuration_version WHERE configuration_id=? AND"
                + " version_no=?",
            Long.class,
            id,
            version);
    if (count == null || count != 1)
      throw new BusinessGovernanceException("CONFIGURATION_VERSION_NOT_FOUND", "配置版本不存在");
    jdbc.update("UPDATE shared_configuration SET current_version=? WHERE id=?", version, id);
    audit(id, version, actor, "SELECT_VERSION");
  }

  public Map<String, Object> resolveForExecutor(String project, String session, String id) {
    String actor =
        authorization.authorize(
            project, true, "SHARED_CONFIGURATION:RESOLVE", "SHARED_CONFIGURATION_RESOLVE");
    var token = io.metersphere.agent.security.AgentTokenContext.get();
    if (token == null) throw AgentBusinessAccess.denied();
    var rows =
        jdbc.queryForList(
            """
SELECT v.version_no,v.kind,v.encrypted_value,v.file_name
FROM external_executor_session s JOIN external_executor_configuration pin ON pin.session_id=s.id
JOIN shared_configuration c ON c.id=pin.configuration_id AND c.project_id=s.project_id
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
        cipher.decrypt((String) row.get("encrypted_value"), project + ":" + id + ":" + version);
    audit(id, version, actor, "EXECUTOR_RESOLVE");
    return Map.of(
        "version",
        version,
        "kind",
        row.get("kind"),
        "fileName",
        row.get("file_name"),
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
            cipher.decrypt((String) row.get("encrypted_value"), project + ":" + id + ":" + version),
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

  private Map<String, Object> configuration(String project, String id, boolean lock) {
    var rows =
        jdbc.queryForList(
            "SELECT current_version FROM shared_configuration WHERE id=? AND project_id=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            project);
    if (rows.isEmpty())
      throw new BusinessGovernanceException("CONFIGURATION_NOT_ACCESSIBLE", "公共配置不存在或无权访问");
    return rows.getFirst();
  }

  private void audit(String id, int version, String actor, String event) {
    jdbc.update(
        "INSERT INTO"
            + " shared_configuration_audit(id,configuration_id,version_no,actor_id,event_type,create_time)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        id,
        version,
        actor,
        event,
        System.currentTimeMillis());
  }
}
