package io.metersphere.functional.planreview;

import java.sql.SQLException;
import java.util.concurrent.atomic.LongAdder;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Bounded classifications and identifiers only; exception messages and payloads never reach logs. */
public final class PlanReviewSyncDiagnostics {
    private final LongAdder completed=new LongAdder(),failed=new LongAdder(),exhausted=new LongAdder(),delayed=new LongAdder();
    private final LongAdder observedFailures=new LongAdder(),persistenceFailures=new LongAdder();
    enum FailureStage { TARGET_SYNC, FAILURE_PERSIST, PROGRESS_READ, POLL }
    void monitor(MeterRegistry registry){
        FunctionCounter.builder("metersphere.planreview.sync.completed",completed,LongAdder::doubleValue).register(registry);
        FunctionCounter.builder("metersphere.planreview.sync.failures",failed,LongAdder::doubleValue).register(registry);
        FunctionCounter.builder("metersphere.planreview.sync.exhausted",exhausted,LongAdder::doubleValue).register(registry);
        FunctionCounter.builder("metersphere.planreview.sync.observed.failures",observedFailures,LongAdder::doubleValue).register(registry);
        FunctionCounter.builder("metersphere.planreview.sync.persistence.failures",persistenceFailures,LongAdder::doubleValue).register(registry);
    }
    void monitorDelay(MeterRegistry registry){
        FunctionCounter.builder("metersphere.planreview.sync.delayed",delayed,LongAdder::doubleValue).register(registry);
    }
    void completed(String project,String relation,String generation,long version){
        committed(()->{completed.increment();emit("COMPLETED",project,relation,generation,version,0,new Failure("NONE","NONE","NONE",0));});
    }
    void delayed(String project,String relation,String generation,long version){
        committed(()->{delayed.increment();emit("DELAYED",project,relation,generation,version,0,new Failure("SYNC_LAG","NONE","NONE",0));});
    }
    void failed(String project,String relation,String generation,long version,int attempts,boolean stop,Exception error){
        Failure failure=classify(error);
        committed(()->{failed.increment();if(stop)exhausted.increment();emit(stop?"RETRY_EXHAUSTED":"FAILED",project,relation,generation,version,attempts,failure);});
    }
    /** Fault observations must survive a failed/uncertain database commit. They are not persisted results. */
    void observedFailure(FailureStage stage,String project,String relation,String generation,long version,int attempts,Exception error){
        observedFailures.increment();if(stage==FailureStage.FAILURE_PERSIST)persistenceFailures.increment();
        emit("OBSERVED_FAILURE",project,relation,generation,version,attempts,classify(error),stage.name(),"UNCONFIRMED");
    }
    private record Failure(String category,String exceptionType,String sqlState,int vendorCode){}
    private static Failure classify(Exception error){
        String category=error instanceof org.springframework.dao.CannotAcquireLockException?"DB_LOCK":error instanceof org.springframework.dao.QueryTimeoutException||error instanceof org.springframework.transaction.TransactionTimedOutException?"DB_TIMEOUT":error instanceof DataAccessException?"DATABASE":"LOCAL_STATE",sqlState="NONE";
        String type=error instanceof DataAccessException?"DataAccessException":error instanceof PlanReviewFailure?"PlanReviewFailure":error instanceof NullPointerException?"NullPointerException":error instanceof IllegalArgumentException?"IllegalArgumentException":"RuntimeException";
        int vendorCode=0;
        Throwable cause=error;
        for(int depth=0;cause!=null&&depth<10;depth++,cause=cause.getCause())if(cause instanceof SQLException sql){
            if("LOCAL_STATE".equals(category))category="DATABASE";type="SQLException";String state=sql.getSQLState();
            sqlState=state!=null&&state.matches("[A-Z0-9]{5}")?state:"NONE";vendorCode=sql.getErrorCode();break;
        }
        return new Failure(category,type,sqlState,vendorCode);
    }
    public static void importFailure(String project,String operation,String job,Exception error){
        Failure failure=classify(error);
        try{LoggerFactory.getLogger(PlanReviewSyncDiagnostics.class).warn("Plan review import outcome=FAILED; project={}; operation={}; job={}; stage=IMPORT_ASSOCIATION; category={}; exceptionType={}; sqlState={}; vendorCode={}; time={}",id(project),id(operation),id(job),failure.category(),failure.exceptionType(),failure.sqlState(),failure.vendorCode(),System.currentTimeMillis());}
        catch(RuntimeException unavailable){/* Preserve original failure handling. */}
    }
    private static void committed(Runnable work){
        if(TransactionSynchronizationManager.isSynchronizationActive()&&TransactionSynchronizationManager.isActualTransactionActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){work.run();}});
        else work.run();
    }
    private static String id(String value){return value!=null&&value.matches("[A-Za-z0-9_-]{1,64}")?value:"UNAVAILABLE";}
    private static void emit(String outcome,String project,String relation,String generation,long version,int attempts,Failure failure){
        emit(outcome,project,relation,generation,version,attempts,failure,"TARGET_SYNC","COMMITTED");
    }
    private static void emit(String outcome,String project,String relation,String generation,long version,int attempts,Failure failure,String stage,String commit){
        try{
            var logger=LoggerFactory.getLogger(PlanReviewSyncDiagnostics.class);
            String format="Plan review sync outcome={}; project={}; relation={}; generation={}; stage={}; stateCommit={}; version={}; attempts={}; category={}; exceptionType={}; sqlState={}; vendorCode={}; time={}";
            Object[] fields={outcome,id(project),id(relation),id(generation),stage,commit,version,attempts,failure.category(),failure.exceptionType(),failure.sqlState(),failure.vendorCode(),System.currentTimeMillis()};
            if("COMPLETED".equals(outcome))logger.info(format,fields);else logger.warn(format,fields);
        }catch(RuntimeException unavailable){/* Logging must never replace the business result. */}
    }
}
