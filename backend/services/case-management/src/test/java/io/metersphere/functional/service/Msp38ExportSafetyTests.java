package io.metersphere.functional.service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class Msp38ExportSafetyTests {
 @Test void maintenanceFailuresAreSafeAndNextInvocationRetries(){
  String secret="msp38-maintenance-secret";var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var failure=new org.springframework.dao.DataAccessResourceFailureException(secret,new java.sql.SQLException(secret,"08001",99));
  when(jdbc.update(anyString(),any(Object[].class))).thenThrow(failure);when(jdbc.queryForList(anyString(),any(Object[].class))).thenThrow(failure);
  var service=new FunctionalCaseExportTaskService();ReflectionTestUtils.setField(service,"jdbc",jdbc);
  var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();root.addAppender(logs);
  try {
   assertDoesNotThrow(service::recover);assertDoesNotThrow(service::cleanupFiles);
   var messages=logs.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));assertTrue(messages.contains("phase=EXPORT_RECOVERY"));assertTrue(messages.contains("phase=EXPORT_CLEANUP_SCAN"));assertTrue(messages.contains("sqlState=08001"));assertFalse(messages.contains(secret));assertTrue(logs.list.stream().allMatch(e->e.getThrowableProxy()==null));
   when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(java.util.List.of());assertDoesNotThrow(service::recover);assertDoesNotThrow(service::cleanupFiles);verify(jdbc,times(3)).update(anyString(),any(Object[].class));verify(jdbc,times(2)).queryForList(anyString(),any(Object[].class));
  } finally {root.detachAppender(logs);logs.stop();service.close();}
 }
 @Test void downloadFailureHasSafeCorrelationWithoutLeakingCause() throws Exception {
  String secret="msp38-download-secret";var service=new FunctionalCaseFileService();var tasks=mock(FunctionalCaseExportTaskService.class);var storage=mock(io.metersphere.system.service.FileService.class);var projects=mock(io.metersphere.project.mapper.ProjectMapper.class);ReflectionTestUtils.setField(service,"exportTaskService",tasks);ReflectionTestUtils.setField(service,"fileService",storage);ReflectionTestUtils.setField(service,"projectMapper",projects);
  var task=new io.metersphere.functional.dto.ExportTaskDTO();task.setTaskId("safe-task");task.setFileId("safe-file");task.setFileType("xlsx");when(tasks.forDownload("hub","safe-file","user")).thenReturn(task);var project=new io.metersphere.project.domain.Project();project.setName("demo");when(projects.selectByPrimaryKey("hub")).thenReturn(project);when(storage.getFileAsStream(any())).thenThrow(new IllegalStateException(secret,new java.net.SocketTimeoutException(secret)));
  var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();root.addAppender(logs);
  try {var failure=assertThrows(io.metersphere.sdk.exception.MSException.class,()->service.downloadFile("hub","safe-file","user"));assertNull(failure.getCause());assertTrue(failure.getMessage().contains("safe-task"));assertFalse(failure.getMessage().contains(secret));assertTrue(logs.list.stream().anyMatch(e->e.getFormattedMessage().contains("phase=EXPORT_DOWNLOAD")&&e.getFormattedMessage().contains("category=NETWORK")));assertTrue(logs.list.stream().allMatch(e->!e.getFormattedMessage().contains(secret)&&e.getThrowableProxy()==null));}finally{root.detachAppender(logs);logs.stop();}
 }
}
