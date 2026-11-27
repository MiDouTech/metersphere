package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class DataAssetBatchServiceTests {
  @Test
  void recoveryAfterLastItemCommittedMarksSuccessWithoutRepeatingMutation() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var service = mock(DataAssetService.class);
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var batch = new DataAssetBatchService(service, jdbc, access);
    jdbc.update(
        "INSERT INTO"
            + " data_asset_batch_job(id,organization_id,actor_id,status,request_json,result_json,create_time,update_time)"
            + " VALUES('committed','org','user','RUNNING',?,?,0,0)",
        "[{\"id\":\"done\",\"expectedRevision\":1}]",
        "[{\"id\":\"done\",\"status\":\"SUCCESS\"}]");
    assertEquals("SUCCESS", batch.retry("committed").get("status"));
    verifyNoInteractions(service);
  }

  @Test
  void failureBetweenBusinessWriteAndResultPersistenceRollsBackMutation() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var asset = fixture.service.save(null, Map.of("name", "unchanged", "content", List.of()));
    String id = asset.get("id").toString();
    var service = mock(DataAssetService.class);
    when(service.save(isNull(), eq(id), anyMap()))
        .thenAnswer(
            invocation -> {
              jdbc.update("UPDATE data_asset SET revision=revision+1 WHERE id=?", id);
              throw new IllegalStateException("injected crash after business write");
            });
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var batch = new DataAssetBatchService(service, jdbc, access);
    var result = batch.submit(List.of(Map.of("id", id, "expectedRevision", 1)));
    assertEquals(1, ((Number) fixture.service.detail(id).get("revision")).intValue());
    assertEquals("FAILED", ((Map<?, ?>) ((List<?>) result.get("items")).get(0)).get("status"));
    assertEquals("FAILED", batch.get(result.get("id").toString()).get("status"));
  }

  @Test
  void interruptedBatchRetrySkipsSuccessAndIncludesMissingItems() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var service = mock(DataAssetService.class);
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var batch = new DataAssetBatchService(service, jdbc, access);
    String request =
        "[{\"id\":\"done\",\"expectedRevision\":1},{\"id\":\"failed\",\"expectedRevision\":1},{\"id\":\"missing\",\"expectedRevision\":1}]";
    jdbc.update(
        "INSERT INTO"
            + " data_asset_batch_job(id,organization_id,actor_id,status,request_json,result_json,create_time,update_time)"
            + " VALUES('old','org','user','RUNNING',?,?,?,?)",
        request,
        "[{\"id\":\"done\",\"status\":\"SUCCESS\"},{\"id\":\"failed\",\"status\":\"FAILED\"}]",
        System.currentTimeMillis() - 600000,
        System.currentTimeMillis() - 600000);
    assertEquals("INTERRUPTED", batch.get("old").get("status"));
    var retried = batch.retry("old");
    assertEquals(2, ((List<?>) retried.get("items")).size());
    verify(service, never()).save(isNull(), eq("done"), anyMap());
    verify(service).save(isNull(), eq("failed"), anyMap());
    verify(service).save(isNull(), eq("missing"), anyMap());
    assertEquals("RETRIED", batch.get("old").get("status"));
    assertEquals(retried.get("id"), batch.get("old").get("retryJobId"));
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> batch.retry("old"));
  }

  @Test
  void currentRunningBatchCannotBeClaimedForRetry() throws Exception {
    var fixture = new DataAssetServiceTests();
    fixture.setup();
    var jdbc = fixture.jdbc;
    var service = mock(DataAssetService.class);
    var access = mock(GlobalAssetAccess.class);
    when(access.authorize(isNull(), anyString()))
        .thenReturn(new GlobalAssetAccess.Scope("org", "user"));
    var batch = new DataAssetBatchService(service, jdbc, access);
    jdbc.update(
        "INSERT INTO"
            + " data_asset_batch_job(id,organization_id,actor_id,status,request_json,result_json,create_time,update_time)"
            + " VALUES('fresh','org','user','RUNNING','[]','[]',?,?)",
        System.currentTimeMillis(),
        System.currentTimeMillis());
    assertThrows(io.metersphere.sdk.exception.MSException.class, () -> batch.retry("fresh"));
    verifyNoInteractions(service);
  }
}
