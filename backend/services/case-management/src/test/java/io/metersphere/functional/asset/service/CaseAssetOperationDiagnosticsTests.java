package io.metersphere.functional.asset.service;
import io.metersphere.functional.asset.controller.CaseAssetDatabaseExceptionHandler;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
class CaseAssetOperationDiagnosticsTests {
 Logger logger;ListAppender<ILoggingEvent> appender;
 @BeforeEach void setup(){logger=(Logger)LoggerFactory.getLogger(CaseAssetOperationDiagnostics.class);appender=new ListAppender<>();appender.start();logger.addAppender(appender);}
 @AfterEach void close(){logger.detachAppender(appender);appender.stop();}
 @Test void classifiesWhitelistedFailuresWithoutExceptionText(){
  CaseAssetOperationDiagnostics.record("NETWORK","ref",new RuntimeException("private-secret-sentinel",new java.net.SocketTimeoutException("private-secret-sentinel")));
  assertTrue(appender.list.getLast().getFormattedMessage().contains("category=NETWORK"));
  CaseAssetOperationDiagnostics.record("PARSE","ref",new NumberFormatException("private-secret-sentinel"));assertTrue(appender.list.getLast().getFormattedMessage().contains("category=PARSE"));
  CaseAssetOperationDiagnostics.record("PARSE","ref",new org.xml.sax.SAXException("private-secret-sentinel"));assertTrue(appender.list.getLast().getFormattedMessage().contains("category=PARSE"));
  CaseAssetOperationDiagnostics.record("FILE","ref",new java.io.IOException("private-secret-sentinel"));assertTrue(appender.list.getLast().getFormattedMessage().contains("category=FILE_STORAGE"));
  CaseAssetOperationDiagnostics.record("SQL","ref",new SQLException("private-secret-sentinel","45000",1644));
  String sql=appender.list.getLast().getFormattedMessage();assertTrue(sql.contains("category=DATABASE"));assertTrue(sql.contains("sqlState=45000"));assertTrue(sql.contains("vendorCode=1644"));assertTrue(sql.contains("rootType=SQLException"));assertTrue(sql.matches(".*time=[0-9]+.*"));
  CaseAssetOperationDiagnostics.record("bad/private-secret-sentinel","https://private-secret-sentinel",new SQLException("private-secret-sentinel","private-secret-sentinel",7));assertTrue(appender.list.getLast().getFormattedMessage().contains("sqlState=UNKNOWN"));
  for(var event:appender.list){assertFalse(event.getFormattedMessage().contains("private-secret-sentinel"));assertNull(event.getThrowableProxy());}
 }
 @Test void causeCyclesTerminateAndAdvicePreservesSafeProtocol(){
  var first=new RuntimeException("private-secret-sentinel");var second=new RuntimeException("private-secret-sentinel");first.initCause(second);second.initCause(first);
  assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->CaseAssetOperationDiagnostics.record("CYCLE","ref",first));
  var response=new CaseAssetDatabaseExceptionHandler().handle(new org.springframework.dao.DataAccessResourceFailureException("private-secret-sentinel",new SQLException("private-secret-sentinel","45000",1644)));
  assertEquals(500,response.getStatusCode().value());var body=response.getBody();assertNotNull(body);assertDoesNotThrow(()->java.util.UUID.fromString(body.getRequestId()));assertTrue(body.getMessage().contains(body.getRequestId()));assertFalse(body.getMessage().contains("private-secret-sentinel"));assertEquals("api.internalError",body.getMessageKey());
  String logged=appender.list.getLast().getFormattedMessage();assertTrue(logged.contains("sqlState=45000"));assertTrue(logged.contains("vendorCode=1644"));assertTrue(logged.contains("phase=ASSET_DATABASE"));
  for(var event:appender.list){assertFalse(event.getFormattedMessage().contains("private-secret-sentinel"));assertNull(event.getThrowableProxy());}
 }
 @Test void assetDatabaseAdviceWinsOverGlobalHandlerInMvc() throws Exception {
  var controller=new io.metersphere.functional.asset.controller.CaseAssetSystemController();var service=org.mockito.Mockito.mock(CaseAssetSystemService.class);org.springframework.test.util.ReflectionTestUtils.setField(controller,"service",service);org.mockito.Mockito.when(service.list()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private-secret-sentinel",new SQLException("private-secret-sentinel","08001",99)));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CaseAssetDatabaseExceptionHandler(),new io.metersphere.system.controller.handler.RestControllerExceptionHandler()).build();
  var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/case-asset/systems").accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError()).andReturn();var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertEquals("api.internalError",body.path("messageKey").asText());assertFalse(body.toString().contains("private-secret-sentinel"));assertTrue(appender.list.stream().anyMatch(e->e.getFormattedMessage().contains("phase=ASSET_DATABASE")&&e.getFormattedMessage().contains(body.path("requestId").asText())));
 }

}
