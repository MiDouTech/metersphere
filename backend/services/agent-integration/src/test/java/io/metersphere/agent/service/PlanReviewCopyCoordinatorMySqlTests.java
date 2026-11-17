package io.metersphere.agent.service;

import io.metersphere.functional.planreview.*;
import io.metersphere.plan.domain.*;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL reservation/orchestration transactions; native object factory is a contract double. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class PlanReviewCopyCoordinatorMySqlTests {
    ImportFollowupCoordinatorMySqlTests fixture;
    PlanReviewCopyCoordinator coordinator;
    PlanReviewCreationJournal journal;
    TestPlanMapper mapper;
    PlanReviewAuthorization authorization;
    final Map<String,TestPlan> sources=new LinkedHashMap<>();
    final Set<String> failures=new HashSet<>();
    final Map<String,Integer> creations=new HashMap<>();
    final PlanReviewSettings settings=new PlanReviewSettings("Review","root",List.of("reviewer"),"SINGLE",false);

    @BeforeEach void setup() throws Exception {
        fixture=new ImportFollowupCoordinatorMySqlTests();fixture.setup();
        fixture.jdbc.execute("ALTER TABLE test_plan ADD module_id VARCHAR(50),ADD group_id VARCHAR(50),ADD pos BIGINT");
        fixture.jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50),project_id VARCHAR(50),deleted BOOLEAN)");
        fixture.jdbc.execute("CREATE TABLE test_plan_functional_case(test_plan_id VARCHAR(50),functional_case_id VARCHAR(50))");
        authorization=mock(PlanReviewAuthorization.class);
        when(authorization.current(anyString(),anyString(),anyBoolean())).thenReturn(new PlanReviewPolicy.Access(EnumSet.allOf(PlanReviewPolicy.Permission.class),true));
        var nativeJournal=new PlanReviewCreationJournal();
        ReflectionTestUtils.setField(nativeJournal,"jdbc",fixture.jdbc);
        ReflectionTestUtils.setField(nativeJournal,"authorization",authorization);
        journal=fixture.transactional(nativeJournal);
        var requests=new PlanReviewCopyRequestJournal();
        ReflectionTestUtils.setField(requests,"jdbc",fixture.jdbc);
        ReflectionTestUtils.setField(requests,"authorization",authorization);
        mapper=mock(TestPlanMapper.class);
        when(mapper.selectByPrimaryKey(anyString())).thenAnswer(call->sources.get(call.getArgument(0)));
        when(mapper.selectByExample(any())).thenAnswer(call->sources.values().stream().filter(plan->"TEST_PLAN".equals(plan.getType())&&"group".equals(plan.getGroupId())).toList());
        var copies=mock(TestPlanBatchOperationService.class);
        doAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"orchestrator must suspend caller transaction");
            String source=call.getArgument(1),target=call.getArgument(2);boolean group=call.getArgument(5);
            Long position=call.getArgument(6);
            fixture.tx.executeWithoutResult(status->{
                var reservation=journal.copyReservation(source);
                if(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM test_plan WHERE id=?",Integer.class,reservation.planId())>0)return;
                fixture.jdbc.update("INSERT INTO test_plan(id,project_id,status,name,type,module_id,group_id,pos) VALUES(?,'project','PREPARED',?,?,?, ?,?)",reservation.planId(),source,group?"GROUP":"TEST_PLAN",group?target:"root",group?"NONE":target,position);
                if(failures.contains(source))throw new IllegalStateException("Injected failure after plan insert");
                if(!group){
                    fixture.jdbc.update("INSERT INTO case_review VALUES(?,'project','PREPARED','Review')",reservation.reviewId());
                    String relation=UUID.randomUUID().toString(),generation=UUID.randomUUID().toString();
                    fixture.jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_changed_at,original_actor,create_time,update_time) VALUES(?,'project',?,?,?,?,?,'ACTIVE','PENDING',1,'user',1,1)",relation,reservation.planId(),reservation.reviewId(),generation,reservation.planId(),reservation.reviewId());
                    fixture.jdbc.update("UPDATE plan_review_operation SET relation_id=?,generation=? WHERE id=?",relation,generation,reservation.id());
                }
                fixture.jdbc.update("UPDATE plan_review_operation SET stage=?,state=? WHERE id=?",group?"COMPLETED":"TARGET_PENDING",group?"COMPLETED":"PENDING",reservation.id());
                creations.merge(source,1,Integer::sum);
            });return null;
        }).when(copies).copyReservedItem(anyString(),anyString(),anyString(),anyString(),anyString(),anyBoolean(),nullable(Long.class));
        var target=new PlanReviewCopyCoordinator();
        ReflectionTestUtils.setField(target,"mapper",mapper);ReflectionTestUtils.setField(target,"jdbc",fixture.jdbc);
        ReflectionTestUtils.setField(target,"journal",journal);ReflectionTestUtils.setField(target,"requests",fixture.transactional(requests));
        ReflectionTestUtils.setField(target,"copies",copies);ReflectionTestUtils.setField(target,"authorization",authorization);
        coordinator=fixture.transactional(target);
    }
    @AfterEach void cleanup(){if(fixture!=null)fixture.cleanup();}
    TestPlan source(String id,String type,String group){
        var plan=new TestPlan();plan.setId(id);plan.setProjectId("project");plan.setName(id);plan.setType(type);plan.setStatus("PREPARED");plan.setGroupId(group);plan.setModuleId("root");sources.put(id,plan);return plan;
    }
    @Test void partialChildrenCommitIndependentlyAndRetryPreservesIdsScopeAndOrder(){
        var group=source("group","GROUP","NONE");source("a","TEST_PLAN","group");source("b","TEST_PLAN","group");source("c","TEST_PLAN","group");failures.add("b");
        var first=coordinator.execute("project",List.of(group),"root","MODULE","request",settings,"user",true);
        assertEquals(3,first.createdCount());assertEquals(1,first.failedCount());
        assertEquals(3,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));
        assertEquals(2,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        var reserved=fixture.jdbc.queryForList("SELECT id,plan_id,review_id FROM plan_review_operation ORDER BY id");
        source("d","TEST_PLAN","group");failures.clear();
        var retry=coordinator.execute("project",List.of(group),"root","MODULE","request",settings,"user",true);
        assertEquals(first.requestId(),retry.requestId());assertEquals(4,retry.createdCount());assertEquals(0,retry.failedCount());
        assertEquals(reserved,fixture.jdbc.queryForList("SELECT id,plan_id,review_id FROM plan_review_operation ORDER BY id"));
        assertEquals(Map.of("group",1,"a",1,"b",1,"c",1),creations);
        assertEquals(List.of(5000L,10000L,15000L),fixture.jdbc.queryForList("SELECT pos FROM test_plan WHERE type='TEST_PLAN' ORDER BY pos",Long.class));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation r JOIN test_plan p ON p.id=r.plan_id WHERE p.type='GROUP'",Integer.class));
        assertEquals(3,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }
    @Test void callerRollbackCannotUndoSuccessfulChildrenOrDurableReservations(){
        var a=source("a","TEST_PLAN","NONE");
        assertThrows(IllegalStateException.class,()->fixture.tx.executeWithoutResult(status->{
            fixture.jdbc.update("INSERT INTO default_hub_sync_job VALUES('outer','project','user','IMPORT_CASE','PENDING')");
            var outcome=coordinator.execute("project",List.of(a),"root","MODULE","request",settings,"user",true);
            assertEquals(1,outcome.createdCount());throw new IllegalStateException("outer rollback");
        }));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM default_hub_sync_job",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));
    }
    @Test void emptyGroupRequiresOnlyPlanPermissionsAndCreatesNoReview(){
        var group=source("group","GROUP","NONE");
        when(authorization.current(anyString(),anyString(),anyBoolean())).thenReturn(new PlanReviewPolicy.Access(EnumSet.of(PlanReviewPolicy.Permission.PLAN_READ,PlanReviewPolicy.Permission.PLAN_ADD),false));
        assertEquals(false,coordinator.scope("project",List.of(group),List.of("group"),"user").get("settingsRequired"));
        var result=coordinator.execute("project",List.of(group),"root","MODULE","empty",null,"user",true);
        assertEquals(1,result.createdCount());assertEquals(0,result.failedCount());
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertNull(result.items().getFirst().reviewId());
    }
    @Test void sameKeyCannotChangeTargetOrScopeAndForeignSourcesLeaveNoMarkers(){
        var a=source("a","TEST_PLAN","NONE");
        coordinator.execute("project",List.of(a),"root","MODULE","request",settings,"user",true);
        assertThrows(PlanReviewFailure.class,()->coordinator.execute("project",List.of(a),"different","MODULE","request",settings,"user",true));
        var foreign=source("foreign","TEST_PLAN","NONE");foreign.setProjectId("foreign-project");
        assertThrows(PlanReviewFailure.class,()->coordinator.execute("project",List.of(foreign),"root","MODULE","foreign",settings,"user",true));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_copy_request",Integer.class));
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));
    }
    @Test void missingSuccessfulGroupIsNeverRecreated(){
        var group=source("group","GROUP","NONE");
        var result=coordinator.execute("project",List.of(group),"root","MODULE","empty",null,"user",true);
        var item=result.items().getFirst();fixture.jdbc.update("DELETE FROM test_plan WHERE id=?",item.planId());
        var reservation=new PlanReviewCreationJournal.Reservation(item.operationId(),item.planId(),null,"COMPLETED");
        assertThrows(PlanReviewFailure.class,()->fixture.tx.executeWithoutResult(status->journal.lockAndExists(reservation,"project","user")));
        assertEquals(0,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));
    }
}
