package io.metersphere.agent.controller;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class BusinessGovernanceExceptionHandlerTests {
  @Test
  void databaseFailureIsCorrelatedAndDoesNotLogRawMessagesOrStack() {
    var logger = (Logger) LoggerFactory.getLogger(BusinessGovernanceExceptionHandler.class);
    var capture = new ListAppender<ILoggingEvent>();
    capture.start();
    logger.addAppender(capture);
    try {
      var request = new MockHttpServletRequest();
      request.addHeader("X-Trace-Id", "private-password");
      var error =
          new DataAccessResourceFailureException(
              "password=top-secret SQL statement",
              new SQLException("token=also-secret", "08001", 1045));
      var response = new BusinessGovernanceExceptionHandler().handle(error, request);
      assertEquals(500, response.getStatusCode().value());
      assertNotNull(response.getBody().getTraceId());
      String log = capture.list.getFirst().getFormattedMessage();
      assertTrue(log.contains("08001"));
      assertTrue(log.contains("1045"));
      assertTrue(log.contains(response.getBody().getTraceId()));
      assertFalse(log.contains("top-secret"));
      assertFalse(log.contains("also-secret"));
      assertFalse(log.contains("private-password"));
      assertNull(capture.list.getFirst().getThrowableProxy());
    } finally {
      logger.detachAppender(capture);
      capture.stop();
    }
  }
}
