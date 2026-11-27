package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class DataAssetMigrationMySqlTests {
  @Test
  void mixedLegacyAndNewCollationsSupportCategoriesInventoryAndProvenance() throws Exception {
    var ds =
        new SingleConnectionDataSource(
            "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true",
            "root",
            "quality-test-only",
            true);
    var jdbc = new JdbcTemplate(ds);
    String schema = "data_mixed_" + UUID.randomUUID().toString().replace("-", "");
    try {
      jdbc.execute(
          "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
      jdbc.execute("USE `" + schema + "`");
      Path root = Path.of("").toAbsolutePath();
      while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
      for (String statement :
          Files.readString(
                  root.resolve(
                      "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_160__data_assets.sql"))
              .split(";")) if (!statement.isBlank()) jdbc.execute(statement);
      jdbc.execute(
          "CREATE TABLE project(id varchar(64),name varchar(255),organization_id"
              + " varchar(64),deleted boolean,enable boolean) DEFAULT CHARSET=utf8mb4"
              + " COLLATE=utf8mb4_general_ci");
      jdbc.update("INSERT INTO project VALUES('project','legacy project','org',false,true)");
      jdbc.execute(
          "CREATE TABLE test_asset_category(id varchar(64),organization_id varchar(64),parent_id"
              + " varchar(64),name varchar(100),path varchar(1024),level int,sort bigint,deleted"
              + " boolean) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
      jdbc.update(
          "INSERT INTO test_asset_category"
              + " VALUES('category','org','','Category','Category',1,1,false)");
      jdbc.execute(
          "CREATE TABLE file_metadata(id varchar(64),project_id varchar(64),ref_id varchar(64),name"
              + " varchar(255),type varchar(16),size bigint,create_time bigint) DEFAULT"
              + " CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
      jdbc.update(
          "INSERT INTO file_metadata VALUES('legacy-file','project','family','file','JSON',10,1)");
      jdbc.execute(
          "CREATE TABLE test_asset_relation(project_id varchar(64),source_asset_id"
              + " varchar(64),target_asset_id varchar(64)) DEFAULT CHARSET=utf8mb4"
              + " COLLATE=utf8mb4_general_ci");
      var global = mock(GlobalAssetAccess.class);
      when(global.authorize(any(), anyString()))
          .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
      var sourceAccess = mock(AgentBusinessAccess.class);
      when(sourceAccess.userHasPermission(anyString(), anyString(), anyString())).thenReturn(true);
      var assets = new DataAssetService(jdbc, global);
      var field = DataAssetService.class.getDeclaredField("projectAccess");
      field.setAccessible(true);
      field.set(assets, sourceAccess);
      var first =
          assets.save(
              null, Map.of("name", "data", "categoryId", "category", "content", List.of(1)));
      String id = first.get("id").toString();
      jdbc.update(
          "INSERT INTO data_asset_source VALUES('legacy-file','project',?,?)",
          id,
          first.get("currentVersionId"));
      jdbc.update("INSERT INTO test_asset_relation VALUES('project',?,'historic-task')", id);
      assertEquals(1L, ((Number) assets.categories().stream().filter(c -> "category".equals(c.get("id"))).findFirst().orElseThrow().get("assetCount")).longValue());
      var detail = assets.detail(id);
      assertEquals(1, ((List<?>) detail.get("sources")).size());
      assertEquals(1, ((List<?>) detail.get("projectReferences")).size());
      var legacy =
          new DataAssetLegacyService(
              jdbc,
              sourceAccess,
              global,
              assets,
              new DataAssetParser(),
              mock(io.metersphere.project.service.FileManagementService.class));
      assertEquals(
          1L,
          ((Number) legacy.inventory("project").getFirst().get("migratedVersions")).longValue());
    } finally {
      jdbc.execute("USE quality_verify");
      jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      ds.destroy();
    }
  }

  @Test
  void actualMigrationSupportsVersionedCrudIsolationRollbackAndPersistedParsing() throws Exception {
    var ds =
        new SingleConnectionDataSource(
            "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true",
            "root",
            "quality-test-only",
            true);
    var jdbc = new JdbcTemplate(ds);
    String schema = "data_asset_" + UUID.randomUUID().toString().replace("-", "");
    try {
      jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      jdbc.execute("USE `" + schema + "`");
      Path root = Path.of("").toAbsolutePath();
      while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
      String sql =
          Files.readString(
              root.resolve(
                  "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_160__data_assets.sql"));
      for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
      jdbc.execute(
          "CREATE TABLE test_asset_relation(source_asset_type varchar(32),target_asset_type"
              + " varchar(32),source_asset_id varchar(64),target_asset_id varchar(64))");
      jdbc.execute(
          "CREATE TABLE ai_test_data_lease(dataset_id varchar(64),status varchar(32),expires_at"
              + " bigint)");
      jdbc.execute("CREATE TABLE ai_execution_task(id varchar(64),status varchar(32))");
      var access = mock(GlobalAssetAccess.class);
      when(access.authorize(isNull(), anyString()))
          .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
      var assets = new DataAssetService(jdbc, access);
      var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
      var first =
          tx.execute(
              status ->
                  assets.save(
                      null,
                      Map.of(
                          "name",
                          "dataset",
                          "content",
                          Map.of("rows", List.of(List.of(1, 2))),
                          "requestId",
                          "create-1")));
      String id = first.get("id").toString(), version = first.get("currentVersionId").toString();
      assertEquals(
          id,
          tx.execute(
                  status ->
                      assets.save(
                          null,
                          Map.of(
                              "name",
                              "dataset",
                              "content",
                              Map.of("rows", List.of(List.of(1, 2))),
                              "requestId",
                              "create-1")))
              .get("id"));
      tx.execute(
          status ->
              assets.save(
                  id,
                  Map.of(
                      "expectedRevision", 1, "content", Map.of("rows", List.of(List.of(3, 4))))));
      assertEquals(
          Map.of("rows", List.of(List.of(1, 2))), assets.content(id, version).get("content"));
      assertEquals(2, assets.versions(id).size());
      assertThrows(
          io.metersphere.sdk.exception.MSException.class,
          () ->
              tx.execute(
                  status ->
                      assets.save(
                          id, Map.of("expectedRevision", 2, "description", "x".repeat(2001)))));
      assertEquals(2, ((Number) assets.detail(id).get("revision")).intValue());
      assertEquals(2, assets.versions(id).size());
      var pending =
          tx.execute(status -> assets.save(null, Map.of("name", "parse", "content", List.of())));
      String pendingId = pending.get("id").toString(),
          pendingVersion = pending.get("currentVersionId").toString();
      jdbc.update(
          "UPDATE data_asset_version SET parse_status='PENDING' WHERE id=?", pendingVersion);
      jdbc.update(
          "INSERT INTO data_asset_file VALUES(?,?,?)",
          pendingVersion,
          "original/immutable",
          "data.json");
      jdbc.update(
          "INSERT INTO data_asset_parse_job VALUES('parse','org','actor',?,?,'PENDING',0,?,NULL)",
          pendingId,
          pendingVersion,
          System.currentTimeMillis());
      var worker = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
      var repository = mock(io.metersphere.sdk.file.FileRepository.class);
      try (var center = mockStatic(io.metersphere.sdk.file.FileCenter.class)) {
        center
            .when(() -> io.metersphere.sdk.file.FileCenter.getRepository("MINIO"))
            .thenReturn(repository);
        when(repository.getFileAsStream(any()))
            .thenReturn(
                new java.io.ByteArrayInputStream(
                    "{\"value\":12}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        worker.drain();
      }
      assertEquals("SUCCESS", worker.get("parse").get("status"));
      assertEquals(Map.of("value", 12), assets.content(pendingId, null).get("content"));
      jdbc.update(
          "INSERT INTO ai_test_data_lease VALUES(?,'ACTIVE',?)",
          id,
          System.currentTimeMillis() + 60000);
      assertThrows(
          io.metersphere.sdk.exception.MSException.class,
          () ->
              tx.execute(
                  status -> {
                    assets.delete(id, 2);
                    return null;
                  }));
      jdbc.update("DELETE FROM ai_test_data_lease");
      tx.execute(
          status -> {
            assets.delete(id, 2);
            return null;
          });
      assertThrows(io.metersphere.sdk.exception.MSException.class, () -> assets.detail(id));
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM data_asset_version WHERE asset_id=?", Integer.class, id));
    } finally {
      jdbc.execute("USE quality_verify");
      jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      ds.destroy();
    }
  }
}
