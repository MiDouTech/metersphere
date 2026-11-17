package io.metersphere.functional.planreview;

import java.util.*;
import java.sql.SQLException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewFailurePersistenceTests {
    enum Fault { QUERY, UPDATE, AUDIT, COMMIT, COMMIT_UNKNOWN, NONE, PROGRESS, POLL }
    PlanReviewTestDatabase.Database db;SimpleMeterRegistry registry;
    Logger logger,root;Level previous;ListAppender<ILoggingEvent> logs,allLogs;
    @BeforeEach void setup()throws Exception{
        db=PlanReviewTestDatabase.create();registry=new SimpleMeterRegistry();
        db.jdbc().update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_version,source_changed_at,original_actor,create_time,update_time) VALUES('relation','project','plan','review','generation','plan','review','ACTIVE','PENDING',3,1,'actor',1,1)");
        logger=(Logger)org.slf4j.LoggerFactory.getLogger(PlanReviewSyncDiagnostics.class);previous=logger.getLevel();logger.setLevel(Level.INFO);
        logs=new ListAppender<>();logs.start();logger.addAppender(logs);
        root=(Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        allLogs=new ListAppender<>();allLogs.start();root.addAppender(allLogs);
    }
    @AfterEach void close(){logger.detachAppender(logs);logger.setLevel(previous);root.detachAppender(allLogs);logs.stop();allLogs.stop();registry.close();if(System.getProperty("plan.review.mysql.config")==null)db.jdbc().execute("SHUTDOWN");}
    static RuntimeException sql(String state,int code){return new UncategorizedSQLException("PRIVATE_TASK_SENTINEL","PRIVATE_SQL_SENTINEL",new SQLException("PRIVATE_MESSAGE_SENTINEL",state,code));}
    PlanReviewApplicationService service(Fault fault,boolean success){
        var jdbc=new JdbcTemplate(db.jdbc().getDataSource()){
            @Override public List<Map<String,Object>> queryForList(String sql,Object... args){
                if(fault==Fault.POLL&&sql.contains("ORDER BY update_time,id LIMIT 1"))throw sql("08006",2006);
                if(fault==Fault.QUERY&&sql.startsWith("SELECT * FROM plan_review_relation WHERE id=? AND generation=?"))throw sql("08006",2006);
                return super.queryForList(sql,args);
            }
            @Override public int update(String sql,Object... args){
                if(fault==Fault.UPDATE&&sql.startsWith("UPDATE plan_review_relation SET attempts="))throw sql("08006",2006);
                if(fault==Fault.AUDIT&&sql.startsWith("INSERT INTO plan_review_audit"))throw sql("08006",2006);
                return super.update(sql,args);
            }
            @Override public <T> List<T> queryForList(String sql,Class<T> type,Object... args){
                if(fault==Fault.PROGRESS&&sql.startsWith("SELECT applied_batches"))throw sql("08006",2006);
                return super.queryForList(sql,type,args);
            }
        };
        var manager=new DataSourceTransactionManager(db.jdbc().getDataSource()){
            @Override protected void doCommit(DefaultTransactionStatus status){
                if(fault==Fault.COMMIT){super.doRollback(status);throw new org.springframework.transaction.TransactionSystemException("PRIVATE_COMMIT_SENTINEL",new SQLException("PRIVATE_MESSAGE_SENTINEL","08006",2006));}
                super.doCommit(status);
                if(fault==Fault.COMMIT_UNKNOWN)throw new org.springframework.transaction.TransactionSystemException("PRIVATE_COMMIT_SENTINEL",new SQLException("PRIVATE_MESSAGE_SENTINEL","08006",2006));
            }
        };
        var service=new PlanReviewApplicationService(jdbc,null,null,manager,null){
            @Override void synchronize(String id,String generation){
                if(!success)throw sql("42000",1142);
                jdbc.update("UPDATE plan_review_relation SET applied_batches=applied_batches+1,sync_state='SYNCED' WHERE id=?",id);
            }
        };
        service.monitor(registry);return service;
    }
    void scheduled(PlanReviewApplicationService service){
        List<Throwable> escaped=new ArrayList<>();
        Runnable task=org.springframework.scheduling.support.TaskUtils.decorateTaskWithErrorHandler(service::drain,escaped::add,true);
        assertDoesNotThrow(task::run);assertTrue(escaped.isEmpty(),"raw exceptions must not reach the scheduler error handler");
    }
    void safeLogs(){
        for(var event:allLogs.list){String text=event.getFormattedMessage();assertFalse(text.contains("PRIVATE_"));assertNull(event.getThrowableProxy());}
    }
    double metric(String name){return registry.get("metersphere.planreview.sync."+name).functionCounter().count();}
    @ParameterizedTest @EnumSource(value=Fault.class,names={"QUERY","UPDATE","AUDIT","COMMIT","COMMIT_UNKNOWN"})
    void bookkeepingFailureRetainsBothSafeFaultsWithoutClaimingCommittedResult(Fault fault){
        scheduled(service(fault,false));
        assertEquals(2,logs.list.size());
        String original=logs.list.get(0).getFormattedMessage(),persist=logs.list.get(1).getFormattedMessage();
        assertTrue(original.contains("stage=TARGET_SYNC"));assertTrue(original.contains("sqlState=42000"));assertTrue(original.contains("vendorCode=1142"));
        assertTrue(persist.contains("stage=FAILURE_PERSIST"));assertTrue(persist.contains("sqlState=08006"));assertTrue(persist.contains("vendorCode=2006"));
        for(var event:logs.list){assertTrue(event.getFormattedMessage().contains("relation=relation"));assertTrue(event.getFormattedMessage().contains("generation=generation"));assertTrue(event.getFormattedMessage().contains("stateCommit=UNCONFIRMED"));}
        assertEquals(0,metric("completed"));assertEquals(0,metric("failures"));assertEquals(0,metric("exhausted"));assertEquals(2,metric("observed.failures"));assertEquals(1,metric("persistence.failures"));
        boolean committed=fault==Fault.COMMIT_UNKNOWN;
        assertEquals(committed?1:0,db.jdbc().queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));
        assertEquals(committed?"WAITING_RETRY":"PENDING",db.jdbc().queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        assertEquals(committed?1:0,db.jdbc().queryForObject("SELECT COUNT(*) FROM plan_review_audit",Integer.class));
        safeLogs();
    }
    @Test void successfulBookkeepingRecordsExactlyOneAttemptAndACommittedFailure(){
        scheduled(service(Fault.NONE,false));
        assertEquals(1,db.jdbc().queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));assertEquals("WAITING_RETRY",db.jdbc().queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        assertEquals(2,logs.list.size());assertTrue(logs.list.get(1).getFormattedMessage().contains("stateCommit=COMMITTED"));assertEquals(1,metric("failures"));assertEquals(1,metric("observed.failures"));assertEquals(0,metric("persistence.failures"));safeLogs();
    }
    @Test void progressReadFailureDoesNotBecomeAnotherBusinessAttempt(){
        scheduled(service(Fault.PROGRESS,true));
        assertEquals(0,db.jdbc().queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));assertEquals("SYNCED",db.jdbc().queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        assertEquals(1,logs.list.size());assertTrue(logs.list.getFirst().getFormattedMessage().contains("stage=PROGRESS_READ"));assertEquals(0,metric("failures"));safeLogs();
    }
    @Test void pollFailureIsSafelyBoundedWithoutAnyBusinessWrite(){
        scheduled(service(Fault.POLL,false));assertEquals(0,db.jdbc().queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));assertEquals(1,logs.list.size());assertTrue(logs.list.getFirst().getFormattedMessage().contains("stage=POLL"));safeLogs();
    }
    @Test void normalSuccessDoesNotEnterFailureAccounting(){
        scheduled(service(Fault.NONE,true));assertTrue(logs.list.isEmpty());assertEquals("SYNCED",db.jdbc().queryForObject("SELECT sync_state FROM plan_review_relation",String.class));assertEquals(0,metric("observed.failures"));assertEquals(0,db.jdbc().queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));
    }
}
