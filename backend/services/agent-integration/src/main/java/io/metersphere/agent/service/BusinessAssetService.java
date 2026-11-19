package io.metersphere.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.metersphere.agent.dto.BusinessAssetSubmission;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.utils.SessionUtils;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class BusinessAssetService {
  public static final String READ = "BUSINESS_ASSET:READ";
  public static final String WRITE = "BUSINESS_ASSET:WRITE";
  private final JdbcTemplate jdbc;
  private final AgentBusinessAccess access;

  public BusinessAssetService(JdbcTemplate jdbc, AgentBusinessAccess access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  String authorize(String project, boolean write) {
    return authorize(
        project,
        write,
        write ? WRITE : READ,
        write ? "BUSINESS_ASSET_WRITE" : "BUSINESS_ASSET_READ");
  }

  String authorize(String project, boolean write, String permission, String scope) {
    String user;
    if (AgentTokenContext.get() != null) {
      var token = access.activeToken(scope);
      if (!io.metersphere.agent.security.AgentTokenProjectAccess.allows(token, project))
        throw AgentBusinessAccess.denied();
      user = token.getUserId();
    } else {
      user = SessionUtils.getUserId();
      if (user == null || !SecurityUtils.getSubject().isAuthenticated())
        throw AgentBusinessAccess.denied();
    }
    if (!access.userHasPermission(user, project, permission)) {
      Long scopedAdmin =
          jdbc.queryForObject(
              """
SELECT COUNT(*) FROM user u JOIN user_role_relation r ON r.user_id=u.id
JOIN user_role role ON role.id=r.role_id AND role.enabled=1
JOIN project p ON p.id=? WHERE u.id=? AND u.enable=1 AND u.deleted=0 AND
((role.id='project_admin' AND role.type='PROJECT' AND r.source_id=p.id)
OR (role.id='org_admin' AND role.type='ORGANIZATION' AND r.source_id=p.organization_id))
""",
              Long.class,
              project,
              user);
      if (scopedAdmin == null || scopedAdmin < 1) throw AgentBusinessAccess.denied();
    }
    Long active =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM project WHERE id=? AND deleted=0"
                + (write ? " AND enable=1" : ""),
            Long.class,
            project);
    if (active == null || active != 1) throw AgentBusinessAccess.denied();
    return user;
  }

  public List<Map<String, Object>> list(String project, String keyword, boolean archived) {
    String actor = authorize(project, false);
    return BusinessAssetRows.canonical(
            jdbc.queryForList(
                """
SELECT a.id,a.revision,a.archived,a.current_version_id AS currentVersionId,
       v.title,v.system_key AS systemKey,v.module_key AS moduleKey,v.flow_key AS flowKey,
       v.page_keys_json AS pageKeysJson,v.branch_count AS branchCount,v.version_no AS versionNo,v.status,a.update_time AS updateTime,
       (SELECT COUNT(*) FROM business_asset_reader restriction WHERE restriction.asset_id=a.id) AS restrictedCount
FROM business_asset a LEFT JOIN business_asset_version v ON v.id=COALESCE(a.current_version_id,
    (SELECT x.id FROM business_asset_version x WHERE x.asset_id=a.id ORDER BY x.version_no DESC LIMIT 1))
WHERE a.project_id=? AND a.archived=? AND (?='' OR LOWER(v.title) LIKE ?)
  AND (NOT EXISTS(SELECT 1 FROM business_asset_reader r WHERE r.asset_id=a.id)
       OR EXISTS(SELECT 1 FROM business_asset_reader r WHERE r.asset_id=a.id AND r.user_id=?))
ORDER BY a.update_time DESC,a.id
""",
                project,
                archived,
                safeKeyword(keyword),
                "%" + safeKeyword(keyword).toLowerCase(Locale.ROOT) + "%",
                actor),
            "id",
            "revision",
            "archived",
            "currentVersionId",
            "title",
            "systemKey",
            "moduleKey",
            "flowKey",
            "pageKeysJson",
            "branchCount",
            "versionNo",
            "status",
            "updateTime",
            "restrictedCount")
        .stream()
        .map(
            row -> {
              Object pages = row.remove("pageKeysJson");
              row.put(
                  "pageKeys",
                  pages == null ? List.of() : JSON.parseArray(pages.toString(), String.class));
              row.put("restricted", ((Number) row.remove("restrictedCount")).longValue() > 0);
              return row;
            })
        .toList();
  }

  private String safeKeyword(String value) {
    return value == null ? "" : value.substring(0, Math.min(value.length(), 255));
  }

  public List<Map<String, Object>> unassignedLegacy(String project) {
    String actor = authorize(project, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            """
SELECT k.id,JSON_UNQUOTE(JSON_EXTRACT(k.payload,'$.title')) AS title,
       (SELECT COUNT(*) FROM retired_msp_knowledge_version v WHERE v.knowledge_id=k.id) AS versionCount
FROM retired_msp_knowledge k JOIN project p ON p.organization_id=k.organization_id AND p.id=?
WHERE k.creator_id=? AND COALESCE(JSON_LENGTH(JSON_EXTRACT(k.payload,'$.sources')),0)=0
  AND NOT EXISTS(SELECT 1 FROM retired_msp_knowledge_version v WHERE v.knowledge_id=k.id
      AND COALESCE(JSON_LENGTH(JSON_EXTRACT(v.payload,'$.sources')),0)>0)
  AND NOT EXISTS(SELECT 1 FROM business_asset a WHERE a.id=SHA2(CONCAT(k.id,':',p.id),256))
ORDER BY k.update_time DESC,k.id LIMIT 1000
""",
            project,
            actor),
        "id",
        "title",
        "versionCount");
  }

  public Map<String, String> importUnassignedLegacy(String project, String legacyId) {
    String actor = authorize(project, true);
    jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", project);
    String id = DigestUtils.sha256Hex(legacyId + ":" + project);
    var existing =
        jdbc.queryForList(
            "SELECT id FROM business_asset WHERE project_id=? AND id=?", String.class, project, id);
    if (!existing.isEmpty()) {
      asset(project, id, false);
      return Map.of("assetId", id);
    }
    boolean allowed =
        unassignedLegacy(project).stream().anyMatch(row -> legacyId.equals(row.get("id")));
    if (!allowed) throw AgentBusinessAccess.denied();
    var versions =
        jdbc.queryForList(
            "SELECT version,payload,content_hash,author_id,create_time FROM"
                + " retired_msp_knowledge_version WHERE knowledge_id=? ORDER BY version",
            legacyId);
    if (versions.isEmpty())
      throw new BusinessGovernanceException("LEGACY_VERSION_NOT_FOUND", "历史业务理解没有可导入的版本");
    long now = System.currentTimeMillis();
    jdbc.update(
        "INSERT INTO"
            + " business_asset(id,project_id,identity_key,current_version_id,revision,archived,create_time,update_time)"
            + " VALUES(?,?,?,NULL,0,FALSE,?,?)",
        id,
        project,
        DigestUtils.sha256Hex("legacy:" + legacyId + ":" + project),
        now,
        now);
    for (var old : versions) {
      int version = ((Number) old.get("version")).intValue();
      Map<String, Object> original =
          JSON.parseObject(
              (String) old.get("payload"), new TypeReference<Map<String, Object>>() {});
      String title = Objects.toString(original.get("title"), "历史业务理解");
      var snapshot = new LinkedHashMap<String, Object>();
      snapshot.put("projectId", project);
      snapshot.put("schemaVersion", "1.0");
      snapshot.put("operation", "UPDATE");
      snapshot.put("assetId", id);
      snapshot.put("baseVersion", 0);
      snapshot.put("idempotencyKey", "legacy:" + legacyId + ":" + version);
      snapshot.put(
          "scope", Map.of("systemKey", "", "moduleKey", "", "pageKeys", List.of(), "flowKey", ""));
      snapshot.put("title", title);
      snapshot.put(
          "document",
          Map.of("format", "markdown", "content", Objects.toString(original.get("body"), "")));
      snapshot.put("branches", List.of());
      snapshot.put("changeSummary", "人工确认归入本项目，保留历史版本待归类");
      snapshot.put("legacyPayload", original);
      jdbc.update(
          "INSERT INTO"
              + " business_asset_version(id,asset_id,project_id,version_no,base_version,title,system_key,module_key,flow_key,page_keys_json,snapshot_json,content_hash,branch_count,status,actor_id,create_time)"
              + " VALUES(?,?,?,?,0,?,'','','','[]',?,?,0,'UNCLASSIFIED',?,?)",
          UUID.randomUUID().toString(),
          id,
          project,
          version,
          title.substring(0, Math.min(255, title.length())),
          JSON.toJSONString(snapshot),
          old.get("content_hash"),
          old.get("author_id"),
          old.get("create_time"));
    }
    jdbc.update("INSERT INTO business_asset_reader(asset_id,user_id) VALUES(?,?)", id, actor);
    audit(project, id, "IMPORT_LEGACY", actor, null, "人工确认无项目来源的历史业务理解归属");
    return Map.of("assetId", id);
  }

  public Map<String, Object> detail(String project, String id) {
    authorize(project, false);
    var asset = asset(project, id, false);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", asset.get("id"));
    result.put("revision", asset.get("revision"));
    result.put("archived", asset.get("archived"));
    result.put("currentVersionId", asset.get("currentVersionId"));
    result.put("restricted", asset.get("restricted"));
    result.put("versions", versionsInternal(project, id));
    result.put(
        "audit",
        BusinessAssetRows.canonical(
            jdbc.queryForList(
                "SELECT event_type AS eventType,actor_id AS actorId,version_id AS"
                    + " versionId,reason,create_time AS createTime FROM business_asset_audit WHERE"
                    + " project_id=? AND asset_id=? ORDER BY create_time DESC,id",
                project,
                id),
            "eventType",
            "actorId",
            "versionId",
            "reason",
            "createTime"));
    result.put(
        "relations",
        BusinessAssetRows.canonical(
            jdbc.queryForList(
                "SELECT source_id AS sourceId,target_id AS targetId,relation_type AS relationType"
                    + " FROM business_asset_relation WHERE project_id=? AND (source_id=? OR"
                    + " target_id=?)",
                project,
                id,
                id),
            "sourceId",
            "targetId",
            "relationType"));
    return result;
  }

  public List<Map<String, Object>> versions(String project, String id) {
    authorize(project, false);
    asset(project, id, false);
    return versionsInternal(project, id);
  }

  void requireReadable(String project, String id) {
    asset(project, id, false);
  }

  void requireVersion(String project, String assetId, String versionId) {
    asset(project, assetId, false);
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM business_asset_version WHERE project_id=? AND asset_id=? AND id=?"
                + " AND status IN ('CURRENT','HISTORY')",
            Long.class,
            project,
            assetId,
            versionId);
    if (count == null || count != 1) throw AgentBusinessAccess.denied();
  }

  private List<Map<String, Object>> versionsInternal(String project, String id) {
    return jdbc.query(
        "SELECT * FROM business_asset_version WHERE project_id=? AND asset_id=? ORDER BY version_no"
            + " DESC",
        (rs, row) -> {
          Map<String, Object> version = new LinkedHashMap<>();
          version.put("id", rs.getString("id"));
          version.put("versionNo", rs.getInt("version_no"));
          version.put("baseVersion", rs.getInt("base_version"));
          version.put("status", rs.getString("status"));
          version.put("createdAt", rs.getLong("create_time"));
          version.put("actorId", rs.getString("actor_id"));
          version.put(
              "snapshot",
              JSON.parseObject(
                  rs.getString("snapshot_json"), new TypeReference<Map<String, Object>>() {}));
          return version;
        },
        project,
        id);
  }

  public Map<String, Object> submit(BusinessAssetSubmission request) {
    String user = authorize(request.projectId(), true);
    validate(request);
    // Serialize submissions per project, including first creation, to prevent duplicate identity
    // races.
    jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", request.projectId());
    String snapshot = JSON.toJSONString(request), hash = DigestUtils.sha256Hex(snapshot);
    var prior =
        jdbc.queryForList(
            "SELECT request_hash,result_json FROM business_asset_submission WHERE project_id=? AND"
                + " actor_id=? AND request_key=?",
            request.projectId(),
            user,
            request.idempotencyKey());
    if (!prior.isEmpty()) {
      if (!hash.equals(prior.getFirst().get("request_hash")))
        throw new BusinessGovernanceException(
            "ASSET_IDEMPOTENCY_CONFLICT", "同一提交标识对应不同内容，请使用新的提交标识");
      return JSON.parseObject(
          (String) prior.getFirst().get("result_json"),
          new TypeReference<Map<String, Object>>() {});
    }
    var scope = request.scope();
    String identity = identity(scope);
    String id = request.assetId();
    if (id == null || id.isBlank()) {
      var matches =
          jdbc.queryForList(
              "SELECT id FROM business_asset WHERE project_id=? AND identity_key=?",
              String.class,
              request.projectId(),
              identity);
      if (!matches.isEmpty()) id = matches.getFirst();
      else {
        if ("UPDATE".equals(request.operation()))
          throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "更新业务资产需要指定已有资产");
        id = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO"
                + " business_asset(id,project_id,identity_key,current_version_id,revision,archived,create_time,update_time)"
                + " VALUES(?,?,?,NULL,0,FALSE,?,?)",
            id,
            request.projectId(),
            identity,
            System.currentTimeMillis(),
            System.currentTimeMillis());
      }
    }
    var asset = asset(request.projectId(), id, true);
    if (Boolean.TRUE.equals(asset.get("archived"))
        || "1".equals(String.valueOf(asset.get("archived"))))
      throw new BusinessGovernanceException("ASSET_ARCHIVED", "请先恢复已归档的业务资产");
    int current = currentVersion(asset);
    int next =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(version_no),0)+1 FROM business_asset_version WHERE asset_id=?",
            Integer.class,
            id);
    boolean conflict = request.baseVersion() != current;
    boolean unclassified = scope.systemKey().isBlank() || scope.moduleKey().isBlank();
    if (!unclassified) {
      Long sameScope =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM business_asset WHERE project_id=? AND identity_key=? AND id<>?",
              Long.class,
              request.projectId(),
              identity,
              id);
      if (sameScope != null && sameScope > 0) conflict = true;
    }
    String status = conflict ? "CONFLICT" : unclassified ? "UNCLASSIFIED" : "CURRENT";
    String versionId = UUID.randomUUID().toString();
    jdbc.update(
        """
INSERT INTO business_asset_version(id,asset_id,project_id,version_no,base_version,title,system_key,module_key,flow_key,page_keys_json,snapshot_json,content_hash,branch_count,status,actor_id,create_time)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
        versionId,
        id,
        request.projectId(),
        next,
        request.baseVersion(),
        request.title().trim(),
        scope.systemKey(),
        scope.moduleKey(),
        scope.flowKey(),
        JSON.toJSONString(scope.pageKeys()),
        snapshot,
        hash,
        request.branches().size(),
        status,
        user,
        System.currentTimeMillis());
    if (!conflict && !unclassified) {
      jdbc.update(
          "UPDATE business_asset_version SET status='HISTORY' WHERE asset_id=? AND"
              + " status='CURRENT'",
          id);
      jdbc.update("UPDATE business_asset_version SET status='CURRENT' WHERE id=?", versionId);
      jdbc.update(
          "UPDATE business_asset SET"
              + " current_version_id=?,identity_key=?,revision=revision+1,update_time=? WHERE id=?",
          versionId,
          identity,
          System.currentTimeMillis(),
          id);
    } else
      jdbc.update(
          "UPDATE business_asset SET revision=revision+1,update_time=? WHERE id=?",
          System.currentTimeMillis(),
          id);
    audit(request.projectId(), id, "SUBMIT_" + status, user, versionId, request.changeSummary());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("assetId", id);
    result.put("versionId", versionId);
    result.put("versionNo", next);
    result.put("status", status);
    result.put("currentVersion", conflict || unclassified ? current : next);
    result.put(
        "currentVersionId",
        conflict || unclassified ? Objects.toString(asset.get("currentVersionId"), "") : versionId);
    if (conflict) result.put("message", "提交内容已保留为冲突版本，请读取当前版本并合并后重新提交");
    jdbc.update(
        "INSERT INTO"
            + " business_asset_submission(project_id,actor_id,request_key,request_hash,result_json)"
            + " VALUES(?,?,?,?,?)",
        request.projectId(),
        user,
        request.idempotencyKey(),
        hash,
        JSON.toJSONString(result));
    return result;
  }

  public Map<String, Object> merge(
      String sourceId, int sourceRevision, BusinessAssetSubmission target) {
    String actor = authorize(target.projectId(), true);
    if (sourceId.equals(target.assetId()) || target.assetId() == null)
      throw new BusinessGovernanceException("MERGE_TARGET_INVALID", "请选择另一份已有业务资产作为合并目标");
    jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", target.projectId());
    var source = asset(target.projectId(), sourceId, true);
    var prior =
        jdbc.queryForList(
            "SELECT result_json,request_hash FROM business_asset_submission WHERE project_id=? AND"
                + " actor_id=? AND request_key=?",
            target.projectId(),
            actor,
            target.idempotencyKey());
    if (!prior.isEmpty()) {
      if (!DigestUtils.sha256Hex(JSON.toJSONString(target))
          .equals(prior.getFirst().get("request_hash")))
        throw new BusinessGovernanceException("MERGE_REQUEST_CONFLICT", "同一合并标识对应不同内容");
      Map<String, Object> replay =
          JSON.parseObject(
              (String) prior.getFirst().get("result_json"),
              new TypeReference<Map<String, Object>>() {});
      if ("CURRENT".equals(replay.get("status"))) {
        Long relation =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_asset_relation WHERE project_id=? AND source_id=?"
                    + " AND target_id=? AND relation_type='MERGED_INTO'",
                Long.class,
                target.projectId(),
                sourceId,
                target.assetId());
        if (relation == null || relation != 1)
          throw new BusinessGovernanceException("MERGE_REQUEST_CONFLICT", "该提交标识已用于其他操作，请重新发起合并");
      }
      return replay;
    }
    if (((Number) source.get("revision")).intValue() != sourceRevision)
      throw new BusinessGovernanceException("ASSET_VERSION_CONFLICT", "源资产已变化，请刷新后重新合并");
    var result = submit(target);
    if ("CURRENT".equals(result.get("status"))) {
      jdbc.update(
          "UPDATE business_asset SET archived=TRUE,revision=revision+1,update_time=? WHERE id=?",
          System.currentTimeMillis(),
          sourceId);
      jdbc.update(
          "INSERT INTO"
              + " business_asset_relation(project_id,source_id,target_id,relation_type,create_time)"
              + " VALUES(?,?,?,'MERGED_INTO',?)",
          target.projectId(),
          sourceId,
          target.assetId(),
          System.currentTimeMillis());
      audit(
          target.projectId(),
          sourceId,
          "MERGE",
          actor,
          (String) result.get("versionId"),
          "合并至 " + target.assetId());
    }
    return result;
  }

  private void validate(BusinessAssetSubmission request) {
    if (AgentTokenContext.get() != null && request.source() == null)
      throw new BusinessGovernanceException("EXECUTOR_SOURCE_REQUIRED", "AI 上传必须提供已连接的执行器会话来源");
    if (io.metersphere.agent.security.BusinessAssetSafety.containsSecret(JSON.toJSONString(request))
        || request.document().content().matches("(?s).*(?:密码|口令|密钥)\\s*[:：=]\\s*[^\\s]+.*"))
      throw new BusinessGovernanceException("ASSET_CONTAINS_SECRET", "业务理解包含敏感凭据，请移入公共配置后重新提交");
    if (request.branches().stream()
            .map(BusinessAssetSubmission.Branch::branchKey)
            .distinct()
            .count()
        != request.branches().size())
      throw new BusinessGovernanceException("ASSET_BRANCH_KEY_DUPLICATE", "操作分支标识不能重复");
    if (request.source() != null) {
      var token = AgentTokenContext.get();
      if (token == null) throw AgentBusinessAccess.denied();
      Long executor =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM external_executor_session WHERE id=? AND project_id=? AND"
                  + " executor_id=? AND token_id=? AND actor_id=?",
              Long.class,
              request.source().taskId(),
              request.projectId(),
              request.source().executorId(),
              token.getId(),
              token.getUserId());
      if (executor == null || executor < 1) throw AgentBusinessAccess.denied();
    }
    for (var branch : request.branches())
      for (String evidence : branch.evidenceRefs()) {
        Long count =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM external_executor_evidence e JOIN external_executor_session s"
                    + " ON s.id=e.session_id WHERE e.id=? AND s.project_id=?",
                Long.class,
                evidence,
                request.projectId());
        if (count == null || count != 1) throw AgentBusinessAccess.denied();
        if (AgentTokenContext.get() != null) {
          Long own =
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM external_executor_evidence WHERE id=? AND session_id=?",
                  Long.class,
                  evidence,
                  request.source().taskId());
          if (own == null || own != 1) throw AgentBusinessAccess.denied();
        }
      }
  }

  static String identity(BusinessAssetSubmission.Scope scope) {
    // Flow is authoritative across pages; otherwise all operations on a module/page set share one
    // asset.
    String anchor =
        !scope.flowKey().isBlank() ? "flow:" + scope.flowKey() : "module:" + scope.moduleKey();
    if (scope.systemKey().isBlank() || scope.moduleKey().isBlank())
      return UUID.randomUUID().toString();
    return DigestUtils.sha256Hex(
        JSON.toJSONString(
            List.of(
                scope.systemKey(), scope.flowKey().isBlank() ? scope.moduleKey() : "", anchor)));
  }

  public void select(String project, String id, String version, int revision, String reason) {
    String actor = authorize(project, true);
    requireReason(reason);
    jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", project);
    var current = asset(project, id, true);
    if (((Number) current.get("revision")).intValue() != revision)
      throw new BusinessGovernanceException("ASSET_VERSION_CONFLICT", "资产已变化，请刷新后再切换版本");
    var rows =
        jdbc.queryForList(
            "SELECT status,snapshot_json FROM business_asset_version WHERE project_id=? AND"
                + " asset_id=? AND id=?",
            project,
            id,
            version);
    if (rows.isEmpty() || !Set.of("CURRENT", "HISTORY").contains(rows.getFirst().get("status")))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "冲突或待归类版本须先合并整理，不能直接生效");
    Map<String, Object> snapshot =
        JSON.parseObject(
            (String) rows.getFirst().get("snapshot_json"),
            new TypeReference<Map<String, Object>>() {});
    var scope =
        JSON.parseObject(
            JSON.toJSONString(snapshot.get("scope")), BusinessAssetSubmission.Scope.class);
    String restoredIdentity = identity(scope);
    Long duplicate =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM business_asset WHERE project_id=? AND identity_key=? AND id<>?",
            Long.class,
            project,
            restoredIdentity,
            id);
    if (duplicate != null && duplicate > 0)
      throw new BusinessGovernanceException("ASSET_IDENTITY_CONFLICT", "该历史版本的业务范围已有其他资产，请合并后再切换");
    jdbc.update(
        "UPDATE business_asset_version SET status='HISTORY' WHERE asset_id=? AND status='CURRENT'",
        id);
    jdbc.update("UPDATE business_asset_version SET status='CURRENT' WHERE id=?", version);
    jdbc.update(
        "UPDATE business_asset SET"
            + " current_version_id=?,identity_key=?,revision=revision+1,update_time=? WHERE id=?",
        version,
        restoredIdentity,
        System.currentTimeMillis(),
        id);
    audit(project, id, "SELECT_VERSION", actor, version, reason);
  }

  public void archive(String project, String id, boolean archived, int revision, String reason) {
    String actor = authorize(project, true);
    requireReason(reason);
    asset(project, id, true);
    if (jdbc.update(
            "UPDATE business_asset SET archived=?,revision=revision+1,update_time=? WHERE id=? AND"
                + " revision=?",
            archived,
            System.currentTimeMillis(),
            id,
            revision)
        != 1) throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "资产已变化，请刷新后重试");
    audit(project, id, archived ? "ARCHIVE" : "RESTORE", actor, null, reason);
  }

  private Map<String, Object> asset(String project, String id, boolean lock) {
    String user = authorize(project, lock);
    Long restricted =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM business_asset_reader WHERE asset_id=?", Long.class, id);
    if (restricted != null && restricted > 0) {
      Long reader =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM business_asset_reader WHERE asset_id=? AND user_id=?",
              Long.class,
              id,
              user);
      if (reader == null || reader != 1) throw AgentBusinessAccess.denied();
    }
    var rows =
        jdbc.queryForList(
            "SELECT id,revision,archived,current_version_id AS currentVersionId FROM business_asset"
                + " WHERE project_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            project,
            id);
    if (rows.isEmpty()) throw AgentBusinessAccess.denied();
    rows.getFirst().put("restricted", restricted != null && restricted > 0);
    return rows.getFirst();
  }

  private int currentVersion(Map<String, Object> asset) {
    Object current = asset.get("currentVersionId");
    return current == null
        ? 0
        : jdbc.queryForObject(
            "SELECT version_no FROM business_asset_version WHERE id=?", Integer.class, current);
  }

  private void requireReason(String reason) {
    if (reason == null
        || reason.isBlank()
        || reason.length() > 500
        || io.metersphere.agent.security.BusinessAssetSafety.containsSecret(reason))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "请填写不超过 500 字的变更原因");
  }

  private void audit(
      String project, String id, String event, String actor, String version, String reason) {
    jdbc.update(
        "INSERT INTO"
            + " business_asset_audit(id,project_id,asset_id,event_type,actor_id,version_id,reason,create_time)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        project,
        id,
        event,
        actor,
        version,
        reason.substring(0, Math.min(500, reason.length())),
        System.currentTimeMillis());
  }
}
