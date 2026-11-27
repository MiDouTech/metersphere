package io.metersphere.agent.service;

import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.service.FileManagementService;
import io.metersphere.sdk.exception.MSException;
import java.util.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataAssetLegacyService {
  private final JdbcTemplate jdbc;
  private final AgentBusinessAccess sourceAccess;
  private final GlobalAssetAccess global;
  private final DataAssetService assets;
  private final DataAssetParser parser;
  private final FileManagementService files;

  public DataAssetLegacyService(
      JdbcTemplate jdbc,
      AgentBusinessAccess sourceAccess,
      GlobalAssetAccess global,
      DataAssetService assets,
      DataAssetParser parser,
      FileManagementService files) {
    this.jdbc = jdbc;
    this.sourceAccess = sourceAccess;
    this.global = global;
    this.assets = assets;
    this.parser = parser;
    this.files = files;
  }

  private GlobalAssetAccess.Scope authorize(String project) {
    var scope = global.authorize(project, "TEST_DATA_ASSET:READ+UPDATE");
    if (!sourceAccess.userHasPermission(scope.actorId(), project, "PROJECT_FILE_MANAGEMENT:READ"))
      throw AgentBusinessAccess.denied();
    return scope;
  }

  public List<Map<String, Object>> inventory(String project) {
    var scope = authorize(project);
    return jdbc.queryForList(
        "SELECT COALESCE(NULLIF(f.ref_id,''),f.id) AS familyId,COUNT(*) AS versionCount,SUM(f.size)"
            + " AS totalBytes,MAX(f.name) AS name,COUNT(s.source_file_id) AS migratedVersions FROM"
            + " file_metadata f JOIN project p ON CAST(p.id AS BINARY(256))=CAST(f.project_id AS"
            + " BINARY(256)) LEFT JOIN data_asset_source s ON CAST(s.source_file_id AS"
            + " BINARY(256))=CAST(f.id AS BINARY(256)) WHERE f.project_id=? AND p.organization_id=?"
            + " AND p.deleted=0 AND UPPER(f.type) IN ('CSV','JSON','TXT','XLS','XLSX') GROUP BY"
            + " COALESCE(NULLIF(f.ref_id,''),f.id)",
        project,
        scope.organizationId());
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Object> importFile(String project, String id) {
    var scope = authorize(project);
    var source =
        jdbc.queryForList(
            "SELECT COALESCE(NULLIF(ref_id,''),id) AS familyId FROM file_metadata WHERE"
                + " project_id=? AND (id=? OR ref_id=?) ORDER BY create_time,id LIMIT 1",
            project,
            id,
            id);
    if (source.isEmpty()) throw new MSException("来源文件不存在或无访问权限");
    String family = source.getFirst().get("familyId").toString();
    var versions =
        jdbc.queryForList(
            "SELECT id,size FROM file_metadata WHERE project_id=? AND"
                + " COALESCE(NULLIF(ref_id,''),id)=? ORDER BY create_time,id FOR UPDATE",
            project,
            family);
    if (versions.size() > 50
        || versions.stream().mapToLong(v -> ((Number) v.get("size")).longValue()).sum()
            > 20_000_000) throw new MSException("单次迁移最多50个版本且总量不超过20MB");
    var mapped =
        jdbc.queryForList(
            "SELECT DISTINCT s.asset_id FROM data_asset_source s JOIN file_metadata f ON CAST(f.id"
                + " AS BINARY(256))=CAST(s.source_file_id AS BINARY(256)) WHERE f.project_id=? AND"
                + " COALESCE(NULLIF(f.ref_id,''),f.id)=?",
            project,
            family);
    if (mapped.size() > 1) throw new MSException("来源版本已映射多个资产，需要先处理映射冲突");
    String target = mapped.isEmpty() ? null : mapped.getFirst().get("asset_id").toString();
    String preserved = null;
    if (target != null) {
      var current = assets.detail(project, target);
      var last =
          jdbc.queryForList(
              "SELECT s.source_version_id FROM data_asset_source s JOIN data_asset_version v ON"
                  + " v.id=s.source_version_id WHERE s.asset_id=? ORDER BY v.version_no DESC LIMIT"
                  + " 1",
              target);
      if (!last.isEmpty()
          && !current.get("currentVersionId").equals(last.getFirst().get("source_version_id")))
        preserved = current.get("currentVersionId").toString();
    }
    long[] budget = {0};
    Map<String, Object> result = null;
    for (var version : versions) {
      result = importVersion(project, version.get("id").toString(), target, budget);
      target = result.get("id").toString();
    }
    if (preserved != null)
      jdbc.update("UPDATE data_asset SET current_version_id=? WHERE id=?", preserved, target);
    return assets.detail(project, target);
  }

  private Map<String, Object> importVersion(
      String project, String id, String target, long[] budget) {
    var scope = global.authorize(project, "TEST_DATA_ASSET:READ+UPDATE");
    if (!sourceAccess.userHasPermission(scope.actorId(), project, "PROJECT_FILE_MANAGEMENT:READ"))
      throw AgentBusinessAccess.denied();
    var source =
        jdbc.query(
            "SELECT f.* FROM file_metadata f JOIN project p ON CAST(p.id AS"
                + " BINARY(256))=CAST(f.project_id AS BINARY(256)) WHERE f.id=? AND f.project_id=?"
                + " AND p.organization_id=? AND p.deleted=0 FOR UPDATE",
            BeanPropertyRowMapper.newInstance(FileMetadata.class),
            id,
            project,
            scope.organizationId());
    if (source.size() != 1) throw new MSException("来源文件不存在或无访问权限");
    var mapped =
        jdbc.queryForList("SELECT asset_id FROM data_asset_source WHERE source_file_id=?", id);
    if (!mapped.isEmpty())
      return assets.detail(project, (String) mapped.getFirst().get("asset_id"));
    var file = source.getFirst();
    if (file.getSize() == null
        || file.getSize() > io.metersphere.agent.service.DataAssetLimits.MAX_BYTES)
      throw new MSException("来源文件超过2MB限制");
    String format = Objects.toString(file.getType(), "").toUpperCase(Locale.ROOT).replace(".", "");
    try {
      byte[] bytes;
      try (var stream =
          io.metersphere.sdk.file.FileCenter.getRepository(file.getStorage())
              .getFileAsStream(files.imageReadRequest(file))) {
        bytes = stream.readNBytes((io.metersphere.agent.service.DataAssetLimits.MAX_BYTES + 1));
      }
      budget[0] += bytes.length;
      if (budget[0] > 20_000_000) throw new MSException("来源实际总量超过20MB限制");
      var request = new HashMap<String, Object>();
      if (target != null) {
        var current = assets.detail(project, target);
        request.put("expectedRevision", current.get("revision"));
        request.put("name", current.get("name"));
      } else {
        var categories =
            jdbc.queryForList(
                "SELECT m.category_id FROM test_asset_metadata m JOIN test_asset_category c ON"
                    + " CAST(c.id AS BINARY(256))=CAST(m.category_id AS BINARY(256)) AND"
                    + " c.organization_id=? AND c.deleted=0 WHERE m.project_id=? AND"
                    + " m.asset_type='DATASET' AND m.asset_id=? LIMIT 1",
                scope.organizationId(),
                project,
                Objects.toString(file.getRefId(), file.getId()));
        if (!categories.isEmpty())
          request.put("categoryId", categories.getFirst().get("category_id"));
      }
      if (target == null) request.put("name", file.getName());
      request.put("format", format);
      request.put("content", parser.parse(bytes, format));
      var d =
          assets.uploadVersion(
              project,
              target,
              bytes,
              Objects.toString(file.getOriginalName(), file.getName()),
              request);
      jdbc.update(
          "UPDATE data_asset_version SET metadata_json=?,metadata_source='LEGACY_SOURCE_VERSION'"
              + " WHERE id=? AND asset_id=?",
          DataAssetMetadata.encode(file.getName(), file.getDescription(), null, format),
          d.get("currentVersionId"),
          d.get("id"));
      jdbc.update(
          "INSERT INTO data_asset_source VALUES(?,?,?,?)",
          id,
          project,
          d.get("id"),
          d.get("currentVersionId"));
      return d;
    } catch (MSException e) {
      throw e;
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(DataAssetLegacyService.class)
          .warn(
              "data_asset legacy import failed sourceId={} stage=storage rootType={}",
              id,
              e.getClass().getSimpleName());
      throw new MSException("来源文件暂时无法导入");
    }
  }
}
