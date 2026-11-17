package io.metersphere.functional.planreview;

import org.junit.jupiter.api.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewSyncDiagnosticsTests {
    Logger logger;Level previous;ListAppender<ILoggingEvent> logs;
    PlanReviewSyncDiagnostics diagnostics;SimpleMeterRegistry registry;
    @BeforeEach void setup(){
        logger=(Logger)org.slf4j.LoggerFactory.getLogger(PlanReviewSyncDiagnostics.class);previous=logger.getLevel();logger.setLevel(Level.INFO);
        logs=new ListAppender<>();logs.start();logger.addAppender(logs);
        diagnostics=new PlanReviewSyncDiagnostics();registry=new SimpleMeterRegistry();diagnostics.monitor(registry);diagnostics.monitorDelay(registry);
    }
    @AfterEach void close(){logger.detachAppender(logs);logger.setLevel(previous);logs.stop();registry.close();}
    @Test void failurePreservesSqlClassificationWithoutLoggingSqlOrThrowable(){
        diagnostics.failed("project","relation","generation",2,4,true,new org.springframework.jdbc.UncategorizedSQLException("LEAK_SENTINEL","private SQL",new java.sql.SQLException("secret payload","42000",1142)));
        String text=logs.list.getFirst().getFormattedMessage();assertTrue(text.contains("RETRY_EXHAUSTED"));assertTrue(text.contains("relation=relation"));assertTrue(text.contains("sqlState=42000"));assertTrue(text.contains("vendorCode=1142"));
        assertFalse(text.contains("LEAK_SENTINEL"));assertFalse(text.contains("private SQL"));assertFalse(text.contains("secret payload"));assertNull(logs.list.getFirst().getThrowableProxy());
        assertEquals(1,registry.get("metersphere.planreview.sync.exhausted").functionCounter().count());
    }
    @Test void malformedIdentifiersAndSqlStateCannotInjectLogs(){
        diagnostics.failed("bad\nproject","https://private.example/path","generation",1,1,false,new org.springframework.jdbc.UncategorizedSQLException("secret","secret",new java.sql.SQLException("secret","bad\nstate",0)));
        String text=logs.list.getFirst().getFormattedMessage();assertTrue(text.contains("project=UNAVAILABLE"));assertTrue(text.contains("relation=UNAVAILABLE"));assertTrue(text.contains("sqlState=NONE"));assertFalse(text.contains("private.example"));
    }
    @Test void importFailureIncludesJobAndOperationWithoutLeakingExceptionMessage(){
        PlanReviewSyncDiagnostics.importFailure("project","operation","job",new org.springframework.jdbc.UncategorizedSQLException("secret","private SQL",new java.sql.SQLException("LEAK_SENTINEL","42000",1142)));
        String text=logs.list.getFirst().getFormattedMessage();assertTrue(text.contains("stage=IMPORT_ASSOCIATION"));assertTrue(text.contains("job=job"));assertTrue(text.contains("operation=operation"));assertTrue(text.contains("exceptionType=SQLException"));assertFalse(text.contains("LEAK_SENTINEL"));assertNull(logs.list.getFirst().getThrowableProxy());
    }
    @Test void rollbackDoesNotPublishSuccessfulOrFailedCommittedState()throws Exception{
        var db=PlanReviewTestDatabase.create();
        db.tx().executeWithoutResult(s->{diagnostics.completed("p","r","g",1);diagnostics.failed("p","r","g",1,1,false,new RuntimeException("secret"));s.setRollbackOnly();});
        assertTrue(logs.list.isEmpty());assertEquals(0,registry.get("metersphere.planreview.sync.completed").functionCounter().count());assertEquals(0,registry.get("metersphere.planreview.sync.failures").functionCounter().count());
        db.tx().executeWithoutResult(s->{diagnostics.completed("p","r","g",1);diagnostics.delayed("p","r","g",2);});
        assertEquals(2,logs.list.size());assertEquals(1,registry.get("metersphere.planreview.sync.completed").functionCounter().count());assertEquals(1,registry.get("metersphere.planreview.sync.delayed").functionCounter().count());
        if(System.getProperty("plan.review.mysql.config")==null)db.jdbc().execute("SHUTDOWN");
    }
    @Test void loggingBackendFailureDoesNotReplaceBusinessOutcome(){
        var broken=new ch.qos.logback.core.AppenderBase<ILoggingEvent>(){@Override protected void append(ILoggingEvent event){throw new IllegalStateException("logging failed");}};
        broken.start();logger.addAppender(broken);
        try{assertDoesNotThrow(()->diagnostics.failed("p","r","g",1,1,false,new RuntimeException("secret")));assertDoesNotThrow(()->diagnostics.completed("p","r","g",1));}
        finally{logger.detachAppender(broken);broken.stop();}
    }
}
