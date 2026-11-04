package io.metersphere.functional.planreview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewLifecycleTests {
    JdbcTemplate jdbc; TransactionTemplate tx; PlanReviewLifecycleService lifecycle; PlanReviewApplicationService application;
    @BeforeEach void setup() throws Exception {
        var database=PlanReviewTestDatabase.create(); jdbc=database.jdbc(); tx=database.tx();
        jdbc.execute("CREATE TABLE user(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE project(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50),type VARCHAR(32),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('actor',true,false)"); jdbc.update("INSERT INTO project VALUES('project',true,false)");
        jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',true)"); jdbc.update("INSERT INTO user_role_relation VALUES('actor','admin','system')");
        var authorization=new PlanReviewAuthorization(jdbc); var resolver=new PlanReviewPlanStateResolver(List.of());
        application=new PlanReviewApplicationService(jdbc,database.store(),authorization,database.manager(),resolver);
        lifecycle=new PlanReviewLifecycleService(jdbc,database.store(),authorization,resolver);
        run(()->application.grant("project",0L,true,"actor"));
        String preview=(String)run(()->application.preview("plan","review","PAIR_EXISTING","actor")).get("previewId");
        var pair=run(()->application.confirm(preview,"pair",true,"actor"));
        tx.executeWithoutResult(s->application.synchronize((String)pair.get("relationId"),(String)pair.get("generation")));
    }
    <T>T run(Supplier<T> action){return tx.execute(s->action.get());}
    Map<String,Object> change(String side,String action){
        var preview=run(()->lifecycle.preview(side,side.equals("plan")?"plan":"review",action,"actor"));
        return run(()->lifecycle.confirm((String)preview.get("previewId"),true,"actor"));
    }
    @Test void restoringArchiveAndRestoringEditAreSeparateAndNeverRestoreTheOtherSideOrMembers(){
        change("review","ARCHIVE");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
        assertEquals("OBJECT_STATE_CONFLICT",assertThrows(PlanReviewFailure.class,()->change("review","RESTORE_EDIT")).code());
        assertEquals("COMPLETED",change("review","RESTORE_ARCHIVE").get("objectState"));
        assertEquals("UNDERWAY",change("review","RESTORE_EDIT").get("objectState"));
        assertEquals("UNDERWAY",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
        assertEquals("RECONFIRM_REQUIRED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_review_functional_case",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
    }
    @Test void staleOrCancelledLifecyclePreviewHasNoStateOrSnapshotSideEffects(){
        var preview=run(()->lifecycle.preview("review","review","ARCHIVE","actor"));
        assertEquals("UNDERWAY",jdbc.queryForObject("SELECT status FROM case_review",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
        jdbc.update("UPDATE case_review SET update_time=update_time+1");
        assertEquals("PREVIEW_STALE",assertThrows(PlanReviewFailure.class,()->run(()->lifecycle.confirm((String)preview.get("previewId"),true,"actor"))).code());
        assertEquals("UNDERWAY",jdbc.queryForObject("SELECT status FROM case_review",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
    }
    @Test void unresolvedSyncBlocksCompletionButExplicitArchivePreservesItsVersions(){
        jdbc.update("UPDATE plan_review_relation SET source_version=2,applied_version=1,sync_state='FAILED_MANUAL'");
        assertEquals("SYNC_NOT_CONVERGED",assertThrows(PlanReviewFailure.class,()->change("review","COMPLETE")).code());
        change("review","ARCHIVE");
        assertEquals(2L,jdbc.queryForObject("SELECT source_version FROM plan_review_frozen_snapshot",Long.class));
        assertEquals(1L,jdbc.queryForObject("SELECT applied_version FROM plan_review_frozen_snapshot",Long.class));
    }

    @Test void independentRestoredPlanStaysEditableUntilNewExecutionThenFreezesWithoutInventingRelation(){
        jdbc.update("UPDATE plan_review_relation SET state='ENDED',active_plan_id=NULL,active_review_id=NULL");
        jdbc.update("UPDATE test_plan SET status='COMPLETED'");
        var revision=new java.util.concurrent.atomic.AtomicReference<>("before");
        var provider=new PlanReviewPlanStateProvider(){
            @Override public String businessState(String id){return "COMPLETED";}
            @Override public String executionRevision(String id){return revision.get();}
            @Override public Map<String,Object> statisticsSnapshot(String id){return Map.of("status","COMPLETED","caseTotal",1,"successCount",1);}
        };
        var resolver=new PlanReviewPlanStateResolver(List.of(provider));
        var service=new PlanReviewLifecycleService(jdbc,new PlanReviewPersistence(jdbc),new PlanReviewAuthorization(jdbc),resolver);
        var preview=run(()->service.preview("plan","plan","RESTORE_EDIT","actor"));
        run(()->service.confirm((String)preview.get("previewId"),true,"actor"));
        var guard=new PlanReviewCompletionGuard(jdbc,resolver);
        assertEquals("NOT_ARCHIVED",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
        assertEquals("UNDERWAY",guard.visibleStatus("plan","COMPLETED"));
        tx.executeWithoutResult(status->service.observePlanCompletion("plan"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
        revision.set("after");
        assertFalse(guard.reopenUnchanged("plan"));
        tx.executeWithoutResult(status->service.observePlanCompletion("plan"));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot WHERE relation_id IS NULL AND generation IS NULL",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE state='ACTIVE'",Integer.class));
    }

    @Test void automaticPlanCompletionWaitsForConvergenceAndPersistsOneImmutableSnapshot(){
        jdbc.update("UPDATE test_plan SET status='NOT_ARCHIVED'");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var store=new PlanReviewPersistence(jdbc);
        tx.executeWithoutResult(s->store.sourceChanged("plan"));
        var provider=new PlanReviewPlanStateProvider(){
            @Override public String businessState(String id){return "UNDERWAY";}
            @Override public String completionState(String id){return "COMPLETED";}
            @Override public Map<String,Object> statisticsSnapshot(String id){return Map.of("id",id,"status","UNDERWAY","caseTotal",1,"successCount",1,"pendingCount",0);}
        };
        var resolver=new PlanReviewPlanStateResolver(List.of(provider));
        var guard=new PlanReviewCompletionGuard(jdbc);
        var service=new PlanReviewLifecycleService(jdbc,store,new PlanReviewAuthorization(jdbc),resolver);
        assertFalse(guard.permit("plan"));
        tx.executeWithoutResult(s->service.observePlanCompletion("plan"));
        assertEquals("NOT_ARCHIVED",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
        var sync=new PlanReviewApplicationService(jdbc,store,new PlanReviewAuthorization(jdbc),tx.getTransactionManager(),resolver);
        var relation=jdbc.queryForMap("SELECT id,generation FROM plan_review_relation");
        tx.executeWithoutResult(s->sync.synchronize((String)relation.get("id"),(String)relation.get("generation")));
        assertTrue(guard.permit("plan"));
        assertEquals("UNDERWAY",guard.visibleStatus("plan","COMPLETED"));
        tx.executeWithoutResult(s->service.observePlanCompletion("plan"));
        tx.executeWithoutResult(s->service.observePlanCompletion("plan"));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
        var frozen=new PlanReviewFrozenReader(jdbc);
        assertEquals(1,frozen.statistics("plan").get("caseTotal"));
        jdbc.update("UPDATE functional_case SET deleted=1 WHERE id='a'");
        tx.executeWithoutResult(s->store.sourceChanged("plan"));
        assertEquals("COMPLETED",guard.visibleStatus("plan","UNDERWAY"));
        assertEquals(1,frozen.statistics("plan").get("caseTotal"));
        var restore=run(()->service.preview("plan","plan","RESTORE_EDIT","actor"));
        run(()->service.confirm((String)restore.get("previewId"),true,"actor"));
        assertEquals("NOT_ARCHIVED",jdbc.queryForObject("SELECT status FROM test_plan",String.class));
        assertEquals("RECONFIRM_REQUIRED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        tx.executeWithoutResult(s->service.observePlanCompletion("plan"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
    }
}
