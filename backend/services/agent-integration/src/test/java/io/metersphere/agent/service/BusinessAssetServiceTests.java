package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.dto.BusinessAssetSubmission;
import io.metersphere.sdk.exception.MSException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BusinessAssetServiceTests {
  JdbcTemplate jdbc;
  BusinessAssetService service;
  BusinessAssetService target;

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
    jdbc.execute("CREATE TABLE project(id VARCHAR(64) PRIMARY KEY)");
    jdbc.update("INSERT INTO project VALUES('p')");
    Path root = Path.of("").toAbsolutePath();
    while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
    String sql =
        Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_150__business_understanding_assets.sql"));
    for (String statement : sql.substring(0, sql.indexOf("-- Independent")).split(";"))
      if (!statement.isBlank()) jdbc.execute(statement);
    target = spy(new BusinessAssetService(jdbc, mock(AgentBusinessAccess.class)));
    doReturn("actor").when(target).authorize("p", true);
    doReturn("actor").when(target).authorize("p", false);
    service = BusinessAssetTransactionProxy.wrap(target, jdbc);
  }

  BusinessAssetSubmission request(String key, String id, int base, String content) {
    return new BusinessAssetSubmission(
        "p",
        "1.0",
        id == null ? "CREATE" : "UPDATE",
        id,
        base,
        key,
        new BusinessAssetSubmission.Scope("system", "module", List.of("page"), "flow"),
        "订单提交",
        new BusinessAssetSubmission.Document("markdown", content),
        List.of(new BusinessAssetSubmission.Branch("submit", "提交", "OBSERVED", List.of())),
        "完善理解",
        null);
  }

  @Test
  void retryIsIdempotentAndDifferentPayloadIsRejected() {
    var request = request("same", null, 0, "first");
    var first = service.submit(request);
    assertEquals(first, service.submit(request));
    assertThrows(MSException.class, () -> service.submit(request("same", null, 0, "changed")));
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM business_asset_version", Integer.class));
  }

  @Test
  void sameFlowUpdatesOneDocumentAndPreservesConflict() {
    var first = service.submit(request("one", null, 0, "v1"));
    String id = (String) first.get("assetId");
    var second = service.submit(request("two", id, 1, "v2"));
    var conflict = service.submit(request("three", id, 1, "stale"));
    assertEquals("CONFLICT", conflict.get("status"));
    assertEquals(2, conflict.get("currentVersion"));
    assertEquals(3, service.versions("p", id).size());
    assertEquals(second.get("versionId"), service.detail("p", id).get("currentVersionId"));
    assertEquals(1, service.list("p", "", false).size());
  }

  @Test
  void switchingBackRetainsLaterVersionsAndNextNumberIsMonotonic() {
    var first = service.submit(request("one", null, 0, "v1"));
    String id = (String) first.get("assetId");
    service.submit(request("two", id, 1, "v2"));
    service.select("p", id, (String) first.get("versionId"), 2, "回切核对");
    var next = service.submit(request("three", id, 1, "v3"));
    assertEquals(3, next.get("versionNo"));
    assertEquals(3, service.versions("p", id).size());
    assertThrows(
        MSException.class,
        () -> service.select("p", id, (String) first.get("versionId"), 0, "stale"));
  }

  @Test
  void scopeIdentityGroupsCrossPageFlowAndSeparatesSystems() {
    var a = new BusinessAssetSubmission.Scope("s", "module1", List.of("p1"), "flow");
    var b = new BusinessAssetSubmission.Scope("s", "module2", List.of("p2"), "flow");
    assertEquals(BusinessAssetService.identity(a), BusinessAssetService.identity(b));
    assertNotEquals(
        BusinessAssetService.identity(a),
        BusinessAssetService.identity(
            new BusinessAssetSubmission.Scope("other", "module1", List.of("p1"), "flow")));
  }

  @Test
  void objectFromOtherProjectIsNotReadable() {
    var first = service.submit(request("one", null, 0, "v1"));
    doReturn("actor").when(target).authorize("other", false);
    assertThrows(MSException.class, () -> service.detail("other", (String) first.get("assetId")));
  }

  @Test
  void moduleOnlyUnderstandingGroupsOperationsWithoutRequiringFrozenPages() {
    var first = request("one", null, 0, "v1");
    var scope = new BusinessAssetSubmission.Scope("system", "module", List.of(), "");
    var module =
        new BusinessAssetSubmission(
            first.projectId(),
            first.schemaVersion(),
            first.operation(),
            null,
            0,
            first.idempotencyKey(),
            scope,
            first.title(),
            first.document(),
            first.branches(),
            first.changeSummary(),
            null);
    assertEquals("CURRENT", service.submit(module).get("status"));
    assertEquals(
        BusinessAssetService.identity(scope),
        BusinessAssetService.identity(
            new BusinessAssetSubmission.Scope("system", "module", List.of("another-page"), "")));
  }

  @Test
  void mergingPreservesSourceVersionsAndReplayDoesNotRepeatSideEffects() {
    var one = service.submit(request("one", null, 0, "target"));
    String target = (String) one.get("assetId");
    var original = request("two", null, 0, "source");
    var sourceRequest =
        new BusinessAssetSubmission(
            original.projectId(),
            original.schemaVersion(),
            original.operation(),
            null,
            0,
            "source",
            new BusinessAssetSubmission.Scope("system", "other", List.of(), "other-flow"),
            original.title(),
            original.document(),
            original.branches(),
            original.changeSummary(),
            null);
    var source = service.submit(sourceRequest);
    String sourceId = (String) source.get("assetId");
    var merge = request("merge", target, 1, "target and source");
    var result = service.merge(sourceId, 1, merge);
    assertEquals(result, service.merge(sourceId, 1, merge));
    assertEquals(1, service.versions("p", sourceId).size());
    assertEquals(2, service.versions("p", target).size());
    assertEquals(1, service.list("p", "", true).size());
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM business_asset_relation", Integer.class));
  }

  @Test
  void legacyReaderRestrictionsAreEnforcedOnDetailAndList() {
    var one = service.submit(request("one", null, 0, "private legacy"));
    String id = (String) one.get("assetId");
    jdbc.update("INSERT INTO business_asset_reader VALUES(?,?)", id, "other-user");
    assertTrue(service.list("p", "", false).isEmpty());
    assertThrows(MSException.class, () -> service.detail("p", id));
  }

  @Test
  void actualHttpControllerPersistsAndReturnsTheFrontendVersionContract() throws Exception {
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new io.metersphere.agent.controller.BusinessAssetController(service))
            .defaultRequest(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON))
            .setControllerAdvice(
                new io.metersphere.agent.controller.BusinessGovernanceExceptionHandler())
            .build();
    var result =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/business-assets")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(
                        io.metersphere.sdk.util.JSON.toJSONString(
                            request("http-one", null, 0, "first"))))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn();
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var saved = mapper.readTree(result.getResponse().getContentAsString());
    String id = saved.get("assetId").asText();
    String firstVersion = saved.get("versionId").asText();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/business-assets")
                .param("projectId", "p"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$[0].currentVersionId")
                .value(firstVersion))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$[0].pageKeys[0]")
                .value("page"));
    service.submit(request("http-two", id, 1, "changed"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/business-assets/" + id + "/current-version")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    io.metersphere.sdk.util.JSON.toJSONString(
                        Map.of(
                            "projectId",
                            "p",
                            "versionId",
                            firstVersion,
                            "revision",
                            2,
                            "reason",
                            "验证版本回切"))))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/business-assets/" + id)
                .param("projectId", "p"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.currentVersionId")
                .value(firstVersion))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.versions.length()")
                .value(2));
    var invalid =
        mapper.readTree(
            io.metersphere.sdk.util.JSON.toJSONString(request("invalid", null, 0, "content")));
    ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("title", "");
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/business-assets")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(invalid)))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                .value("VALIDATION_ERROR"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM business_asset", Integer.class));
  }

  @Test
  void selectingHistoricalScopeRestoresTheMatchingIdentity() {
    var first = service.submit(request("identity-one", null, 0, "scope A"));
    String id = (String) first.get("assetId");
    var original = request("identity-two", id, 1, "scope B");
    var moved =
        new BusinessAssetSubmission(
            original.projectId(),
            original.schemaVersion(),
            original.operation(),
            id,
            1,
            original.idempotencyKey(),
            new BusinessAssetSubmission.Scope("system", "other", List.of(), "flow-b"),
            original.title(),
            original.document(),
            original.branches(),
            original.changeSummary(),
            null);
    service.submit(moved);
    service.select("p", id, (String) first.get("versionId"), 2, "恢复原业务范围");
    var discovered = service.submit(request("identity-three", null, 1, "scope A changed"));
    assertEquals(id, discovered.get("assetId"));
    assertEquals(1, service.list("p", "", false).size());
  }
}
