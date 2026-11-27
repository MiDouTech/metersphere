package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.dto.TestAssetVersionDTO;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class GlobalDataLeaseTests {
  @Test
  void deletedGlobalDatasetCannotAcquireLeaseAfterPreflightEvenWithValidSnapshot() {
    var jdbc = mock(JdbcTemplate.class);
    var preflight = mock(AgentExecutionPreflightService.class);
    var global = mock(GlobalAssetAccess.class);
    var service = new AgentTestDataLeaseService();
    ReflectionTestUtils.setField(service, "jdbc", jdbc);
    ReflectionTestUtils.setField(service, "preflightService", preflight);
    ReflectionTestUtils.setField(service, "globalAssetAccess", global);
    when(jdbc.queryForMap(anyString(), eq("task")))
        .thenReturn(
            Map.of(
                "project_id",
                "p",
                "current_execution_id",
                "run",
                "status",
                "RUNNING",
                "preflight_id",
                "pre"));
    var version = new TestAssetVersionDTO();
    version.setSourceVersion("raw");
    version.setContentSnapshot("{\"value\":1}");
    version.setContentHash(
        org.apache.commons.codec.digest.DigestUtils.sha256Hex(version.getContentSnapshot()));
    when(preflight.assertFrozenExecutableAsset("pre", "DATASET", "asset")).thenReturn(version);
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("raw"), eq("asset"))).thenReturn(1L);
    when(jdbc.queryForList(anyString(), eq(String.class), eq("pre"), eq("task"), eq("p")))
        .thenReturn(java.util.List.of("actor"));
    when(global.authorizeForActor("p", "actor", "TEST_DATA_ASSET:READ"))
        .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    when(jdbc.queryForList(anyString(), eq("asset"), eq("org"))).thenReturn(java.util.List.of());
    var error =
        assertThrows(
            io.metersphere.sdk.exception.MSException.class,
            () -> service.acquire("task", "asset", "key", 1000));
    assertEquals("TEST_DATASET_NOT_AVAILABLE", error.getMessage());
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void verifiesFrozenGlobalSnapshotBeforeLeasingAndNeverReadsLatestFile() {
    var jdbc = mock(JdbcTemplate.class);
    var preflight = mock(AgentExecutionPreflightService.class);
    var files = mock(io.metersphere.project.service.FileMetadataService.class);
    var service = new AgentTestDataLeaseService();
    ReflectionTestUtils.setField(service, "jdbc", jdbc);
    ReflectionTestUtils.setField(service, "preflightService", preflight);
    ReflectionTestUtils.setField(service, "fileMetadataService", files);
    when(jdbc.queryForMap(anyString(), eq("task")))
        .thenReturn(
            Map.of(
                "project_id",
                "p",
                "current_execution_id",
                "run",
                "status",
                "RUNNING",
                "preflight_id",
                "pre"));
    var frozen = new TestAssetVersionDTO();
    frozen.setSourceVersion("old-immutable-version");
    frozen.setContentSnapshot("tampered");
    frozen.setContentHash("wrong");
    when(preflight.assertFrozenExecutableAsset("pre", "DATASET", "asset")).thenReturn(frozen);
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("old-immutable-version"), eq("asset")))
        .thenReturn(1L);
    var error =
        assertThrows(
            io.metersphere.sdk.exception.MSException.class,
            () -> service.acquire("task", "asset", "key", 1000));
    assertEquals("TEST_DATA_SNAPSHOT_INTEGRITY_FAILED", error.getMessage());
    verifyNoInteractions(files);
  }
}
