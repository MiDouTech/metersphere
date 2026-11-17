package io.metersphere.functional.handoff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.metersphere.functional.handoff.HandoffSourceReader.*;
import static io.metersphere.functional.handoff.HandoffStore.*;

/** Commit possible-send and budget before I/O; never hold source/config transaction locks during network I/O. */
@Component
public class HandoffWorker implements DisposableBean {
    private final HandoffStore store;private final HandoffService service;
    private final TransactionTemplate tx;
    private volatile boolean enabled;
    private volatile boolean closing;
    private final Object lifecycle=new Object();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling=new AtomicBoolean(),maintaining=new AtomicBoolean();
    private final AtomicLong saturated=new AtomicLong(),rejected=new AtomicLong(),failures=new AtomicLong(),maintenanceFailures=new AtomicLong();
    private final AtomicReference<Failure> lastFailure=new AtomicReference<>(new Failure("NONE","NONE",0));
    private final AtomicLong preparationFailures=new AtomicLong();
    private final AtomicReference<PreparationFailure> lastPreparationFailure=new AtomicReference<>();
    private final Map<PreparationStage,AtomicLong> stageFailures=new EnumMap<>(PreparationStage.class);
    private final JdbcTemplate pollingJdbc;
    private ThreadPoolExecutor network;
    private Semaphore admission;
    private int parallelism;
    private String lastProject="",scanAfter="";
    @Autowired
    public HandoffWorker(HandoffStore store,HandoffService service,PlatformTransactionManager manager){
        this.store=store;this.service=service;this.tx=new TransactionTemplate(manager);this.tx.setTimeout(5);
        this.pollingJdbc=new JdbcTemplate(Objects.requireNonNull(store.jdbc.getDataSource()));this.pollingJdbc.setQueryTimeout(2);
        for(var stage:PreparationStage.values())stageFailures.put(stage,new AtomicLong());
        configureExecution(4,4);
    }
    /** Bind to the application's existing registry; no new endpoint, remote collector or event-ID metric tags. */
    @Autowired(required=false)
    public void monitor(io.micrometer.core.instrument.MeterRegistry registry){
        io.micrometer.core.instrument.FunctionCounter.builder("metersphere.handoff.preparation.failures",preparationFailures,AtomicLong::doubleValue).register(registry);
        io.micrometer.core.instrument.FunctionCounter.builder("metersphere.handoff.worker.failures",failures,AtomicLong::doubleValue).register(registry);
        io.micrometer.core.instrument.FunctionCounter.builder("metersphere.handoff.maintenance.failures",maintenanceFailures,AtomicLong::doubleValue).register(registry);
        for(var stage:PreparationStage.values())io.micrometer.core.instrument.FunctionCounter.builder("metersphere.handoff.stage.failures",stageFailures.get(stage),AtomicLong::doubleValue).tag("stage",stage.name()).register(registry);
    }
    @Value("${metersphere.hifox-handoff.http-worker-enabled:false}")
    public void setEnabled(boolean value){enabled=value;}
    @Autowired
    public void configureExecution(@Value("${metersphere.hifox-handoff.http-concurrency:4}") int concurrency,
                                   @Value("${metersphere.hifox-handoff.http-queue-capacity:4}") int queueCapacity){
        if(concurrency<1||concurrency>16||queueCapacity<0||queueCapacity>64)throw new IllegalArgumentException("Webhook执行容量无效");
        synchronized(lifecycle){
            if(closing||!pending.isEmpty())throw new IllegalStateException("执行期间不能替换Webhook资源");
            if(network!=null)network.shutdown();parallelism=concurrency;
            BlockingQueue<Runnable> queue=queueCapacity==0?new SynchronousQueue<>():new ArrayBlockingQueue<>(queueCapacity);
            var sequence=new AtomicInteger();
            network=new ThreadPoolExecutor(concurrency,concurrency,0,TimeUnit.MILLISECONDS,queue,
                    task->new Thread(task,"handoff-http-"+sequence.incrementAndGet()),new ThreadPoolExecutor.AbortPolicy());
            admission=new Semaphore(concurrency+queueCapacity);
        }
    }
    /** Dedicated poll thread: bounded reads and submissions only, never waits for POST or source replay. */
    @Scheduled(fixedDelay=1000,scheduler="handoffPollScheduler")
    public void tick(){
        if(!enabled||closing||!polling.compareAndSet(false,true))return;
        try{
            int slots=Math.max(0,parallelism-pending.size());
            if(slots==0){long count=saturated.incrementAndGet();if(count==1||count%60==0)safeWarning("CAPACITY_BUSY");return;}
            long now=System.currentTimeMillis();
            var projects=pollingJdbc.queryForList("SELECT project_id FROM hifox_http_delivery WHERE state IN ('READY','RETRY') AND next_at<=? AND project_id>? GROUP BY project_id ORDER BY project_id LIMIT 32",now,lastProject);
            if(projects.isEmpty()&&!lastProject.isEmpty())projects=pollingJdbc.queryForList("SELECT project_id FROM hifox_http_delivery WHERE state IN ('READY','RETRY') AND next_at<=? GROUP BY project_id ORDER BY project_id LIMIT 32",now);
            if(projects.isEmpty())return;
            var candidates=new LinkedHashMap<String,ArrayDeque<Map<String,Object>>>();
            for(var project:projects){
                String id=text(project,"project_id");
                candidates.put(id,new ArrayDeque<>(pollingJdbc.queryForList("SELECT project_id,business_key FROM hifox_http_delivery WHERE project_id=? AND state IN ('READY','RETRY') AND next_at<=? ORDER BY next_at,create_time,business_key LIMIT ?",id,now,parallelism)));
            }
            boolean progress=true;
            while(slots>0&&progress){
                progress=false;
                for(var entry:candidates.entrySet()){
                    while(!entry.getValue().isEmpty()){
                        var row=entry.getValue().removeFirst();
                        if(enqueue(text(row,"project_id"),text(row,"business_key"))){lastProject=entry.getKey();slots--;progress=true;break;}
                    }
                    if(slots==0)break;
                }
            }
        }catch(RuntimeException unavailable){failures.incrementAndGet();deferred("POLL_DEFERRED",unavailable);}
        finally{polling.set(false);}
    }
    /** Queue contains references only; no attempt, payload freeze, deadline or budget is created here. */
    public boolean enqueue(String project,String key){
        synchronized(lifecycle){
            if(!enabled||closing){rejected.incrementAndGet();return false;}
            if(!pending.add(key))return false;
            if(!admission.tryAcquire()){pending.remove(key);saturated.incrementAndGet();return false;}
            var job=new Job(project,key,admission);
            try{network.execute(job);return true;}
            catch(RejectedExecutionException full){job.release();rejected.incrementAndGet();safeWarning("EXECUTOR_REJECTED");return false;}
        }
    }
    private final class Job implements Runnable {
        private final String project,key;private final Semaphore reservation;private final AtomicBoolean released=new AtomicBoolean();
        private Job(String project,String key,Semaphore reservation){this.project=project;this.key=key;this.reservation=reservation;}
        public void run(){try{if(!closing&&enabled)dispatch(project,key);}catch(RuntimeException unavailable){failures.incrementAndGet();deferred("EXECUTION_DEFERRED",unavailable);}finally{release();}}
        void release(){if(released.compareAndSet(false,true)){pending.remove(key);reservation.release();}}
    }
    public record Statistics(int concurrency,int active,int queued,int pending,long saturated,long rejected,long failures,long maintenanceFailures,Failure lastFailure,boolean accepting,long preparationFailures,PreparationFailure lastPreparationFailure){ }
    public Statistics statistics(){synchronized(lifecycle){return new Statistics(parallelism,network.getActiveCount(),network.getQueue().size(),pending.size(),saturated.get(),rejected.get(),failures.get(),maintenanceFailures.get(),lastFailure.get(),enabled&&!closing,preparationFailures.get(),lastPreparationFailure.get());}}
    public record Failure(String category,String sqlState,int vendorCode){ }
    public enum PreparationStage { CONFIGURATION, SOURCE_CAPTURE, TIME_EVIDENCE, LEGACY_CHECK, PAYLOAD_BUILD, PAYLOAD_SAVE, URL_DECRYPT, TOKEN_DECRYPT, ATTEMPT_INSERT, ATTEMPT_RESERVE }
    public record PreparationFailure(String stage,String eventId,String businessKey,String category,String exceptionType,String sqlState,int vendorCode,long occurredAt){ }
    private void deferred(String operation,RuntimeException error){
        var diagnostic=HandoffDiagnostic.from(error);
        lastFailure.set(new Failure(diagnostic.category(),diagnostic.sqlState(),diagnostic.vendorCode()));
        safeWarning(operation);
        try{org.slf4j.LoggerFactory.getLogger(getClass()).warn("Webhook worker operation={}; category={}; exceptionType={}; sqlState={}; vendorCode={}",operation,diagnostic.category(),diagnostic.exceptionType(),diagnostic.sqlState(),diagnostic.vendorCode());}catch(RuntimeException loggingUnavailable){/* Diagnostics must not replace the original failure. */}
    }
    private void preparationFailed(PreparationStage stage,Map<String,Object> delivery,String key,Map<String,Object> config,RuntimeException error){
        var diagnostic=HandoffDiagnostic.from(error);
        var failure=new PreparationFailure(stage.name(),HandoffDiagnostic.event(text(delivery,"event_id")),HandoffDiagnostic.key(key),diagnostic.category(),diagnostic.exceptionType(),diagnostic.sqlState(),diagnostic.vendorCode(),System.currentTimeMillis());
        failures.incrementAndGet();preparationFailures.incrementAndGet();stageFailures.get(stage).incrementAndGet();lastPreparationFailure.set(failure);
        lastFailure.set(new Failure(diagnostic.category(),diagnostic.sqlState(),diagnostic.vendorCode()));
        // Log only value-checked identifiers, enum stage/classification and numeric revisions. Never log the Throwable.
        try{org.slf4j.LoggerFactory.getLogger(getClass()).warn("Webhook preparation failed event={}; businessKey={}; stage={}; category={}; exceptionType={}; sqlState={}; vendorCode={}; urlRevision={}; credentialRevision={}; time={}",failure.eventId(),failure.businessKey(),failure.stage(),failure.category(),failure.exceptionType(),failure.sqlState(),failure.vendorCode(),number(config,"url_revision"),number(config,"credential_revision"),failure.occurredAt());}catch(RuntimeException loggingUnavailable){/* Failure state and retry policy remain authoritative. */}
    }
    private void safeWarning(String category){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Webhook worker category={}; pending={}; rejected={}",category,pending.size(),rejected.get());}
    /** Separate bounded maintenance loop; it cannot occupy the polling or common scheduling pool. */
    @Scheduled(fixedDelay=1000,scheduler="handoffMaintenanceScheduler")
    public void maintain(){
        if(!enabled||closing||!maintaining.compareAndSet(false,true))return;
        try{recover();replay();}catch(RuntimeException unavailable){maintenanceFailures.incrementAndGet();deferred("MAINTENANCE_DEFERRED",unavailable);}finally{maintaining.set(false);}
    }
    @Override public void destroy(){
        synchronized(lifecycle){
            if(closing)return;closing=true;
            var cancelled=new ArrayList<Runnable>();network.getQueue().drainTo(cancelled);
            for(Runnable task:cancelled)if(task instanceof HandoffWorker.Job job)job.release();
            network.shutdown();
        }
        try{if(!network.awaitTermination(40,TimeUnit.SECONDS)){
            for(Runnable task:network.shutdownNow())if(task instanceof HandoffWorker.Job job)job.release();
            if(!network.awaitTermination(5,TimeUnit.SECONDS))safeWarning("SHUTDOWN_RECOVERY_REQUIRED");
        }}catch(InterruptedException interrupted){for(Runnable task:network.shutdownNow())if(task instanceof HandoffWorker.Job job)job.release();Thread.currentThread().interrupt();safeWarning("SHUTDOWN_INTERRUPTED");}
    }
    /** InnoDB INSERT IGNORE may take a shared duplicate-key lock. Acquire X first to avoid concurrent lock upgrades. */
    private Map<String,Object> lockProject(String project){
        store.jdbc.update("INSERT INTO plan_review_grant(project_id,update_time) VALUES(?,?) ON DUPLICATE KEY UPDATE project_id=project_id",project,System.currentTimeMillis());
        return Map.of();
    }
    /** Persistent completion snapshots are replayable even when the immediate capture hook failed. */
    private void replay(){
        var plans=store.jdbc.queryForList("SELECT id,project_id FROM test_plan WHERE type='TEST_PLAN' AND id>? ORDER BY id LIMIT 4",scanAfter);
        if(plans.isEmpty()){scanAfter="";return;}
        long budget=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(200);
        for(var plan:plans){if(closing||System.nanoTime()>budget)break;scanAfter=text(plan,"id");try{tx.executeWithoutResult(s->{lockProject(text(plan,"project_id"));service.capture(text(plan,"project_id"),text(plan,"id"));});}catch(RuntimeException unavailable){maintenanceFailures.incrementAndGet();deferred("REPLAY_DEFERRED",unavailable);}}
    }
    public void recover(){
        for(var row:store.jdbc.queryForList("SELECT project_id,business_key FROM hifox_http_delivery WHERE state='IN_FLIGHT' AND next_at<? ORDER BY next_at LIMIT 4",System.currentTimeMillis())){
            tx.executeWithoutResult(status->{String project=text(row,"project_id"),key=text(row,"business_key");lockProject(project);var d=store.delivery(project,key);
                if(!"IN_FLIGHT".equals(d.get("state"))||number(d,"next_at")>=System.currentTimeMillis())return;
                finishLocked(project,d,text(d,"active_attempt"),new HandoffHttpTransport.Result(null,"PROCESS_INTERRUPTED",true,true,0));
            });
        }
    }
    public void dispatch(String project,String key){
        if(!enabled||closing)return;
        var call=tx.execute(status->{
            lockProject(project);var d=store.delivery(project,key);var c=d.isEmpty()?Map.<String,Object>of():store.deliveryConfig(d);long now=System.currentTimeMillis();
            if(!enabled||closing)return null;
            if(d.isEmpty()||!Set.of("READY","RETRY").contains(text(d,"state"))||number(d,"next_at")>now||number(d,"attempts_used")>=4)return null;
            if(!key.equals(service.key(project,text(d,"plan_id")))){stop(key,"PAUSED","SOURCE_ENVIRONMENT_CHANGED");return null;}
            if(!truth(c.get("enabled"))||!truth(c.get("external_grant"))){stop(key,"PAUSED",!truth(c.get("enabled"))?"DISABLED":"PG_REVOKED");return null;}
            PreparationStage stage=PreparationStage.CONFIGURATION;
            try{
                service.ready(c);stage=PreparationStage.SOURCE_CAPTURE;service.capture(project,text(d,"plan_id"));d=store.delivery(project,key);
                if(!Set.of("READY","RETRY").contains(text(d,"state")))return null;
                stage=PreparationStage.TIME_EVIDENCE;
                var f=store.jdbc.queryForMap("SELECT * FROM hifox_http_fact WHERE business_key=?",key);
                service.requireTime(f);
                if(!truth(f.get("eligible"))||truth(d.get("source_changed"))||!Objects.equals(f.get("time_revision"),d.get("time_revision"))){stop(key,"SOURCE_CHANGED","SOURCE_CHANGED");return null;}
                stage=PreparationStage.LEGACY_CHECK;
                if(store.legacy(project,text(d,"plan_id"))){stop(key,"FAILED","LEGACY_READ_ONLY");return null;}
                if(number(d,"attempts_used")>0&&number(d,"url_revision")!=number(c,"url_revision")){stop(key,"URL_CHANGED","URL_CHANGED");return null;}
                byte[] payload;
                if(d.get("payload_json")==null){stage=PreparationStage.PAYLOAD_BUILD;payload=service.payload(project,d,f);String json=new String(payload,StandardCharsets.UTF_8);
                    stage=PreparationStage.PAYLOAD_SAVE;
                    store.jdbc.update("UPDATE hifox_http_delivery SET payload_json=?,payload_hash=?,url_revision=? WHERE business_key=?",json,HandoffPolicy.hash(json),number(c,"url_revision"),key);
                }else payload=text(d,"payload_json").getBytes(StandardCharsets.UTF_8);
                stage=PreparationStage.URL_DECRYPT;
                String url=service.openSecret(store.configurationScope(d),"url",text(c,"url_secret"));
                stage=PreparationStage.TOKEN_DECRYPT;
                String token="BEARER".equals(c.get("auth_mode"))?service.openSecret(store.configurationScope(d),"token",text(c,"token_secret")):null;
                if(!enabled||closing)return null;
                now=System.currentTimeMillis();
                String id=UUID.randomUUID().toString();long sequence=number(d,"attempts_used")+1;
                stage=PreparationStage.ATTEMPT_INSERT;
                store.jdbc.update("INSERT INTO hifox_http_attempt(attempt_id,business_key,sequence_no,started_at,credential_revision) VALUES(?,?,?,?,?)",id,key,sequence,now,number(c,"credential_revision"));
                stage=PreparationStage.ATTEMPT_RESERVE;
                store.jdbc.update("UPDATE hifox_http_delivery SET state='IN_FLIGHT',attempts_used=?,active_attempt=?,next_at=?,reason=NULL,revision=revision+1,update_time=? WHERE business_key=?",sequence,id,now+35_000,now,key);
                return new Call(id,url,token,payload);
            }catch(RuntimeException unavailable){preparationFailed(stage,d,key,c,unavailable);stop(key,"FAILED",unavailable instanceof io.metersphere.functional.planreview.PlanReviewFailure f?f.code():"CONFIG_OR_SOURCE_UNAVAILABLE");return null;}
        });
        if(call==null)return;
        // Reservation is the audited start boundary. Revocation after it cannot retract an already started attempt.
        HandoffHttpTransport.Result result;
        try{result=service.postWebhook(call.url(),call.token(),call.payload());}
        catch(RuntimeException unavailable){result=new HandoffHttpTransport.Result(null,"NETWORK_ERROR",true,true,0);}
        var outcome=result;
        tx.executeWithoutResult(status->{lockProject(project);var d=store.delivery(project,key);finishLocked(project,d,call.id(),outcome);});
    }
    private record Call(String id,String url,String token,byte[] payload){@Override public String toString(){return "Call[REDACTED]";}}
    private void stop(String key,String state,String reason){
        var d=store.jdbc.queryForMap("SELECT project_id,state,reason,revision FROM hifox_http_delivery WHERE business_key=?",key);
        if(state.equals(d.get("state"))&&reason.equals(d.get("reason")))return;
        store.jdbc.update("UPDATE hifox_http_delivery SET state=?,reason=?,revision=revision+1,update_time=? WHERE business_key=?",state,reason,System.currentTimeMillis(),key);
        store.audit(text(d,"project_id"),key,"SYSTEM",("PAUSED".equals(state)?"PAUSE_":"STOP_")+reason,number(d,"revision")+1);
    }
    static long backoff(long used){return used<=1?10_000:used==2?30_000:60_000;}
    void finishLocked(String project,Map<String,Object> d,String id,HandoffHttpTransport.Result result){
        long now=System.currentTimeMillis();String key=text(d,"business_key");
        boolean success=result.status()!=null&&result.status()>=200&&result.status()<=299;
        var attempt=store.jdbc.queryForList("SELECT * FROM hifox_http_attempt WHERE attempt_id=? AND business_key=? FOR UPDATE",id,key);
        if(attempt.size()!=1)return;
        // A late failure cannot overwrite a completed result; late 2xx remains an HTTP fact for this attempt.
        if(attempt.getFirst().get("ended_at")!=null&&!success)return;
        store.jdbc.update("UPDATE hifox_http_attempt SET ended_at=?,http_status=?,error_code=?,result_unknown=? WHERE attempt_id=?",now,result.status(),result.error(),result.unknown(),id);
        if(success){
            store.jdbc.update("UPDATE hifox_http_delivery SET state='SUCCESS',http_status=?,reason=NULL,had_unknown=CASE WHEN ? THEN 1 ELSE had_unknown END,revision=revision+1,update_time=? WHERE business_key=?",result.status(),result.unknown(),now,key);return;
        }
        if(!id.equals(d.get("active_attempt"))||"SUCCESS".equals(d.get("state")))return;
        var c=store.deliveryConfig(d);String state,reason=result.error();
        if(truth(d.get("source_changed"))){state="SOURCE_CHANGED";reason="SOURCE_CHANGED";}
        else if(number(d,"url_revision")!=number(c,"url_revision")){state="URL_CHANGED";reason="URL_CHANGED";}
        else if(!truth(c.get("enabled"))||!truth(c.get("external_grant"))||Set.of("DISABLED","PG_REVOKED","SOURCE_ACCESS_UNAVAILABLE").contains(text(d,"reason"))){state="PAUSED";reason=!truth(c.get("enabled"))?"DISABLED":"PG_REVOKED";}
        else if(number(d,"attempts_used")>=4){state=result.unknown()||truth(d.get("had_unknown"))?"UNKNOWN_STOPPED":"FAILED";}
        else state=result.retryable()?"RETRY":"FAILED";
        long next=Math.max(now+backoff(number(d,"attempts_used")),result.retryAfter());
        store.jdbc.update("UPDATE hifox_http_delivery SET state=?,reason=?,http_status=?,next_at=?,had_unknown=CASE WHEN ? THEN 1 ELSE had_unknown END,revision=revision+1,update_time=? WHERE business_key=?",state,reason,result.status(),next,result.unknown(),now,key);
    }
}
