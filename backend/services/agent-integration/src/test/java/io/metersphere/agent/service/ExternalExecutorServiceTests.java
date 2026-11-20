package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ExternalExecutorServiceTests {
  JdbcTemplate jdbc;
  BusinessAssetService assets;
  ExternalExecutorService service;

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
    jdbc.execute(
        "CREATE TABLE business_asset(id VARCHAR(64),project_id VARCHAR(64),current_version_id"
            + " VARCHAR(64),archived BOOLEAN)");
    jdbc.execute(
        "CREATE TABLE business_asset_version(id VARCHAR(64),snapshot_json TEXT,version_no INT"
            + " DEFAULT 1)");
    jdbc.execute("CREATE TABLE business_asset_reader(asset_id VARCHAR(64),user_id VARCHAR(64))");
    jdbc.execute(
        "CREATE TABLE shared_configuration(id VARCHAR(64),project_id VARCHAR(64),current_version"
            + " INT)");
    jdbc.execute(
        "CREATE TABLE external_executor_configuration(session_id VARCHAR(64),configuration_id"
            + " VARCHAR(64),version_no INT)");
    Path root = Path.of("").toAbsolutePath();
    while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
    var sql =
        Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_151__external_executor_sessions.sql"));
    for (var statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    var evidence =
        Files.readString(
            root.resolve(
                "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_154__external_executor_evidence.sql"));
    jdbc.execute(evidence);
    assets = mock(BusinessAssetService.class);
    when(assets.authorize("p", true)).thenReturn("actor");
    when(assets.authorize("p", false)).thenReturn("actor");
    service = new ExternalExecutorService(jdbc, assets);
    org.springframework.test.util.ReflectionTestUtils.setField(
        service,
        "evidenceCipher",
        new io.metersphere.agent.security.SharedConfigurationCipher(
            Base64.getEncoder().encodeToString(new byte[32])));
    service = BusinessAssetTransactionProxy.wrap(service, jdbc);
    var token = new AgentToken();
    token.setId("token");
    token.setUserId("actor");
    AgentTokenContext.set(token);
  }

  @AfterEach
  void cleanup() {
    AgentTokenContext.clear();
  }

  String connect() {
    return (String) service.connect("p", "executor", "request").get("sessionId");
  }

  @Test
  void externalExecutionAcceptsEventsAndCorrectionWithoutFrozenActionsOrLease() {
    String id = connect();
    service.event("p", id, "event1", "DEVIATION", "BUSINESS_RULE", "EXPECTED_CHANGED", "RUNNING");
    AgentTokenContext.clear();
    String command = service.correct("p", id, "重新读取业务规则并检查审批分支");
    var token = new AgentToken();
    token.setId("token");
    token.setUserId("actor");
    AgentTokenContext.set(token);
    service.acknowledge("p", id, command, "APPLIED", "CONTEXT_REFRESHED");
    service.acknowledge("p", id, command, "APPLIED", "CONTEXT_REFRESHED");
    service.event("p", id, "event2", "COMPLETE", "FINISH", "NONE", "PASSED");
    assertEquals(2, ((List<?>) service.monitor("p", id).get("events")).size());
    assertThrows(
        MSException.class, () -> service.acknowledge("p", id, command, "FAILED", "OVERRIDE"));
  }

  @Test
  void anotherTokenCannotForgeEventsAndDuplicateEventCannotChangePayload() {
    String id = connect();
    service.event("p", id, "one", "OBSERVATION", "PAGE", "NONE", "RUNNING");
    assertThrows(
        MSException.class,
        () -> service.event("p", id, "one", "OBSERVATION", "OTHER", "NONE", "RUNNING"));
    var token = new AgentToken();
    token.setId("different");
    token.setUserId("actor");
    AgentTokenContext.set(token);
    assertThrows(
        MSException.class,
        () -> service.event("p", id, "two", "HEARTBEAT", "PAGE", "NONE", "RUNNING"));
  }

  @Test
  void contextRemainsPinnedAfterAssetChanges() {
    jdbc.update("INSERT INTO business_asset VALUES('asset','p','v1',FALSE)");
    jdbc.update("INSERT INTO business_asset_version(id,snapshot_json) VALUES('v1','first')");
    String id = connect();
    jdbc.update("UPDATE business_asset SET current_version_id='v2'");
    jdbc.update("INSERT INTO business_asset_version(id,snapshot_json) VALUES('v2','changed')");
    assertEquals("v1", service.context("p", id).getFirst().get("versionId"));
    assertEquals(id, connect());
  }

  @Test
  void evidenceIsEncryptedValidatedAndBoundToTheAuthenticatedSession() throws Exception {
    String session = connect();
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        out);
    String content = Base64.getEncoder().encodeToString(out.toByteArray());
    assertThrows(
        MSException.class, () -> service.uploadEvidence("p", session, "e1", content, false));
    var first = service.uploadEvidence("p", session, "e1", content, true);
    assertEquals(first, service.uploadEvidence("p", session, "e1", content, true));
    assertEquals(content, service.evidence("p", first.get("evidenceId")).get("content"));
    var stored =
        jdbc.queryForObject(
            "SELECT encrypted_content FROM external_executor_evidence WHERE id=?",
            String.class,
            first.get("evidenceId"));
    assertFalse(stored.contains(content));
    var token = new AgentToken();
    token.setId("different");
    token.setUserId("actor");
    AgentTokenContext.set(token);
    assertThrows(MSException.class, () -> service.evidence("p", first.get("evidenceId")));
  }

  @Test
  void deliberateContextReplacementNotifiesAndRecordsItsExactVersion() {
    jdbc.update("INSERT INTO business_asset VALUES('asset','p','v1',FALSE)");
    jdbc.update("INSERT INTO business_asset_version VALUES('v1','{}',1),('v2','{}',2)");
    String session = connect();
    service.refreshContext("p", session, "asset", "v2", "v1", "审批规则更新");
    service.refreshContext("p", session, "asset", "v2", "v1", "审批规则更新");
    assertEquals("v2", service.context("p", session).getFirst().get("versionId"));
    assertEquals(2, service.readContext("p", session, "asset").get("versionNo"));
    assertEquals(1, ((List<?>) service.monitor("p", session).get("events")).size());
    assertEquals(1, service.commands("p", session).size());
    assertThrows(
        MSException.class,
        () -> service.refreshContext("p", session, "asset", "v1", "stale", "过期请求"));
  }

  @Test
  void completedCommandHistoryCannotStarveNewCorrections() {
    String session = connect();
    var prior = new ArrayList<Object[]>();
    for (int i = 0; i < 1000; i++)
      prior.add(new Object[] {"past-" + i, session, "历史纠正", "APPLIED", "actor", (long) i});
    jdbc.batchUpdate(
        "INSERT INTO"
            + " external_executor_command(id,session_id,instruction,status,actor_id,create_time)"
            + " VALUES(?,?,?,?,?,?)",
        prior);
    String command = service.correct("p", session, "检查新的业务分支");
    assertEquals(command, service.commands("p", session).getFirst().get("id"));
    assertEquals(1, service.commands("p", session).size());
    assertEquals(1001L, service.commandHistory("p", session, 1, 100).get("total"));
    assertEquals(
        100, ((List<?>) service.commandHistory("p", session, 2, 100).get("records")).size());
  }

  @Test
  void longRunningObservationHistoryShowsNewEventsAndKeepsOlderPages() {
    String session = connect();
    var prior = new ArrayList<Object[]>();
    for (int i = 0; i < 2000; i++)
      prior.add(
          new Object[] {
            "event-" + i, session, "key-" + i, "HEARTBEAT", "PAGE", "NONE", "RUNNING", "", (long) i
          });
    jdbc.batchUpdate(
        "INSERT INTO"
            + " external_executor_event(id,session_id,request_key,event_type,stage,error_category,outcome,summary,create_time)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        prior);
    service.event(
        "p",
        session,
        "new-deviation",
        "DEVIATION",
        "BUSINESS",
        "RULE_CHANGED",
        "RUNNING",
        "新的业务偏差",
        null);
    var monitor = service.monitor("p", session);
    assertEquals(2001L, monitor.get("eventTotal"));
    assertEquals(
        "DEVIATION",
        ((List<Map<String, Object>>) monitor.get("events")).getFirst().get("eventType"));
    assertEquals(100, ((List<?>) service.eventHistory("p", session, 2, 100).get("records")).size());
  }
}
