package io.metersphere.functional.planreview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Local relational tests only; H2 MySQL mode does not prove MySQL migration/concurrency behavior. */
class PlanReviewPersistenceTests {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private PlanReviewPersistence store;

    @BeforeEach
    void setup() throws Exception {
        var database = PlanReviewTestDatabase.create();
        jdbc = database.jdbc(); tx = database.tx(); store = database.store();
    }
    @Test
    void moduleDeletionRejectsMixedProjectNodesBeforeAnyDeletion(){
        jdbc.update("INSERT INTO case_review_module VALUES('own','project'),('foreign','other')");
        assertEquals("RESOURCE_NOT_ACCESSIBLE",assertThrows(PlanReviewFailure.class,()->tx.executeWithoutResult(status->store.lockModules("case_review_module","project",List.of("own","foreign")))).code());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM case_review_module",Integer.class));
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->store.lockModules("user","project",List.of("own"))));
    }
    @Test void backgroundSourceChangeRecordsTheCurrentExecutorOnlyForAnActualMembershipEdge(){
        pair(false);
        tx.executeWithoutResult(status->{jdbc.update("INSERT INTO test_plan_functional_case VALUES('member','plan','a')");store.sourceChanged("plan","new-executor");});
        assertEquals("new-executor",jdbc.queryForObject("SELECT source_actor FROM plan_review_relation",String.class));
        tx.executeWithoutResult(status->store.sourceChanged("plan","reader-only"));
        assertEquals("new-executor",jdbc.queryForObject("SELECT source_actor FROM plan_review_relation",String.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{jdbc.update("DELETE FROM test_plan_functional_case");store.sourceChanged("plan","rolled-back");throw new IllegalStateException("Injected rollback");}));
        assertEquals("new-executor",jdbc.queryForObject("SELECT source_actor FROM plan_review_relation",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case",Integer.class));
    }
    @Test void aReadDiscoveringAnUntrackedEdgeDoesNotAttributeItToTheViewer(){
        pair(false);store.setCurrentActors(List.of(()->"viewer"));
        tx.executeWithoutResult(status->{jdbc.update("INSERT INTO test_plan_functional_case VALUES('old-write','plan','a')");store.sourceChanged("plan");});
        assertNull(jdbc.queryForObject("SELECT source_actor FROM plan_review_relation",String.class));
        store.setCurrentActors(List.of(()->"writer"));
        tx.executeWithoutResult(status->{jdbc.update("INSERT INTO test_plan_functional_case VALUES('new-write','plan','b')");store.sourceMutated("plan");});
        assertEquals("writer",jdbc.queryForObject("SELECT source_actor FROM plan_review_relation",String.class));
    }
    @Test
    void mysqlModuleOwnershipUsesCurrentRowsAfterAnOlderSnapshot() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("plan.review.mysql.config")!=null);
        jdbc.update("INSERT INTO case_review_module VALUES('folder','project')");
        var read=new java.util.concurrent.CountDownLatch(1);var proceed=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            var future=executor.submit(()->{
                var repeatable=new TransactionTemplate(tx.getTransactionManager());repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);repeatable.setTimeout(15);
                return repeatable.execute(status->{
                    jdbc.queryForList("SELECT id FROM case_review_module");read.countDown();
                    try{if(!proceed.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Test coordination timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    return assertThrows(PlanReviewFailure.class,()->store.lockModules("case_review_module","project",List.of("folder"))).code();
                });
            });
            assertTrue(read.await(10,java.util.concurrent.TimeUnit.SECONDS));
            tx.executeWithoutResult(status->{store.lockProject("project");jdbc.update("DELETE FROM case_review_module WHERE id='folder'");});
            proceed.countDown();assertEquals("RESOURCE_NOT_ACCESSIBLE",future.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }finally{proceed.countDown();executor.shutdownNow();}
    }

    private void pair(boolean approvalPending) {
        jdbc.update("""
                INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,
                active_plan_id,active_review_id,state,sync_state,source_changed_at,original_actor,
                create_time,update_time,approval_pending)
                VALUES('relation','project','plan','review','g1','plan','review','ACTIVE','SYNCED',0,'actor',0,0,?)
                """, approvalPending);
    }

    @Test
    void sourceCommitAndItsWatermarkRollbackTogether() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            store.sourceChanged("plan");
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
            store.sourceChanged("plan");
            throw new IllegalStateException("injected business rollback");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_source_member", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_source_clock", Integer.class));
    }

    @Test
    void duplicateCollectionReferenceOnlyExitsOnLastRemovalAndRecoveryAdvancesEpoch() {
        tx.executeWithoutResult(status -> {
            store.sourceChanged("plan");
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m1','plan','a'),('m2','plan','a')");
            assertEquals(1, store.sourceChanged("plan").version());
            jdbc.update("DELETE FROM test_plan_functional_case WHERE id='m1'");
            assertEquals(1, store.sourceChanged("plan").version());
            jdbc.update("DELETE FROM test_plan_functional_case WHERE id='m2'");
            assertEquals(2, store.sourceChanged("plan").version());
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m3','plan','a')");
            assertEquals(2L, store.sourceChanged("plan").epochs().get("a"));
        });
        assertEquals(3L, jdbc.queryForObject("SELECT version FROM plan_review_source_clock", Long.class));
    }

    @Test
    void deletedOrCrossProjectSourcesNeverEnterEffectiveSet() {
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m1','plan','a'),('m2','plan','foreign')");
            assertEquals(java.util.Set.of("a"), store.sourceChanged("plan").present());
            jdbc.update("UPDATE functional_case SET deleted=true WHERE id='a'");
            assertTrue(store.sourceChanged("plan").present().isEmpty());
            jdbc.update("UPDATE functional_case SET deleted=false WHERE id='a'");
            assertEquals(2L, store.sourceChanged("plan").epochs().get("a"));
        });
    }

    @Test
    void sourceChangePausesApprovedRemainderWithoutTouchingTargetOrRetryBudget() {
        pair(true);
        jdbc.update("UPDATE plan_review_relation SET attempts=4,retry_round=2,applied_batches=1 WHERE id='relation'");
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
            store.sourceChanged("plan");
        });
        var row = jdbc.queryForMap("SELECT sync_state,attempts,retry_round,applied_batches,applied_version FROM plan_review_relation");
        assertEquals("RECONFIRM_REQUIRED", row.get("sync_state"));
        assertEquals(4, ((Number) row.get("attempts")).intValue());
        assertEquals(2L, ((Number) row.get("retry_round")).longValue());
        assertEquals(1L, ((Number) row.get("applied_batches")).longValue());
        assertEquals(0L, ((Number) row.get("applied_version")).longValue());
        assertTrue(jdbc.queryForList("SELECT * FROM plan_review_target_member").isEmpty());
    }

    @Test
    void linkedReviewRejectsIndependentMembersAndStaleOrMissingOpinionEpoch() {
        pair(true);
        jdbc.update("INSERT INTO plan_review_target_member VALUES('review','a',2,2,true)");
        assertThrows(PlanReviewFailure.class, () -> tx.executeWithoutResult(s -> store.requireIndependentReview("review")));
        assertThrows(PlanReviewFailure.class, () -> tx.executeWithoutResult(s -> store.opinionChanging("review", null, List.of("a"))));
        assertThrows(PlanReviewFailure.class, () -> tx.executeWithoutResult(s -> store.opinionChanging("review", Map.of("a",1L), List.of("a"))));
        assertEquals(0L, jdbc.queryForObject("SELECT opinion_revision FROM plan_review_relation", Long.class));
        tx.executeWithoutResult(s -> store.opinionChanging("review", Map.of("a",2L), List.of("a")));
        assertEquals(1L, jdbc.queryForObject("SELECT opinion_revision FROM plan_review_relation", Long.class));
        assertEquals("RECONFIRM_REQUIRED", jdbc.queryForObject("SELECT sync_state FROM plan_review_relation", String.class));
        jdbc.update("UPDATE plan_review_target_member SET present=false");
        assertThrows(PlanReviewFailure.class, () -> tx.executeWithoutResult(s -> store.opinionChanging("review", Map.of("a",2L), List.of("a"))));
    }

    @Test
    void independentReviewKeepsLegacyOpinionContractAndEndedRelationFreesOnlyItsOwnOccupancy() {
        tx.executeWithoutResult(s -> {
            store.requireIndependentReview("review");
            store.opinionChanging("review", null, List.of("a"));
        });
        pair(false);
        jdbc.update("UPDATE plan_review_relation SET state='ENDED',active_plan_id=NULL,active_review_id=NULL");
        tx.executeWithoutResult(s -> store.requireIndependentReview("review"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
    }

    @Test
    void databaseConstraintsRejectSecondActivePlanAndNullActiveKeys() {
        pair(false);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,
                active_review_id,state,sync_state,source_changed_at,original_actor,create_time,update_time)
                VALUES('duplicate','project','plan','other-review','g2','plan','other-review','ACTIVE','PENDING',0,'actor',0,0)
                """));
        var failure=assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE plan_review_relation SET active_plan_id=NULL WHERE id='relation'"));
        Throwable cause=failure;while(cause!=null && !(cause instanceof java.sql.SQLException))cause=cause.getCause();
        assertInstanceOf(java.sql.SQLException.class,cause);
        var sql=(java.sql.SQLException)cause;
        assertTrue(sql.getErrorCode()==3819 || sql.getSQLState().startsWith("23"),"The database must reject the invariant, rather than fail for an unrelated reason");
        assertEquals("plan",jdbc.queryForObject("SELECT active_plan_id FROM plan_review_relation WHERE id='relation'",String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
    }
}
