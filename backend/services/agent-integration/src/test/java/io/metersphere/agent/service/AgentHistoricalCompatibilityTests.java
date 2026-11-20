package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.metersphere.agent.dto.AgentExecutionArtifactDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentFactCipher;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

class AgentHistoricalCompatibilityTests {
  JdbcTemplate database() {
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:h2:mem:"
                    + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""));
    jdbc.execute(
        "CREATE TABLE historical_evidence_retention(kind VARCHAR(16),evidence_id"
            + " VARCHAR(64),owner_id VARCHAR(64))");
    return jdbc;
  }

  @Test
  void expirationPreservesHistoricalPersonalHoldsAndCurrentSubmissionRetention() {
    var jdbc = database();
    jdbc.execute(
        "CREATE TABLE agent_case_evidence(id VARCHAR(64),submission_id VARCHAR(64),expires_at"
            + " BIGINT)");
    jdbc.execute(
        "CREATE TABLE agent_case_submission_evidence(evidence_id VARCHAR(64),retain_until BIGINT)");
    jdbc.update(
        "INSERT INTO agent_case_evidence"
            + " VALUES('historical',NULL,0),('retained',NULL,0),('expired',NULL,0),('submitted','submission',0)");
    jdbc.update(
        "INSERT INTO historical_evidence_retention VALUES('PERSONAL','historical','old-run')");
    jdbc.update("INSERT INTO agent_case_submission_evidence VALUES('retained',?)", Long.MAX_VALUE);
    var service = new AgentDirectExecutionService();
    ReflectionTestUtils.setField(service, "jdbc", jdbc);
    service.expirePendingEvidence();
    assertEquals(
        List.of("historical", "retained", "submitted"),
        jdbc.queryForList("SELECT id FROM agent_case_evidence ORDER BY id", String.class));
  }

  @Test
  void historicalTaskHoldStopsStorageDeletion() {
    var jdbc = database();
    jdbc.update("INSERT INTO historical_evidence_retention VALUES('TASK','picture','old-run')");
    var mapper = mock(AgentExecutionMapper.class);
    var artifact = new AgentExecutionArtifactDTO();
    artifact.setId("picture");
    when(mapper.selectExpiredArtifacts(anyLong(), eq(200))).thenReturn(List.of(artifact));
    var service = new AgentExecutionArtifactService();
    ReflectionTestUtils.setField(service, "executionMapper", mapper);
    ReflectionTestUtils.setField(service, "historicalEvidenceJdbc", jdbc);
    service.cleanupExpiredArtifacts();
    verify(mapper, never()).markArtifactDeleted(anyString(), anyString());
  }

  @Test
  void encryptedHistoricalFactsRetainTheirValidationMeaning() {
    var jdbc = database();
    jdbc.execute(
        "CREATE TABLE agent_case_submission(id VARCHAR(64),project_id VARCHAR(64),case_id"
            + " VARCHAR(64),content_hash VARCHAR(64),policy_hash VARCHAR(64),facts CLOB,result"
            + " VARCHAR(32),gate_status VARCHAR(32),create_time BIGINT)");
    var cipher = new AgentFactCipher();
    ReflectionTestUtils.setField(
        cipher, "encodedKey", Base64.getEncoder().encodeToString(new byte[32]));
    String encrypted =
        cipher.protect(
            "{\"password\":\"private-value\",\"resultSource\":\"HISTORICAL_VERIFIED\",\"validationMode\":\"HISTORICAL_MODE\"}");
    assertTrue(AgentFactCipher.protectedValue(encrypted));
    jdbc.update(
        "INSERT INTO agent_case_submission"
            + " VALUES('old','p','case','hash','policy',?,'SUCCESS','ACCEPTED',1)",
        encrypted);
    var service = new AgentExecutionArchiveService();
    ReflectionTestUtils.setField(service, "jdbc", jdbc);
    ReflectionTestUtils.setField(service, "factCipher", cipher);
    Map<String, Object> value =
        ReflectionTestUtils.invokeMethod(service, "source", "SUBMISSION", "old", false);
    assertEquals("HISTORICAL_VERIFIED", value.get("resultSource"));
    assertEquals("HISTORICAL_MODE", value.get("validationMode"));
    String safe =
        AgentExecutionArchiveService.sanitize(io.metersphere.sdk.util.JSON.toJSONString(value));
    assertFalse(safe.contains("private-value"));
    assertEquals(
        encrypted, jdbc.queryForObject("SELECT facts FROM agent_case_submission", String.class));
  }

  @Test
  void cleanupDatabaseFailureLogsSafeRootWithoutPrivateMessageOrStack() {
    var mapper = mock(AgentExecutionMapper.class);
    var artifact = new AgentExecutionArtifactDTO();
    artifact.setId("picture");
    when(mapper.selectExpiredArtifacts(anyLong(), eq(200))).thenReturn(List.of(artifact));
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq("picture")))
        .thenThrow(
            new DataAccessResourceFailureException(
                "password=private-url",
                new java.sql.SQLException("token=private-value", "08001", 1045)));
    var service = new AgentExecutionArtifactService();
    ReflectionTestUtils.setField(service, "executionMapper", mapper);
    ReflectionTestUtils.setField(service, "historicalEvidenceJdbc", jdbc);
    var logger = (Logger) LoggerFactory.getLogger(AgentExecutionArtifactService.class);
    var capture = new ListAppender<ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      service.cleanupExpiredArtifacts();
      assertEquals(1, capture.list.size());
      var entry = capture.list.getFirst();
      String log = entry.getFormattedMessage();
      assertTrue(log.contains("picture"));
      assertTrue(log.contains("08001"));
      assertTrue(log.contains("1045"));
      assertFalse(log.contains("private"));
      assertNull(entry.getThrowableProxy());
      verify(mapper, never()).markArtifactDeleted(anyString(), anyString());
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }

  @Test
  void historicalArchiveReadFailureHasSafeDiagnostics() {
    var jdbc = database();
    jdbc.execute(
        "CREATE TABLE agent_case_submission(id VARCHAR(64),project_id VARCHAR(64),case_id"
            + " VARCHAR(64),content_hash VARCHAR(64),policy_hash VARCHAR(64),facts CLOB,result"
            + " VARCHAR(32),gate_status VARCHAR(32),create_time BIGINT)");
    jdbc.update(
        "INSERT INTO agent_case_submission VALUES('old','p','case','hash','policy','{private"
            + " invalid document','SUCCESS','ACCEPTED',1)");
    var service = new AgentExecutionArchiveService();
    ReflectionTestUtils.setField(service, "jdbc", jdbc);
    ReflectionTestUtils.setField(service, "factCipher", new AgentFactCipher());
    var logger = (Logger) LoggerFactory.getLogger(AgentExecutionArchiveService.class);
    var capture = new ListAppender<ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      assertThrows(
          io.metersphere.sdk.exception.MSException.class,
          () -> ReflectionTestUtils.invokeMethod(service, "source", "SUBMISSION", "old", false));
      var entry = capture.list.getFirst();
      String log = entry.getFormattedMessage();
      assertTrue(log.contains("READ_FACTS"));
      assertTrue(log.contains("JsonParseException"));
      assertFalse(log.contains("private"));
      assertNull(entry.getThrowableProxy());
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }
}
