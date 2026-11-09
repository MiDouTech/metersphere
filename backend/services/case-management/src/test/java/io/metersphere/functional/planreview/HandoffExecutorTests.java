package io.metersphere.functional.planreview;

import io.metersphere.functional.handoff.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class HandoffExecutorTests {
    HandoffPersistenceTests t;
    @BeforeEach void setup()throws Exception{t=new HandoffPersistenceTests();t.setup();}
    @AfterEach void close(){t.closeOwnedInMemoryDatabase();}
    void prepare(int plans){HandoffWorkerTestSources.prepare(t,plans,false,t.target,"https://receiver.example.test/hook");t.worker.configureExecution(1,1);}
    String key(String plan){return t.jdbc.queryForObject("SELECT business_key FROM hifox_http_delivery WHERE plan_id=?",String.class,plan);}
    int used(String plan){return t.jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery WHERE plan_id=?",Integer.class,plan);}
    void await(BooleanSupplier condition){long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while(!condition.getAsBoolean()&&System.nanoTime()<end){try{Thread.sleep(10);}catch(InterruptedException e){throw new RuntimeException(e);}}assertTrue(condition.getAsBoolean());}
    CountDownLatch blockFirst(){var gate=new CountDownLatch(1);t.target.during=()->{if(t.target.calls.get()==1)try{assertTrue(gate.await(6,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();}};return gate;}
    @Test void queueSaturationDuplicateAndExpiredQueuedClockDoNotConsumeBudget(){
        prepare(3);var gate=blockFirst();
        try{
            assertTrue(t.worker.enqueue("project",key("plan")));await(()->t.target.calls.get()==1);
            assertTrue(t.worker.enqueue("project",key("plan2")));assertFalse(t.worker.enqueue("project",key("plan2")));assertFalse(t.worker.enqueue("project",key("plan3")));
            assertEquals(2,t.worker.statistics().pending());assertEquals(1,t.worker.statistics().queued());assertTrue(t.worker.statistics().saturated()>0||t.worker.statistics().rejected()>0);
            assertEquals(0,used("plan2"));assertEquals(0,used("plan3"));assertNull(t.jdbc.queryForObject("SELECT payload_json FROM hifox_http_delivery WHERE plan_id='plan2'",String.class));
            t.jdbc.update("UPDATE hifox_http_delivery SET next_at=0 WHERE plan_id='plan2'");t.worker.recover();assertEquals(0,used("plan2"));assertEquals(1,t.count("hifox_http_attempt"));
        }finally{gate.countDown();}
        t.awaitIdle(t.worker);assertEquals(2,t.target.calls.get());assertEquals(1,used("plan2"));assertEquals(0,used("plan3"));
    }
    @ParameterizedTest @ValueSource(strings={"PG","URL","SOURCE","DISABLED"})
    void queuedWorkRechecksEveryRevocationBeforeAnyAttempt(String change){
        prepare(2);var gate=blockFirst();
        try{
            assertTrue(t.worker.enqueue("project",key("plan")));await(()->t.target.calls.get()==1);assertTrue(t.worker.enqueue("project",key("plan2")));
            switch(change){
                case "PG" -> t.run(()->t.service.grant(HandoffPersistenceTests.PLATFORM,t.revision(),false,"actor"));
                case "URL" -> t.save("https://new-receiver.example.test/hook","NONE","");
                case "SOURCE" -> {t.jdbc.update("UPDATE case_review_functional_case SET status='UN_PASS' WHERE review_id='review2'");t.tx.executeWithoutResult(s->t.service.capture("project","plan2",true));}
                case "DISABLED" -> t.run(()->t.service.disable(HandoffPersistenceTests.PLATFORM,t.revision(),"actor"));
                default -> throw new IllegalArgumentException();
            }
            assertEquals(0,used("plan2"));assertNull(t.jdbc.queryForObject("SELECT payload_json FROM hifox_http_delivery WHERE plan_id='plan2'",String.class));
        }finally{gate.countDown();}
        t.awaitIdle(t.worker);assertEquals(1,t.target.calls.get());assertEquals(0,used("plan2"));assertEquals(1,t.count("hifox_http_attempt"));
    }
    @Test void shutdownRejectsNewWorkDropsOnlyUnstartedQueueAndPersistsInFlightResult()throws Exception{
        prepare(2);var gate=blockFirst();Thread closing=null;
        try{
            assertTrue(t.worker.enqueue("project",key("plan")));await(()->t.target.calls.get()==1);assertTrue(t.worker.enqueue("project",key("plan2")));
            closing=Thread.ofVirtual().start(t.worker::destroy);await(()->!t.worker.statistics().accepting());
            assertFalse(t.worker.enqueue("project",key("plan2")));assertEquals(0,used("plan2"));assertEquals(0,t.worker.statistics().queued());
        }finally{gate.countDown();if(closing!=null)closing.join(6000);}
        assertNotNull(closing);assertFalse(closing.isAlive());assertEquals(0,t.worker.statistics().pending());assertEquals(1,t.target.calls.get());assertEquals("SUCCESS",t.jdbc.queryForObject("SELECT state FROM hifox_http_delivery WHERE plan_id='plan'",String.class));assertEquals(0,used("plan2"));
    }
    @Test void twoWorkerInstancesAndOverlappingPollsClaimOneDatabaseAttempt()throws Exception{
        t.enable();t.complete();var store=new HandoffStore(t.jdbc,new PlanReviewPersistence(t.jdbc));var other=new HandoffWorker(store,t.service,t.tx.getTransactionManager());other.setEnabled(true);var gate=blockFirst();
        try{
            var start=new CountDownLatch(1);var a=Thread.ofVirtual().start(()->{try{start.await();t.worker.enqueue("project",t.key());}catch(InterruptedException e){throw new RuntimeException(e);}});var b=Thread.ofVirtual().start(()->{try{start.await();other.enqueue("project",t.key());}catch(InterruptedException e){throw new RuntimeException(e);}});start.countDown();a.join(3000);b.join(3000);
            await(()->t.target.calls.get()==1);await(()->t.worker.statistics().pending()+other.statistics().pending()==1);
            for(int n=0;n<5;n++){t.worker.tick();other.tick();}
            assertEquals(1,t.target.calls.get());assertEquals(1,used("plan"));assertEquals(1,t.count("hifox_http_attempt"));
        }finally{gate.countDown();t.awaitIdle(t.worker);t.awaitIdle(other);other.destroy();}
        assertEquals("SUCCESS",t.state());
    }
    @Test void futureRetryCannotBeStartedByQueueAdmissionOrPoll(){
        t.enable();t.complete();t.jdbc.update("UPDATE hifox_http_delivery SET state='RETRY',next_at=?",System.currentTimeMillis()+60000);
        assertTrue(t.worker.enqueue("project",t.key()));t.awaitIdle(t.worker);t.worker.tick();t.awaitIdle(t.worker);assertEquals(0,t.target.calls.get());assertEquals(0,used("plan"));assertEquals(0,t.count("hifox_http_attempt"));
    }
    @Test void pollReturnsWhilePostWaitsAndResourcesRemainBounded(){
        prepare(3);t.worker.configureExecution(2,1);var gate=new CountDownLatch(1);t.target.during=()->{try{assertTrue(gate.await(6,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();}};
        try{
            long before=System.nanoTime();t.worker.tick();assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<1000);await(()->t.target.calls.get()==2);t.worker.tick();
            assertEquals(2,t.worker.statistics().pending());assertEquals(2,t.worker.statistics().active());assertTrue(t.worker.statistics().queued()<=1);assertEquals(2,t.count("hifox_http_attempt"));assertEquals(0,used("plan3"));
        }catch(AssertionError failed){System.out.println("BoundedProbe calls="+t.target.calls.get()+" stats="+t.worker.statistics()+" rows="+t.jdbc.queryForList("SELECT plan_id,state,reason,attempts_used FROM hifox_http_delivery ORDER BY plan_id"));throw failed;}
        finally{gate.countDown();}
        t.awaitIdle(t.worker);
    }
    @Test void interruptedExecutionAndLateSuccessKeepOriginalBudgetAndEvent()throws Exception{
        t.enable();t.complete();var gate=blockFirst();
        try{
            t.worker.enqueue("project",t.key());await(()->t.target.calls.get()==1);String event=t.jdbc.queryForObject("SELECT event_id FROM hifox_http_delivery",String.class);
            t.jdbc.update("UPDATE hifox_http_delivery SET next_at=0");t.worker.recover();assertEquals(1,used("plan"));assertEquals("RETRY",t.state());
            gate.countDown();t.awaitIdle(t.worker);assertEquals("SUCCESS",t.state());assertEquals(event,t.jdbc.queryForObject("SELECT event_id FROM hifox_http_delivery",String.class));assertEquals(1,used("plan"));assertTrue(t.jdbc.queryForObject("SELECT had_unknown FROM hifox_http_delivery",Boolean.class));
        }finally{gate.countDown();}
    }
    @Test void dispatcherConfigurationIsDedicatedAndInvalidUnboundedSettingsReject()throws Exception{
        assertEquals("handoffPollScheduler",HandoffWorker.class.getMethod("tick").getAnnotation(Scheduled.class).scheduler());
        assertEquals("handoffMaintenanceScheduler",HandoffWorker.class.getMethod("maintain").getAnnotation(Scheduled.class).scheduler());
        assertThrows(IllegalArgumentException.class,()->t.worker.configureExecution(0,1));assertThrows(IllegalArgumentException.class,()->t.worker.configureExecution(17,1));assertThrows(IllegalArgumentException.class,()->t.worker.configureExecution(4,65));
    }
    @Test void actualThreadInterruptionPreservesPossibleAttemptAndRecoveryBudget(){
        t.enable();t.complete();var executing=new AtomicReference<Thread>();var entered=new CountDownLatch(1);var never=new CountDownLatch(1);
        t.target.result=new HandoffHttpTransport.Result(null,"INTERRUPTED",true,true,0);
        t.target.during=()->{executing.set(Thread.currentThread());entered.countDown();try{never.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}};
        assertTrue(t.worker.enqueue("project",t.key()));await(()->entered.getCount()==0);executing.get().interrupt();t.awaitIdle(t.worker);
        assertEquals(1,used("plan"));assertEquals(1,t.target.calls.get());assertTrue(Set.of("RETRY","IN_FLIGHT").contains(t.state()));
        if("IN_FLIGHT".equals(t.state())){t.jdbc.update("UPDATE hifox_http_delivery SET next_at=0");t.worker.recover();}
        assertEquals("RETRY",t.state());assertTrue(t.jdbc.queryForObject("SELECT had_unknown FROM hifox_http_delivery",Boolean.class));assertEquals(1,t.count("hifox_http_attempt"));
        assertNotNull(t.jdbc.queryForObject("SELECT payload_hash FROM hifox_http_delivery",String.class));t.worker.tick();t.awaitIdle(t.worker);assertEquals(1,t.target.calls.get());
    }
    @Test void slowMaintenanceLockDoesNotHoldPollingOrAnotherProjectsDispatch()throws Exception{
        HandoffWorkerTestSources.prepare(t,2,true,t.target,"https://receiver.example.test/hook");var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        var owner=Thread.ofVirtual().start(()->t.tx.executeWithoutResult(s->{new PlanReviewPersistence(t.jdbc).lockProject("project");held.countDown();try{assertTrue(release.await(6,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}));
        Thread maintenance=null;
        try{
            await(()->held.getCount()==0);maintenance=Thread.ofVirtual().start(t.worker::maintain);long before=System.nanoTime();t.worker.tick();assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<1000);
            await(()->"SUCCESS".equals(t.jdbc.queryForObject("SELECT state FROM hifox_http_delivery WHERE plan_id='plan2'",String.class)));assertEquals(0,used("plan"));assertEquals(1,t.target.calls.get());
        }finally{release.countDown();owner.join(6000);if(maintenance!=null)maintenance.join(6000);t.awaitIdle(t.worker);}
        assertFalse(owner.isAlive());assertNotNull(maintenance);assertFalse(maintenance.isAlive());
    }

}
