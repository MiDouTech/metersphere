package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.metersphere.sdk.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataAssetDiagnosticsTests {
  @Test
  void versionUploadAndOriginalDownloadFailuresCorrelateOnlySafeIdentifiers() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var created = fixture.service.save(null, Map.of("name", "file", "content", List.of()));
    String asset = created.get("id").toString(),
        version = created.get("currentVersionId").toString();
    var repository = mock(FileRepository.class);
    doThrow(new io.metersphere.sdk.exception.MSException(new java.io.IOException(secret, new java.sql.SQLException(secret, "08001", 1045))))
        .when(repository)
        .saveFile(any(byte[].class), any(FileRequest.class));
    when(repository.getFile(any()))
        .thenThrow(
            new java.io.IOException(secret, new java.sql.SQLException(secret, "08001", 1045)));
    try (var capture = new Capture(DataAssetService.class);
        var center = mockStatic(FileCenter.class)) {
      center.when(() -> FileCenter.getRepository("MINIO")).thenReturn(repository);
      var error =
          assertThrows(
              io.metersphere.sdk.exception.MSException.class,
              () ->
                  fixture.service.uploadVersion(
                      null,
                      asset,
                      new byte[] {1},
                      "file.json",
                      Map.of("expectedRevision", 1, "content", List.of())));
      assertEquals("文件存储失败，请稍后重试", error.getMessage());
      assertNull(error.getCause());
      fixture.jdbc.update(
          "INSERT INTO data_asset_file VALUES(?,?,?)", version, secret, "file.json");
      error =
          assertThrows(
              io.metersphere.sdk.exception.MSException.class,
              () -> fixture.service.file(asset, version));
      assertEquals("原始文件暂时无法下载", error.getMessage());
      assertTrue(capture.text().contains("DATA_ASSET_VERSION_UPLOAD_FAILURE assetId=" + asset));
      assertTrue(
          capture
              .text()
              .contains(
                  "DATA_ASSET_SOURCE_DOWNLOAD_FAILURE assetId="
                      + asset
                      + " versionId="
                      + version
                      + " stage=source-download"));
      assertTrue(capture.text().contains("sqlState=08001 sqlCode=1045"));
      assertFalse(capture.text().contains(secret));
      assertTrue(capture.events.list.stream().allMatch(event -> event.getThrowableProxy() == null));
    }
  }

  private final String secret = "sensitive-body-password-token-PRIVATE";

  private static final class Capture implements AutoCloseable {
    final Logger logger;
    final ListAppender<ILoggingEvent> events = new ListAppender<>();

    Capture(Class<?> type) {
      logger = (Logger) org.slf4j.LoggerFactory.getLogger(type);
      events.start();
      logger.addAppender(events);
    }

    String text() {
      return events.list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .collect(java.util.stream.Collectors.joining("\n"));
    }

    public void close() {
      logger.detachAppender(events);
      events.stop();
    }
  }

  @Test
  void sourceUploadFailureRetainsSafeSqlDiagnosticsWithoutCauseOrPayload() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    var service = new DataAssetParseJobService(fixture.jdbc, access, new DataAssetParser());
    var repository = mock(FileRepository.class);
    doThrow(new java.io.IOException(secret, new java.sql.SQLException(secret, "42000", 1064)))
        .when(repository)
        .saveFile(any(byte[].class), any(FileRequest.class));
    try (var capture = new Capture(DataAssetParseJobService.class);
        var center = mockStatic(FileCenter.class)) {
      center.when(() -> FileCenter.getRepository("MINIO")).thenReturn(repository);
      var error =
          assertThrows(
              io.metersphere.sdk.exception.MSException.class,
              () ->
                  service.upload(
                      secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                      "data.json",
                      "data",
                      null,
                      "request"));
      assertEquals("原文件存储失败，请稍后重试", error.getMessage());
      assertNull(error.getCause());
      String logs = capture.text();
      assertTrue(logs.contains("event=DATA_ASSET_SOURCE_UPLOAD_FAILURE"));
      assertTrue(logs.contains("stage=source-upload"));
      assertTrue(logs.contains("rootType=SQLException"));
      assertTrue(logs.contains("sqlState=42000 sqlCode=1064"));
      assertFalse(logs.contains(secret));
      assertTrue(
          capture.events.list.stream()
              .allMatch(event -> event.getThrowableProxy() == null && event.getTimeStamp() > 0));
    }
  }

  @Test
  void corruptedBatchResultAndRetryRequestsHaveSafeStageDiagnostics() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    var service = new DataAssetBatchService(fixture.service, jdbc, access);
    jdbc.update(
        "INSERT INTO"
            + " data_asset_batch_job(id,organization_id,actor_id,status,request_json,result_json,create_time,update_time)"
            + " VALUES('job','org','actor','FAILED',?,?,0,0)",
        secret,
        secret);
    try (var capture = new Capture(DataAssetBatchService.class)) {
      var error =
          assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.get("job"));
      assertEquals("批次结果暂时无法读取", error.getMessage());
      assertNull(error.getCause());
      assertTrue(
          capture
              .text()
              .contains("event=DATA_ASSET_BATCH_RESULT_FAILURE jobId=job stage=batch-result-read"));
      jdbc.update("UPDATE data_asset_batch_job SET result_json='[]' WHERE id='job'");
      error =
          assertThrows(io.metersphere.sdk.exception.MSException.class, () -> service.retry("job"));
      assertEquals("批次暂时无法重试", error.getMessage());
      assertTrue(
          capture
              .text()
              .contains("event=DATA_ASSET_BATCH_RETRY_FAILURE jobId=job stage=batch-retry"));
      assertFalse(capture.text().contains(secret));
      assertTrue(capture.events.list.stream().allMatch(event -> event.getThrowableProxy() == null));
    }
  }

  @Test
  void unvalidatedSqlStateAndPrivateJobIdsAreNeverLogged() {
    try (var capture = new Capture(DataAssetDiagnosticsTests.class)) {
      DataAssetDiagnostics.failure(
          DataAssetDiagnosticsTests.class,
          "DATA_ASSET_TEST_FAILURE",
          "https://private/" + secret,
          "test",
          new java.sql.SQLException(secret, secret, 123));
      assertTrue(capture.text().contains("jobId=NONE"));
      assertTrue(capture.text().contains("sqlState=NONE sqlCode=123"));
      assertFalse(capture.text().contains(secret));
      assertNull(capture.events.list.getFirst().getThrowableProxy());
    }
  }
}
