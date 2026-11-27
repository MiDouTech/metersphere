package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataAssetParseJobService {
  private final JdbcTemplate jdbc;
  private final GlobalAssetAccess access;
  private final DataAssetParser parser;
  private final com.fasterxml.jackson.databind.ObjectMapper json =
      new com.fasterxml.jackson.databind.ObjectMapper();

  public DataAssetParseJobService(
      JdbcTemplate jdbc, GlobalAssetAccess access, DataAssetParser parser) {
    this.jdbc = jdbc;
    this.access = access;
    this.parser = parser;
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> upload(
      byte[] bytes, String filename, String name, String category, String requestId) {
    return upload(null, bytes, filename, name, category, requestId);
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> upload(
      String project,
      byte[] bytes,
      String filename,
      String name,
      String category,
      String requestId) {
    return uploadInternal(project, null, null, bytes, filename, name, category, requestId);
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> replace(
      String project,
      String assetId,
      int expectedRevision,
      byte[] bytes,
      String filename,
      String requestId) {
    if (requestId == null || requestId.isBlank()) throw new MSException("替换文件需要幂等请求标识");
    return uploadInternal(
        project, assetId, expectedRevision, bytes, filename, null, null, requestId);
  }

  private Map<String, Object> uploadInternal(
      String project,
      String assetId,
      Integer expectedRevision,
      byte[] bytes,
      String filename,
      String name,
      String category,
      String requestId) {
    var scope = access.authorize(project, "TEST_DATA_ASSET:READ+UPDATE");
    if (bytes.length > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
      throw new MSException("文件超过2MB限制");
    int dot = filename.lastIndexOf('.');
    String format = dot < 0 ? "" : filename.substring(dot + 1).toUpperCase(Locale.ROOT);
    if (!Set.of("CSV", "JSON", "TXT", "XLS", "XLSX").contains(format))
      throw new MSException("不支持的文件格式");
    if (name == null || name.isBlank()) name = filename;
    if (name.length() > 255) throw new MSException("名称不能超过255字符");
    if (category != null
        && !category.isBlank()
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM test_asset_category WHERE id=? AND organization_id=? AND"
                    + " deleted=0",
                Long.class,
                category,
                scope.organizationId())
            != 1) throw new MSException("分类不存在或无访问权限");
    String hash =
        org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)
            + ":"
            + name
            + ":"
            + Objects.toString(category, "")
            + ":"
            + Objects.toString(assetId, "")
            + ":"
            + Objects.toString(expectedRevision, "");
    String fingerprint = org.apache.commons.codec.digest.DigestUtils.sha256Hex(hash);
    if (requestId != null && !requestId.isBlank()) {
      if (requestId.length() > 128) throw new MSException("请求标识过长");
      var prior =
          jdbc.queryForList(
              "SELECT asset_id,request_hash FROM data_asset_request WHERE organization_id=? AND"
                  + " actor_id=? AND request_id=?",
              scope.organizationId(),
              scope.actorId(),
              requestId);
      if (!prior.isEmpty()) {
        if (!fingerprint.equals(prior.getFirst().get("request_hash")))
          throw new MSException("请求标识已用于不同内容");
        return Map.of("id", prior.getFirst().get("asset_id"));
      }
    }
    String description = "";
    int versionNo = 1;
    if (assetId != null) {
      var existing =
          jdbc.queryForList(
              "SELECT a.name,a.description,a.category_id,a.format,a.revision,v.parse_status FROM"
                  + " data_asset a JOIN data_asset_version v ON v.id=a.current_version_id WHERE"
                  + " a.id=? AND a.organization_id=? AND a.deleted=0 FOR UPDATE",
              assetId,
              scope.organizationId());
      if (existing.size() != 1) throw new MSException("数据资产不存在或无访问权限");
      var old = existing.getFirst();
      if (((Number) old.get("revision")).intValue() != expectedRevision)
        throw new MSException("数据已更新，请刷新后重试");
      if (!format.equals(old.get("format"))) throw new MSException("替换文件必须保持原数据格式");
      if ("PENDING".equals(old.get("parse_status"))) throw new MSException("请等待当前解析完成后再替换");
      name = old.get("name").toString();
      description = Objects.toString(old.get("description"), "");
      category = (String) old.get("category_id");
      versionNo = expectedRevision + 1;
    }
    String asset = assetId == null ? UUID.randomUUID().toString() : assetId,
        version = UUID.randomUUID().toString(),
        job = UUID.randomUUID().toString(),
        path = "data-assets/" + scope.organizationId() + "/" + UUID.randomUUID();
    long now = System.currentTimeMillis();
    var file = new FileRequest(path, "MINIO", "source");
    try {
      FileCenter.getRepository("MINIO").saveFile(bytes, file);
    } catch (Exception e) {
      DataAssetDiagnostics.failure(
          DataAssetParseJobService.class,
          "DATA_ASSET_SOURCE_UPLOAD_FAILURE",
          job,
          "source-upload",
          e);
      throw new MSException("原文件存储失败，请稍后重试");
    }
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED)
                  try {
                    FileCenter.getRepository("MINIO").delete(file);
                  } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(DataAssetParseJobService.class)
                        .warn(
                            "data_asset compensation failed jobId={} stage=rollback"
                                + " rootType=StorageFailure",
                            job);
                  }
              }
            });
    if (assetId == null)
      jdbc.update(
          "INSERT INTO"
              + " data_asset(id,organization_id,name,format,category_id,revision,current_version_id,update_time,create_user,create_time)"
              + " VALUES(?,?,?,?,?,1,?,?,?,?)",
          asset,
          scope.organizationId(),
          name,
          format,
          category,
          version,
          now,
          scope.actorId(),
          now);
    else if (jdbc.update(
            "UPDATE data_asset SET revision=revision+1,current_version_id=?,update_time=? WHERE"
                + " id=? AND organization_id=? AND revision=? AND deleted=0",
            version,
            now,
            asset,
            scope.organizationId(),
            expectedRevision)
        != 1) throw new MSException("数据已更新，请刷新后重试");
    jdbc.update(
        "INSERT INTO"
            + " data_asset_version(id,asset_id,version_no,content_json,create_time,actor_id,parse_status,metadata_json)"
            + " VALUES(?,?,?,'null',?,?,'PENDING',?)",
        version,
        asset,
        versionNo,
        now,
        scope.actorId(),
        DataAssetMetadata.encode(name, description, category, format));
    jdbc.update("INSERT INTO data_asset_file VALUES(?,?,?)", version, path, filename);
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES(?,?,?,?,?,'PENDING',0,?,NULL)",
        job,
        scope.organizationId(),
        scope.actorId(),
        asset,
        version,
        now);
    if (requestId != null && !requestId.isBlank())
      jdbc.update(
          "INSERT INTO data_asset_request VALUES(?,?,?,?,?)",
          scope.organizationId(),
          scope.actorId(),
          requestId,
          fingerprint,
          asset);
    return Map.of("id", asset, "jobId", job, "parseStatus", "PENDING");
  }

  @Scheduled(fixedDelay = 2000)
  public void drain() {
    jdbc.update(
        "UPDATE data_asset_parse_job SET status='CANCELLED',error='资产已删除',update_time=? WHERE"
            + " status IN ('PENDING','RUNNING') AND EXISTS(SELECT 1 FROM data_asset a WHERE"
            + " a.id=data_asset_parse_job.asset_id AND a.deleted=1)",
        System.currentTimeMillis());
    for (var stale :
        jdbc.queryForList(
            "SELECT * FROM data_asset_parse_job WHERE status='RUNNING' AND update_time<?",
            System.currentTimeMillis() - 300000))
      finish(stale, ((Number) stale.get("attempt")).intValue(), null, "解析任务中断，请重试");
    for (var job :
        jdbc.queryForList(
            "SELECT j.*,a.format,f.storage_path FROM data_asset_parse_job j JOIN data_asset a ON"
                + " a.id=j.asset_id JOIN data_asset_file f ON f.version_id=j.version_id WHERE"
                + " a.deleted=0 AND j.status='PENDING' ORDER BY j.update_time LIMIT 5")) {
      String id = job.get("id").toString();
      if (jdbc.update(
              "UPDATE data_asset_parse_job SET status='RUNNING',attempt=attempt+1,update_time=?"
                  + " WHERE id=? AND status='PENDING'",
              System.currentTimeMillis(),
              id)
          != 1) continue;
      int attempt = ((Number) job.get("attempt")).intValue() + 1;
      try {
        byte[] bytes = readOriginal(job);
        String body = json.writeValueAsString(parser.parse(bytes, job.get("format").toString()));
        if (body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
          throw new MSException("解析后内容超过2MB限制");
        finish(job, attempt, body, null);
      } catch (Exception e) {
        String message = e instanceof MSException ? e.getMessage() : "原文件暂时无法解析，请稍后重试";
        finish(job, attempt, null, message);
        org.slf4j.LoggerFactory.getLogger(DataAssetParseJobService.class)
            .warn(
                "data_asset parse failed jobId={} versionId={} stage=parse rootType={}",
                id,
                job.get("version_id"),
                e.getClass().getSimpleName());
      }
    }
  }

  byte[] readOriginal(Map<String, Object> job) throws Exception {
    try (var stream =
        FileCenter.getRepository("MINIO")
            .getFileAsStream(
                new FileRequest(job.get("storage_path").toString(), "MINIO", "source"))) {
      return stream.readNBytes(DataAssetLimits.MAX_BYTES + 1);
    }
  }

  private void finish(Map<String, Object> job, int attempt, String body, String error) {
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                Objects.requireNonNull(jdbc.getDataSource())));
    tx.executeWithoutResult(
        status -> {
          var owned =
              jdbc.queryForList(
                  "SELECT id FROM data_asset_parse_job WHERE id=? AND status='RUNNING' AND"
                      + " attempt=? FOR UPDATE",
                  job.get("id"),
                  attempt);
          if (owned.isEmpty()) return;
          if (jdbc.queryForObject(
                  "SELECT COUNT(*) FROM data_asset WHERE id=? AND deleted=0",
                  Long.class,
                  job.get("asset_id"))
              != 1) {
            jdbc.update(
                "UPDATE data_asset_parse_job SET status='CANCELLED',error='资产已删除',update_time=?"
                    + " WHERE id=?",
                System.currentTimeMillis(),
                job.get("id"));
            return;
          }
          if (error == null)
            jdbc.update(
                "UPDATE data_asset_version SET content_json=?,parse_status='READY',parse_error=NULL"
                    + " WHERE id=? AND parse_status<>'READY'",
                body,
                job.get("version_id"));
          else
            jdbc.update(
                "UPDATE data_asset_version SET parse_status='FAILED',parse_error=? WHERE id=? AND"
                    + " parse_status<>'READY'",
                error,
                job.get("version_id"));
          jdbc.update(
              "UPDATE data_asset_parse_job SET status=?,error=?,update_time=? WHERE id=? AND"
                  + " status='RUNNING' AND attempt=?",
              error == null ? "SUCCESS" : "FAILED",
              error,
              System.currentTimeMillis(),
              job.get("id"),
              attempt);
        });
  }

  public Map<String, Object> get(String id) {
    return get(null, id);
  }

  public Map<String, Object> get(String project, String id) {
    var scope = access.authorize(project, "TEST_DATA_ASSET:READ");
    var rows =
        jdbc.queryForList(
            "SELECT j.id,j.asset_id AS assetId,j.version_id AS"
                + " versionId,j.status,j.attempt,j.error,j.update_time AS updateTime FROM"
                + " data_asset_parse_job j JOIN data_asset a ON a.id=j.asset_id AND"
                + " a.organization_id=j.organization_id AND a.deleted=0 WHERE j.id=? AND"
                + " j.organization_id=?",
            id,
            scope.organizationId());
    if (rows.size() != 1) throw new MSException("解析任务不存在或无访问权限");
    return rows.getFirst();
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> retry(String id) {
    return retry(null, id);
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> retry(String project, String id) {
    var job = get(project, id);
    var actor = access.authorize(project, "TEST_DATA_ASSET:READ+UPDATE");
    if (jdbc.queryForList(
                "SELECT id FROM data_asset WHERE id=? AND organization_id=? AND deleted=0 FOR"
                    + " UPDATE",
                job.get("assetId"),
                actor.organizationId())
            .size()
        != 1) throw new MSException("资产已删除或无访问权限，不能重试解析");
    if (!"FAILED".equals(job.get("status"))) throw new MSException("仅失败的解析任务可重试");
    if (jdbc.update(
            "UPDATE data_asset_parse_job SET status='PENDING',error=NULL,update_time=? WHERE id=?"
                + " AND status='FAILED'",
            System.currentTimeMillis(),
            id)
        != 1) throw new MSException("任务状态已变化，请刷新");
    jdbc.update(
        "UPDATE data_asset_version SET parse_status='PENDING',parse_error=NULL WHERE id=?",
        job.get("versionId"));
    jdbc.update(
        "INSERT INTO data_asset_audit VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        actor.organizationId(),
        job.get("assetId"),
        job.get("versionId"),
        actor.actorId(),
        "PARSE_RETRY",
        System.currentTimeMillis());
    return get(project, id);
  }

  public boolean exists(String id) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM data_asset_parse_job WHERE id=?", Long.class, id)
        > 0;
  }
}
