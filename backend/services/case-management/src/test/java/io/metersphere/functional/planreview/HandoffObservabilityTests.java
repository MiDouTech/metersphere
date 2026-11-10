package io.metersphere.functional.planreview;

import io.metersphere.functional.handoff.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HandoffObservabilityTests {
    HandoffPersistenceTests t;
    ch.qos.logback.classic.Logger logger;
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs;
    @BeforeEach void setup()throws Exception{
        t=new HandoffPersistenceTests();t.setup();t.useTransactionalServiceProxy();
        logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(HandoffWorker.class);
        logs=new ch.qos.logback.core.read.ListAppender<>();logs.start();logger.addAppender(logs);
    }
    @AfterEach void close(){try{t.closeOwnedInMemoryDatabase();}finally{if(logger!=null)logger.detachAppender(logs);if(logs!=null)logs.stop();}}
    int used(){return t.jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class);}
    void assertPreparationFailure(String stage,String category){
        assertEquals("FAILED",t.state());assertEquals(0,t.target.calls.get());assertEquals(0,used());
        assertEquals(0,t.count("hifox_http_attempt"));
        assertEquals("CONFIG_OR_SOURCE_UNAVAILABLE",t.jdbc.queryForObject("SELECT reason FROM hifox_http_delivery",String.class));
        var stats=t.worker.statistics();assertEquals(1,stats.preparationFailures());assertEquals(1,stats.failures());
        var diagnostic=stats.lastPreparationFailure();assertEquals(stage,diagnostic.stage());assertEquals(category,diagnostic.category());
        assertEquals(t.key(),diagnostic.businessKey());assertEquals(t.jdbc.queryForObject("SELECT event_id FROM hifox_http_delivery",String.class),diagnostic.eventId());
        var captured=logs.list.stream().filter(e->e.getFormattedMessage().startsWith("Webhook preparation failed")).toList();
        assertEquals(1,captured.size());assertNull(captured.getFirst().getThrowableProxy());
        assertTrue(captured.getFirst().getFormattedMessage().contains("stage="+stage));
        for(var event:logs.list){assertFalse(event.getFormattedMessage().contains("LEAK_SENTINEL"));assertFalse(event.getFormattedMessage().contains("receiver.example.test"));}
        assertFalse(stats.toString().contains("LEAK_SENTINEL"));
    }
    @Test void invalidUrlCiphertextIsDiagnosableWithoutSendingOrConsumingBudget(){
        var registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();t.worker.monitor(registry);
        t.enable();t.complete();t.jdbc.update("UPDATE hifox_http_config SET url_secret='LEAK_SENTINEL_NOT_BASE64' WHERE project_id=?",HandoffPersistenceTests.PLATFORM);
        t.worker.dispatch("project",t.key());assertPreparationFailure("URL_DECRYPT","CIPHERTEXT_FORMAT");
        assertEquals(1,registry.get("metersphere.handoff.preparation.failures").functionCounter().count());
        assertEquals(1,registry.get("metersphere.handoff.stage.failures").tag("stage","URL_DECRYPT").functionCounter().count());
        assertTrue(registry.getMeters().stream().flatMap(m->m.getId().getTags().stream()).allMatch(tag->tag.getKey().equals("stage")));
        t.worker.tick();t.awaitIdle(t.worker);assertEquals(1,t.worker.statistics().preparationFailures());
    }
    @Test void authenticationMismatchHasItsOwnSafeClassification(){
        t.enable();t.complete();
        byte[] ciphertext=Base64.getDecoder().decode(t.jdbc.queryForObject("SELECT url_secret FROM hifox_http_config WHERE project_id=?",String.class,HandoffPersistenceTests.PLATFORM));ciphertext[ciphertext.length-1]^=1;
        t.jdbc.update("UPDATE hifox_http_config SET url_secret=? WHERE project_id=?",Base64.getEncoder().encodeToString(ciphertext),HandoffPersistenceTests.PLATFORM);
        t.worker.dispatch("project",t.key());assertPreparationFailure("URL_DECRYPT","AUTHENTICATION_FAILED");
    }
    @ParameterizedTest @CsvSource({"url,0","url,1","url,12","url,27","token,0","token,1","token,12","token,27"})
    void shortCiphertextHasFormatDiagnosticWithoutSending(String field,int size){
        assertTrue(List.of("url","token").contains(field));
        if(field.equals("token")){
            t.save("https://receiver.example.test/hook","BEARER","LEAK_SENTINEL_TOKEN");
            t.run(()->t.service.grant(HandoffPersistenceTests.PLATFORM,t.revision(),true,"actor"));
        }
        t.enable();t.complete();
        t.jdbc.update("UPDATE hifox_http_config SET "+field+"_secret=? WHERE project_id=?",Base64.getEncoder().encodeToString(new byte[size]),HandoffPersistenceTests.PLATFORM);
        t.worker.dispatch("project",t.key());assertPreparationFailure(field.equals("url")?"URL_DECRYPT":"TOKEN_DECRYPT","CIPHERTEXT_FORMAT");
    }
    @Test void tokenFailureIsDistinguishedFromUrlFailure(){
        t.save("https://receiver.example.test/hook","BEARER","LEAK_SENTINEL_TOKEN");t.run(()->t.service.grant(HandoffPersistenceTests.PLATFORM,t.revision(),true,"actor"));t.enable();t.complete();
        t.jdbc.update("UPDATE hifox_http_config SET token_secret='LEAK_SENTINEL_NOT_BASE64' WHERE project_id=?",HandoffPersistenceTests.PLATFORM);
        t.worker.dispatch("project",t.key());assertPreparationFailure("TOKEN_DECRYPT","CIPHERTEXT_FORMAT");
    }
    @Test void attemptInsertFailurePreservesSqlCodeButNeverSqlOrExceptionMessage(){
        t.enable();t.complete();
        var rejecting=new org.springframework.jdbc.core.JdbcTemplate(t.jdbc.getDataSource()){
            @Override public int update(String sql,Object... args){
                if(sql.startsWith("INSERT INTO hifox_http_attempt"))throw new org.springframework.jdbc.UncategorizedSQLException("LEAK_SENTINEL_TASK","LEAK_SENTINEL_SQL",new java.sql.SQLException("LEAK_SENTINEL_MESSAGE","42000",1142));
                return super.update(sql,args);
            }
        };
        var original=t.worker;t.worker=new HandoffWorker(new HandoffStore(rejecting,new PlanReviewPersistence(t.jdbc)),t.service,t.tx.getTransactionManager());t.worker.setEnabled(true);original.destroy();
        t.worker.dispatch("project",t.key());assertPreparationFailure("ATTEMPT_INSERT","DB_STATE");
        assertEquals("42000",t.worker.statistics().lastPreparationFailure().sqlState());assertEquals(1142,t.worker.statistics().lastPreparationFailure().vendorCode());
        assertTrue(logs.list.stream().anyMatch(e->e.getFormattedMessage().contains("vendorCode=1142")));
    }
    @Test void successfulDeliveryDoesNotIncrementPreparationFailureCounters(){
        t.enable();t.complete();t.worker.dispatch("project",t.key());
        assertEquals("SUCCESS",t.state());assertEquals(1,used());assertEquals(0,t.worker.statistics().preparationFailures());assertNull(t.worker.statistics().lastPreparationFailure());
    }
}
