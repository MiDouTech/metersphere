package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.security.SharedConfigurationCipher;
import io.metersphere.sdk.exception.MSException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SharedConfigurationServiceTests {
  JdbcTemplate jdbc;
  SharedConfigurationService service;
  SharedConfigurationCipher cipher;

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
    var sql =
        Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_152__shared_configurations.sql"));
    for (var statement : sql.substring(0, sql.indexOf("INSERT IGNORE")).split(";"))
      if (!statement.isBlank()) jdbc.execute(statement);
    jdbc.execute(
        Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_157__shared_configuration_secret_type.sql")));
    jdbc.execute(
        Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_158__shared_configuration_audit_paging.sql")));
    jdbc.execute("CREATE TABLE organization(id VARCHAR(64) PRIMARY KEY)");
    jdbc.update("INSERT INTO organization VALUES('org')");
    jdbc.execute("ALTER TABLE shared_configuration ADD COLUMN organization_id VARCHAR(64)");
    var global = mock(GlobalAssetAccess.class);
    when(global.authorize(any(), anyString()))
        .thenAnswer(
            invocation ->
                new GlobalAssetAccess.Scope(
                    "other".equals(invocation.getArgument(0)) ? "other-org" : "org", "actor"));
    var auth = mock(BusinessAssetService.class);
    when(auth.globalAccess()).thenReturn(global);
    when(auth.authorize(eq("p"), anyBoolean(), anyString(), anyString())).thenReturn("actor");
    cipher = new SharedConfigurationCipher(Base64.getEncoder().encodeToString(new byte[32]));
    service =
        BusinessAssetTransactionProxy.wrap(
            new SharedConfigurationService(jdbc, auth, cipher), jdbc);
  }

  @Test
  void newGlobalConfigurationNeedsNoProjectAndCanBeReadFromAnyProjectInOrganization() {
    var created =
        service.save(
            null, null, "system", "test", "endpoint", "PLAIN", "https://example.invalid", "", 0);
    String id = (String) created.get("id");
    assertEquals(
        "",
        jdbc.queryForObject(
            "SELECT project_id FROM shared_configuration WHERE id=?", String.class, id));
    assertEquals(
        "org",
        jdbc.queryForObject(
            "SELECT organization_id FROM shared_configuration WHERE id=?", String.class, id));
    assertEquals("https://example.invalid", service.read("p2", id, 1, false).get("value"));
  }

  @Test
  void organizationSharedEditsPreserveOriginalEncryptionIdentityAndRejectOtherOrganization() {
    var created =
        service.save(
            "p", null, "system", "test", "baseUrl", "PLAIN", "https://example.invalid", "", 0);
    String id = (String) created.get("id");
    assertEquals(1, service.list("p2").size());
    assertEquals("https://example.invalid", service.read("p2", id, 1, false).get("value"));
    service.save("p2", id, "system", "test", "baseUrl", "PLAIN", "https://changed.invalid", "", 1);
    assertEquals("https://changed.invalid", service.read("p", id, 2, false).get("value"));
    assertEquals(
        "p",
        jdbc.queryForObject(
            "SELECT project_id FROM shared_configuration WHERE id=?", String.class, id));
    assertTrue(service.list("other").isEmpty());
    assertThrows(RuntimeException.class, () -> service.read("other", id, 1, false));
    assertThrows(
        RuntimeException.class,
        () -> service.save("p2", null, "system", "test", "baseUrl", "PLAIN", "duplicate", "", 0));
  }

  @Test
  void secretsNeverAppearInMetadataOrVersionHistoryAndCannotBeReadAsPlain() {
    var result =
        service.save(
            "p", null, "system", "test", "password", "SECRET", "unique-private-value", "", 0);
    String id = (String) result.get("id");
    assertFalse(service.list("p").toString().contains("unique-private-value"));
    assertFalse(service.versions("p", id).toString().contains("unique-private-value"));
    assertEquals("******", service.read("p", id, 1, false).get("value"));
    assertEquals("******", service.read("p", id, 1, true).get("value"));
    var stored =
        jdbc.queryForObject(
            "SELECT encrypted_value FROM shared_configuration_version WHERE configuration_id=?",
            String.class,
            id);
    assertFalse(stored.contains("unique-private-value"));
    service.saveStructured(
        "p", id, "system", "test", "password", "SECRET", null, "", 1, null, true);
    assertEquals("", service.list("p").getFirst().get("secretType"));
    assertEquals(
        "unique-private-value",
        new String(
            cipher.decrypt(
                jdbc.queryForObject(
                    "SELECT encrypted_value FROM shared_configuration_version WHERE"
                        + " configuration_id=? AND version_no=2",
                    String.class,
                    id),
                "p:" + id + ":2"),
            StandardCharsets.UTF_8));
  }

  @Test
  void configurationVersionsRemainAfterRollbackAndStaleUpdateFails() {
    var one =
        service.save(
            "p", null, "system", "test", "baseUrl", "PLAIN", "https://example.invalid", "", 0);
    String id = (String) one.get("id");
    service.save("p", id, "system", "test", "baseUrl", "PLAIN", "https://other.invalid", "", 1);
    service.select("p", id, 1, 2);
    var third =
        service.save("p", id, "system", "test", "baseUrl", "PLAIN", "https://third.invalid", "", 1);
    assertEquals(3, third.get("version"));
    assertEquals(3, service.versions("p", id).size());
    assertThrows(
        MSException.class,
        () -> service.save("p", id, "system", "test", "baseUrl", "PLAIN", "stale", "", 1));
  }

  @Test
  void originalDocumentIsPreservedAndParseFailureIsVisibleAndRetryable() {
    var bytes = "{not valid json".getBytes(StandardCharsets.UTF_8);
    var value = Base64.getEncoder().encodeToString(bytes);
    var saved =
        service.save(
            "p", null, "system", "test", "instructions", "DOCUMENT", value, "instructions.json", 0);
    String id = (String) saved.get("id");
    assertEquals("FAILED", saved.get("parseStatus"));
    assertEquals(value, service.read("p", id, 1, true).get("value"));
    assertEquals("FAILED", service.retryParse("p", id, 1));
  }

  @Test
  void cipherRejectsWrongScopeTamperingAndMissingKeyWithoutLeakingInput() {
    String encrypted = cipher.encrypt("private".getBytes(StandardCharsets.UTF_8), "p:config:1");
    assertThrows(MSException.class, () -> cipher.decrypt(encrypted, "other:config:1"));
    assertThrows(MSException.class, () -> cipher.decrypt("v1:broken:private", "p:config:1"));
    var unavailable = new SharedConfigurationCipher("");
    assertThrows(
        MSException.class,
        () -> unavailable.encrypt("private".getBytes(StandardCharsets.UTF_8), "scope"));
    assertFalse(
        assertThrows(MSException.class, () -> cipher.decrypt("v1:broken:private", "scope"))
            .getMessage()
            .contains("private"));
  }

  @Test
  void uppercaseJsonExtensionStillValidatesDocumentContent() {
    String invalid =
        Base64.getEncoder().encodeToString("{invalid".getBytes(StandardCharsets.UTF_8));
    var failed =
        service.save("p", null, "system", "test", "bad", "DOCUMENT", invalid, "settings.JSON", 0);
    assertEquals("FAILED", failed.get("parseStatus"));
    assertEquals("FAILED", service.retryParse("p", (String) failed.get("id"), 1));
    String valid =
        Base64.getEncoder().encodeToString("{\"timeout\":30}".getBytes(StandardCharsets.UTF_8));
    var parsed =
        service.save("p", null, "system", "test", "good", "DOCUMENT", valid, "settings.JSON", 0);
    assertEquals("PARSED", parsed.get("parseStatus"));
  }

  @Test
  void executorResolvesPinnedSecretAndCannotReadAfterCompletion() {
    var saved =
        service.save("p", null, "system", "test", "password", "SECRET", "first-value", "", 0);
    String id = (String) saved.get("id");
    jdbc.execute(
        "CREATE TABLE external_executor_session(id VARCHAR(64),project_id VARCHAR(64),token_id"
            + " VARCHAR(64),actor_id VARCHAR(64),status VARCHAR(24))");
    jdbc.update(
        "INSERT INTO external_executor_session VALUES('session','p','token','actor','RUNNING')");
    jdbc.update("INSERT INTO external_executor_configuration VALUES('session',?,1)", id);
    service.save("p", id, "system", "test", "password", "SECRET", "changed-value", "", 1);
    var token = new io.metersphere.system.domain.AgentToken();
    token.setId("token");
    token.setUserId("actor");
    io.metersphere.agent.security.AgentTokenContext.set(token);
    try {
      assertEquals("first-value", service.resolveForExecutor("p", "session", id).get("value"));
      jdbc.update("UPDATE external_executor_session SET status='PASSED'");
      assertThrows(MSException.class, () -> service.resolveForExecutor("p", "session", id));
    } finally {
      io.metersphere.agent.security.AgentTokenContext.clear();
    }
  }

  @Test
  void structuredSecretsValidateEncryptAndKeepWithoutReturningValues() {
    String payload =
        "{\"fields\":[{\"name\":\"username\",\"value\":\"demo-user\"},{\"name\":\"password\",\"value\":\"private-password\"},{\"name\":\"tenant\",\"value\":\"private-tenant\"}]}";
    var saved =
        service.saveStructured(
            "p",
            null,
            "system",
            "test",
            "login",
            "SECRET",
            payload,
            "",
            0,
            "ACCOUNT_PASSWORD",
            false);
    String id = (String) saved.get("id");
    assertEquals("ACCOUNT_PASSWORD", service.list("p").getFirst().get("secretType"));
    assertFalse(service.list("p").toString().contains("private"));
    assertFalse(service.versions("p", id).toString().contains("private"));
    assertEquals("******", service.read("p", id, 1, true).get("value"));
    var kept =
        service.saveStructured(
            "p", id, "system", "test", "renamed", "SECRET", "", "", 1, null, true);
    assertEquals(2, kept.get("version"));
    String encrypted =
        jdbc.queryForObject(
            "SELECT encrypted_value FROM shared_configuration_version WHERE configuration_id=? AND"
                + " version_no=2",
            String.class,
            id);
    assertFalse(encrypted.contains("private"));
    assertEquals(
        payload, new String(cipher.decrypt(encrypted, "p:" + id + ":2"), StandardCharsets.UTF_8));
    assertThrows(
        MSException.class,
        () ->
            service.saveStructured(
                "p", id, "system", "test", "stale", "SECRET", "", "", 1, null, true));
    assertThrows(
        MSException.class,
        () ->
            service.saveStructured(
                "other", id, "system", "test", "foreign", "SECRET", "", "", 2, null, true));
  }

  @Test
  void structuredSecretsRejectMissingDuplicateBlankAndExcessiveFields() {
    for (String payload :
        List.of(
            "{}",
            "{\"fields\": []}",
            "{\"fields\": [{\"name\": \"x\", \"value\": \"a\"}, {\"name\": \" x \", \"value\":"
                + " \"b\"}]}",
            "{\"fields\": [{\"name\": \"x\", \"value\": \" \"}]}",
            "{\"fields\": [{\"name\": \"0\", \"value\": \"v\"}, {\"name\": \"1\", \"value\":"
                + " \"v\"}, {\"name\": \"2\", \"value\": \"v\"}, {\"name\": \"3\", \"value\":"
                + " \"v\"}, {\"name\": \"4\", \"value\": \"v\"}, {\"name\": \"5\", \"value\":"
                + " \"v\"}, {\"name\": \"6\", \"value\": \"v\"}, {\"name\": \"7\", \"value\":"
                + " \"v\"}, {\"name\": \"8\", \"value\": \"v\"}, {\"name\": \"9\", \"value\":"
                + " \"v\"}, {\"name\": \"10\", \"value\": \"v\"}, {\"name\": \"11\", \"value\":"
                + " \"v\"}, {\"name\": \"12\", \"value\": \"v\"}, {\"name\": \"13\", \"value\":"
                + " \"v\"}, {\"name\": \"14\", \"value\": \"v\"}, {\"name\": \"15\", \"value\":"
                + " \"v\"}, {\"name\": \"16\", \"value\": \"v\"}, {\"name\": \"17\", \"value\":"
                + " \"v\"}, {\"name\": \"18\", \"value\": \"v\"}, {\"name\": \"19\", \"value\":"
                + " \"v\"}, {\"name\": \"20\", \"value\": \"v\"}]}")) {
      assertThrows(
          MSException.class,
          () ->
              service.saveStructured(
                  "p", null, "s", "e", "bad", "SECRET", payload, "", 0, "OTHER", false));
    }
    assertThrows(
        MSException.class,
        () ->
            service.saveStructured(
                "p", null, "s", "e", "bad", "PLAIN", "v", "", 0, "OTHER", false));
    assertThrows(
        MSException.class,
        () -> service.saveStructured("p", null, "s", "e", "bad", "SECRET", "v", "", 0, null, true));
    assertEquals(0, service.list("p").size());
  }

  @Test
  void structuredExecutorReceivesPinnedFieldsAndType() {
    String payload = "{\"fields\":[{\"name\":\"Token\",\"value\":\"fixture-private\"}]}";
    var saved =
        service.saveStructured(
            "p", null, "system", "test", "service", "SECRET", payload, "", 0, "OTHER", false);
    String id = (String) saved.get("id");
    jdbc.execute(
        "CREATE TABLE external_executor_session(id VARCHAR(64),project_id VARCHAR(64),token_id"
            + " VARCHAR(64),actor_id VARCHAR(64),status VARCHAR(24))");
    jdbc.update(
        "INSERT INTO external_executor_session VALUES('session','p','token','actor','RUNNING')");
    jdbc.update("INSERT INTO external_executor_configuration VALUES('session',?,1)", id);
    service.save("p", id, "system", "test", "service", "SECRET", "new-legacy-value", "", 1);
    var token = new io.metersphere.system.domain.AgentToken();
    token.setId("token");
    token.setUserId("actor");
    io.metersphere.agent.security.AgentTokenContext.set(token);
    try {
      var resolved = service.resolveForExecutor("p", "session", id);
      assertEquals("OTHER", resolved.get("secretType"));
      assertEquals(payload, resolved.get("value"));
      jdbc.update("UPDATE external_executor_session SET status='PASSED'");
      assertThrows(MSException.class, () -> service.resolveForExecutor("p", "session", id));
    } finally {
      io.metersphere.agent.security.AgentTokenContext.clear();
    }
  }

  @Test
  void httpSaveAcceptsStructuredAndKeepSecretRequests() throws Exception {
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new io.metersphere.agent.controller.SharedConfigurationController(service))
            .build();
    String payload =
        "{\"fields\":[{\"name\":\"username\",\"value\":\"u\"},{\"name\":\"password\",\"value\":\"private\"}]}";
    String body =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "projectId",
                    "p",
                    "systemKey",
                    "system",
                    "environmentKey",
                    "test",
                    "name",
                    "http-login",
                    "kind",
                    "SECRET",
                    "secretType",
                    "ACCOUNT_PASSWORD",
                    "value",
                    payload,
                    "expectedVersion",
                    0));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/shared-configurations")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    String id = (String) service.list("p").getFirst().get("id");
    String keep =
        """
{"projectId":"p","id":"%s","systemKey":"system","environmentKey":"test","name":"http-login",
"kind":"SECRET","keepSecret":true,"expectedVersion":1}
"""
            .formatted(id);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/shared-configurations")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(keep))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    assertEquals(2, service.versions("p", id).size());
  }

  @Test
  void invalidStructuredSecretDiagnosticsDoNotLogPayload() {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(SharedConfigurationService.class);
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThrows(
          MSException.class,
          () ->
              service.saveStructured(
                  "p",
                  null,
                  "s",
                  "e",
                  "bad",
                  "SECRET",
                  "{fixture-private-parser-content",
                  "",
                  0,
                  "OTHER",
                  false));
      String logs =
          appender.list.stream()
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
              .reduce("", String::concat);
      assertTrue(logs.contains("CONFIGURATION_FIELDS_INVALID"));
      assertTrue(logs.contains("rootType="));
      assertFalse(logs.contains("fixture-private-parser-content"));
      assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void versionSwitchReasonIsAuditedAndInvalidReasonRollsBack() {
    String id =
        (String)
            service
                .save("p", null, "s", "e", "url", "PLAIN", "https://example.invalid", "", 0)
                .get("id");
    service.save("p", id, "s", "e", "url", "PLAIN", "https://other.invalid", "", 1);
    service.select("p", id, 1, 2, "验证回退");
    assertEquals(
        "验证回退",
        jdbc.queryForObject(
            "SELECT reason FROM shared_configuration_audit WHERE configuration_id=? AND"
                + " event_type='SELECT_VERSION'",
            String.class,
            id));
    assertThrows(MSException.class, () -> service.select("p", id, 2, 1, " "));
    assertEquals(1, service.list("p").getFirst().get("currentVersion"));
  }

  @Test
  void accountPasswordRequiresBothFieldsAndDuplicateRenameIsSafe() {
    assertThrows(
        MSException.class,
        () ->
            service.saveStructured(
                "p",
                null,
                "s",
                "e",
                "bad",
                "SECRET",
                "{\"fields\":[{\"name\":\"username\",\"value\":\"u\"}]}",
                "",
                0,
                "ACCOUNT_PASSWORD",
                false));
    String one =
        (String) service.save("p", null, "s", "e", "one", "PLAIN", "first", "", 0).get("id");
    service.save("p", null, "s", "e", "two", "PLAIN", "second", "", 0);
    var conflict =
        assertThrows(
            BusinessGovernanceException.class,
            () -> service.save("p", one, "s", "e", "two", "PLAIN", "changed", "", 1));
    assertEquals("GOVERNANCE_VALIDATION_ERROR", conflict.code());
    assertEquals(1, service.versions("p", one).size());
  }

  @Test
  void structuredSecretsRejectTrailingTokensAndDuplicateMembers() {
    String valid = "{\"fields\":[{\"name\":\"Token\",\"value\":\"fixture\"}]}";
    for (String payload :
        List.of(
            valid + " {}",
            valid + " garbage",
            "{\"fields\":[{\"name\":\"Token\",\"name\":\"other\",\"value\":\"fixture\"}]}")) {
      assertThrows(
          MSException.class,
          () ->
              service.saveStructured(
                  "p", null, "s", "e", "bad", "SECRET", payload, "", 0, "OTHER", false));
    }
  }

  @Test
  void selectingCurrentVersionDoesNotRecordAChange() {
    String id =
        (String)
            service
                .save("p", null, "s", "e", "url", "PLAIN", "https://example.invalid", "", 0)
                .get("id");
    service.select("p", id, 1, 1, "无需改变");
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM shared_configuration_audit WHERE event_type='SELECT_VERSION'",
            Integer.class));
  }

  @Test
  void failedBatchRollsBackEarlierEntries() {
    var one =
        new io.metersphere.agent.dto.SharedConfigurationSave(
            "p", null, "system", "test", "one", "PLAIN", "value", "", 0);
    var invalid =
        new io.metersphere.agent.dto.SharedConfigurationSave(
            "p", null, "system", "test", "two", "INVALID", "value", "", 0);
    assertThrows(MSException.class, () -> service.batch(List.of(one, invalid)));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM shared_configuration", Integer.class));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM shared_configuration_version", Integer.class));
  }

  @Test
  void auditQueryReturnsOnlyMetadataWithStablePaginationAndEventVersionFilters() {
    String id =
        (String)
            service
                .save("p", null, "s", "test", "audit", "SECRET", "private-audit-fixture", "", 0)
                .get("id");
    service.save("p", id, "s", "test", "audit", "SECRET", "changed-private-fixture", "", 1);
    service.select("p", id, 1, 2, "synthetic rollback reason");
    var page = service.audits("p", id, 1, 2, null, null);
    assertEquals(3L, ((Number) page.get("total")).longValue());
    assertEquals(2, ((List<?>) page.get("items")).size());
    var second = service.audits("p", id, 2, 2, null, null);
    assertEquals(1, ((List<?>) second.get("items")).size());
    var firstIds =
        ((List<?>) page.get("items")).stream().map(r -> ((Map<?, ?>) r).get("id")).toList();
    var secondIds =
        ((List<?>) second.get("items")).stream().map(r -> ((Map<?, ?>) r).get("id")).toList();
    assertTrue(Collections.disjoint(firstIds, secondIds));
    assertEquals(page.get("items"), service.audits("p", id, 1, 2, null, null).get("items"));
    assertFalse(page.toString().contains("private-audit-fixture"));
    assertFalse(page.toString().contains("encrypted_value"));
    var selected = service.audits("p", id, 1, 100, "SELECT_VERSION", 1);
    assertEquals(1L, ((Number) selected.get("total")).longValue());
    var row = (Map<?, ?>) ((List<?>) selected.get("items")).getFirst();
    assertEquals("synthetic rollback reason", row.get("reason"));
    assertEquals("actor", row.get("actorId"));
    assertEquals("SELECT_VERSION", row.get("eventType"));
    assertEquals(id, row.get("configurationId"));
    assertEquals(
        Set.of(
            "id", "configurationId", "versionNo", "actorId", "eventType", "createTime", "reason"),
        row.keySet());
    service.select("p", id, 1, 1, "same version must not create audit");
    assertEquals(
        1L,
        ((Number) service.audits("p", id, 1, 100, "SELECT_VERSION", 1).get("total")).longValue());
    assertTrue(((List<?>) service.audits("p", id, 10, 100, null, null).get("items")).isEmpty());
  }

  @Test
  void auditQueryRejectsInvalidFiltersAndOtherOrganizationsButSharesWithinOrganization() {
    String id =
        (String) service.save("p", null, "s", "test", "audit", "PLAIN", "fixture", "", 0).get("id");
    assertThrows(MSException.class, () -> service.audits("p", id, 0, 20, null, null));
    assertThrows(MSException.class, () -> service.audits("p", id, 1, 101, null, null));
    assertThrows(MSException.class, () -> service.audits("p", id, 1000001, 20, null, null));
    assertThrows(MSException.class, () -> service.audits("p", id, 1, 20, "UNKNOWN", null));
    assertThrows(MSException.class, () -> service.audits("p", id, 1, 20, null, 0));
    assertThrows(MSException.class, () -> service.audits("p", "missing", 1, 20, null, null));
    assertEquals(
        service.audits("p", id, 1, 20, null, null), service.audits("p2", id, 1, 20, null, null));
    assertThrows(MSException.class, () -> service.audits("other", id, 1, 20, null, null));
    var denied = mock(BusinessAssetService.class);
    var deniedGlobal = mock(GlobalAssetAccess.class);
    when(denied.globalAccess()).thenReturn(deniedGlobal);
    when(deniedGlobal.authorize(any(), anyString()))
        .thenThrow(new BusinessGovernanceException("AGENT_PERMISSION_DENIED", "permission denied"));
    var restricted = new SharedConfigurationService(jdbc, denied, cipher);
    assertThrows(MSException.class, () -> restricted.audits("p", id, 1, 20, null, null));
  }
}
