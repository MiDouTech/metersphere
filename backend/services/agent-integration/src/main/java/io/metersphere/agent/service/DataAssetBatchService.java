package io.metersphere.agent.service;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DataAssetBatchService {
  private final DataAssetService service;

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final GlobalAssetAccess access;
  private final com.fasterxml.jackson.databind.ObjectMapper json =
      new com.fasterxml.jackson.databind.ObjectMapper();

  public DataAssetBatchService(
      DataAssetService service,
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      GlobalAssetAccess access) {
    this.service = service;
    this.jdbc = jdbc;
    this.access = access;
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      DataAssetDiagnostics.failure(
          DataAssetBatchService.class,
          "DATA_ASSET_BATCH_SERIALIZATION_FAILURE",
          null,
          "batch-encode",
          e);
      throw new io.metersphere.sdk.exception.MSException("批次内容无效");
    }
  }

  public Map<String, Object> get(String id) {
    return get(null, id);
  }

  public Map<String, Object> get(String project, String id) {
    var scope = access.authorize(project, "TEST_DATA_ASSET:READ");
    jdbc.update(
        "UPDATE data_asset_batch_job SET status='INTERRUPTED' WHERE id=? AND organization_id=? AND"
            + " actor_id=? AND status IN ('RUNNING','RETRYING') AND update_time<?",
        id,
        scope.organizationId(),
        scope.actorId(),
        System.currentTimeMillis() - 300000);
    var rows =
        jdbc.queryForList(
            "SELECT id,status,result_json,retry_job_id FROM data_asset_batch_job WHERE id=? AND"
                + " organization_id=? AND actor_id=?",
            id,
            scope.organizationId(),
            scope.actorId());
    if (rows.size() != 1) throw new io.metersphere.sdk.exception.MSException("批次不存在或无访问权限");
    try {
      var row = rows.getFirst();
      var output = new HashMap<String, Object>();
      output.put("retryJobId", row.get("retry_job_id"));
      output.putAll(
          Map.of(
              "id",
              id,
              "status",
              row.get("status"),
              "items",
              json.readValue(
                  jdbc.queryForObject(
                      "SELECT result_json FROM data_asset_batch_job WHERE id=?", String.class, id),
                  Object.class)));
      return output;
    } catch (Exception e) {
      DataAssetDiagnostics.failure(
          DataAssetBatchService.class,
          "DATA_ASSET_BATCH_RESULT_FAILURE",
          id,
          "batch-result-read",
          e);
      throw new io.metersphere.sdk.exception.MSException("批次结果暂时无法读取");
    }
  }

  public Map<String, Object> retry(String id) {
    return retry(null, id);
  }

  public Map<String, Object> retry(String project, String id) {
    var current = get(project, id);
    var scope = access.authorize(project, "TEST_DATA_ASSET:READ+UPDATE");
    if (!Set.of("FAILED", "INTERRUPTED").contains(current.get("status")))
      throw new io.metersphere.sdk.exception.MSException("仅失败或中断的批次可重试");
    if (jdbc.update(
            "UPDATE data_asset_batch_job SET status='RETRYING',update_time=? WHERE id=? AND"
                + " status=? AND organization_id=? AND actor_id=?",
            System.currentTimeMillis(),
            id,
            current.get("status"),
            scope.organizationId(),
            scope.actorId())
        != 1) throw new io.metersphere.sdk.exception.MSException("批次正在重试，请刷新");
    try {
      var items =
          json.readValue(
              jdbc.queryForObject(
                  "SELECT request_json FROM data_asset_batch_job WHERE id=?", String.class, id),
              new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
      var results = (List<Map<String, Object>>) current.get("items");
      Set<String> succeeded = new HashSet<>();
      for (var result : results)
        if ("SUCCESS".equals(result.get("status"))) succeeded.add(result.get("id").toString());
      var pending = items.stream().filter(item -> !succeeded.contains(item.get("id"))).toList();
      if (pending.isEmpty()) {
        jdbc.update(
            "UPDATE data_asset_batch_job SET status='SUCCESS',update_time=? WHERE id=? AND"
                + " status='RETRYING'",
            System.currentTimeMillis(),
            id);
        return get(project, id);
      }
      var child = submit(project, pending);
      jdbc.update(
          "UPDATE data_asset_batch_job SET status='RETRIED',retry_job_id=?,update_time=? WHERE id=?"
              + " AND status='RETRYING'",
          child.get("id"),
          System.currentTimeMillis(),
          id);
      return child;
    } catch (io.metersphere.sdk.exception.MSException e) {
      throw e;
    } catch (Exception e) {
      DataAssetDiagnostics.failure(
          DataAssetBatchService.class, "DATA_ASSET_BATCH_RETRY_FAILURE", id, "batch-retry", e);
      throw new io.metersphere.sdk.exception.MSException("批次暂时无法重试");
    }
  }

  public Map<String, Object> submit(List<Map<String, Object>> items) {
    return submit(null, items);
  }

  public Map<String, Object> submit(String project, List<Map<String, Object>> items) {
    if (items == null || items.isEmpty() || items.size() > 100)
      throw new io.metersphere.sdk.exception.MSException("每批需要1至100项数据");
    List<Map<String, Object>> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (var item : items) {
      String id = Objects.toString(item.get("id"), "");
      if (id.isBlank() || !seen.add(id) || !(item.get("expectedRevision") instanceof Number))
        throw new io.metersphere.sdk.exception.MSException("批次资产及版本参数无效");
    }
    var scope = access.authorize(project, "TEST_DATA_ASSET:READ+UPDATE");
    String job = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO"
            + " data_asset_batch_job(id,organization_id,actor_id,status,request_json,result_json,create_time,update_time)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        job,
        scope.organizationId(),
        scope.actorId(),
        "RUNNING",
        encode(items),
        "[]",
        System.currentTimeMillis(),
        System.currentTimeMillis());
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                Objects.requireNonNull(jdbc.getDataSource())));
    for (var item : items) {
      String id = item.get("id").toString();
      Map<String, Object> outcome;
      try {
        outcome =
            tx.execute(
                status -> {
                  var owned =
                      jdbc.queryForList(
                          "SELECT id FROM data_asset_batch_job WHERE id=? AND status='RUNNING' FOR"
                              + " UPDATE",
                          job);
                  if (owned.isEmpty())
                    throw new io.metersphere.sdk.exception.MSException("批次已中断，请刷新结果");
                  if (Boolean.TRUE.equals(item.get("delete")))
                    service.delete(project, id, ((Number) item.get("expectedRevision")).intValue());
                  else service.save(project, id, item);
                  var success = Map.<String, Object>of("id", id, "status", "SUCCESS");
                  var persisted = new ArrayList<Map<String, Object>>(result);
                  persisted.add(success);
                  jdbc.update(
                      "UPDATE data_asset_batch_job SET result_json=?,update_time=? WHERE id=? AND"
                          + " status='RUNNING'",
                      encode(persisted),
                      System.currentTimeMillis(),
                      job);
                  return success;
                });
      } catch (RuntimeException failure) {
        DataAssetDiagnostics.failure(
            DataAssetBatchService.class,
            "DATA_ASSET_BATCH_ITEM_FAILURE",
            job,
            "batch-item-transaction",
            failure);
        outcome =
            Map.of(
                "id",
                id,
                "status",
                "FAILED",
                "error",
                failure instanceof io.metersphere.sdk.exception.MSException
                    ? failure.getMessage()
                    : "处理失败，请刷新后重试");
        var persisted = new ArrayList<Map<String, Object>>(result);
        persisted.add(outcome);
        jdbc.update(
            "UPDATE data_asset_batch_job SET result_json=?,update_time=? WHERE id=? AND"
                + " status='RUNNING'",
            encode(persisted),
            System.currentTimeMillis(),
            job);
      }
      result.add(outcome);
    }
    jdbc.update(
        "UPDATE data_asset_batch_job SET status=? WHERE id=?",
        result.stream().anyMatch(item -> "FAILED".equals(item.get("status")))
            ? "FAILED"
            : "SUCCESS",
        job);
    return Map.of("id", job, "items", result);
  }
}
