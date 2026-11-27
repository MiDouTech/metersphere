package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Independent observation/correction channel; does not consume frozen actions or runner leases. */
@Service
@Transactional(rollbackFor = Exception.class)
public class ExternalExecutorService {
  private final JdbcTemplate jdbc;
  private final BusinessAssetService assets;

  @jakarta.annotation.Resource
  private io.metersphere.agent.security.SharedConfigurationCipher evidenceCipher;

  public ExternalExecutorService(JdbcTemplate jdbc, BusinessAssetService assets) {
    this.jdbc = jdbc;
    this.assets = assets;
  }

  private String authorize(String project, boolean write) {
    String actor = assets.authorize(project, write);
    assets.requireProjectAccess(actor, project, write);
    return actor;
  }

  public Map<String, Object> connect(String project, String executor, String requestKey) {
    boundary(project, 64);
    boundary(executor, 64);
    boundary(requestKey, 128);
    String actor = authorize(project, true);
    var token = AgentTokenContext.get();
    if (token == null)
      throw new BusinessGovernanceException(
          "GOVERNANCE_VALIDATION_ERROR", "请使用执行器的专用 Agent Token 建立连接");
    jdbc.queryForList("SELECT id FROM project WHERE id=? FOR UPDATE", project);
    var prior =
        jdbc.queryForList(
            "SELECT id,executor_id FROM external_executor_session WHERE project_id=? AND token_id=?"
                + " AND request_key=?",
            project,
            token.getId(),
            requestKey);
    String id;
    if (!prior.isEmpty()) {
      if (!executor.equals(prior.getFirst().get("executor_id")))
        throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "连接标识已被其他执行器使用");
      id = (String) prior.getFirst().get("id");
    } else {
      id = UUID.randomUUID().toString();
      long now = System.currentTimeMillis();
      jdbc.update(
          "INSERT INTO"
              + " external_executor_session(id,project_id,executor_id,actor_id,token_id,request_key,status,heartbeat_at,create_time)"
              + " VALUES(?,?,?,?,?,?,'RUNNING',?,?)",
          id,
          project,
          executor,
          actor,
          token.getId(),
          requestKey,
          now,
          now);
      jdbc.update(
          "INSERT INTO external_executor_context(session_id,asset_id,version_id) SELECT"
              + " ?,a.id,a.current_version_id FROM business_asset a WHERE a.organization_id=(SELECT"
              + " organization_id FROM project WHERE id=?) AND a.archived=FALSE AND"
              + " a.current_version_id IS NOT NULL AND (NOT EXISTS(SELECT 1 FROM"
              + " business_asset_reader r WHERE r.asset_id=a.id) OR EXISTS(SELECT 1 FROM"
              + " business_asset_reader r WHERE r.asset_id=a.id AND r.user_id=?))",
          id,
          project,
          actor);
      jdbc.update(
          "INSERT INTO external_executor_configuration(session_id,configuration_id,version_no)"
              + " SELECT ?,id,current_version FROM shared_configuration WHERE"
              + " organization_id=(SELECT organization_id FROM project WHERE id=?)",
          id,
          project);
    }
    Map<String, Object> protocol = new LinkedHashMap<>();
    protocol.put("schemaVersion", "1.0");
    protocol.put("readBeforeExecution", true);
    protocol.put("uploadUnderstanding", true);
    protocol.put("updateExistingAsset", true);
    protocol.put("excludeSecrets", true);
    protocol.put("uploadTool", "metersphere.business_asset.upload");
    protocol.put("source", Map.of("taskId", id, "executorId", executor));
    protocol.put(
        "requiredSections",
        List.of("业务范围", "业务目标", "前置条件", "操作分支", "关联流程", "业务规则", "理解依据", "待确认内容", "本次变化"));
    protocol.put("grouping", "同一模块或前后关联流程使用同一文档，不同操作作为分支；先查询已有资产再更新，不为每次执行重复建档。");
    protocol.put(
        "correction", "执行中上报事件并定期拉取 metersphere.executor.commands；每个待处理指令须响应 APPLIED 或 FAILED。");
    return Map.of(
        "sessionId",
        id,
        "status",
        jdbc.queryForObject(
            "SELECT status FROM external_executor_session WHERE id=?", String.class, id),
        "assetProtocol",
        protocol,
        "context",
        context(project, id));
  }

  public List<Map<String, Object>> sessions(String project) {
    authorize(project, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT id,executor_id AS executorId,status,heartbeat_at AS heartbeatAt,CASE WHEN"
                + " status='RUNNING' AND heartbeat_at<? THEN TRUE ELSE FALSE END AS disconnected"
                + " FROM external_executor_session WHERE project_id=? ORDER BY create_time DESC"
                + " LIMIT 200",
            System.currentTimeMillis() - 90000,
            project),
        "id",
        "executorId",
        "status",
        "heartbeatAt",
        "disconnected");
  }

  public List<Map<String, Object>> context(String project, String session) {
    owned(project, session, false);
    String actor = authorize(project, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT c.asset_id AS assetId,c.version_id AS versionId FROM external_executor_context"
                + " c WHERE c.session_id=? AND (NOT EXISTS(SELECT 1 FROM business_asset_reader r"
                + " WHERE r.asset_id=c.asset_id) OR EXISTS(SELECT 1 FROM business_asset_reader r"
                + " WHERE r.asset_id=c.asset_id AND r.user_id=?)) ORDER BY c.asset_id LIMIT 1000",
            session,
            actor),
        "assetId",
        "versionId");
  }

  public Map<String, Object> readContext(String project, String session, String asset) {
    boundary(asset, 64);
    owned(project, session, false);
    assets.requireReadable(project, asset);
    var rows =
        jdbc.queryForList(
            "SELECT c.version_id,v.version_no,v.snapshot_json FROM external_executor_context c JOIN"
                + " business_asset_version v ON v.id=c.version_id WHERE c.session_id=? AND"
                + " c.asset_id=?",
            session,
            asset);
    if (rows.size() != 1) throw AgentBusinessAccess.denied();
    var row = rows.getFirst();
    return Map.of(
        "assetId",
        asset,
        "versionId",
        row.get("version_id"),
        "versionNo",
        row.get("version_no"),
        "snapshot",
        io.metersphere.sdk.util.JSON.parseObject(
            (String) row.get("snapshot_json"),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
  }

  public void refreshContext(
      String project,
      String session,
      String asset,
      String version,
      String expected,
      String reason) {
    boundary(asset, 64);
    boundary(version, 64);
    if (expected != null && expected.length() > 64)
      throw new BusinessGovernanceException("CONTEXT_VERSION_INVALID", "原上下文版本格式无效");
    authorize(project, true);
    owned(project, session, false);
    if (reason == null
        || reason.isBlank()
        || reason.length() > 500
        || io.metersphere.agent.security.BusinessAssetSafety.containsSecret(reason))
      throw new BusinessGovernanceException("CONTEXT_REASON_INVALID", "请填写不含敏感值的上下文更新原因");
    String status =
        jdbc.queryForObject(
            "SELECT status FROM external_executor_session WHERE id=? FOR UPDATE",
            String.class,
            session);
    if (!"RUNNING".equals(status))
      throw new BusinessGovernanceException("EXECUTOR_SESSION_FINISHED", "会话已结束，不能替换任务上下文");
    assets.requireVersion(project, asset, version);
    var old =
        jdbc.queryForList(
            "SELECT version_id FROM external_executor_context WHERE session_id=? AND asset_id=? FOR"
                + " UPDATE",
            String.class,
            session,
            asset);
    String previous = old.isEmpty() ? "" : old.getFirst();
    if (previous.equals(version)) return;
    if (!previous.equals(Objects.toString(expected, "")))
      throw new BusinessGovernanceException("CONTEXT_VERSION_CONFLICT", "任务上下文已变化，请刷新后重试");
    if (old.isEmpty())
      jdbc.update("INSERT INTO external_executor_context VALUES(?,?,?)", session, asset, version);
    else
      jdbc.update(
          "UPDATE external_executor_context SET version_id=? WHERE session_id=? AND asset_id=?",
          version,
          session,
          asset);
    String summary = "业务理解 " + asset + " 从 " + previous + " 更新为 " + version + "；" + reason;
    correct(project, session, summary + "。请重新读取任务上下文并响应此指令。");
    jdbc.update(
        "INSERT INTO"
            + " external_executor_event(id,session_id,request_key,event_type,stage,error_category,outcome,summary,evidence_id,create_time)"
            + " VALUES(?,?,?,'CONTEXT_UPDATED','CONTEXT','NONE','RUNNING',?,NULL,?)",
        UUID.randomUUID().toString(),
        session,
        "context:" + UUID.randomUUID(),
        summary,
        System.currentTimeMillis());
  }

  public void event(
      String project,
      String session,
      String key,
      String type,
      String stage,
      String category,
      String outcome) {
    event(project, session, key, type, stage, category, outcome, "", null);
  }

  public void event(
      String project,
      String session,
      String key,
      String type,
      String stage,
      String category,
      String outcome,
      String summary,
      String evidence) {
    boundary(key, 128);
    boundary(type, 32);
    boundary(stage, 64);
    boundary(category, 64);
    boundary(outcome, 24);
    owned(project, session, true);
    String description = Objects.toString(summary, "");
    if (description.length() > 2000
        || io.metersphere.agent.security.BusinessAssetSafety.containsSecret(description))
      throw new BusinessGovernanceException("EXECUTOR_EVENT_UNSAFE", "执行摘要过长或包含敏感值，请先脱敏");
    if (evidence != null && !evidence.isBlank()) {
      Long count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM external_executor_evidence WHERE id=? AND session_id=?",
              Long.class,
              evidence,
              session);
      if (count == null || count != 1) throw AgentBusinessAccess.denied();
    }
    if (!Set.of("HEARTBEAT", "OBSERVATION", "DEVIATION", "COMPLETE", "FAILED").contains(type)
        || !Set.of("RUNNING", "PASSED", "FAILED", "BLOCKED").contains(outcome)
        || !stage.matches("[A-Z][A-Z0-9_]{0,63}")
        || !category.matches("[A-Z][A-Z0-9_]{0,63}"))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "执行事件类型、阶段或分类无效");
    var prior =
        jdbc.queryForList(
            "SELECT event_type,stage,error_category,outcome,summary,evidence_id FROM"
                + " external_executor_event WHERE session_id=? AND request_key=?",
            session,
            key);
    if (!prior.isEmpty()) {
      var p = prior.getFirst();
      if (!type.equals(p.get("event_type"))
          || !stage.equals(p.get("stage"))
          || !category.equals(p.get("error_category"))
          || !outcome.equals(p.get("outcome"))
          || !description.equals(p.get("summary"))
          || !Objects.equals(evidence, p.get("evidence_id")))
        throw new BusinessGovernanceException("EXECUTOR_EVENT_CONFLICT", "同一事件标识内容不一致");
      return;
    }
    String status =
        jdbc.queryForObject(
            "SELECT status FROM external_executor_session WHERE id=?", String.class, session);
    if (!"RUNNING".equals(status))
      throw new BusinessGovernanceException("EXECUTOR_SESSION_FINISHED", "执行会话已结束，不能继续追加事件");
    if ("COMPLETE".equals(type) && !Set.of("PASSED", "FAILED", "BLOCKED").contains(outcome))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "结束事件必须提供最终状态");
    jdbc.update(
        "INSERT INTO"
            + " external_executor_event(id,session_id,request_key,event_type,stage,error_category,outcome,summary,evidence_id,create_time)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        session,
        key,
        type,
        stage,
        category,
        outcome,
        description,
        evidence,
        System.currentTimeMillis());
    jdbc.update(
        "UPDATE external_executor_session SET heartbeat_at=?,status=? WHERE id=?",
        System.currentTimeMillis(),
        "COMPLETE".equals(type) ? outcome : "FAILED".equals(type) ? "FAILED" : "RUNNING",
        session);
  }

  public Map<String, Object> monitor(String project, String session) {
    owned(project, session, false);
    var history = commandHistory(project, session, 1, 100);
    var events = eventHistory(project, session, 1, 100);
    return Map.of(
        "events",
        events.get("records"),
        "eventTotal",
        events.get("total"),
        "commands",
        history.get("records"),
        "commandTotal",
        history.get("total"));
  }

  public Map<String, Object> eventHistory(String project, String session, int page, int size) {
    owned(project, session, false);
    if (page < 1 || size < 1 || size > 100)
      throw new BusinessGovernanceException("EVENT_PAGE_INVALID", "执行事件页码或每页数量无效");
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM external_executor_event WHERE session_id=?", Long.class, session);
    var records =
        BusinessAssetRows.canonical(
            jdbc.queryForList(
                "SELECT id,event_type AS eventType,stage,error_category AS"
                    + " errorCategory,outcome,summary,evidence_id AS evidenceId,create_time AS"
                    + " createTime FROM external_executor_event WHERE session_id=? ORDER BY"
                    + " create_time DESC,id DESC LIMIT ? OFFSET ?",
                session,
                size,
                (page - 1L) * size),
            "id",
            "eventType",
            "stage",
            "errorCategory",
            "outcome",
            "summary",
            "evidenceId",
            "createTime");
    return Map.of(
        "records", records, "total", total == null ? 0 : total, "page", page, "size", size);
  }

  public Map<String, String> uploadEvidence(
      String project, String session, String key, String encoded, boolean redacted) {
    boundary(key, 128);
    if (encoded == null || encoded.length() > 2800000)
      throw new BusinessGovernanceException("EVIDENCE_SIZE_INVALID", "证据内容为空或超过大小限制");
    owned(project, session, true);
    if (!redacted)
      throw new BusinessGovernanceException("EVIDENCE_REDACTION_REQUIRED", "请遮罩敏感区域后上传证据");
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException invalid) {
      throw new BusinessGovernanceException("EVIDENCE_FORMAT_INVALID", "证据编码无效");
    }
    if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024)
      throw new BusinessGovernanceException("EVIDENCE_SIZE_INVALID", "证据文件必须在 2 MB 以内");
    String type;
    if (bytes.length >= 8
        && bytes[0] == (byte) 0x89
        && bytes[1] == 'P'
        && bytes[2] == 'N'
        && bytes[3] == 'G'
        && bytes[4] == 13
        && bytes[5] == 10
        && bytes[6] == 26
        && bytes[7] == 10) type = "image/png";
    else if (bytes.length >= 3
        && bytes[0] == (byte) 0xff
        && bytes[1] == (byte) 0xd8
        && bytes[2] == (byte) 0xff) type = "image/jpeg";
    else throw new BusinessGovernanceException("EVIDENCE_FORMAT_INVALID", "当前支持 PNG 或 JPEG 图片证据");
    try (var input =
        javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
      var readers = javax.imageio.ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IllegalArgumentException();
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 20000000 || reader.read(0) == null)
          throw new IllegalArgumentException();
      } finally {
        reader.dispose();
      }
    } catch (Exception invalid) {
      throw new BusinessGovernanceException("EVIDENCE_IMAGE_INVALID", "证据图片损坏或尺寸过大，请重新生成");
    }
    String hash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
    var prior =
        jdbc.queryForList(
            "SELECT id,content_hash FROM external_executor_evidence WHERE session_id=? AND"
                + " request_key=?",
            session,
            key);
    if (!prior.isEmpty()) {
      if (!hash.equals(prior.getFirst().get("content_hash")))
        throw new BusinessGovernanceException("EVIDENCE_REQUEST_CONFLICT", "同一证据提交标识对应不同内容");
      return Map.of("evidenceId", (String) prior.getFirst().get("id"));
    }
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " external_executor_evidence(id,session_id,request_key,content_hash,content_type,encrypted_content,create_time)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        session,
        key,
        hash,
        type,
        evidenceCipher.encrypt(bytes, project + ":evidence:" + id),
        System.currentTimeMillis());
    return Map.of("evidenceId", id);
  }

  public Map<String, String> evidence(String project, String id) {
    authorize(project, false);
    var rows =
        jdbc.queryForList(
            "SELECT e.session_id,e.content_type,e.encrypted_content FROM external_executor_evidence"
                + " e JOIN external_executor_session s ON s.id=e.session_id WHERE e.id=? AND"
                + " s.project_id=?",
            id,
            project);
    if (rows.size() != 1) throw AgentBusinessAccess.denied();
    var row = rows.getFirst();
    owned(project, (String) row.get("session_id"), false);
    return Map.of(
        "contentType",
        (String) row.get("content_type"),
        "content",
        Base64.getEncoder()
            .encodeToString(
                evidenceCipher.decrypt(
                    (String) row.get("encrypted_content"), project + ":evidence:" + id)));
  }

  public String correct(String project, String session, String instruction) {
    boundary(instruction, 5000);
    if (io.metersphere.agent.security.BusinessAssetSafety.containsSecret(instruction))
      throw new BusinessGovernanceException(
          "CORRECTION_CONTAINS_SECRET", "纠正指令不能包含密码或 Token，请使用公共配置引用");
    String actor = authorize(project, true);
    owned(project, session, false);
    String status =
        jdbc.queryForObject(
            "SELECT status FROM external_executor_session WHERE id=? FOR UPDATE",
            String.class,
            session);
    if (!"RUNNING".equals(status))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "执行会话已结束，不能下发纠正指令");
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " external_executor_command(id,session_id,instruction,status,actor_id,create_time)"
            + " VALUES(?,?,?,'PENDING',?,?)",
        id,
        session,
        instruction,
        actor,
        System.currentTimeMillis());
    return id;
  }

  public List<Map<String, Object>> commands(String project, String session) {
    owned(project, session, false);
    return BusinessAssetRows.canonical(
        jdbc.queryForList(
            "SELECT id,instruction,status,response_category AS responseCategory,create_time AS"
                + " createTime,responded_at AS respondedAt FROM external_executor_command WHERE"
                + " session_id=? AND status='PENDING' ORDER BY create_time,id LIMIT 1000",
            session),
        "id",
        "instruction",
        "status",
        "responseCategory",
        "createTime",
        "respondedAt");
  }

  public Map<String, Object> commandHistory(String project, String session, int page, int size) {
    owned(project, session, false);
    if (page < 1 || size < 1 || size > 100)
      throw new BusinessGovernanceException("COMMAND_PAGE_INVALID", "纠正历史页码或每页数量无效");
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM external_executor_command WHERE session_id=?",
            Long.class,
            session);
    var records =
        BusinessAssetRows.canonical(
            jdbc.queryForList(
                "SELECT id,instruction,status,response_category AS responseCategory,create_time AS"
                    + " createTime,responded_at AS respondedAt FROM external_executor_command WHERE"
                    + " session_id=? ORDER BY create_time DESC,id DESC LIMIT ? OFFSET ?",
                session,
                size,
                (page - 1L) * size),
            "id",
            "instruction",
            "status",
            "responseCategory",
            "createTime",
            "respondedAt");
    return Map.of(
        "records", records, "total", total == null ? 0 : total, "page", page, "size", size);
  }

  public void acknowledge(
      String project, String session, String command, String outcome, String category) {
    boundary(command, 64);
    boundary(outcome, 24);
    boundary(category, 64);
    owned(project, session, true);
    if (!Set.of("APPLIED", "FAILED").contains(outcome) || !category.matches("[A-Z][A-Z0-9_]{0,63}"))
      throw new BusinessGovernanceException("GOVERNANCE_VALIDATION_ERROR", "纠正响应状态或分类无效");
    var rows =
        jdbc.queryForList(
            "SELECT status,response_category FROM external_executor_command WHERE id=? AND"
                + " session_id=? FOR UPDATE",
            command,
            session);
    if (rows.isEmpty()) throw AgentBusinessAccess.denied();
    var old = rows.getFirst();
    if (!"PENDING".equals(old.get("status"))) {
      if (outcome.equals(old.get("status")) && category.equals(old.get("response_category")))
        return;
      throw new BusinessGovernanceException("EXECUTOR_RESPONSE_CONFLICT", "纠正指令已经响应，不能覆盖原结果");
    }
    jdbc.update(
        "UPDATE external_executor_command SET status=?,response_category=?,responded_at=? WHERE"
            + " id=?",
        outcome,
        category,
        System.currentTimeMillis(),
        command);
  }

  private void owned(String project, String session, boolean executorOnly) {
    boundary(project, 64);
    boundary(session, 64);
    authorize(project, executorOnly);
    var rows =
        jdbc.queryForList(
            "SELECT actor_id,token_id FROM external_executor_session WHERE id=? AND project_id=?"
                + (executorOnly ? " FOR UPDATE" : ""),
            session,
            project);
    if (rows.isEmpty()) throw AgentBusinessAccess.denied();
    var token = AgentTokenContext.get();
    if (executorOnly || token != null) {
      if (token == null
          || !token.getId().equals(rows.getFirst().get("token_id"))
          || !token.getUserId().equals(rows.getFirst().get("actor_id")))
        throw AgentBusinessAccess.denied();
    }
  }

  private void boundary(String value, int maximum) {
    if (value == null || value.isBlank() || value.length() > maximum)
      throw new BusinessGovernanceException("EXECUTOR_VALIDATION_ERROR", "执行器字段为空或超出长度限制");
  }
}
