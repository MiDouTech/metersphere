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
    var auth = mock(BusinessAssetService.class);
    when(auth.authorize(eq("p"), anyBoolean(), anyString(), anyString())).thenReturn("actor");
    cipher = new SharedConfigurationCipher(Base64.getEncoder().encodeToString(new byte[32]));
    service =
        BusinessAssetTransactionProxy.wrap(
            new SharedConfigurationService(jdbc, auth, cipher), jdbc);
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
}
