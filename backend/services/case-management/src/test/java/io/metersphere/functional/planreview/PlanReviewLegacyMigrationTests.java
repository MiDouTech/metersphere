package io.metersphere.functional.planreview;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Development migration tests; MySQL cases execute the same program in a second real empty schema. */
class PlanReviewLegacyMigrationTests {
    JdbcTemplate jdbc;TransactionTemplate tx;PlanReviewLegacyMigrationService migration;PlanReviewLegacyInventory inventory;
    PlanReviewLegacySnapshot snapshots;PlanReviewApplicationService app;
    @BeforeEach void setup() throws Exception {
        var db=PlanReviewTestDatabase.create();jdbc=db.jdbc();tx=db.tx();
        jdbc.execute("CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE project(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(32),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('actor',1,0),('owner',1,0),('reader',1,0)");jdbc.update("INSERT INTO project VALUES('project',1,0)");
        jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',1),('owner','PROJECT',1),('reader','PROJECT',1)");
        jdbc.update("INSERT INTO user_role_relation VALUES('actor','admin','system'),('owner','owner','project'),('reader','reader','project')");
        jdbc.update("INSERT INTO user_role_permission VALUES('owner','PLAN_REVIEW:TAKEOVER_APPROVE'),('owner','PROJECT_TEST_PLAN:READ'),('owner','CASE_REVIEW:READ'),('reader','PROJECT_TEST_PLAN:READ'),('reader','CASE_REVIEW:READ')");
        var auth=new PlanReviewAuthorization(jdbc);var resolver=new PlanReviewPlanStateResolver(List.of());
        app=new PlanReviewApplicationService(jdbc,db.store(),auth,db.manager(),resolver);run(()->app.grant("project",0L,true,"actor"));
        inventory=new PlanReviewLegacyInventory(jdbc,auth,resolver);snapshots=new PlanReviewLegacySnapshot(jdbc,resolver);
        migration=new PlanReviewLegacyMigrationService(jdbc,auth,db.store(),inventory,snapshots,app);
        group("old");jdbc.update("INSERT INTO test_plan_functional_case VALUES('source','plan','a')");
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status,create_time,update_time,pos) VALUES('kept','review','b','PASS',1,1,1)");
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status,content,deleted,abandoned) VALUES('opinion','review','b','PASS',?,0,0)",new byte[]{1,2,3});
        jdbc.update("INSERT INTO case_review_user VALUES('review','actor')");
    }
    <T> T run(Supplier<T> call){return tx.execute(status->call.get());}
    void group(String id){jdbc.update("INSERT INTO functional_import_followup_group(id,project_id,plan_id,review_id,create_user,create_time) VALUES(?,'project','plan','review','actor',1)",id);}
    PlanReviewLegacyDecision choice(String key,Map<String,String> groups,Map<String,PlanReviewLegacyDecision.Job> jobs){return new PlanReviewLegacyDecision("project",key,(String)inventory.scan("project","actor").get("manifestHash"),groups,jobs);}
    Map<String,Object> preview(String key){return run(()->migration.preview(choice(key,Map.of("old","KEEP"),Map.of()),"actor"));}
    String change(Map<String,Object> preview){return (String)preview.get("id");}
    String hash(Map<String,Object> preview){return (String)preview.get("snapshot_hash");}
    void realRehearsal(Map<String,Object> preview) throws Exception {
        Assumptions.assumeTrue(System.getProperty("plan.review.mysql.config")!=null,"Real MySQL rehearsal only");
        migration.setSandboxes(List.of(new PlanReviewLegacyJdbcSandbox(jdbc,PlanReviewTestDatabase.emptyMysql())));
        run(()->migration.backup("project",change(preview),"actor"));run(()->migration.rehearse("project",change(preview),"actor"));
        run(()->migration.approve("project",change(preview),hash(preview),"1".repeat(64),"2".repeat(40),true,"owner"));
    }
    @Test void incompleteProtocolNeverMovesAnOldEdgeAndReadPermissionCannotExecute(){
        var preview=preview("first");assertEquals("PREVIEW",preview.get("state"));
        assertThrows(PlanReviewFailure.class,()->run(()->migration.execute("project",change(preview),hash(preview),true,"actor")));
        run(()->migration.backup("project",change(preview),"actor"));
        assertEquals("ISOLATED_REHEARSAL_UNAVAILABLE",assertThrows(PlanReviewFailure.class,()->run(()->migration.rehearse("project",change(preview),"actor"))).code());
        assertThrows(PlanReviewFailure.class,()->run(()->migration.preview(choice("reader",Map.of("old","KEEP"),Map.of()),"reader")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        assertEquals(preview.get("id"),preview("first").get("id"));
    }
    @Test void currentOpinionOrWatermarkChangesInvalidateTheExactBackup(){
        var preview=preview("first");jdbc.update("UPDATE case_review_history SET content=? WHERE id='opinion'",new byte[]{9});
        assertEquals("MIGRATION_SNAPSHOT_STALE",assertThrows(PlanReviewFailure.class,()->run(()->migration.backup("project",change(preview),"actor"))).code());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group",Integer.class));
    }
    @Test void obsoleteGrantEndpointsDoNotInvalidateAMigrationSnapshot(){
        var preview=preview("first");run(()->app.grant("project",1L,false,"actor"));run(()->app.grant("project",2L,true,"actor"));
        assertDoesNotThrow(()->run(()->migration.backup("project",change(preview),"actor")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
    }
    @Test void anAlreadyEndedUnifiedGenerationIsNotRecreatedByChoosingItsOldLegacyEdge(){
        jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,state,sync_state,source_changed_at,original_actor,ended_reason,create_time,update_time) VALUES('ended','project','plan','review','old-generation','ENDED','ENDED',1,'actor','UNLINKED',1,1)");
        assertEquals("MIGRATION_RELATION_OCCUPIED",assertThrows(PlanReviewFailure.class,()->preview("first")).code());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE state='ENDED'",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_legacy_change",Integer.class));
    }
    @Test void everyConflictingGroupAndWritingJobMustBeExplicitlyChosen(){
        group("duplicate");
        assertEquals("MIGRATION_SCOPE_INCOMPLETE",assertThrows(PlanReviewFailure.class,()->preview("first")).code());
        jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES('second','project','UNDERWAY')");jdbc.update("UPDATE functional_import_followup_group SET review_id='second' WHERE id='duplicate'");
        var choice=choice("conflict",Map.of("old","KEEP","duplicate","KEEP"),Map.of());
        assertEquals("EXPLICIT_CONFLICT_DECISION_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->migration.preview(choice,"actor"))).code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_legacy_change",Integer.class));
    }
    @Test void mysqlRealBackupApplyReplayAndMetadataRollbackKeepBothMemberSetsAndOpinions() throws Exception {
        var preview=preview("first");realRehearsal(preview);
        var first=run(()->migration.execute("project",change(preview),hash(preview),true,"actor"));
        assertEquals(first,run(()->migration.execute("project",change(preview),hash(preview),true,"actor")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group",Integer.class));
        assertEquals("RECONFIRM_REQUIRED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        assertEquals(List.of("a"),jdbc.queryForList("SELECT functional_case_id FROM test_plan_functional_case",String.class));assertEquals(List.of("b"),jdbc.queryForList("SELECT case_id FROM case_review_functional_case",String.class));
        assertArrayEquals(new byte[]{1,2,3},jdbc.queryForObject("SELECT content FROM case_review_history WHERE id='opinion'",byte[].class));
        var rollback=run(()->migration.rollbackPreview("project",change(preview),"actor"));String p=(String)rollback.get("previewHash");
        assertEquals("RESTORE_METADATA",rollback.get("mode"));run(()->migration.approveRollback("project",change(preview),p,true,"owner"));run(()->migration.rollback("project",change(preview),p,true,"actor"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group WHERE id='old'",Integer.class));assertEquals("ENDED",jdbc.queryForObject("SELECT state FROM plan_review_relation",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_legacy_rehearsal WHERE state='PASSED'",Integer.class));
    }
    @Test void mysqlLaterOpinionForcesExplicitCompensationAndStaleRollbackApprovalCannotEraseIt() throws Exception {
        var preview=preview("first");realRehearsal(preview);run(()->migration.execute("project",change(preview),hash(preview),true,"actor"));
        var before=run(()->migration.rollbackPreview("project",change(preview),"actor"));String old=(String)before.get("previewHash");run(()->migration.approveRollback("project",change(preview),old,true,"owner"));
        jdbc.update("UPDATE plan_review_legacy_change SET rollback_expires_at=0");assertEquals("ROLLBACK_PREVIEW_EXPIRED",assertThrows(PlanReviewFailure.class,()->run(()->migration.rollback("project",change(preview),old,true,"actor"))).code());
        var refreshed=run(()->migration.rollbackPreview("project",change(preview),"actor"));String refreshedHash=(String)refreshed.get("previewHash");assertNotEquals(old,refreshedHash);run(()->migration.approveRollback("project",change(preview),refreshedHash,true,"owner"));
        jdbc.update("UPDATE case_review_history SET content=? WHERE id='opinion'",new byte[]{4,5,6});
        assertEquals("ROLLBACK_PREVIEW_STALE",assertThrows(PlanReviewFailure.class,()->run(()->migration.rollback("project",change(preview),refreshedHash,true,"actor"))).code());
        var next=run(()->migration.rollbackPreview("project",change(preview),"actor"));String p=(String)next.get("previewHash");assertEquals("COMPENSATE_KEEP_FACTS",next.get("mode"));
        run(()->migration.approveRollback("project",change(preview),p,true,"owner"));run(()->migration.rollback("project",change(preview),p,true,"actor"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group",Integer.class));assertArrayEquals(new byte[]{4,5,6},jdbc.queryForObject("SELECT content FROM case_review_history WHERE id='opinion'",byte[].class));
    }
    @Test void mysqlInFlightOldWorkerCommitIsReadAfterItsRowLockAndMakesTheBackupStale() throws Exception {
        Assumptions.assumeTrue(System.getProperty("plan.review.mysql.config")!=null,"Real row-lock test only");
        jdbc.update("INSERT INTO functional_import_followup(job_id,project_id,group_id,plan_id,review_id,policy,state,user_id,request_json,create_time,update_time,source_kind) VALUES('old-job','project','old','plan','review','CREATE_NEW_PAIR','PENDING','actor','{}',1,1,'FILE')");
        jdbc.update("INSERT INTO functional_import_followup_item VALUES('old-job','b',0,0)");
        var selection=choice("old-worker",Map.of("old","KEEP"),Map.of("old-job",new PlanReviewLegacyDecision.Job("TERMINATE",null,null)));
        var preview=run(()->migration.preview(selection,"actor"));var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var started=new java.util.concurrent.CountDownLatch(1);
        try(var threads=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var worker=threads.submit(()->tx.executeWithoutResult(status->{
                // Exact v1 consumer fence: it rechecks PENDING while holding the original job row.
                jdbc.queryForMap("SELECT * FROM functional_import_followup WHERE job_id='old-job' AND state='PENDING' FOR UPDATE");locked.countDown();
                try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Test release timed out");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                jdbc.update("INSERT INTO test_plan_functional_case VALUES('old-committed','plan','b')");jdbc.update("UPDATE functional_import_followup_item SET plan_done=1 WHERE job_id='old-job' AND case_id='b'");jdbc.update("UPDATE functional_import_followup SET update_time=2 WHERE job_id='old-job'");
            }));
            assertTrue(locked.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var backup=threads.submit(()->{started.countDown();return run(()->migration.backup("project",change(preview),"actor"));});assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
            try{assertThrows(java.util.concurrent.TimeoutException.class,()->backup.get(100,java.util.concurrent.TimeUnit.MILLISECONDS));}finally{release.countDown();}
            worker.get(5,java.util.concurrent.TimeUnit.SECONDS);
            var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->backup.get(5,java.util.concurrent.TimeUnit.SECONDS));assertInstanceOf(PlanReviewFailure.class,failure.getCause());assertEquals("MIGRATION_SNAPSHOT_STALE",((PlanReviewFailure)failure.getCause()).code());
        }finally{release.countDown();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case WHERE id='old-committed'",Integer.class));assertEquals(true,jdbc.queryForObject("SELECT plan_done FROM functional_import_followup_item WHERE job_id='old-job'",Boolean.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));assertEquals("PREVIEW",jdbc.queryForObject("SELECT state FROM plan_review_legacy_change",String.class));
    }
}
