package io.metersphere.agent.tool.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataAssetMcpToolsTests {
  @Test
  void outerRevisionAndIdempotencyAreForwardedWithoutMutatingData() {
    var service = mock(DataAssetService.class);
    var tool =
        new DataAssetMcpTools()
            .dataAssetManage(
                service,
                mock(DataAssetBatchService.class),
                mock(DataAssetParseJobService.class),
                mock(DataAssetLegacyService.class));
    var data = Map.<String, Object>of("name", "updated", "content", List.of(1));
    tool.execute(
        Map.of(
            "sourceProjectId",
            "project",
            "action",
            "update",
            "id",
            "asset",
            "expectedRevision",
            7,
            "requestId",
            "update-request",
            "data",
            data));
    org.mockito.ArgumentCaptor<Map<String, Object>> update =
        org.mockito.ArgumentCaptor.forClass((Class) Map.class);
    verify(service).save(eq("project"), eq("asset"), update.capture());
    assertEquals(7, update.getValue().get("expectedRevision"));
    assertEquals("update-request", update.getValue().get("requestId"));
    assertFalse(data.containsKey("expectedRevision"));
    assertNotSame(data, update.getValue());
    tool.execute(
        Map.of(
            "sourceProjectId",
            "project",
            "action",
            "create",
            "requestId",
            "create-request",
            "data",
            Map.of("name", "created", "content", List.of())));
    org.mockito.ArgumentCaptor<Map<String, Object>> create =
        org.mockito.ArgumentCaptor.forClass((Class) Map.class);
    verify(service).save(eq("project"), isNull(), create.capture());
    assertEquals("create-request", create.getValue().get("requestId"));
  }

  @Test
  void equalNestedValuesAndLegacyNestedOnlyValuesRemainCompatible() {
    var service = mock(DataAssetService.class);
    var tool =
        new DataAssetMcpTools()
            .dataAssetManage(
                service,
                mock(DataAssetBatchService.class),
                mock(DataAssetParseJobService.class),
                mock(DataAssetLegacyService.class));
    tool.execute(
        Map.of(
            "sourceProjectId",
            "project",
            "action",
            "update",
            "id",
            "equal",
            "expectedRevision",
            7,
            "requestId",
            "same",
            "data",
            Map.of("expectedRevision", 7L, "requestId", "same")));
    verify(service)
        .save(
            eq("project"),
            eq("equal"),
            argThat(
                data ->
                    data.get("expectedRevision").equals(7)
                        && data.get("requestId").equals("same")));
    tool.execute(
        Map.of(
            "sourceProjectId",
            "project",
            "action",
            "update",
            "id",
            "nested",
            "data",
            Map.of("expectedRevision", 9, "requestId", "nested-request")));
    verify(service)
        .save(
            eq("project"),
            eq("nested"),
            argThat(
                data ->
                    data.get("expectedRevision").equals(9)
                        && data.get("requestId").equals("nested-request")));
  }

  @Test
  void conflictingOuterAndNestedContractFieldsFailBeforeServiceAndNeverEchoValues() {
    var service = mock(DataAssetService.class);
    var tool =
        new DataAssetMcpTools()
            .dataAssetManage(
                service,
                mock(DataAssetBatchService.class),
                mock(DataAssetParseJobService.class),
                mock(DataAssetLegacyService.class));
    var revision =
        assertThrows(
            DataAssetFailure.class,
            () ->
                tool.execute(
                    Map.of(
                        "sourceProjectId",
                        "project",
                        "action",
                        "update",
                        "id",
                        "asset",
                        "expectedRevision",
                        7,
                        "data",
                        Map.of("expectedRevision", 8))));
    assertEquals("DATA_ASSET_VALIDATION_ERROR", revision.code());
    assertEquals(400, revision.httpStatus());
    var request =
        assertThrows(
            DataAssetFailure.class,
            () ->
                tool.execute(
                    Map.of(
                        "sourceProjectId",
                        "project",
                        "action",
                        "create",
                        "requestId",
                        "PRIVATE_OUTER_VALUE",
                        "data",
                        Map.of("requestId", "PRIVATE_INNER_VALUE"))));
    assertEquals("DATA_ASSET_VALIDATION_ERROR", request.code());
    assertEquals("外层与数据内的版本号或请求标识不一致", request.getMessage());
    assertFalse(request.getMessage().contains("PRIVATE"));
    verifyNoInteractions(service);
  }

  @Test
  void schemaExposesParityWithoutTokenScopesAndPassesExplicitOrganizationContext() {
    var service = mock(DataAssetService.class);
    var jobs = mock(DataAssetParseJobService.class);
    var batches = mock(DataAssetBatchService.class);
    var legacy = mock(DataAssetLegacyService.class);
    var tool = new DataAssetMcpTools().dataAssetManage(service, batches, jobs, legacy);
    assertEquals("USER_RBAC", tool.requiredScope());
    String schema = tool.inputSchema().toString();
    for (String action :
        List.of(
            "upload", "download", "job", "retry_job", "legacy_import", "legacy_inventory", "batch"))
      assertTrue(schema.contains(action));
    when(service.detail("project", "asset")).thenReturn(Map.of("id", "asset"));
    assertEquals(
        Map.of("id", "asset"),
        tool.execute(Map.of("sourceProjectId", "project", "action", "detail", "id", "asset")));
    verify(service).detail("project", "asset");
    assertThrows(
        io.metersphere.sdk.exception.MSException.class,
        () -> tool.execute(Map.of("action", "list")));
  }

  @Test
  void uploadUsesBoundedBase64AndPersistedParseService() {
    var service = mock(DataAssetService.class);
    var jobs = mock(DataAssetParseJobService.class);
    var batches = mock(DataAssetBatchService.class);
    var legacy = mock(DataAssetLegacyService.class);
    var tool = new DataAssetMcpTools().dataAssetManage(service, batches, jobs, legacy);
    when(jobs.upload(eq("project"), any(), eq("data.json"), isNull(), isNull(), eq("request")))
        .thenReturn(Map.of("id", "asset", "jobId", "job"));
    assertEquals(
        Map.of("id", "asset", "jobId", "job"),
        tool.execute(
            Map.of(
                "sourceProjectId",
                "project",
                "action",
                "upload",
                "fileName",
                "data.json",
                "base64",
                "e30=",
                "requestId",
                "request")));
    verify(jobs)
        .upload(
            eq("project"),
            argThat(bytes -> Arrays.equals(bytes, new byte[] {'{', '}'})),
            eq("data.json"),
            isNull(),
            isNull(),
            eq("request"));
  }
}
