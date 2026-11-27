package io.metersphere.agent.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import io.metersphere.agent.service.*;
import io.metersphere.agent.tool.data.DataAssetMcpTools;
import io.metersphere.sdk.exception.MSException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataAssetExceptionHandlerTests {
  @Test
  void globalCatalogRejectsUnsupportedTypesBeforeLegacyDispatch() {
    var access = mock(GlobalAssetAccess.class);
    var projects = mock(AgentBusinessAccess.class);
    var legacy = mock(TestAssetCatalogService.class);
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var global = new GlobalAssetCatalogService(access, projects, legacy, jdbc);
    for (String type : List.of("PRIVATE_TOKEN_TYPE", "TASK", "STEP")) {
      var error =
          assertThrows(
              MSException.class,
              () -> global.catalog(null, type, null, null, null, null, null, false, 1, 20));
      assertEquals("请选择有效的资产类型", error.getMessage());
      assertEquals(400, DataAssetErrors.map(error).status());
    }
    verifyNoInteractions(access, projects, legacy, jdbc);
  }

  DataAssetService service;
  TestAssetCatalogService legacyCatalog;
  TestAssetGovernanceService governance;
  MockMvc mvc;
  final com.fasterxml.jackson.databind.ObjectMapper json =
      new com.fasterxml.jackson.databind.ObjectMapper();

  @BeforeEach
  void setup() throws Exception {
    service = mock(DataAssetService.class);
    governance = mock(TestAssetGovernanceService.class);
    var controller =
        new DataAssetController(
            service,
            new DataAssetParser(),
            mock(DataAssetBatchService.class),
            mock(DataAssetLegacyService.class),
            mock(DataAssetParseJobService.class));
    var categories = new TestAssetController();
    legacyCatalog = mock(TestAssetCatalogService.class);
    var catalogField = TestAssetController.class.getDeclaredField("service");
    catalogField.setAccessible(true);
    catalogField.set(categories, legacyCatalog);
    var field = TestAssetController.class.getDeclaredField("governanceService");
    field.setAccessible(true);
    field.set(categories, governance);
    var publication =
        new DataAssetPublicationController(
            service,
            mock(GlobalAssetAccess.class),
            mock(AgentBusinessAccess.class),
            mock(TestAssetVersionService.class));
    mvc =
        MockMvcBuilders.standaloneSetup(
                controller,
                categories,
                publication,
                new GlobalAssetCatalogController(mock(GlobalAssetCatalogService.class)))
            .defaultRequest(get("/").accept(org.springframework.http.MediaType.APPLICATION_JSON))
            .setControllerAdvice(
                new AgentExecutionExceptionHandler(new AgentSafeErrorMapper()),
                new DataAssetExceptionHandler(),
                new io.metersphere.system.controller.handler.ResultResponseBodyAdvice())
            .build();
  }

  private Map<?, ?> body(MvcResult result) throws Exception {
    return json.readValue(
        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
        Map.class);
  }

  @Test
  void legacyPublishedAndContentCodesRetainTheirExistingProtocol() throws Exception {
    when(legacyCatalog.publishAsset("project", "DOCUMENT", "asset"))
        .thenThrow(new MSException("ASSET_VERSION_NOT_FOUND"));
    var published =
        mvc.perform(
                post("/test-assets/catalog/DOCUMENT/asset/publish").param("projectId", "project"))
            .andReturn();
    assertEquals(404, published.getResponse().getStatus());
    assertEquals("ASSET_VERSION_NOT_FOUND", body(published).get("code"));
    when(legacyCatalog.detail("project", "CASE", "asset"))
        .thenThrow(
            new AgentCaseContentReader.ContentException(
                "CASE_CONTENT_FORMAT_INVALID", "/content", null));
    var content =
        mvc.perform(get("/test-assets/catalog/CASE/asset").param("projectId", "project"))
            .andReturn();
    assertEquals(400, content.getResponse().getStatus());
    assertEquals("CASE_CONTENT_FORMAT_INVALID", body(content).get("code"));
    assertEquals(
        "DATA_ASSET_INTERNAL_ERROR",
        DataAssetErrors.map(new MSException("PRIVATE_TOKEN_SECRET")).code());
  }

  @Test
  void nativeTraceMatchesSafeDiagnosticAndUnknownMessagesRemainOpaque() throws Exception {
    String secret = "PRIVATE_SQL_PASSWORD_token";
    when(service.detail("project", "asset"))
        .thenThrow(
            new org.springframework.dao.DataAccessResourceFailureException(
                secret, new java.sql.SQLException(secret, "42000", 1064)));
    var tool =
        new DataAssetMcpTools()
            .dataAssetManage(
                service,
                mock(DataAssetBatchService.class),
                mock(DataAssetParseJobService.class),
                mock(DataAssetLegacyService.class));
    var logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DataAssetErrors.class);
    var capture =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      var failure =
          assertThrows(
              DataAssetFailure.class,
              () ->
                  tool.execute(
                      Map.of("sourceProjectId", "project", "action", "detail", "id", "asset")));
      var dto = new AgentSafeErrorMapper().toApiError(failure, "ignored-supplied-trace");
      assertEquals(failure.traceId(), dto.getTraceId());
      assertEquals("DATA_ASSET_INTERNAL_ERROR", dto.getCode());
      assertFalse(json.writeValueAsString(dto).contains(secret));
      assertNull(failure.getCause());
      assertTrue(
          capture.list.stream()
              .anyMatch(
                  event ->
                      event.getFormattedMessage().contains(dto.getTraceId())
                          && event.getFormattedMessage().contains("42000")
                          && event.getFormattedMessage().contains("1064")));
      assertTrue(
          capture.list.stream()
              .noneMatch(
                  event ->
                      event.getFormattedMessage().contains(secret)
                          || event.getThrowableProxy() != null));
      assertEquals(
          "DATA_ASSET_INTERNAL_ERROR", DataAssetErrors.map(new MSException("FAILED")).code());
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }

  @Test
  void expectedConflictPermissionNotFoundAndStorageRetainCorrectStatusAndSafeMessages()
      throws Exception {
    when(service.save(eq("asset"), anyMap())).thenThrow(new MSException("数据已更新，请刷新后重试"));
    var conflict =
        mvc.perform(
                put("/test-data-assets/asset")
                    .contentType("application/json")
                    .content("{\"expectedRevision\":1}"))
            .andReturn();
    assertEquals(409, conflict.getResponse().getStatus());
    assertEquals("DATA_ASSET_VERSION_CONFLICT", body(conflict).get("code"));
    assertEquals("数据已更新，请刷新后重试", body(conflict).get("message"));
    when(service.detail("asset")).thenThrow(AgentBusinessAccess.denied());
    var denied = mvc.perform(get("/test-data-assets/asset")).andReturn();
    assertEquals(403, denied.getResponse().getStatus());
    assertEquals("AGENT_RESOURCE_NOT_ACCESSIBLE", body(denied).get("code"));
    doThrow(new MSException("数据资产不存在或无访问权限")).when(service).detail("asset");
    assertEquals(
        404, mvc.perform(get("/test-data-assets/asset")).andReturn().getResponse().getStatus());
    doThrow(new MSException("原始文件暂时无法下载")).when(service).detail("asset");
    var storage = mvc.perform(get("/test-data-assets/asset")).andReturn();
    assertEquals(503, storage.getResponse().getStatus());
    assertEquals("DATA_ASSET_STORAGE_UNAVAILABLE", body(storage).get("code"));
  }

  @Test
  void frameworkValidationAndCategoryBusinessErrorsUseScopedAdvice() throws Exception {
    var missing = mvc.perform(delete("/test-data-assets/asset")).andReturn();
    assertEquals(400, missing.getResponse().getStatus());
    assertEquals("DATA_ASSET_VALIDATION_ERROR", body(missing).get("code"));
    var malformed =
        mvc.perform(
                post("/test-data-assets")
                    .contentType("application/json")
                    .content("{broken sensitive-token"))
            .andReturn();
    assertEquals(400, malformed.getResponse().getStatus());
    assertFalse(malformed.getResponse().getContentAsString().contains("sensitive-token"));
    doThrow(new MSException("分类包含资产或子分类，请选择迁移或转为未分类"))
        .when(governance)
        .delete(eq("category"), any());
    var category =
        mvc.perform(
                delete("/test-assets/categories/category")
                    .contentType("application/json")
                    .content("{\"strategy\":\"BLOCK\"}"))
            .andReturn();
    assertEquals(409, category.getResponse().getStatus());
    assertEquals("ASSET_CATEGORY_CONFLICT", body(category).get("code"));
    assertEquals("分类包含资产或子分类，请选择迁移或转为未分类", body(category).get("message"));
  }

  @Test
  void unknownSqlAndUnknownBusinessMessagesNeverLeakAndCarrySafeCorrelatedDiagnostics()
      throws Exception {
    String secret = "password=PRIVATE_SQL_token";
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(DataAssetExceptionHandler.class);
    var capture =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      when(service.detail("asset"))
          .thenThrow(
              new org.springframework.dao.DataAccessResourceFailureException(
                  secret, new java.sql.SQLException(secret, "08001", 1045)));
      var response =
          mvc.perform(get("/test-data-assets/asset").header("X-Trace-Id", secret)).andReturn();
      assertEquals(500, response.getResponse().getStatus());
      assertEquals("DATA_ASSET_INTERNAL_ERROR", body(response).get("code"));
      assertFalse(response.getResponse().getContentAsString().contains(secret));
      String trace = body(response).get("traceId").toString();
      assertDoesNotThrow(() -> UUID.fromString(trace));
      String log = capture.list.getFirst().getFormattedMessage();
      assertTrue(log.contains(trace));
      assertTrue(log.contains("sqlState=08001"));
      assertTrue(log.contains("1045"));
      assertFalse(log.contains(secret));
      assertNull(capture.list.getFirst().getThrowableProxy());
      doThrow(new MSException(secret)).when(service).detail("asset");
      response = mvc.perform(get("/test-data-assets/asset")).andReturn();
      assertEquals(500, response.getResponse().getStatus());
      assertFalse(response.getResponse().getContentAsString().contains(secret));
      assertFalse(
          capture.list.stream()
              .anyMatch(
                  event ->
                      event.getFormattedMessage().contains(secret)
                          || event.getThrowableProxy() != null));
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }

  @Test
  void httpAndNativeShareConflictAndValidationCodes() throws Exception {
    when(service.save(eq("asset"), anyMap())).thenThrow(new MSException("数据已更新，请刷新后重试"));
    when(service.save(eq("project"), eq("asset"), anyMap()))
        .thenThrow(new MSException("数据已更新，请刷新后重试"));
    var web =
        mvc.perform(
                put("/test-data-assets/asset")
                    .contentType("application/json")
                    .content("{\"expectedRevision\":1}"))
            .andReturn();
    var tool =
        new DataAssetMcpTools()
            .dataAssetManage(
                service,
                mock(DataAssetBatchService.class),
                mock(DataAssetParseJobService.class),
                mock(DataAssetLegacyService.class));
    var nativeError =
        assertThrows(
            BusinessGovernanceException.class,
            () ->
                tool.execute(
                    Map.of(
                        "sourceProjectId",
                        "project",
                        "action",
                        "update",
                        "id",
                        "asset",
                        "data",
                        Map.of("expectedRevision", 1))));
    assertEquals(body(web).get("code"), nativeError.code());
    assertEquals(body(web).get("message"), nativeError.getMessage());
    var missing = mvc.perform(delete("/test-data-assets/asset")).andReturn();
    nativeError =
        assertThrows(
            BusinessGovernanceException.class, () -> tool.execute(Map.of("action", "list")));
    assertEquals(body(missing).get("code"), nativeError.code());
  }
}
