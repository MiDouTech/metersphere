package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DataAssetServiceTests {
  @Test
  void projectResolutionFreezesHistoricalMetadataWithHistoricalContent() throws Exception {
    var first =
        service.save(
            null,
            Map.of(
                "name", "old title", "description", "old purpose", "content", Map.of("old", true)));
    String id = first.get("id").toString(), version = first.get("currentVersionId").toString();
    service.save(
        id,
        Map.of(
            "expectedRevision",
            1,
            "name",
            "new title",
            "description",
            "new purpose",
            "content",
            Map.of("new", true)));
    jdbc.execute(
        "CREATE TABLE project(id varchar(64),name varchar(255),organization_id varchar(64),deleted"
            + " boolean,enable boolean)");
    jdbc.update("INSERT INTO project VALUES('project','Project','org',false,true)");
    jdbc.execute("ALTER TABLE test_asset_relation ADD project_id varchar(64)");
    var global = mock(GlobalAssetAccess.class);
    when(global.authorize(any(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "caller"));
    var resolver = new DataAssetService(jdbc, global);
    var projectAccess = mock(AgentBusinessAccess.class);
    when(projectAccess.userHasPermission(anyString(), anyString(), anyString())).thenReturn(true);
    var field = DataAssetService.class.getDeclaredField("projectAccess");
    field.setAccessible(true);
    field.set(resolver, projectAccess);
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()));
    var frozen = tx.execute(status -> resolver.resolveForProject("project", id, version));
    assertEquals("old title", frozen.get("name"));
    assertEquals("old purpose", frozen.get("description"));
    assertEquals(Map.of("old", true), frozen.get("content"));
    assertEquals(version, frozen.get("versionId"));
    assertEquals(true, frozen.get("metadataAvailable"));
    assertEquals("new title", resolver.detail("project", id).get("name"));
  }

  @Test
  void immutableVersionMetadataRetainsOldNameDescriptionAndCategory() {
    jdbc.execute(
        "CREATE TABLE test_asset_category(id varchar(64),organization_id varchar(64),deleted"
            + " boolean)");
    jdbc.update(
        "INSERT INTO test_asset_category"
            + " VALUES('old-category','org',false),('new-category','org',false)");
    var first =
        service.save(
            null,
            Map.of(
                "name",
                "old name",
                "description",
                "old purpose",
                "categoryId",
                "old-category",
                "content",
                List.of(1)));
    String id = first.get("id").toString(), version = first.get("currentVersionId").toString();
    service.save(
        id,
        Map.of(
            "expectedRevision",
            1,
            "name",
            "new name",
            "description",
            "new purpose",
            "categoryId",
            "new-category"));
    var expected =
        Map.of(
            "name",
            "old name",
            "description",
            "old purpose",
            "categoryId",
            "old-category",
            "format",
            "JSON");
    assertEquals(expected, service.content(id, version).get("metadata"));
    assertEquals(expected, service.versions(id).get(1).get("metadata"));
    assertEquals(true, service.content(id, version).get("metadataAvailable"));
    assertEquals("ASSET_VERSION", service.content(id, version).get("metadataSource"));
    assertEquals("new name", ((Map<?, ?>) service.content(id, null).get("metadata")).get("name"));
    assertEquals(
        "new purpose", ((Map<?, ?>) service.content(id, null).get("metadata")).get("description"));
  }

  @Test
  void dateCellRoundTripPreservesSerialAndNormalizesEdits() {
    double serial = 12345.67890123;
    String value = org.apache.poi.ss.usermodel.DateUtil.getLocalDateTime(serial, true).toString();
    var date =
        Map.<String, Object>of(
            "type",
            "DATE",
            "value",
            value,
            "serial",
            serial,
            "date1904",
            true,
            "format",
            "yyyy-mm-dd hh:mm:ss");
    var root = Map.of("sheets", List.of(Map.of("name", "dates", "rows", List.of(List.of(date)))));
    String id =
        service
            .save(null, Map.of("name", "dates", "format", "XLSX", "content", root))
            .get("id")
            .toString();
    assertEquals(root, service.content(id, null).get("content"));
    var changed = new HashMap<String, Object>(date);
    changed.put("value", "2026-11-27");
    var edited =
        Map.of("sheets", List.of(Map.of("name", "dates", "rows", List.of(List.of(changed)))));
    service.save(id, Map.of("expectedRevision", 1, "content", edited));
    var content = (Map<?, ?>) service.content(id, null).get("content");
    var sheet = (Map<?, ?>) ((List<?>) content.get("sheets")).get(0);
    var actual = (Map<?, ?>) ((List<?>) ((List<?>) sheet.get("rows")).get(0)).get(0);
    assertEquals(
        org.apache.poi.ss.usermodel.DateUtil.getExcelDate(
            java.time.LocalDate.of(2026, 11, 27).atStartOfDay(), true),
        actual.get("serial"));
    assertEquals("2026-11-27T00:00", actual.get("value"));
    assertEquals("yyyy-mm-dd hh:mm:ss", actual.get("format"));
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () ->
            service.save(
                id,
                Map.of(
                    "expectedRevision",
                    2,
                    "content",
                    Map.of(
                        "sheets",
                        List.of(
                            Map.of(
                                "name",
                                "dates",
                                "rows",
                                List.of(List.of(Map.of("secret", "arbitrary")))))))));
  }

  @Test
  void manualLimitCountsUtf8BytesRatherThanCharacters() {
    String value = "汉".repeat(DataAssetLimits.MAX_CONTENT_BYTES / 3 + 1);
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> service.save(null, Map.of("name", "too big", "format", "TXT", "content", value)));
    assertEquals(0L, service.list("", "", 1, 20).get("total"));
  }

  JdbcTemplate jdbc;
  DataAssetService service;

  @BeforeEach
  void setup() throws Exception {
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:h2:mem:"
                    + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""));
    java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
    while (!java.nio.file.Files.exists(root.resolve("backend/framework/domain")))
      root = root.getParent();
    String migration =
        java.nio.file.Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_160__data_assets.sql"));
    for (String statement : migration.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    jdbc.execute(
        "CREATE TABLE test_asset_relation(source_asset_type varchar(32),target_asset_type"
            + " varchar(32),source_asset_id varchar(64),target_asset_id varchar(64))");
    jdbc.execute(
        "CREATE TABLE ai_test_data_lease(dataset_id varchar(64),status varchar(32),expires_at"
            + " bigint)");
    jdbc.execute("CREATE TABLE ai_execution_task(id varchar(64),status varchar(32))");
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    service = new DataAssetService(jdbc, access);
  }

  @Test
  void expiredLeasesAndHistoricalReferencesDoNotPreventLogicalDeletion() {
    var first = service.save(null, Map.of("name", "history", "content", List.of(1)));
    String id = first.get("id").toString();
    jdbc.update(
        "INSERT INTO ai_test_data_lease VALUES(?,'ACTIVE',?)",
        id,
        System.currentTimeMillis() + 60000);
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.delete(id, 1));
    jdbc.update("UPDATE ai_test_data_lease SET expires_at=0");
    jdbc.update("INSERT INTO test_asset_relation VALUES('DATASET','TASK',?,'finished')", id);
    jdbc.update("INSERT INTO ai_execution_task VALUES('finished','SUCCESS')");
    service.delete(id, 1);
    assertEquals(
        1L,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM data_asset_version WHERE asset_id=?", Long.class, id));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.detail(id));
  }

  @Test
  void paginatedPreviewNeverReplacesCompleteContent() {
    List<Integer> rows = java.util.stream.IntStream.range(0, 1000).boxed().toList();
    String id = service.save(null, Map.of("name", "rows", "content", rows)).get("id").toString();
    var page = service.preview(id, null, 2, 100, 0);
    assertEquals(100, ((List<?>) page.get("content")).size());
    assertEquals(1000, page.get("total"));
    var request = new HashMap<String, Object>(page);
    request.put("expectedRevision", 1);
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.save(id, request));
    assertEquals(rows, service.content(id, null).get("content"));
  }

  @Test
  void createRequestIdReplaysOnlyIdenticalContents() {
    var request =
        Map.<String, Object>of("name", "stable", "content", List.of(1), "requestId", "request-1");
    var first = service.save(null, request);
    assertEquals(first.get("id"), service.save(null, request).get("id"));
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () ->
            service.save(
                null, Map.of("name", "changed", "content", List.of(1), "requestId", "request-1")));
    assertEquals(1L, service.list("", "", 1, 20).get("total"));
  }

  @Test
  void formatValidationRejectsWrongContentAndFormatChanges() {
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> service.save(null, Map.of("name", "text", "format", "TXT", "content", List.of())));
    var first = service.save(null, Map.of("name", "text", "format", "TXT", "content", "hello"));
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () ->
            service.save(
                (String) first.get("id"),
                Map.of("expectedRevision", 1, "format", "JSON", "content", Map.of())));
  }

  @Test
  void versionSnapshotsRemainImmutableAndStaleUpdatesFail() {
    var first =
        service.save(
            null, Map.of("name", "sample", "format", "JSON", "content", Map.of("value", 1)));
    String id = (String) first.get("id");
    String version = (String) first.get("currentVersionId");
    service.save(id, Map.of("expectedRevision", 1, "content", Map.of("value", 2)));
    assertEquals(Map.of("value", 1), service.content(id, version).get("content"));
    assertEquals(Map.of("value", 2), service.content(id, null).get("content"));
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> service.save(id, Map.of("expectedRevision", 1, "content", List.of())));
    assertEquals(2, service.versions(id).size());
  }

  @Test
  void metadataOnlyUpdatePreservesEntireContent() {
    List<Integer> rows = java.util.stream.IntStream.range(0, 1000).boxed().toList();
    var created = service.save(null, Map.of("name", "rows", "content", rows));
    String id = (String) created.get("id");
    service.save(id, Map.of("name", "renamed", "expectedRevision", 1));
    assertEquals(rows, service.content(id, null).get("content"));
  }

  @Test
  void organizationIsolationAndReferencedDeletion() {
    var created = service.save(null, Map.of("name", "private", "content", List.of()));
    String id = (String) created.get("id");
    jdbc.update(
        "INSERT INTO ai_test_data_lease VALUES(?,'ACTIVE',?)",
        id,
        System.currentTimeMillis() + 60000);
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.delete(id, 1));
    jdbc.update("UPDATE data_asset SET organization_id='other' WHERE id=?", id);
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.detail(id));
  }
}
