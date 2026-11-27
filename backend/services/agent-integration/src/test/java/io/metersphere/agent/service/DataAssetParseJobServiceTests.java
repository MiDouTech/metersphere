package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.sdk.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataAssetParseJobServiceTests {
  @Test
  void organizationReadersCanReadSharedParseStatusAndWritersRetryWithOwnAudit() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "shared", "content", List.of()));
    String id = asset.get("id").toString(), version = asset.get("currentVersionId").toString();
    jdbc.update("UPDATE data_asset_version SET parse_status='FAILED' WHERE id=?", version);
    jdbc.update(
        "INSERT INTO data_asset_parse_job"
            + " VALUES('shared-job','org','original-uploader',?,?,'FAILED',1,?,NULL)",
        id,
        version,
        System.currentTimeMillis());
    var current =
        new java.util.concurrent.atomic.AtomicReference<>(
            new GlobalAssetAccess.Scope("org", "different-reader"));
    var access = mock(GlobalAssetAccess.class);
    var writable = new java.util.concurrent.atomic.AtomicBoolean(false);
    when(access.authorize(isNull(), anyString()))
        .thenAnswer(
            invocation -> {
              if ("TEST_DATA_ASSET:READ+UPDATE".equals(invocation.getArgument(1))
                  && !writable.get()) throw AgentBusinessAccess.denied();
              return current.get();
            });
    var worker = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
    assertEquals("FAILED", worker.get("shared-job").get("status"));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.retry("shared-job"));
    assertEquals("FAILED", worker.get("shared-job").get("status"));
    writable.set(true);
    var tx =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()));
    assertEquals("PENDING", tx.execute(status -> worker.retry("shared-job")).get("status"));
    assertEquals(
        "different-reader",
        jdbc.queryForObject(
            "SELECT actor_id FROM data_asset_audit WHERE asset_id=? AND action='PARSE_RETRY'",
            String.class,
            id));
    verify(access, times(2)).authorize(null, "TEST_DATA_ASSET:READ+UPDATE");
    current.set(new GlobalAssetAccess.Scope("outside-org", "different-reader"));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.get("shared-job"));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.retry("shared-job"));
    current.set(new GlobalAssetAccess.Scope("org", "different-reader"));
    jdbc.update("UPDATE data_asset SET deleted=1 WHERE id=?", id);
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.get("shared-job"));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.retry("shared-job"));
  }

  @Test
  void replacementCreatesPendingImmutableVersionWithCasAndKeepsPreviousReadable() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var original =
        fixture.service.save(
            null, Map.of("name", "original title", "content", Map.of("old", true)));
    String id = original.get("id").toString(),
        oldVersion = original.get("currentVersionId").toString();
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(eq("project"), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var target = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
    var factory = new org.springframework.aop.framework.ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
    var service = (DataAssetParseJobService) factory.getProxy();
    var repository = mock(FileRepository.class);
    byte[] bytes = "{\"new\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(repository.getFileAsStream(any()))
        .thenAnswer(invocation -> new java.io.ByteArrayInputStream(bytes));
    try (var center = mockStatic(FileCenter.class)) {
      center.when(() -> FileCenter.getRepository("MINIO")).thenReturn(repository);
      var pending = service.replace("project", id, 1, bytes, "replacement.json", "replace-1");
      assertEquals(id, pending.get("id"));
      assertEquals("PENDING", pending.get("parseStatus"));
      assertEquals("original title", fixture.service.detail(id).get("name"));
      assertEquals(Map.of("old", true), fixture.service.content(id, oldVersion).get("content"));
      assertThrows(
          io.metersphere.sdk.exception.MSException.class, () -> fixture.service.content(id, null));
      assertEquals(
          id, service.replace("project", id, 1, bytes, "replacement.json", "replace-1").get("id"));
      assertThrows(
          io.metersphere.sdk.exception.MSException.class,
          () -> service.replace("project", id, 1, bytes, "replacement.json", "different-request"));
      assertEquals(2, fixture.service.versions(id).size());
      service.drain();
      assertEquals(Map.of("new", true), fixture.service.content(id, null).get("content"));
      assertEquals(Map.of("old", true), fixture.service.content(id, oldVersion).get("content"));
      verify(repository, times(1)).saveFile(any(byte[].class), any(FileRequest.class));
    }
  }

  @Test
  void explicitProjectUploadRunsTransactionAndPersistsIdempotentOriginal() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(eq("project"), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var target = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
    var factory = new org.springframework.aop.framework.ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
    var service = (DataAssetParseJobService) factory.getProxy();
    var repository = mock(FileRepository.class);
    byte[] bytes = "{\"value\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try (var center = mockStatic(FileCenter.class)) {
      center.when(() -> FileCenter.getRepository("MINIO")).thenReturn(repository);
      var first = service.upload("project", bytes, "data.json", "data", null, "same-request");
      assertEquals("PENDING", first.get("parseStatus"));
      assertEquals(
          first.get("id"),
          service.upload("project", bytes, "data.json", "data", null, "same-request").get("id"));
      assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_file", Long.class));
      assertEquals(
          1L, jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_parse_job", Long.class));
      verify(repository, times(1)).saveFile(any(byte[].class), any(FileRequest.class));
      verify(repository, never()).delete(any());
    }
  }

  @Test
  void compressedWorkbookCannotExceedExpandedUtf8Budget() throws Exception {
    byte[] workbook;
    try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var output = new java.io.ByteArrayOutputStream()) {
      var sheet = book.createSheet("data");
      for (int row = 0; row < 1000; row++)
        sheet.createRow(row).createCell(0).setCellValue("汉".repeat(800));
      book.write(output);
      workbook = output.toByteArray();
    }
    assertTrue(workbook.length < DataAssetLimits.MAX_BYTES);
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "pending", "content", List.of()));
    String id = asset.get("id").toString(), version = asset.get("currentVersionId").toString();
    jdbc.update("UPDATE data_asset SET format='XLSX' WHERE id=?", id);
    jdbc.update("UPDATE data_asset_version SET parse_status='PENDING' WHERE id=?", version);
    jdbc.update(
        "INSERT INTO data_asset_file VALUES(?,?,?)", version, "immutable/path", "data.xlsx");
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES('job','org','user',?,?,'PENDING',0,?,NULL)",
        id,
        version,
        System.currentTimeMillis());
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var worker = spy(new DataAssetParseJobService(jdbc, access, new DataAssetParser()));
    doReturn(workbook).when(worker).readOriginal(anyMap());
    worker.drain();
    assertEquals("FAILED", worker.get("job").get("status"));
    assertEquals("解析后内容超过2MB限制", worker.get("job").get("error"));
  }

  @Test
  void staleWorkerCannotOverwriteNewAttemptSuccess() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "pending", "content", List.of()));
    String id = asset.get("id").toString(), version = asset.get("currentVersionId").toString();
    jdbc.update("UPDATE data_asset_version SET parse_status='PENDING' WHERE id=?", version);
    jdbc.update(
        "INSERT INTO data_asset_file VALUES(?,?,?)", version, "immutable/path", "data.json");
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES('job','org','user',?,?,'PENDING',0,?,NULL)",
        id,
        version,
        System.currentTimeMillis());
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var worker = spy(new DataAssetParseJobService(jdbc, access, new DataAssetParser()));
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            invocation -> {
              if (reads.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                  throw new java.io.IOException();
                throw new java.io.IOException("old worker storage failed");
              }
              return "{\"new\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            })
        .when(worker)
        .readOriginal(anyMap());
    var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      var stale = executor.submit(() -> worker.drain());
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
      jdbc.update("UPDATE data_asset_parse_job SET update_time=0 WHERE id='job'");
      worker.drain();
      worker.retry("job");
      worker.drain();
      assertEquals("SUCCESS", worker.get("job").get("status"));
      release.countDown();
      stale.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals("SUCCESS", worker.get("job").get("status"));
      assertEquals(Map.of("new", true), fixture.service.content(id, null).get("content"));
      assertEquals(2, ((Number) worker.get("job").get("attempt")).intValue());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void pendingDeletedAssetIsCancelledWithoutStorageReads() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "pending", "content", List.of()));
    String id = asset.get("id").toString(), version = asset.get("currentVersionId").toString();
    jdbc.update("UPDATE data_asset SET deleted=1 WHERE id=?", id);
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES('job','org','user',?,?,'PENDING',0,?,NULL)",
        id,
        version,
        System.currentTimeMillis());
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var worker = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
    try (var center = mockStatic(FileCenter.class)) {
      worker.drain();
      center.verifyNoInteractions();
    }
    assertEquals(
        "CANCELLED",
        jdbc.queryForObject(
            "SELECT status FROM data_asset_parse_job WHERE id='job'", String.class));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.get("job"));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> worker.retry("job"));
  }

  @Test
  void staleClaimIsFailedAndFreshClaimIsNeverClaimedAgain() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "pending", "content", List.of()));
    String id = asset.get("id").toString(), version = asset.get("currentVersionId").toString();
    jdbc.update("UPDATE data_asset_version SET parse_status='PENDING' WHERE id=?", version);
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES('stale','org','user',?,?,'RUNNING',1,?,NULL)",
        id,
        version,
        System.currentTimeMillis() - 600000);
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES('fresh','org','user',?,?,'RUNNING',1,?,NULL)",
        id,
        version,
        System.currentTimeMillis());
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var worker = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
    try (var center = mockStatic(FileCenter.class)) {
      worker.drain();
      worker.drain();
      center.verifyNoInteractions();
    }
    assertEquals("FAILED", worker.get("stale").get("status"));
    assertEquals(
        "FAILED",
        jdbc.queryForObject(
            "SELECT parse_status FROM data_asset_version WHERE id=?", String.class, version));
    assertEquals("RUNNING", worker.get("fresh").get("status"));
    assertEquals(1, ((Number) worker.get("fresh").get("attempt")).intValue());
  }

  @Test
  void persistedParseFailureCanBeRetriedWithoutReplacingOriginal() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "data", "content", List.of()));
    String id = asset.get("id").toString(), version = asset.get("currentVersionId").toString();
    jdbc.update("UPDATE data_asset_version SET parse_status='PENDING' WHERE id=?", version);
    jdbc.update(
        "INSERT INTO data_asset_file VALUES(?,?,?)", version, "immutable/path", "data.json");
    jdbc.update("UPDATE data_asset SET format='JSON' WHERE id=?", id);
    jdbc.update(
        "INSERT INTO data_asset_parse_job VALUES('job','org','user',?,?,'PENDING',0,?,NULL)",
        id,
        version,
        System.currentTimeMillis());
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var worker = new DataAssetParseJobService(jdbc, access, new DataAssetParser());
    var repository = mock(FileRepository.class);
    try (var center = mockStatic(FileCenter.class)) {
      center.when(() -> FileCenter.getRepository("MINIO")).thenReturn(repository);
      when(repository.getFileAsStream(any()))
          .thenReturn(
              new java.io.ByteArrayInputStream(
                  "broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      worker.drain();
      assertEquals("FAILED", worker.get("job").get("status"));
      assertThrows(
          io.metersphere.sdk.exception.MSException.class, () -> fixture.service.content(id, null));
      worker.retry("job");
      when(repository.getFileAsStream(any()))
          .thenReturn(
              new java.io.ByteArrayInputStream(
                  "{\"rows\":[1,2]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      worker.drain();
      assertEquals("SUCCESS", worker.get("job").get("status"));
      assertEquals(Map.of("rows", List.of(1, 2)), fixture.service.content(id, null).get("content"));
      assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_file", Long.class));
      assertEquals(2, ((Number) worker.get("job").get("attempt")).intValue());
    }
  }
}
