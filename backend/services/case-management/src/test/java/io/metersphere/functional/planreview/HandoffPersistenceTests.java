package io.metersphere.functional.planreview;

import io.metersphere.functional.handoff.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class HandoffPersistenceTests {
    static final String PLATFORM=io.metersphere.system.service.PlatformNotificationScope.ID;
    JdbcTemplate jdbc;TransactionTemplate tx;HandoffService service;HandoffWorker worker;
    PlanReviewLifecycleService lifecycle;FakeTarget target;
    static class FakeTarget implements HandoffWebhookTarget {
        AtomicInteger calls=new AtomicInteger();List<byte[]> payloads=new java.util.concurrent.CopyOnWriteArrayList<>();List<String> tokens=new java.util.concurrent.CopyOnWriteArrayList<>();List<String> urls=new java.util.concurrent.CopyOnWriteArrayList<>();
        HandoffHttpTransport.Result result=new HandoffHttpTransport.Result(204,null,false,false,0);
        Runnable during=()->{};
        public void validate(String url){HandoffHttpTransport.syntax(url);}
        public HandoffHttpTransport.Result post(String url,String token,byte[] payload){calls.incrementAndGet();payloads.add(payload.clone());tokens.add(token);urls.add(url);during.run();return result;}
    }
    @BeforeEach void setup()throws Exception{
        var db=PlanReviewTestDatabase.create();jdbc=db.jdbc();tx=db.tx();
        String schema=Files.readString(Path.of(System.getProperty("plan.review.repo.root"),"backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_145__hifox_handoff_legacy.sql"));
        schema=schema.replaceAll("(?m)^--.*$","");
        if(System.getProperty("plan.review.mysql.config")==null)schema=schema.replace(" COLLATE utf8mb4_bin","").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
        for(String statement:schema.split(";"))if(!statement.isBlank())jdbc.execute(statement);
        String httpSchema=Files.readString(Path.of(System.getProperty("plan.review.repo.root"),"backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_146__handoff_http_v17.sql"));
        httpSchema=httpSchema.replaceAll("(?m)^--.*$","");
        if(System.getProperty("plan.review.mysql.config")==null)httpSchema=httpSchema.replace(" COLLATE utf8mb4_bin","").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
        for(String statement:httpSchema.split(";"))if(!statement.isBlank())jdbc.execute(statement);
        jdbc.execute("ALTER TABLE hifox_http_delivery ADD COLUMN config_scope VARCHAR(50) NULL");
        jdbc.execute("CREATE TABLE user(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");jdbc.execute("CREATE TABLE project(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN,name VARCHAR(255))");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50),type VARCHAR(32),enabled BOOLEAN)");jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('actor',true,false)");jdbc.update("INSERT INTO project VALUES('project',true,false,'Test project')");jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',true)");jdbc.update("INSERT INTO user_role_relation VALUES('actor','admin','system')");
        jdbc.execute("CREATE TABLE notification(id BIGINT AUTO_INCREMENT PRIMARY KEY,type VARCHAR(40),receiver VARCHAR(50),subject VARCHAR(200),status VARCHAR(30),create_time BIGINT,operator VARCHAR(50),operation VARCHAR(50),resource_id VARCHAR(50),project_id VARCHAR(50),organization_id VARCHAR(50),resource_type VARCHAR(50),resource_name VARCHAR(255),content LONGTEXT)");
        var authorization=new PlanReviewAuthorization(jdbc);var states=new PlanReviewPlanStateResolver(List.of());
        var app=new PlanReviewApplicationService(jdbc,db.store(),authorization,db.manager(),states);
        run(()->app.grant("project",0L,true,"actor"));
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('ma','plan','a'),('mb','plan','b')");
        var preview=run(()->app.preview("plan","review","PAIR_EXISTING","actor"));var pair=run(()->app.confirm((String)preview.get("previewId"),"pair",true,"actor"));
        tx.executeWithoutResult(s->app.synchronize((String)pair.get("relationId"),(String)pair.get("generation")));
        lifecycle=new PlanReviewLifecycleService(jdbc,db.store(),authorization,states);
        target=new FakeTarget();var store=new HandoffStore(jdbc,db.store());var auth=new HandoffAuthorization(jdbc,authorization);
        service=new HandoffService(store,new HandoffSourceReader(jdbc,states,authorization,new PlanReviewFrozenReader(jdbc)),auth,db.store(),target,new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32])),"isolated-test");
        service.setSourceBaseUrl("https://msp.example.test");
        worker=new HandoffWorker(store,service,db.manager());worker.setEnabled(true);
        run(()->service.saveConfig(PLATFORM,0,new HandoffConfiguration("https://receiver.example.test/hook?secret=example","NONE","",false),"actor"));
        run(()->service.grant(PLATFORM,1,true,"actor"));
    }
    <T>T run(Supplier<T> work){return tx.execute(s->work.get());}
    @AfterEach void closeOwnedInMemoryDatabase(){
        if(worker!=null)worker.destroy();
        if(jdbc!=null&&System.getProperty("plan.review.mysql.config")==null)jdbc.execute("SHUTDOWN");
    }
    void enable(){var p=run(()->service.preview(PLATFORM,"","ENABLE",false,"actor"));run(()->service.confirm(PLATFORM,(String)p.get("previewId"),true,"actor"));}
    void complete(){jdbc.update("UPDATE case_review_functional_case SET status='PASS'");var p=run(()->lifecycle.preview("review","review","COMPLETE","actor"));run(()->lifecycle.confirm((String)p.get("previewId"),true,"actor"));tx.executeWithoutResult(s->service.capture("project","plan",true));}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}

    String key(){return jdbc.queryForObject("SELECT business_key FROM hifox_http_delivery",String.class);}
    @ParameterizedTest @ValueSource(strings={"NONE","BEARER"})
    void transactionalServiceProxyDecryptsAndPostsWithoutHoldingTransaction(String mode){
        if("BEARER".equals(mode))save("https://receiver.example.test/hook?secret=example",mode,"test-token");
        useTransactionalServiceProxy();
        target.during=()->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("IN_FLIGHT",state());
            assertEquals(1,count("hifox_http_attempt"));
        };
        enable();complete();dispatch();
        assertEquals("SUCCESS",state());assertEquals(1,target.calls.get());
        assertEquals("https://receiver.example.test/hook?secret=example",target.urls.get(0));
        assertEquals("BEARER".equals(mode)?"test-token":null,target.tokens.get(0));
        assertEquals(1,jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class));
    }
    void useTransactionalServiceProxy(){
        var factory=new org.springframework.aop.framework.ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        service=(HandoffService)factory.getProxy();
        worker.destroy();
        var locks=new PlanReviewPersistence(jdbc);
        worker=new HandoffWorker(new HandoffStore(jdbc,locks),service,tx.getTransactionManager());worker.setEnabled(true);
    }
    @Test void statisticsCompletionAndExistingSnapshotUseTheSameOpinionFingerprint(){
        enable();
        jdbc.update("UPDATE case_review_functional_case SET id=CASE case_id WHEN 'a' THEN 'z-member' ELSE 'a-member' END,status='PASS'");
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status,content) VALUES('opinion','review','a','PASS',?)", "中文意见🙂".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var states=new PlanReviewPlanStateResolver(List.of());var locks=new PlanReviewPersistence(jdbc);
        var app=new PlanReviewApplicationService(jdbc,locks,new PlanReviewAuthorization(jdbc),tx.getTransactionManager(),states);
        run(()->app.refreshOpinionStatistics("review"));
        // Preserve and validate the actual frozen bytes produced by the statistics path.
        byte[] before=jdbc.queryForObject("SELECT snapshot FROM plan_review_frozen_snapshot WHERE object_side='review'",byte[].class);
        run(()->{service.capture("project","plan",true);return null;});
        assertEquals(1,count("hifox_http_delivery"),"unchanged passed review must be eligible after statistics completion");
        assertArrayEquals(before,jdbc.queryForObject("SELECT snapshot FROM plan_review_frozen_snapshot WHERE object_side='review'",byte[].class));
        jdbc.update("UPDATE case_review_history SET content=? WHERE id='opinion'", "改动意见".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        dispatch();assertEquals(0,target.calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"content","deleted","abandoned","status"})
    void legacyApplicationSnapshotsRemainReadableButOpinionMutationsAreRejected(String field){
        enable();complete();
        jdbc.update("UPDATE case_review_functional_case SET id=CASE case_id WHEN 'a' THEN 'z-member' ELSE 'a-member' END");
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status,content) VALUES('opinion','review','a','PASS',?)",new byte[]{0,1,-1,32});
        String legacy=HandoffPolicy.hash(io.metersphere.sdk.util.JSON.toJSONString(List.of(
                jdbc.queryForList("SELECT id,case_id,status,update_time FROM case_review_functional_case WHERE review_id=? ORDER BY case_id", "review"),
                jdbc.queryForList("SELECT id,case_id,status,deleted,abandoned,content body FROM case_review_history WHERE review_id=? ORDER BY id", "review"))));
        jdbc.update("UPDATE plan_review_frozen_snapshot SET target_fingerprint=? WHERE object_side='review'",legacy);
        var reader=new HandoffSourceReader(jdbc,new PlanReviewPlanStateResolver(List.of()),new PlanReviewAuthorization(jdbc),new PlanReviewFrozenReader(jdbc));
        assertTrue(HandoffPolicy.evaluate(run(()->reader.read("project","plan")).snapshot()).eligible());
        switch(field){
            case "content" -> jdbc.update("UPDATE case_review_history SET content=?",new byte[]{1,2,3});
            case "deleted" -> jdbc.update("UPDATE case_review_history SET deleted=1");
            case "abandoned" -> jdbc.update("UPDATE case_review_history SET abandoned=1");
            case "status" -> jdbc.update("UPDATE case_review_history SET status='FAIL'");
        }
        assertEquals("OPINIONS_CHANGED",HandoffPolicy.evaluate(run(()->reader.read("project","plan")).snapshot()).reason());
        assertEquals(legacy,jdbc.queryForObject("SELECT target_fingerprint FROM plan_review_frozen_snapshot WHERE object_side='review'",String.class));
    }
    String state(){return jdbc.queryForObject("SELECT state FROM hifox_http_delivery",String.class);}
    void dispatch(){worker.dispatch("project",key());}
    void tickWorker(){worker.maintain();worker.tick();awaitIdle(worker);}
    void awaitIdle(HandoffWorker target){long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);while(target.statistics().pending()>0&&System.nanoTime()<end){try{Thread.sleep(10);}catch(InterruptedException e){throw new RuntimeException(e);}}assertEquals(0,target.statistics().pending());}
    void due(){jdbc.update("UPDATE hifox_http_delivery SET next_at=0");}
    long revision(){return jdbc.queryForObject("SELECT revision FROM hifox_http_config WHERE project_id=?",Long.class,PLATFORM);}
    Map<String,Object> preview(String action){return run(()->service.preview(action.equals("ENABLE")?PLATFORM:"project",action.equals("ENABLE")?"":"plan",action,false,"actor"));}
    Map<String,Object> confirm(Map<String,Object> p){return run(()->service.confirm("ENABLE".equals(p.get("action"))?PLATFORM:"project",(String)p.get("previewId"),true,"actor"));}
    void save(String url,String mode,String token){run(()->service.saveConfig(PLATFORM,revision(),new HandoffConfiguration(url,mode,token,true),"actor"));}
    @Test void platformReplacementCannotRetargetAnExistingProjectDelivery() {
        enable();complete();
        String oldUrl="https://legacy.example.test/old";
        var secrets=new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32]));
        jdbc.update("INSERT INTO hifox_http_config(project_id,enabled,external_grant,url_secret,url_revision,update_time) VALUES('project',1,1,?,1,?)",
                secrets.seal("project","url",oldUrl),System.currentTimeMillis());
        jdbc.update("UPDATE hifox_http_delivery SET config_scope=NULL");
        save("https://platform.example.test/new","NONE","");
        dispatch();
        assertEquals(List.of(oldUrl),target.urls);
        assertEquals("SUCCESS",state());
        assertNull(jdbc.queryForObject("SELECT config_scope FROM hifox_http_delivery",String.class));
        assertThrows(io.metersphere.sdk.exception.MSException.class,()->run(()->service.saveConfig("project",0,new HandoffConfiguration("https://other.example.test","NONE","",false),"actor")));
    }
    @Test void newDeliveryUsesPlatformCiphertextAndKeepsBusinessSource() {
        enable();complete();dispatch();
        assertEquals(PLATFORM,jdbc.queryForObject("SELECT config_scope FROM hifox_http_delivery",String.class));
        assertEquals("project",jdbc.queryForObject("SELECT project_id FROM hifox_http_delivery",String.class));
        assertEquals("project",jdbc.queryForObject("SELECT project_id FROM hifox_http_fact",String.class));
        assertEquals(List.of("https://receiver.example.test/hook?secret=example"),target.urls);
    }
    @Test void systemNotificationPermissionDoesNotInheritProjectAdministratorBypass() {
        var scope=new io.metersphere.system.service.PlatformNotificationScope(jdbc);
        String read="SYSTEM_CONFIG_WECOM_BOT:READ",write="SYSTEM_CONFIG_WECOM_BOT:UPDATE";
        jdbc.update("INSERT INTO user VALUES('project-admin',1,0)");
        jdbc.update("INSERT INTO user_role VALUES('project_admin','PROJECT',1),('notification-reader','SYSTEM',1)");
        jdbc.update("INSERT INTO user_role_relation VALUES('project-admin','project_admin','project')");
        assertThrows(org.apache.shiro.authz.UnauthorizedException.class,()->scope.requireSystemPermissions("project-admin",new String[]{read},true));
        jdbc.update("INSERT INTO user_role_relation VALUES('project-admin','notification-reader','system')");
        jdbc.update("INSERT INTO user_role_permission VALUES('notification-reader',?)",read);
        scope.requireSystemPermissions("project-admin",new String[]{read},true);
        assertThrows(org.apache.shiro.authz.UnauthorizedException.class,()->scope.requireSystemPermissions("project-admin",new String[]{write},true));
        jdbc.update("UPDATE user SET enable=0 WHERE id='project-admin'");
        assertThrows(org.apache.shiro.authz.UnauthorizedException.class,()->scope.requireSystemPermissions("project-admin",new String[]{read},true));
    }
    @ParameterizedTest @ValueSource(ints={200,202,204,299}) void all2xxStopWithoutRemoteBusinessProof(int code){
        enable();complete();target.result=new HandoffHttpTransport.Result(code,null,false,false,0);dispatch();tickWorker();
        assertEquals("SUCCESS",state());assertEquals(1,target.calls.get());assertEquals(code,jdbc.queryForObject("SELECT http_status FROM hifox_http_delivery",Integer.class));assertEquals(0,count("hifox_handoff_notice"));
    }
    @Test void secretConfigurationIsEncryptedAndNeverReadBack(){
        save("https://receiver.example.test/hook?secret=example","BEARER","test-token");
        String wire=service.config(PLATFORM,"actor").toString();assertFalse(wire.contains("test-token"));assertFalse(wire.contains("secret=example"));
        assertFalse(jdbc.queryForObject("SELECT url_secret FROM hifox_http_config",String.class).contains("https"));assertFalse(jdbc.queryForObject("SELECT token_secret FROM hifox_http_config",String.class).contains("test-token"));assertEquals(0,target.calls.get());
    }
    @Test void fixedPayloadAndPlanUniqueness(){enable();complete();dispatch();tx.executeWithoutResult(s->service.capture("project","plan",true));tickWorker();assertEquals(1,target.calls.get());assertEquals(1,count("hifox_http_delivery"));String body=new String(target.payloads.getFirst(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(body.contains("functional_test_plan.review_passed"));assertTrue(body.contains("occurred_at"));assertTrue(body.contains("Test project"));assertFalse(body.contains("receiver.example"));assertFalse(body.contains("owner"));assertFalse(body.contains("credential"));}
    @ParameterizedTest @ValueSource(ints={408,429,500,503}) void retriesUseOneEventSameBytesFourTotal(int code){
        enable();complete();target.result=new HandoffHttpTransport.Result(code,"HTTP_"+code,true,false,0);
        for(int n=1;n<=4;n++){due();dispatch();assertEquals(n,target.calls.get());assertEquals(n,jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class));}
        due();dispatch();assertEquals(4,target.calls.get());assertEquals("FAILED",state());for(byte[] bytes:target.payloads)assertArrayEquals(target.payloads.getFirst(),bytes);
        assertThrows(PlanReviewFailure.class,()->preview("RESUME"));
    }
    @Test void timeoutUnknownRetriesThenStopsAtFour(){enable();complete();target.result=new HandoffHttpTransport.Result(null,"TIMEOUT",true,true,0);for(int n=0;n<4;n++){due();dispatch();}assertEquals("UNKNOWN_STOPPED",state());assertTrue(jdbc.queryForObject("SELECT had_unknown FROM hifox_http_delivery",Boolean.class));assertEquals(4,target.calls.get());}
    @ParameterizedTest @ValueSource(ints={301,302,307,308,400,401,403,404}) void permanentErrorsStopWithoutAutomaticRetry(int code){enable();complete();target.result=new HandoffHttpTransport.Result(code,"HTTP_"+code,false,false,0);dispatch();due();dispatch();assertEquals(1,target.calls.get());assertEquals("FAILED",state());}
    @Test void noEarlyRetryAndRetryAfterWins(){enable();complete();long later=System.currentTimeMillis()+120000;target.result=new HandoffHttpTransport.Result(429,"HTTP_429",true,false,later);dispatch();assertTrue(jdbc.queryForObject("SELECT next_at FROM hifox_http_delivery",Long.class)>=later);dispatch();assertEquals(1,target.calls.get());}
    @Test void tokenRotationKeepsFrozenBytesAndBudget(){enable();complete();target.result=new HandoffHttpTransport.Result(401,"HTTP_401",false,false,0);dispatch();save("","BEARER","new-token");confirm(preview("RESUME"));target.result=new HandoffHttpTransport.Result(204,null,false,false,0);dispatch();assertEquals(2,target.calls.get());assertArrayEquals(target.payloads.get(0),target.payloads.get(1));assertEquals("new-token",target.tokens.get(1));}
    @Test void urlReplacementStopsOldAttemptsAndDoesNotRetarget(){enable();complete();target.result=new HandoffHttpTransport.Result(null,"TIMEOUT",true,true,0);dispatch();save("https://other.example.test/hook","NONE","");due();dispatch();assertEquals("URL_CHANGED",state());assertEquals(1,target.calls.get());assertThrows(PlanReviewFailure.class,()->preview("RESUME"));}
    @Test void revokeAndReenableNeverUnlockPausedRecord(){enable();complete();run(()->service.grant(PLATFORM,revision(),false,"actor"));dispatch();assertEquals(0,target.calls.get());run(()->service.grant(PLATFORM,revision(),true,"actor"));dispatch();assertEquals(0,target.calls.get());confirm(preview("RESUME"));dispatch();assertEquals(1,target.calls.get());}
    @Test void disableInFlightPreservesResultAndStopsNext(){enable();complete();target.result=new HandoffHttpTransport.Result(null,"TIMEOUT",true,true,0);target.during=()->run(()->service.disable(PLATFORM,revision(),"actor"));dispatch();assertEquals("PAUSED",state());due();dispatch();assertEquals(1,target.calls.get());}
    @Test void interruptedReservationConsumesOnceAndUsesRemainingBudget(){enable();complete();target.result=new HandoffHttpTransport.Result(null,"TIMEOUT",true,true,0);dispatch();jdbc.update("UPDATE hifox_http_delivery SET state='IN_FLIGHT',next_at=0");jdbc.update("UPDATE hifox_http_attempt SET ended_at=NULL");worker.recover();assertEquals(1,jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class));assertEquals("RETRY",state());due();dispatch();assertEquals(2,target.calls.get());}
    @Test void historicalPassIsManualAndConfirmationIsIdempotent(){complete();enable();tickWorker();assertEquals(0,target.calls.get());var p=preview("PUSH");assertEquals(confirm(p),confirm(p));assertEquals(1,count("hifox_http_delivery"));dispatch();assertEquals(1,target.calls.get());}
    @Test void missingOriginalTimeCannotPreviewConfirmOrPost(){complete();enable();jdbc.update("UPDATE plan_review_frozen_snapshot SET create_time=0");assertEquals("ORIGINAL_TIME_UNVERIFIED",assertThrows(PlanReviewFailure.class,()->preview("PUSH")).code());assertEquals(0,target.calls.get());assertEquals(0,count("hifox_http_delivery"));}
    @Test void timeKnownIntervalUnknownAllowsManualWithOriginalTime(){complete();enable();jdbc.update("DELETE FROM hifox_http_interval");jdbc.update("UPDATE hifox_http_fact SET interval_known=0");long passed=jdbc.queryForObject("SELECT passed_at FROM hifox_http_fact",Long.class);var p=preview("PUSH");assertTrue(p.toString().contains("启用区间归属待确认"));confirm(p);dispatch();assertTrue(new String(target.payloads.getFirst(),java.nio.charset.StandardCharsets.UTF_8).contains(java.time.Instant.ofEpochMilli(passed).toString()));}
    @Test void changedEvidenceInvalidatesPreview(){complete();enable();var p=preview("PUSH");jdbc.update("UPDATE plan_review_frozen_snapshot SET create_time=create_time+1000");assertThrows(PlanReviewFailure.class,()->confirm(p));assertEquals(0,count("hifox_http_delivery"));}
    @Test void legacyPossiblySentRecordIsNeverRequeued(){complete();enable();jdbc.update("INSERT INTO hifox_handoff_delivery(business_key,project_id,plan_id,plan_name,plan_number,fact_revision,candidate_kind,target_json,snapshot_json,task_json,attempt_json,state,create_time,update_time) VALUES('old','project','plan','old','1',1,'OLD','{}','{}','{}','{}','UNKNOWN',0,0)");assertThrows(PlanReviewFailure.class,()->preview("PUSH"));assertEquals(0,count("hifox_http_delivery"));}
    @Test void sourceChangeAfterPossibleSendStopsRetriesAndPreservesBody(){enable();complete();target.result=new HandoffHttpTransport.Result(null,"TIMEOUT",true,true,0);dispatch();String payload=jdbc.queryForObject("SELECT payload_json FROM hifox_http_delivery",String.class);jdbc.update("UPDATE case_review_functional_case SET status='UN_REVIEW'");tx.executeWithoutResult(s->service.capture("project","plan",true));due();dispatch();assertEquals("SOURCE_CHANGED",state());assertEquals(payload,jdbc.queryForObject("SELECT payload_json FROM hifox_http_delivery",String.class));assertEquals(1,target.calls.get());}
    @Test void cmOnlyCanEnableWithoutSourceDisclosure(){
        jdbc.update("INSERT INTO user VALUES('cm',true,false)");jdbc.update("INSERT INTO user_role VALUES('config','PROJECT',true)");jdbc.update("INSERT INTO user_role_relation VALUES('cm','config','project')");jdbc.update("INSERT INTO user_role_permission VALUES('config','HIFOX_HANDOFF:CONFIG_MANAGE')");
        complete();
        assertThrows(org.apache.shiro.authz.UnauthorizedException.class,
                () -> run(() -> service.preview(PLATFORM,"","ENABLE",false,"cm")));
        assertFalse(jdbc.queryForObject("SELECT enabled FROM hifox_http_config",Boolean.class));
        assertEquals(0,target.calls.get());
    }
    @Test void permissionChangeInvalidatesEnablePreview(){var p=preview("ENABLE");jdbc.update("INSERT INTO user_role_permission VALUES('admin','CASE_REVIEW:READ')");assertThrows(PlanReviewFailure.class,()->confirm(p));assertFalse(jdbc.queryForObject("SELECT enabled FROM hifox_http_config",Boolean.class));}
    @Test void inventoryChangeInvalidatesEnablePreview(){var p=preview("ENABLE");complete();assertThrows(PlanReviewFailure.class,()->confirm(p));assertFalse(jdbc.queryForObject("SELECT enabled FROM hifox_http_config",Boolean.class));}
    @Test void expiredPreviewWritesNothing(){complete();enable();var p=preview("PUSH");jdbc.update("UPDATE hifox_handoff_preview SET expires_at=0");assertThrows(PlanReviewFailure.class,()->confirm(p));assertEquals(0,count("hifox_http_delivery"));}
    @Test void wrongProjectAndActorCannotConfirm(){complete();enable();var p=preview("PUSH");assertThrows(PlanReviewFailure.class,()->run(()->service.confirm("other",(String)p.get("previewId"),true,"actor")));assertThrows(PlanReviewFailure.class,()->run(()->service.confirm("project",(String)p.get("previewId"),true,"missing")));assertEquals(0,count("hifox_http_delivery"));}
    @Test void doubleWorkerClaimDoesNotStartParallelPost()throws Exception {enable();complete();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);target.during=()->{entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}};String key=key();var thread=Thread.ofVirtual().start(()->worker.dispatch("project",key));assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));worker.dispatch("project",key);release.countDown();thread.join(5000);assertEquals(1,target.calls.get());assertEquals("SUCCESS",state());}
    @Test void failedImmediateCaptureReplaysAuthoritativeSnapshotWithoutChangingPassTime(){
        enable();jdbc.update("UPDATE case_review_functional_case SET status='PASS'");var p=run(()->lifecycle.preview("review","review","COMPLETE","actor"));run(()->lifecycle.confirm((String)p.get("previewId"),true,"actor"));
        assertEquals(0,count("hifox_http_delivery"));long original=jdbc.queryForObject("SELECT create_time FROM plan_review_frozen_snapshot WHERE object_side='review' ORDER BY create_time DESC LIMIT 1",Long.class);
        tickWorker();assertEquals(1,target.calls.get());assertTrue(new String(target.payloads.getFirst(),java.nio.charset.StandardCharsets.UTF_8).contains(java.time.Instant.ofEpochMilli(original).toString()));
    }
    @Test void closedIntervalPassStaysHistoricalOnReenable(){enable();run(()->service.disable(PLATFORM,revision(),"actor"));complete();enable();tickWorker();assertEquals(0,target.calls.get());confirm(preview("PUSH"));dispatch();assertEquals(1,target.calls.get());}
    @Test void sourceObserverCapturesWithoutCallingRemote(){enable();complete();var factory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(new PlanReviewPersistence(jdbc));factory.addAspect(new HandoffSourceObserver(jdbc,service));PlanReviewPersistence observed=factory.getProxy();tx.executeWithoutResult(s->{observed.lockSource("plan");jdbc.update("UPDATE case_review_functional_case SET status='UN_PASS' WHERE case_id='a'");});assertFalse(jdbc.queryForObject("SELECT eligible FROM hifox_http_fact",Boolean.class));assertEquals(0,target.calls.get());}
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.EnableAspectJAutoProxy(proxyTargetClass=true)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class AspectConfiguration { }
    @Test void productionServiceGraphHasRealHttpProviderAndSourceCaptureFailureDoesNotRollbackOriginal(){
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of("metersphere.hifox-handoff.source-environment","isolated-test","metersphere.hifox-handoff.source-base-url","https://msp.example.test")));
            context.register(AspectConfiguration.class);context.registerBean(JdbcTemplate.class,()->jdbc);context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,()->new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
            context.register(PlanReviewPersistence.class,PlanReviewAuthorization.class,PlanReviewPlanStateResolver.class,PlanReviewFrozenReader.class,HandoffAuthorization.class,HandoffSourceReader.class,HandoffStore.class,HandoffService.class,HandoffSourceObserver.class,HandoffWorker.class,HandoffHttpTransport.class,HandoffSecrets.class);
            context.refresh();assertNotNull(context.getBean(HandoffWebhookTarget.class));PlanReviewPersistence observed=context.getBean(PlanReviewPersistence.class);
            jdbc.execute("DROP TABLE hifox_http_fact");tx.executeWithoutResult(s->{observed.lockSource("plan");jdbc.update("UPDATE test_plan SET name='original-change-commits' WHERE id='plan'");});
            assertEquals("original-change-commits",jdbc.queryForObject("SELECT name FROM test_plan WHERE id='plan'",String.class));
        }
    }

    @Test void noTokenAndSecretClearRequireExplicitConfirmation(){
        assertThrows(PlanReviewFailure.class,()->save("","BEARER",""));save("","BEARER","test-token");
        assertThrows(PlanReviewFailure.class,()->run(()->service.saveConfig(PLATFORM,revision(),new HandoffConfiguration("","NONE","",false),"actor")));
        save("","NONE","");assertNull(jdbc.queryForObject("SELECT token_secret FROM hifox_http_config",String.class));assertEquals(0,target.calls.get());
    }
    @Test void sourceLabelsStayJsonDataAndNoIdentifierIsInvented(){
        jdbc.update("UPDATE test_plan SET num=NULL,name=? WHERE id='plan'","Name \"quoted\"\n@someone");enable();complete();dispatch();String body=new String(target.payloads.getFirst(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(body.contains("\"identifier\":null"));assertTrue(body.contains("@someone"));assertTrue(body.contains("\\n"));assertFalse(body.contains("sourceUserId"));
    }
    @Test void oversizedPayloadNeverPostsOrConsumesBudget(){
        jdbc.execute("ALTER TABLE test_plan MODIFY name LONGTEXT");jdbc.update("UPDATE test_plan SET name=? WHERE id='plan'","x".repeat(65536));enable();complete();dispatch();assertEquals(0,target.calls.get());assertEquals("FAILED",state());assertEquals(0,jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class));assertEquals("PAYLOAD_TOO_LARGE",jdbc.queryForObject("SELECT reason FROM hifox_http_delivery",String.class));
    }
    @Test void literalLogFilterAndDoubleReadAreEnforced(){enable();complete();dispatch();assertEquals(1L,((Number)run(()->service.list("project",1,20,"plan",null,"actor")).get("total")).longValue());assertEquals(0L,((Number)run(()->service.list("project",1,20,"%",null,"actor")).get("total")).longValue());assertThrows(PlanReviewFailure.class,()->run(()->service.list("project",0,20,"",null,"actor")));assertThrows(PlanReviewFailure.class,()->run(()->service.list("other",1,20,"",null,"actor")));}
    @Test void summaryAndListsUseHttpResultWithoutRemoteReceipts(){enable();complete();dispatch();var summary=run(()->service.summary("project","plan","actor"));String safe=io.metersphere.sdk.util.JSON.toJSONString(summary);assertFalse(safe.contains("receiver.example"));assertFalse(safe.contains("notices"));var views=new LinkedHashMap<String,Map<String,Object>>();views.put("plan",Map.of());run(()->{new HandoffListReader(jdbc,new PlanReviewAuthorization(jdbc)).decorate(true,"project",List.of("plan"),views,"actor");return null;});assertTrue(views.toString().contains("SUCCESS"));assertFalse(views.toString().contains("event_id"));}

    @Test void sourcePermissionRevocationPausesWithoutInventingNewBusinessTime(){enable();complete();long original=jdbc.queryForObject("SELECT passed_at FROM hifox_http_fact",Long.class);jdbc.update("UPDATE user SET enable=false WHERE id='actor'");dispatch();assertEquals(0,target.calls.get());assertEquals("PAUSED",state());jdbc.update("UPDATE user SET enable=true WHERE id='actor'");dispatch();assertEquals(0,target.calls.get());assertEquals(original,jdbc.queryForObject("SELECT passed_at FROM hifox_http_fact",Long.class));confirm(preview("RESUME"));dispatch();assertEquals(1,target.calls.get());}

    @Test void provenUnsentLegacyNeedsExplicitMigrationAndPreservesOldId(){complete();enable();jdbc.update("INSERT INTO hifox_handoff_delivery(business_key,project_id,plan_id,plan_name,plan_number,fact_revision,candidate_kind,target_json,snapshot_json,task_json,attempt_json,state,create_time,update_time) VALUES('old','project','plan','old','1',1,'OLD','{}','{}','{}',?,'READY',0,0)","{\"attempts\":0,\"requestId\":null}");assertThrows(PlanReviewFailure.class,()->preview("PUSH"));var p=preview("MIGRATE_UNSENT");assertEquals(0,target.calls.get());assertEquals(confirm(p),confirm(p));assertEquals("old",jdbc.queryForObject("SELECT business_key FROM hifox_handoff_delivery",String.class));assertEquals("READY",jdbc.queryForObject("SELECT original_state FROM hifox_http_legacy_migration",String.class));dispatch();assertEquals(1,target.calls.get());assertEquals("MIGRATED_HTTP17",jdbc.queryForObject("SELECT state FROM hifox_handoff_delivery",String.class));}
    @Test void anyLegacyAttemptOrRemoteTraceBlocksMigration(){complete();enable();jdbc.update("INSERT INTO hifox_handoff_delivery(business_key,project_id,plan_id,plan_name,plan_number,fact_revision,candidate_kind,target_json,snapshot_json,task_json,attempt_json,state,create_time,update_time) VALUES('old','project','plan','old','1',1,'OLD','{}','{}','{}',?,'READY',0,0)","{\"attempts\":1,\"requestId\":\"old-request\"}");assertThrows(PlanReviewFailure.class,()->preview("MIGRATE_UNSENT"));assertEquals(0,count("hifox_http_legacy_migration"));assertEquals(0,count("hifox_http_delivery"));}

    void addLogRow(String id,long created,boolean changed,String state){
        var row=new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM hifox_http_delivery LIMIT 1"));
        row.put("business_key",id);row.put("event_id",UUID.randomUUID().toString());row.put("plan_id",id);row.put("create_time",created);row.put("update_time",created);row.put("source_changed",changed);row.put("state",state);
        var columns=String.join(",",row.keySet());jdbc.update("INSERT INTO hifox_http_delivery("+columns+") VALUES("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray());
    }
    Map<String,Object> logs(int page,int size,String state,Boolean changed,Long from,Long to){return run(()->service.list("project",page,size,"",state,changed,from,to,"actor"));}
    @Test void sourceChangeAndInclusiveCreationTimeCombineWithStablePagination(){
        enable();complete();dispatch();jdbc.update("UPDATE hifox_http_delivery SET create_time=1000,source_changed=0");
        addLogRow("changed-start",2000,true,"SUCCESS");addLogRow("changed-end",3000,true,"SUCCESS");addLogRow("changed-after",3001,true,"SUCCESS");addLogRow("unchanged",2500,false,"SUCCESS");addLogRow("failed",2500,true,"FAILED");
        var first=logs(1,1,"SUCCESS",true,2000L,3000L);var second=logs(2,1,"SUCCESS",true,2000L,3000L);
        assertEquals(2L,((Number)first.get("total")).longValue());assertEquals(2L,((Number)second.get("total")).longValue());
        assertEquals("changed-end",((Map<?,?>)((List<?>)first.get("items")).getFirst()).get("business_key"));assertEquals("changed-start",((Map<?,?>)((List<?>)second.get("items")).getFirst()).get("business_key"));
        assertEquals(1L,((Number)logs(1,20,"SUCCESS",false,2000L,3000L).get("total")).longValue());assertEquals(1L,((Number)logs(1,20,"SUCCESS",true,2000L,2000L).get("total")).longValue());
        assertEquals(4L,((Number)logs(1,20,null,true,null,null).get("total")).longValue());
    }
    @Test void invalidTimeRangesRejectWithoutChangingStateOrBudget(){enable();complete();long count=count("hifox_http_delivery");assertThrows(PlanReviewFailure.class,()->logs(1,20,null,null,-1L,null));assertThrows(PlanReviewFailure.class,()->logs(1,20,null,null,3000L,2000L));assertEquals(count,count("hifox_http_delivery"));assertEquals(0,target.calls.get());assertEquals(0,jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class));}
    @Test void inspectionUsesRealEvidenceAndAuditsAreScopedAndSecretFree(){
        enable();complete();dispatch();var summary=run(()->service.summary("project","plan","actor"));var delivery=(Map<?,?>)summary.get("delivery");
        var inspection=(HandoffInspection)delivery.get("inspection");var stored=jdbc.queryForMap("SELECT * FROM hifox_http_delivery");
        assertEquals(stored.get("time_revision"),inspection.timeEvidence().revision());assertEquals(stored.get("event_id"),inspection.frozenEventId());assertEquals(2,inspection.functionalCount());assertFalse(inspection.timeEvidence().reference().isBlank());
        var audits=(List<?>)delivery.get("audits");assertEquals(3,audits.size());assertTrue(audits.stream().allMatch(a->((HandoffInspection.Audit)a).actor().equals("actor")));
        jdbc.update("INSERT INTO hifox_handoff_audit VALUES('other-project','other',NULL,'private-other','PRIVATE',1,1)");jdbc.update("INSERT INTO hifox_handoff_audit VALUES('other-plan','project','other-key','private-plan','PRIVATE',1,1)");
        var wire=io.metersphere.sdk.util.JSON.toJSONString(run(()->service.summary("project","plan","actor")));assertFalse(wire.contains("private-other"));assertFalse(wire.contains("private-plan"));assertFalse(wire.contains("receiver.example"));assertFalse(wire.contains("secret=example"));
        jdbc.update("DELETE FROM hifox_handoff_audit WHERE project_id IN (?,?)","project",PLATFORM);assertTrue(((List<?>)((Map<?,?>)run(()->service.summary("project","plan","actor")).get("delivery")).get("audits")).isEmpty());
    }
    @Test void resumePreviewDisplaysFrozenEventVersionsAndAllUnresolvedPauseCauses(){
        enable();complete();target.result=new HandoffHttpTransport.Result(500,"HTTP_500",true,false,0);dispatch();
        run(()->service.grant(PLATFORM,revision(),false,"actor"));run(()->service.disable(PLATFORM,revision(),"actor"));
        run(()->service.grant(PLATFORM,revision(),true,"actor"));enable();
        var preview=preview("RESUME");var inspection=(HandoffInspection)((Map<?,?>)preview.get("payload")).get("inspection");
        assertTrue(inspection.pauseReasons().containsAll(List.of("DISABLED","PG_REVOKED")));assertNotNull(inspection.frozenEventId());assertEquals(2,inspection.functionalCount());assertFalse(inspection.sourceVersion().revision().isBlank());
        var first=confirm(preview);assertEquals(first,confirm(preview));assertEquals(1,jdbc.queryForObject("SELECT attempts_used FROM hifox_http_delivery",Integer.class));
        var audits=(List<?>)((Map<?,?>)run(()->service.summary("project","plan","actor")).get("delivery")).get("audits");assertTrue(audits.stream().anyMatch(a->((HandoffInspection.Audit)a).action().equals("RESUME")));
        assertTrue(((HandoffInspection)((Map<?,?>)run(()->service.summary("project","plan","actor")).get("delivery")).get("inspection")).pauseReasons().isEmpty());
        dispatch();run(()->service.grant(PLATFORM,revision(),false,"actor"));run(()->service.grant(PLATFORM,revision(),true,"actor"));confirm(preview("RESUME"));
        var again=(List<?>)((Map<?,?>)run(()->service.summary("project","plan","actor")).get("delivery")).get("audits");assertEquals(2L,again.stream().filter(a->((HandoffInspection.Audit)a).action().equals("RESUME")).count());
    }
    @Test void firstManualPushShowsUnfrozenEventAndSingleReadCannotGetInspection(){
        complete();enable();var inspection=(HandoffInspection)((Map<?,?>)preview("PUSH").get("payload")).get("inspection");assertNull(inspection.frozenEventId());assertEquals(2,inspection.functionalCount());
        jdbc.update("INSERT INTO user VALUES('plan-reader',true,false)");jdbc.update("INSERT INTO user_role VALUES('plan-only','PROJECT',true)");jdbc.update("INSERT INTO user_role_relation VALUES('plan-reader','plan-only','project')");jdbc.update("INSERT INTO user_role_permission VALUES('plan-only','PROJECT_TEST_PLAN:READ')");
        assertThrows(PlanReviewFailure.class,()->run(()->service.summary("project","plan","plan-reader")));assertThrows(PlanReviewFailure.class,()->run(()->service.preview("project","plan","PUSH",false,"plan-reader")));
    }

}
