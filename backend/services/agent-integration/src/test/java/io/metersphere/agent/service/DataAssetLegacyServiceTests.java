package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.project.service.FileManagementService;
import io.metersphere.sdk.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DataAssetLegacyServiceTests {
  @Test
  void familyMigrationRetainsEveryVersionCategoryAndManualCurrent() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    jdbc.execute(
        "CREATE TABLE project(id varchar(64),organization_id varchar(64),deleted boolean)");
    jdbc.update("INSERT INTO project VALUES('project','org',false)");
    jdbc.execute(
        "CREATE TABLE file_metadata(id varchar(64),name varchar(255),type varchar(16),size"
            + " bigint,project_id varchar(64),storage varchar(16),create_time bigint,ref_id"
            + " varchar(64),original_name varchar(255))");
    jdbc.execute(
        "CREATE TABLE test_asset_category(id varchar(64),organization_id varchar(64),deleted"
            + " boolean)");
    jdbc.execute(
        "CREATE TABLE test_asset_metadata(project_id varchar(64),asset_type varchar(32),asset_id"
            + " varchar(64),category_id varchar(64))");
    jdbc.update("INSERT INTO test_asset_category VALUES('category','org',false)");
    jdbc.update("INSERT INTO test_asset_metadata VALUES('project','DATASET','family','category')");
    for (int i = 1; i <= 2; i++)
      jdbc.update(
          "INSERT INTO file_metadata"
              + " VALUES(?,?,'JSON',20,'project','MINIO',?,'family','source.json')",
          "file" + i,
          "source",
          i);
    var global = mock(GlobalAssetAccess.class);
    when(global.authorize(any(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var source = mock(AgentBusinessAccess.class);
    when(source.userHasPermission(anyString(), anyString(), anyString())).thenReturn(true);
    var files = mock(FileManagementService.class);
    when(files.imageReadRequest(any()))
        .thenAnswer(
            inv ->
                new FileRequest(
                    "source",
                    "MINIO",
                    ((io.metersphere.project.domain.FileMetadata) inv.getArgument(0)).getId()));
    var assets = new DataAssetService(jdbc, global);
    var legacy =
        new DataAssetLegacyService(jdbc, source, global, assets, new DataAssetParser(), files);
    var repository = mock(FileRepository.class);
    when(repository.getFileAsStream(any()))
        .thenAnswer(
            inv -> {
              String file = ((FileRequest) inv.getArgument(0)).getFileName();
              return new java.io.ByteArrayInputStream(
                  ("{\"version\":" + file.substring(4) + "}")
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });
    var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    try (var center = mockStatic(FileCenter.class)) {
      center.when(() -> FileCenter.getRepository("MINIO")).thenReturn(repository);
      var first = tx.execute(status -> legacy.importFile("project", "file2"));
      String id = first.get("id").toString();
      assertEquals("category", first.get("categoryId"));
      var migratedMetadata = (Map<?, ?>) assets.content("project", id, null).get("metadata");
      assertTrue(migratedMetadata.containsKey("categoryId"));
      assertNull(migratedMetadata.get("categoryId"));
      assertEquals(
          "LEGACY_SOURCE_VERSION", assets.content("project", id, null).get("metadataSource"));
      assertEquals("category", assets.detail("project", id).get("categoryId"));
      assertEquals(2, assets.versions("project", id).size());
      assertEquals(Map.of("version", 2), assets.content("project", id, null).get("content"));
      tx.execute(status -> legacy.importFile("project", "family"));
      assertEquals(2, assets.versions("project", id).size());
      var manual =
          tx.execute(
              status ->
                  assets.save(
                      "project",
                      id,
                      Map.of("expectedRevision", 2, "content", Map.of("manual", true))));
      jdbc.update(
          "INSERT INTO file_metadata"
              + " VALUES('file3','source','JSON',20,'project','MINIO',3,'family','source.json')");
      tx.execute(status -> legacy.importFile("project", "family"));
      assertEquals(
          manual.get("currentVersionId"), assets.detail("project", id).get("currentVersionId"));
      assertEquals(Map.of("manual", true), assets.content("project", id, null).get("content"));
      assertEquals(4, assets.versions("project", id).size());
      assertEquals(
          3L,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM data_asset_source WHERE asset_id=?", Long.class, id));
      assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata", Long.class));
      verify(repository, never()).delete(any());
      for (int number = 4; number <= 5; number++)
        jdbc.update(
            "INSERT INTO file_metadata"
                + " VALUES(?,?,'JSON',20,'project','MINIO',?,'family','source.json')",
            "file" + number,
            "source",
            number);
      doAnswer(
              invocation -> {
                String file = ((FileRequest) invocation.getArgument(0)).getFileName();
                String body = "file5".equals(file) ? "invalid" : "{\"version\":4}";
                return new java.io.ByteArrayInputStream(
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
              })
          .when(repository)
          .getFileAsStream(any());
      assertThrows(
          io.metersphere.sdk.exception.MSException.class,
          () -> tx.execute(status -> legacy.importFile("project", "family")));
      assertEquals(4, assets.versions("project", id).size());
      assertEquals(
          manual.get("currentVersionId"), assets.detail("project", id).get("currentVersionId"));
      assertEquals(
          3L,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM data_asset_source WHERE asset_id=?", Long.class, id));
      verify(repository).delete(any());
    }
  }
}
