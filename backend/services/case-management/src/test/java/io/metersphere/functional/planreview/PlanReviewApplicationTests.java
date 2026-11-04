package io.metersphere.functional.planreview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Development integration tests on an isolated local database, never formal deployed acceptance. */
class PlanReviewApplicationTests {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private PlanReviewApplicationService service;
    private PlanReviewPersistence store;

    @BeforeEach
    void setup() throws Exception {
        var database = PlanReviewTestDatabase.create();
        jdbc = database.jdbc(); tx = database.tx(); store = database.store();
        jdbc.execute("CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE project(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(32),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('actor',true,false),('reader',true,false),('outsider',true,false)");
        jdbc.update("INSERT INTO project VALUES('project',true,false),('other',true,false)");
        jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',true),('reader','PROJECT',true)");
        jdbc.update("INSERT INTO user_role_relation VALUES('actor','admin','system'),('reader','reader','project')");
        jdbc.update("INSERT INTO user_role_permission VALUES('reader','PROJECT_TEST_PLAN:READ')");
        jdbc.update("INSERT INTO case_review_user VALUES('review','actor')");
        service = new PlanReviewApplicationService(jdbc, store, new PlanReviewAuthorization(jdbc), database.manager(), new PlanReviewPlanStateResolver(List.of()));
        run(() -> service.grant("project", 0L, true, "actor"));
    }

    private <T> T run(Supplier<T> action) { return tx.execute(s -> action.get()); }
    private String preview() { return (String) run(() -> service.preview("plan", "review", "PAIR_EXISTING", "actor")).get("previewId"); }
    @Test void legacyOwnershipBlocksNewPairAndLateLegacyOwnershipInvalidatesAnExistingPreview(){
        String preview=preview();
        jdbc.update("INSERT INTO functional_import_followup_group(id,project_id,plan_id,review_id) VALUES('old','project','plan','review')");
        assertEquals("LEGACY_ADOPTION_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.confirm(preview,"key",true,"actor"))).code());
        assertEquals("LEGACY_ADOPTION_REQUIRED",assertThrows(PlanReviewFailure.class,()->preview()).code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));
        assertEquals(0L,((Number)run(()->service.newReviewCandidates("project","",1,20,"actor")).get("total")).longValue());
    }
    private Map<String, Object> pair() { String preview = preview(); return run(() -> service.confirm(preview, "pair-key", true, "actor")); }
    private void sync(Map<String, Object> pair) { tx.executeWithoutResult(s -> service.synchronize((String) pair.get("relationId"), (String) pair.get("generation"))); }
    private Set<String> current() { return new java.util.HashSet<>(jdbc.queryForList("SELECT case_id FROM case_review_functional_case WHERE review_id='review'", String.class)); }
    @Test void schedulerCanCommitSeveralBoundedBatchesWithoutAddingOneSecondPerHundredMembers(){
        var cases=new java.util.ArrayList<Object[]>();var members=new java.util.ArrayList<Object[]>();
        for(int i=0;i<1001;i++){cases.add(new Object[]{"capacity-"+i,"project",false});members.add(new Object[]{"capacity-member-"+i,"plan","capacity-"+i});}
        jdbc.batchUpdate("INSERT INTO functional_case(id,project_id,deleted) VALUES(?,?,?)",cases);jdbc.batchUpdate("INSERT INTO test_plan_functional_case VALUES(?,?,?)",members);
        var relation=pair();service.drain();
        assertEquals("SYNCED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation WHERE id=?",String.class,relation.get("relationId")));
        assertEquals(1001,current().size());assertEquals(11L,jdbc.queryForObject("SELECT applied_batches FROM plan_review_relation",Long.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_member_history",Integer.class));
        assertEquals(11,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_audit WHERE action='SYNC_BATCH'",Integer.class));
    }
    @Test void actualTargetSqlFailureKeepsSourceAndHonorsTheOriginalIndependentRetryBudget(){
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('failure-source','plan','a')");pair();
        jdbc.execute("ALTER TABLE case_review_functional_case ADD CONSTRAINT test_target_failure CHECK(status<>'UN_REVIEWED')");
        for(int failure=1;failure<=4;failure++){
            jdbc.update("UPDATE plan_review_relation SET next_retry_at=0");service.drain();
            assertEquals(failure,jdbc.queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));assertEquals(List.of("a"),jdbc.queryForList("SELECT functional_case_id FROM test_plan_functional_case",String.class));assertTrue(current().isEmpty());
            if(failure<4){assertEquals("WAITING_RETRY",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));service.drain();assertEquals(failure,jdbc.queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));}
        }
        assertEquals("FAILED_MANUAL",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        jdbc.execute("ALTER TABLE case_review_functional_case DROP "+(System.getProperty("plan.review.mysql.config")==null?"CONSTRAINT":"CHECK")+" test_target_failure");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('new-source','plan','b')");tx.executeWithoutResult(status->store.sourceChanged("plan","actor"));service.drain();
        assertEquals("RECONFIRM_REQUIRED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));assertEquals(4,jdbc.queryForObject("SELECT attempts FROM plan_review_relation",Integer.class));assertTrue(current().isEmpty());
    }
    @Test void rebindingExistingReviewPreservesOpinionsButRejectsTheOldBindingOpinionEpoch(){
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('first-a','plan','a'),('first-b','plan','b')");
        var first=pair();sync(first);
        jdbc.update("UPDATE case_review_functional_case SET status='PASS' WHERE review_id='review' AND case_id='a'");
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status,content) VALUES('old-opinion','review','a','PASS',?)",new byte[]{1,2,3});
        run(()->service.unlink((String)first.get("relationId"),(String)first.get("generation"),"unlink-key","actor"));
        jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES('second-plan','project','TEST_PLAN','UNDERWAY')");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('second-a','second-plan','a'),('second-b','second-plan','b')");
        var preview=run(()->service.preview("second-plan","review","PAIR_EXISTING","actor"));
        var second=run(()->service.confirm((String)preview.get("previewId"),"second-pair",true,"actor"));sync(second);
        assertEquals("PASS",jdbc.queryForObject("SELECT status FROM case_review_functional_case WHERE review_id='review' AND case_id='a'",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review_history WHERE id='old-opinion'",Integer.class));
        assertEquals(2L,jdbc.queryForObject("SELECT epoch FROM plan_review_target_member WHERE review_id='review' AND case_id='a'",Long.class));
        assertEquals("MEMBERSHIP_EPOCH_STALE",assertThrows(PlanReviewFailure.class,()->tx.executeWithoutResult(status->store.opinionChanging("review",Map.of("a",1L),List.of("a")))).code());
        assertNotEquals(first.get("generation"),second.get("generation"));
    }

    private void reserveCreation(String actor, String type) {
        jdbc.update("""
                INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,
                request_json,original_actor,executing_actor,plan_id,review_id,stage,state,create_time,update_time)
                VALUES('creation','project','PLAN_SERVICE',?,'create-key','hash','{}',?,?,'plan','review','VALIDATED','PENDING',1,1)
                """, type, actor, actor);
    }
    @Test
    void operationCanBeFoundByItsOriginalCompositeKeyWithoutAnEntityId(){
        reserveCreation("actor","CREATE_PAIR");
        var found=run(()->service.operations("project",null,"actor",1,20,false,"PLAN_SERVICE","CREATE_PAIR","create-key"));
        assertEquals(1L,((Number)found.get("total")).longValue());
        var row=(Map<?,?>)((List<?>)found.get("items")).getFirst();
        assertEquals("creation",row.get("operationId"));assertEquals("create-key",row.get("requestKey"));
        assertEquals(0L,((Number)run(()->service.operations("project",null,"actor",1,20,false,"PLAN_SERVICE","CREATE_PAIR","other-key")).get("total")).longValue());
        assertEquals(0L,((Number)run(()->service.operations("project",null,"reader",1,20,false,"PLAN_SERVICE","CREATE_PAIR","create-key")).get("total")).longValue());
    }
    @Test
    void projectWideOperationLookupRequiresExplicitApprovalPermissionAndProjectFilter(){
        reserveCreation("actor","CREATE_PAIR");
        assertEquals("PERMISSION_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.operations("project",null,"reader",1,20,true))).code());
        assertEquals(1L,((Number)run(()->service.operations("project",null,"actor",1,20,true)).get("total")).longValue());
        assertEquals(0L,((Number)run(()->service.operations("other",null,"actor",1,20,true)).get("total")).longValue());
    }

    @Test
    void createdPairUsesReservedObjectsAndReplaysSameGenerationWithoutPreview() {
        reserveCreation("actor", "CREATE_PAIR");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"));
        String generation = jdbc.queryForObject("SELECT generation FROM plan_review_relation", String.class);
        tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
        assertEquals(generation, jdbc.queryForObject("SELECT generation FROM plan_review_operation WHERE id='creation'", String.class));
        assertFalse(jdbc.queryForObject("SELECT approval_pending FROM plan_review_relation", Boolean.class));
        var paired = jdbc.queryForMap("SELECT relation_id relationId,generation FROM plan_review_operation WHERE id='creation'");
        sync(paired);
        assertEquals(Set.of("a"), current());
    }

    @Test
    void creationRollbackKeepsReservationButCannotAdoptPopulatedOrEndedObjects() {
        reserveCreation("actor", "CREATE_PAIR");
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            service.linkCreatedPair("creation", "actor");
            throw new IllegalStateException("simulated source transaction failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
        assertEquals("VALIDATED", jdbc.queryForObject("SELECT stage FROM plan_review_operation", String.class));
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status) VALUES('x','review','a','PASS')");
        assertEquals("RESERVATION_CONFLICT", assertThrows(PlanReviewFailure.class,
                () -> tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"))).code());
        jdbc.update("DELETE FROM case_review_functional_case");
        tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"));
        var relation = jdbc.queryForMap("SELECT id,generation FROM plan_review_relation");
        run(() -> service.unlink((String) relation.get("id"), (String) relation.get("generation"), "unlink-created", "actor"));
        assertEquals("OPERATION_TERMINATED", assertThrows(PlanReviewFailure.class,
                () -> tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"))).code());
    }

    @Test
    void createdPairCannotBeTakenOverByAnotherActorOrUsedForManualConfirm() {
        reserveCreation("reader", "CREATE_PAIR");
        assertEquals("RESOURCE_NOT_ACCESSIBLE", assertThrows(PlanReviewFailure.class,
                () -> tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"))).code());
        jdbc.update("UPDATE plan_review_operation SET original_actor='actor',executing_actor='actor',operation_type='CONFIRM'");
        assertEquals("RESOURCE_NOT_ACCESSIBLE", assertThrows(PlanReviewFailure.class,
                () -> tx.executeWithoutResult(s -> service.linkCreatedPair("creation", "actor"))).code());
    }

    @Test
    void previewDetailsAreNamedPagedAndCannotBeReadOrConfirmedByAnotherActor() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m1','plan','a'),('m2','plan','b')");
        String preview = preview();
        var details = run(() -> service.previewItems(preview, "added", 1, 1, "actor"));
        assertEquals(2, details.get("total"));
        var items = (List<?>) details.get("items");
        assertEquals(1, items.size());
        assertEquals("case-name", ((Map<?, ?>) items.getFirst()).get("name"));
        assertEquals("RESOURCE_NOT_ACCESSIBLE", assertThrows(PlanReviewFailure.class,
                () -> run(() -> service.previewItems(preview, "added", 1, 20, "reader"))).code());
        assertEquals("RESOURCE_NOT_ACCESSIBLE", assertThrows(PlanReviewFailure.class,
                () -> run(() -> service.confirm(preview, "steal-preview", true, "reader"))).code());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
    }

    @Test
    void exitedHistoryIsReadableOnlyWithReviewReadAndDoesNotRestoreCurrentMembership() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m1','plan','a')");
        var pair = pair(); sync(pair);
        tx.executeWithoutResult(s -> {
            store.lockSource("plan"); jdbc.update("DELETE FROM test_plan_functional_case"); store.sourceChanged("plan");
        });
        sync(pair);
        var history = run(() -> service.history("review", "a", 1L, 1, 20, "actor"));
        assertEquals(1L, history.get("total"));
        assertEquals(true, history.get("readonly"));
        assertEquals(1, ((List<?>) history.get("items")).size());
        assertTrue(current().isEmpty());
        assertEquals("RESOURCE_NOT_ACCESSIBLE", assertThrows(PlanReviewFailure.class,
                () -> run(() -> service.history("review", null, null, 1, 20, "reader"))).code());
    }

    @Test
    void finalOpinionFreezesStatisticsAndLaterSourceChangesDoNotRewriteTheCompletedReview() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var pair = pair(); sync(pair);
        jdbc.update("UPDATE case_review_functional_case SET status='PASS'");
        tx.executeWithoutResult(s -> assertTrue(service.refreshOpinionStatistics("review")));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM case_review",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
        tx.executeWithoutResult(s -> {
            store.lockSource("plan"); jdbc.update("DELETE FROM test_plan_functional_case"); store.sourceChanged("plan");
        });
        sync(pair);
        assertEquals(Set.of("a"), current());
        assertEquals(1,jdbc.queryForObject("SELECT case_count FROM case_review",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
    }

    @Test
    void deletingSourceEndsGenerationAndKeepsOppositeObjectAndMembers() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var pair = pair(); sync(pair);
        tx.executeWithoutResult(s -> service.deletingObject(true,"plan","project"));
        sync(pair);
        assertEquals("SOURCE_DELETED",jdbc.queryForObject("SELECT ended_reason FROM plan_review_relation",String.class));
        assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM plan_review_operation",String.class));
        assertEquals(Set.of("a"),current());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }

    private Map<String,Object> reviewDraft() {
        return Map.of("projectId","project","name","new review","moduleId","root","reviewers",List.of("actor"),"reviewPassRule","SINGLE");
    }
    private void writeNewReview(){
        service.setReviewWriters(List.of((draft,id,actor)->{jdbc.update("INSERT INTO case_review(id,project_id,status,name) VALUES(?,?,'PREPARED',?)",id,draft.get("projectId"),draft.get("name"));jdbc.update("INSERT INTO case_review_user VALUES(?,?)",id,"actor");}));
    }
    @Test void agentNewReviewCannotBorrowAWebSessionOrAnotherCredentialAfterPreview(){
        assertEquals("CREDENTIAL_CONTEXT_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.previewNewReview("plan","agent-key",reviewDraft(),"AGENT","actor"))).code());
        var reference=new java.util.concurrent.atomic.AtomicReference<String>("opaque-reference");
        service.setExecutionCredentials(List.of(new PlanReviewExecutionCredential(){public String currentReference(){return reference.get();}public boolean validate(String value,String project,String actor){return Objects.equals(reference.get(),value);}}));
        var preview=run(()->service.previewNewReview("plan","agent-key",reviewDraft(),"AGENT","actor"));
        reference.set(null);
        assertEquals("CREDENTIAL_CONTEXT_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.confirmNewReview((String)preview.get("previewId"),"agent-key",true,"actor"))).code());
        reference.set("another-reference");
        assertEquals("CREDENTIAL_CONTEXT_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.recoverNewReview("project",(String)preview.get("operationId"),"actor"))).code());
        assertEquals("opaque-reference",jdbc.queryForObject("SELECT original_credential_id FROM plan_review_operation",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
    }
    @Test void approvedNewReviewTakeoverRequiresFreshPreviewAndExplicitConfirmationWithTheOriginalIds(){
        jdbc.update("INSERT INTO user VALUES('creator',true,false)");jdbc.update("INSERT INTO user_role VALUES('creator-role','PROJECT',true)");jdbc.update("INSERT INTO user_role_relation VALUES('creator','creator-role','project')");
        for(String permission:List.of("CASE_REVIEW:READ+ADD","PROJECT_TEST_PLAN:READ","PROJECT_TEST_PLAN:READ+UPDATE","CASE_REVIEW:READ"))jdbc.update("INSERT INTO user_role_permission VALUES('creator-role',?)",permission);
        var reference=new java.util.concurrent.atomic.AtomicReference<String>("opaque-original");
        service.setExecutionCredentials(List.of(new PlanReviewExecutionCredential(){public String currentReference(){return reference.get();}public boolean validate(String value,String project,String actor){return java.util.Objects.equals(reference.get(),value);}}));
        var original=run(()->service.previewNewReview("plan","original-key",reviewDraft(),"AGENT","creator"));
        String operation=(String)original.get("operationId"),reservedReview=jdbc.queryForObject("SELECT review_id FROM plan_review_operation",String.class);
        var operations=new PlanReviewOperationService(jdbc,new PlanReviewAuthorization(jdbc),List.of(new PlanReviewLinkedReviewResumer(service,jdbc)));
        reference.set(null);jdbc.update("UPDATE user SET enable=false WHERE id='creator'");
        var takeover=run(()->operations.takeoverPreview("project",operation,0,"actor","actor"));String approval=(String)takeover.get("previewId");
        run(()->operations.approve("project",approval,true,"actor"));run(()->operations.takeover("project",approval,0,"takeover","actor"));
        var resumed=run(()->operations.resume("project",operation,1,"resume","actor"));
        @SuppressWarnings("unchecked") var preview=(Map<String,Object>)resumed.get("newReviewPreview");
        assertEquals(operation,preview.get("operationId"));assertEquals("original-key",preview.get("requestKey"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        writeNewReview();var created=run(()->service.confirmNewReview((String)preview.get("previewId"),"original-key",true,"actor"));
        assertEquals(reservedReview,created.get("reviewId"));assertEquals("creator",jdbc.queryForObject("SELECT original_actor FROM plan_review_operation WHERE id=?",String.class,operation));
        assertEquals("opaque-original",jdbc.queryForObject("SELECT original_credential_id FROM plan_review_operation WHERE id=?",String.class,operation));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }
    @Test void terminatedNewReviewCannotGenerateAnotherPreviewForItsOriginalKey(){
        var preview=run(()->service.previewNewReview("plan","original-key",reviewDraft(),"WEB","actor"));
        var operations=new PlanReviewOperationService(jdbc,new PlanReviewAuthorization(jdbc),List.of(new PlanReviewLinkedReviewResumer(service,jdbc)));
        run(()->operations.terminate("project",(String)preview.get("operationId"),0,"actor"));
        assertEquals("OPERATION_TERMINATED",assertThrows(PlanReviewFailure.class,()->run(()->service.previewNewReview("plan","original-key",reviewDraft(),"WEB","actor"))).code());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_preview",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }

    @Test
    void linkedNewReviewUsesM03WithoutPlanCreateReviewUpdateOrMemberPermission() {
        jdbc.update("INSERT INTO user VALUES('creator',true,false)");
        jdbc.update("INSERT INTO user_role VALUES('creator-role','PROJECT',true)");
        jdbc.update("INSERT INTO user_role_relation VALUES('creator','creator-role','project')");
        for(String permission:List.of("CASE_REVIEW:READ+ADD","PROJECT_TEST_PLAN:READ","PROJECT_TEST_PLAN:READ+UPDATE","CASE_REVIEW:READ"))
            jdbc.update("INSERT INTO user_role_permission VALUES('creator-role',?)",permission);
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        service.setReviewWriters(List.of((draft,id,actor)->{
            jdbc.update("INSERT INTO case_review(id,project_id,status,name) VALUES(?,?,'PREPARED',?)",id,draft.get("projectId"),draft.get("name"));
            jdbc.update("INSERT INTO case_review_user VALUES(?,?)",id,"actor");
        }));
        var preview=run(()->service.previewNewReview("plan","m03-key",reviewDraft(),"WEB","creator"));
        assertEquals(1,run(()->service.newReviewCandidates("project","",1,20,"creator")).get("total"));
        var detail=run(()->service.previewItems((String)preview.get("previewId"),"added",1,20,"creator"));
        assertEquals(1,detail.get("total"));
        assertEquals("case-name",((Map<?,?>)((List<?>)detail.get("items")).getFirst()).get("name"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        var created=run(()->service.confirmNewReview((String)preview.get("previewId"),"m03-key",true,"creator"));
        assertEquals(preview.get("generation"),created.get("generation"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals(created.get("operationId"),run(()->service.confirmNewReview((String)preview.get("previewId"),"m03-key",true,"creator")).get("operationId"));
        sync(created);
        assertEquals("a",jdbc.queryForObject("SELECT case_id FROM case_review_functional_case WHERE review_id=?",String.class,created.get("reviewId")));
    }

    @Test
    void newReviewStalePreviewAndWriterFailureCreateNoBusinessObjectsAndKeepReservedIdentity() {
        var preview=run(()->service.previewNewReview("plan","new-key",reviewDraft(),"WEB","actor"));
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        assertEquals("PREVIEW_STALE",assertThrows(PlanReviewFailure.class,()->run(()->service.confirmNewReview((String)preview.get("previewId"),"new-key",true,"actor"))).code());
        var refreshed=run(()->service.previewNewReview("plan","new-key",reviewDraft(),"WEB","actor"));
        assertEquals(preview.get("operationId"),refreshed.get("operationId"));
        service.setReviewWriters(List.of((draft,id,actor)->{
            jdbc.update("INSERT INTO case_review(id,project_id,status) VALUES(?,'project','PREPARED')",id);
            throw new IllegalStateException("native creation failed");
        }));
        assertThrows(IllegalStateException.class,()->run(()->service.confirmNewReview((String)refreshed.get("previewId"),"new-key",true,"actor")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        assertEquals("VALIDATED",jdbc.queryForObject("SELECT stage FROM plan_review_operation",String.class));
    }

    @Test
    void newReviewPreviewDoesNotReserveAnExistingPlanAgainstOtherValidConfirmations() {
        var preview=run(()->service.previewNewReview("plan","race-new",reviewDraft(),"WEB","actor"));
        pair();
        assertEquals("RELATION_OCCUPIED",assertThrows(PlanReviewFailure.class,()->run(()->service.confirmNewReview((String)preview.get("previewId"),"race-new",true,"actor"))).code());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        assertEquals("VALIDATED",jdbc.queryForObject("SELECT stage FROM plan_review_operation WHERE request_key='race-new'",String.class));
    }

    @Test
    void listLinksAreBatchedAndRestrictedWithoutLeakingOppositeIdentity() {
        pair();
        var reader=new PlanReviewLinkReader(jdbc,new PlanReviewAuthorization(jdbc),new PlanReviewPlanStateResolver(List.of()));
        var restricted=run(()->reader.read(true,"project",List.of("plan"),"reader"));
        assertEquals(Map.of("relationStatus","RESTRICTED","restricted",true),restricted.get("plan"));
        var normal=run(()->reader.read(true,"project",List.of("plan"),"actor")).get("plan");
        assertEquals("review",((Map<?,?>)normal.get("target")).get("id"));
        assertEquals("PENDING",normal.get("syncState"));
        jdbc.update("INSERT INTO test_plan(id,project_id,type,status) VALUES('other-plan','other','TEST_PLAN','UNDERWAY'),('group','project','GROUP','UNDERWAY')");
        assertEquals("NONE",run(()->reader.read(true,"project",List.of("group"),"actor")).get("group").get("relationStatus"));
        assertEquals("RESOURCE_NOT_ACCESSIBLE",assertThrows(PlanReviewFailure.class,()->run(()->reader.read(true,"project",List.of("plan","other-plan"),"actor"))).code());
    }

    @Test
    void independentReviewCompletesAndFreezesWithoutAPlanOrServiceGrant() {
        run(()->service.grant("project",1L,false,"actor"));
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status) VALUES('r','review','a','PASS')");
        tx.executeWithoutResult(s->service.refreshOpinionStatistics("review"));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM case_review",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot WHERE relation_id IS NULL AND generation IS NULL",Integer.class));
        assertEquals("OBJECT_LOCKED",assertThrows(PlanReviewFailure.class,()->tx.executeWithoutResult(s->store.opinionChanging("review",null,List.of("a")))).code());
        jdbc.update("UPDATE functional_case SET deleted=1 WHERE id='a'");
        tx.executeWithoutResult(s->service.refreshOpinionStatistics("review"));
        assertEquals(1,jdbc.queryForObject("SELECT case_count FROM case_review",Integer.class));
        var lifecycle=new PlanReviewLifecycleService(jdbc,store,new PlanReviewAuthorization(jdbc),new PlanReviewPlanStateResolver(List.of()));
        var preview=run(()->lifecycle.preview("review","review","ARCHIVE","actor"));
        run(()->lifecycle.confirm((String)preview.get("previewId"),true,"actor"));
        assertEquals(1,run(()->lifecycle.frozen("review","review","actor")).get("total"));
    }

    @Test
    void mysqlRepeatableReadCannotUseAServiceGrantRevokedAfterItsSnapshot() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("plan.review.mysql.config")!=null);
        staleSnapshotMustReject("SELECT enabled FROM plan_review_grant WHERE project_id='project'",()->run(()->service.grant("project",1L,false,"actor")),"PERMISSION_REQUIRED");
    }

    @Test
    void mysqlRepeatableReadCannotUseAnAdministratorRoleRevokedAfterItsSnapshot() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("plan.review.mysql.config")!=null);
        staleSnapshotMustReject("SELECT enabled FROM user_role WHERE id='admin'",()->jdbc.update("UPDATE user_role SET enabled=0 WHERE id='admin'"),"RESOURCE_NOT_ACCESSIBLE");
    }

    @Test
    void unknownReviewStatusNeverBecomesACompletedSnapshot(){
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var paired=pair();sync(paired);
        jdbc.update("UPDATE case_review_functional_case SET status='UNRECOGNIZED'");
        run(()->service.refreshOpinionStatistics("review"));
        assertEquals("UNDERWAY",jdbc.queryForObject("SELECT status FROM case_review",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_frozen_snapshot",Integer.class));
    }

    @Test
    void mysqlSynchronizationReadsCommittedMembersAfterWaitingWithAnOlderSnapshot() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("plan.review.mysql.config")!=null);
        var paired=pair();sync(paired);
        var read=new java.util.concurrent.CountDownLatch(1);
        var proceed=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var future=executor.submit(()->{
                var repeatable=new TransactionTemplate(tx.getTransactionManager());
                repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                repeatable.setTimeout(15);
                repeatable.executeWithoutResult(status->{
                    jdbc.queryForList("SELECT * FROM test_plan_functional_case WHERE test_plan_id='plan'");
                    read.countDown();
                    try { if(!proceed.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Test coordination timeout"); }
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    service.synchronize((String)paired.get("relationId"),(String)paired.get("generation"));
                });
            });
            assertTrue(read.await(10,java.util.concurrent.TimeUnit.SECONDS));
            tx.executeWithoutResult(status->{
                store.lockSource("plan");
                jdbc.update("INSERT INTO test_plan_functional_case VALUES('fresh','plan','a')");
                store.sourceChanged("plan");
            });
            proceed.countDown();future.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(Set.of("a"),current());
            assertEquals(1L,jdbc.queryForObject("SELECT version FROM plan_review_source_clock WHERE plan_id='plan'",Long.class));
            assertEquals("SYNCED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
            assertEquals(1L,jdbc.queryForObject("SELECT applied_version FROM plan_review_relation",Long.class));
        } finally {proceed.countDown();executor.shutdownNow();}
    }

    private void staleSnapshotMustReject(String query,Runnable revoke,String expected) throws Exception {
        var read=new java.util.concurrent.CountDownLatch(1);var continueRequest=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            var future=executor.submit(()->{
                var repeatable=new TransactionTemplate(tx.getTransactionManager());repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);repeatable.setTimeout(15);
                return repeatable.execute(status->{jdbc.queryForList(query);read.countDown();try{if(!continueRequest.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Test coordination timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                    return assertThrows(PlanReviewFailure.class,()->service.preview("plan","review","PAIR_EXISTING","actor")).code();});
            });
            assertTrue(read.await(10,java.util.concurrent.TimeUnit.SECONDS));revoke.run();continueRequest.countDown();
            assertEquals(expected,future.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_preview",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        }finally{continueRequest.countDown();executor.shutdownNow();}
    }

    @Test
    void removingAnInvalidLegacyForeignMemberDoesNotCopyForeignCaseContentIntoHistory() {
        jdbc.update("UPDATE functional_case SET name='foreign-marker' WHERE id='foreign'");
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status) VALUES('old','review','foreign','UN_REVIEWED')");
        var paired=pair();sync(paired);
        var history=run(()->service.history("review","foreign",null,1,20,"actor"));
        assertEquals(1L,history.get("total"));
        assertFalse(io.metersphere.sdk.util.JSON.toJSONString(history).contains("foreign-marker"));
        assertTrue(current().isEmpty());
    }

    @Test
    void previewCancelDoesNotPairAndConfirmIsIdempotentThenConverges() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        String preview = preview();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
        assertTrue(current().isEmpty());
        var paired = run(() -> service.confirm(preview, "same-key", true, "actor"));
        var replay = run(() -> service.confirm(preview, "same-key", true, "actor"));
        assertEquals(paired.get("operationId"), replay.get("operationId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
        sync(paired);
        assertEquals(Set.of("a"), current());
        assertEquals("SYNCED", jdbc.queryForObject("SELECT sync_state FROM plan_review_relation", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT case_count FROM case_review", Integer.class));
    }

    @Test
    void sourceOrOpinionChangeBeforeConfirmRejectsWithoutRelationship() {
        String before = preview();
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var failure = assertThrows(PlanReviewFailure.class, () -> run(() -> service.confirm(before, "k", true, "actor")));
        assertEquals("PREVIEW_STALE", failure.code());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation", Integer.class));
        String fresh = preview();
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status) VALUES('h','review','a','PASS')");
        assertEquals("PREVIEW_STALE", assertThrows(PlanReviewFailure.class,
                () -> run(() -> service.confirm(fresh, "k2", true, "actor"))).code());
        assertTrue(current().isEmpty());
    }

    @Test
    void preserveRetainedOpinionArchiveExitAndReentryStartsUnreviewed() {
        jdbc.update("INSERT INTO functional_case(id,project_id,deleted) VALUES('c','project',false)");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m1','plan','a'),('m2','plan','b')");
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status,update_time,pos) VALUES('rb','review','b','PASS',1,5000),('rc','review','c','PASS',1,10000)");
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status) VALUES('hb','review','b','PASS'),('hc','review','c','PASS')");
        var paired = pair(); sync(paired);
        assertEquals(Set.of("a", "b"), current());
        assertEquals("PASS", jdbc.queryForObject("SELECT status FROM case_review_functional_case WHERE case_id='b'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_member_history WHERE case_id='c'", Integer.class));
        tx.executeWithoutResult(s -> {
            store.lockSource("plan");
            jdbc.update("DELETE FROM test_plan_functional_case WHERE functional_case_id='b'");
            store.sourceChanged("plan");
        });
        sync(paired);
        assertEquals(Set.of("a"), current());
        tx.executeWithoutResult(s -> {
            store.lockSource("plan");
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m3','plan','b')");
            store.sourceChanged("plan");
        });
        sync(paired);
        assertEquals("UN_REVIEWED", jdbc.queryForObject("SELECT status FROM case_review_functional_case WHERE case_id='b'", String.class));
        assertEquals(2L, jdbc.queryForObject("SELECT epoch FROM plan_review_target_member WHERE case_id='b'", Long.class));
        assertTrue(jdbc.queryForObject("SELECT deleted FROM case_review_history WHERE id='hb'", Boolean.class));
    }

    @Test
    void opinionAfterConfirmedPairPausesOldApprovalWithoutLosingRelationOrOpinion() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var paired = pair();
        jdbc.update("INSERT INTO case_review_history(id,review_id,case_id,status) VALUES('h','review','a','PASS')");
        sync(paired);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE state='ACTIVE'", Integer.class));
        assertEquals("RECONFIRM_REQUIRED", jdbc.queryForObject("SELECT sync_state FROM plan_review_relation", String.class));
        assertTrue(current().isEmpty());
        String fresh = (String) run(() -> service.preview("plan", "review", "RECONFIRM", "actor")).get("previewId");
        var resumed = run(() -> service.confirm(fresh, "resume", true, "actor"));
        assertEquals(paired.get("generation"), resumed.get("generation"));
        sync(resumed);
        assertEquals(Set.of("a"), current());
    }

    @Test
    void restrictedSummaryNeverReturnsOppositeObjectIdAndUnauthorizedPairHasZeroWrites() {
        var paired = pair();
        var restricted = run(() -> service.summary("plan", "plan", "reader"));
        assertEquals(Map.of("relationStatus", "RESTRICTED", "restricted", true), restricted);
        int previews = jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_preview", Integer.class);
        assertThrows(PlanReviewFailure.class, () -> run(() -> service.preview("plan", "review", "PAIR_EXISTING", "outsider")));
        assertEquals(previews, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_preview", Integer.class));
        assertEquals(paired.get("generation"), jdbc.queryForObject("SELECT generation FROM plan_review_relation", String.class));
    }

    @Test
    void unlinkKeepsMembersAndOldGenerationCannotWriteNewRelationship() {
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
        var old = pair(); sync(old);
        run(() -> service.unlink((String) old.get("relationId"), (String) old.get("generation"), "unlink", "actor"));
        assertEquals(Set.of("a"), current());
        String preview = preview();
        var fresh = run(() -> service.confirm(preview, "new-pair", true, "actor"));
        assertNotEquals(old.get("generation"), fresh.get("generation"));
        sync(old);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation WHERE state='ACTIVE'", Integer.class));
        assertEquals(fresh.get("generation"), jdbc.queryForObject("SELECT generation FROM plan_review_relation WHERE state='ACTIVE'", String.class));
    }

    @Test
    void revokedGrantOrFrozenReviewPreventsTargetWritesAndSourceCommitRemains() {
        var paired = pair(); sync(paired);
        run(() -> service.grant("project", 1L, false, "actor"));
        tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('m','plan','a')");
            store.sourceChanged("plan");
        });
        sync(paired);
        assertTrue(current().isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case", Integer.class));
        run(() -> service.grant("project", 2L, true, "actor"));
        sync(paired);
        assertTrue(current().isEmpty()); // grant restoration alone is not a new approval
    }
}
