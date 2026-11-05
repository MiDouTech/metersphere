package io.metersphere.functional.planreview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewOperationTests {
    JdbcTemplate jdbc;TransactionTemplate tx;PlanReviewOperationService service;
    @BeforeEach void setup() throws Exception {
        var database=PlanReviewTestDatabase.create();jdbc=database.jdbc();tx=database.tx();
        jdbc.execute("CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE project(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(32),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('old',true,false),('new',true,false),('approver',true,false),('third',true,false)");
        jdbc.update("INSERT INTO project VALUES('project',true,false),('other',true,false)");
        jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',true),('developer','PROJECT',true)");
        jdbc.update("INSERT INTO user_role_relation VALUES('approver','admin','system'),('old','developer','project'),('new','developer','project'),('third','developer','project')");
        for(String resource:List.of("PROJECT_TEST_PLAN:READ","PROJECT_TEST_PLAN:READ+ADD","PROJECT_TEST_PLAN:READ+ASSOCIATION","CASE_REVIEW:READ","CASE_REVIEW:READ+ADD"))jdbc.update("INSERT INTO user_role_permission VALUES('developer',?)",resource);
        jdbc.update("INSERT INTO plan_review_grant(project_id,enabled,revision,update_time) VALUES('project',true,1,1)");
        jdbc.update("INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,original_actor,executing_actor,plan_id,review_id,stage,state,create_time,update_time) VALUES('operation','project','PLAN_SERVICE','CREATE_PAIR','original-key','hash','{}','old','old','plan','review','VALIDATED','PENDING',1,1)");
        service=new PlanReviewOperationService(jdbc,new PlanReviewAuthorization(jdbc),List.of(provider(false)));
    }
    PlanReviewOperationResumer provider(boolean fail){return new PlanReviewOperationResumer(){
        public boolean supports(String source,String type){return "PLAN_SERVICE".equals(source)&&"CREATE_PAIR".equals(type);}
        public void resume(Map<String,Object> operation,String actor){
            jdbc.update("INSERT INTO test_plan_functional_case VALUES('recovered','plan','a')");
            if(fail)throw new IllegalStateException("Injected native transaction failure");
            jdbc.update("UPDATE plan_review_operation SET stage='TARGET_PENDING',state='PENDING' WHERE id=?",operation.get("id"));
        }
    };}
    <T>T run(Supplier<T> action){return tx.execute(status->action.get());}
    String approved(){
        var preview=run(()->service.takeoverPreview("project","operation",0,"new","old"));
        String id=(String)preview.get("previewId");run(()->service.approve("project",id,true,"approver"));return id;
    }
    @Test void sameAccountCanExplicitlyTransferAnAgentOperationToWebOnlyAfterOwnerApproval(){
        jdbc.update("UPDATE plan_review_operation SET executing_credential_id='opaque-original',original_credential_id='opaque-original'");
        assertEquals("CREDENTIAL_CONTEXT_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.resume("project","operation",0,"before-approval","old"))).code());
        var preview=run(()->service.takeoverPreview("project","operation",0,"old","old"));String approval=(String)preview.get("previewId");
        assertEquals(true,preview.get("credentialToWeb"));
        run(()->service.approve("project",approval,true,"approver"));run(()->service.takeover("project",approval,0,"explicit-web","old"));
        assertEquals("old",jdbc.queryForObject("SELECT original_actor FROM plan_review_operation",String.class));
        assertEquals("old",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation",String.class));
        assertEquals("opaque-original",jdbc.queryForObject("SELECT original_credential_id FROM plan_review_operation",String.class));
        assertNull(jdbc.queryForObject("SELECT executing_credential_id FROM plan_review_operation",String.class));
        assertDoesNotThrow(()->run(()->service.resume("project","operation",1,"resume-web","old")));
    }
    @Test void groupShellTakeoverAndResumeNeedNoReviewRightsOrServiceGrant(){
        jdbc.update("UPDATE plan_review_operation SET operation_type='COPY_GROUP',review_id=NULL");
        jdbc.update("DELETE FROM user_role_permission WHERE permission_id LIKE 'CASE_REVIEW:%'");
        jdbc.update("UPDATE plan_review_grant SET enabled=false");
        jdbc.update("DELETE FROM user_role_relation WHERE user_id='approver'");
        jdbc.update("INSERT INTO user_role VALUES('approval','PROJECT',true)");
        jdbc.update("INSERT INTO user_role_relation VALUES('approver','approval','project')");
        jdbc.update("INSERT INTO user_role_permission VALUES('approval','PLAN_REVIEW:TAKEOVER_APPROVE'),('approval','PROJECT_TEST_PLAN:READ')");
        service=new PlanReviewOperationService(jdbc,new PlanReviewAuthorization(jdbc),List.of(new PlanReviewOperationResumer(){
            public boolean supports(String source,String type){return "COPY_GROUP".equals(type);}
            public void resume(Map<String,Object> operation,String actor){jdbc.update("UPDATE plan_review_operation SET stage='COMPLETED',state='COMPLETED' WHERE id=?",operation.get("id"));}
        }));
        String approval=approved();run(()->service.takeover("project",approval,0,"handoff","new"));
        assertDoesNotThrow(()->run(()->service.resume("project","operation",1,"resume","new")));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT state FROM plan_review_operation",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }
    @Test void approvalIsExplicitAndConsumedWithIdentityChangeAndSameRequestReplay(){
        String id=approved();var result=run(()->service.takeover("project",id,0,"takeover-key","new"));
        assertEquals(1L,result.get("revision"));
        assertEquals("old",jdbc.queryForObject("SELECT original_actor FROM plan_review_operation",String.class));
        assertEquals("new",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation",String.class));
        assertEquals("approver",jdbc.queryForObject("SELECT approver FROM plan_review_operation",String.class));
        assertTrue((Boolean)run(()->service.takeover("project",id,0,"takeover-key","new")).get("replayed"));
        assertEquals("APPROVAL_CONSUMED",assertThrows(PlanReviewFailure.class,()->run(()->service.takeover("project",id,0,"different-key","new"))).code());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_audit WHERE action='TAKEOVER'",Integer.class));
    }
    @Test void previewWithoutApprovalCannotChangeActor(){
        String id=(String)run(()->service.takeoverPreview("project","operation",0,"new","old")).get("previewId");
        assertEquals("APPROVAL_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.takeover("project",id,0,"key","new"))).code());
        assertEquals("PERMISSION_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.approve("project",id,true,"old"))).code());
        assertEquals("old",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation",String.class));
        assertNull(jdbc.queryForObject("SELECT consumed_at FROM plan_review_takeover",Long.class));
    }
    @Test void newExecutingActorCanResumeAfterOriginalAccountIsRevoked(){
        String id=approved();run(()->service.takeover("project",id,0,"takeover","new"));
        jdbc.update("UPDATE user SET enable=0 WHERE id='old'");
        var first=run(()->service.resume("project","operation",1,"resume","new"));
        var replay=run(()->service.resume("project","operation",1,"resume","new"));
        assertEquals(((Number)first.get("revision")).longValue(),((Number)replay.get("revision")).longValue());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation_command",Integer.class));
        assertEquals("RESOURCE_NOT_ACCESSIBLE",assertThrows(PlanReviewFailure.class,()->run(()->service.resume("project","operation",2,"third","third"))).code());
    }
    @Test void nativeFailureRollsBackFactsRevisionAndReplayReceipt(){
        service=new PlanReviewOperationService(jdbc,new PlanReviewAuthorization(jdbc),List.of(provider(true)));
        assertThrows(IllegalStateException.class,()->run(()->service.resume("project","operation",0,"resume","old")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case",Integer.class));
        assertEquals(0L,jdbc.queryForObject("SELECT revision FROM plan_review_operation",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation_command",Integer.class));
    }
    @Test void expiredOrStaleApprovalCannotBeConsumed(){
        String id=approved();jdbc.update("UPDATE plan_review_takeover SET expires_at=1 WHERE id=?",id);
        assertEquals("PREVIEW_EXPIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.takeover("project",id,0,"key","new"))).code());
        jdbc.update("UPDATE plan_review_takeover SET expires_at=? WHERE id=?",System.currentTimeMillis()+600000,id);
        jdbc.update("UPDATE plan_review_operation SET revision=1");
        assertEquals("OPERATION_STALE",assertThrows(PlanReviewFailure.class,()->run(()->service.takeover("project",id,0,"key","new"))).code());
        assertNull(jdbc.queryForObject("SELECT consumed_at FROM plan_review_takeover",Long.class));
    }
    @Test void terminationRetainsExistingObjectsAndBlocksResume(){
        run(()->service.terminate("project","operation",0,"old"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals("OPERATION_TERMINATED",assertThrows(PlanReviewFailure.class,()->run(()->service.resume("project","operation",1,"resume","old"))).code());
    }
    @Test void endedGenerationStopsApprovedTakeoverWithoutRecreatingRelation(){
        jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_changed_at,original_actor,create_time,update_time) VALUES('relation','project','plan','review','g1','plan','review','ACTIVE','SYNCED',1,'old',1,1)");
        jdbc.update("UPDATE plan_review_operation SET relation_id='relation',generation='g1'");
        String id=approved();jdbc.update("UPDATE plan_review_relation SET state='ENDED',active_plan_id=NULL,active_review_id=NULL");
        assertEquals("RELATION_CHANGED",assertThrows(PlanReviewFailure.class,()->run(()->service.takeover("project",id,0,"key","new"))).code());
        assertEquals("old",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_relation",Integer.class));
        assertNull(jdbc.queryForObject("SELECT consumed_at FROM plan_review_takeover",Long.class));
    }
    @Test void currentPermissionRevocationBlocksResumeBeforeAnyNativeWrite(){
        jdbc.update("DELETE FROM user_role_permission WHERE permission_id='CASE_REVIEW:READ+ADD'");
        assertEquals("PERMISSION_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.resume("project","operation",0,"key","old"))).code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case",Integer.class));
        assertEquals(0L,jdbc.queryForObject("SELECT revision FROM plan_review_operation",Long.class));
    }
    @Test void identityAndApprovalConsumptionRollbackTogetherIfAuditCannotCommit(){
        String id=approved();
        jdbc.execute("ALTER TABLE plan_review_audit ADD CONSTRAINT reject_takeover_test CHECK(action<>'TAKEOVER')");
        assertThrows(org.springframework.dao.DataAccessException.class,()->run(()->service.takeover("project",id,0,"key","new")));
        assertEquals("old",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation",String.class));
        assertEquals(0L,jdbc.queryForObject("SELECT revision FROM plan_review_operation",Long.class));
        assertNull(jdbc.queryForObject("SELECT consumed_at FROM plan_review_takeover",Long.class));
    }
    @Test void parallelReplayConsumesApprovalOnce() throws Exception {
        String id=approved();var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try{
            java.util.concurrent.Callable<Map<String,Object>> action=()->{assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS));return run(()->service.takeover("project",id,0,"same-key","new"));};
            var first=executor.submit(action);var second=executor.submit(action);start.countDown();
            boolean replay1=(Boolean)first.get(20,java.util.concurrent.TimeUnit.SECONDS).get("replayed");
            boolean replay2=(Boolean)second.get(20,java.util.concurrent.TimeUnit.SECONDS).get("replayed");
            assertNotEquals(replay1,replay2);
            assertEquals(1L,jdbc.queryForObject("SELECT revision FROM plan_review_operation",Long.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_audit WHERE action='TAKEOVER'",Integer.class));
        }finally{start.countDown();executor.shutdownNow();}
    }
    @Test void emptyCreationDoesNotGainAnExtraMembershipPermissionRequirementDuringRecovery(){
        jdbc.update("DELETE FROM user_role_permission WHERE permission_id='PROJECT_TEST_PLAN:READ+ASSOCIATION'");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('existing','plan','a')");
        service=new PlanReviewOperationService(jdbc,new PlanReviewAuthorization(jdbc),List.of(new PlanReviewOperationResumer(){
            public boolean supports(String source,String type){return true;}
            public void resume(Map<String,Object> operation,String actor){jdbc.update("UPDATE plan_review_operation SET stage='TARGET_PENDING' WHERE id=?",operation.get("id"));}
        }));
        var result=run(()->service.resume("project","operation",0,"key","old"));
        assertEquals("TARGET_PENDING",result.get("stage"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case",Integer.class));
    }
    @Test void agentCredentialCannotBeSilentlyReplacedByWebButApprovedTakeoverUsesNewWebIdentity(){
        jdbc.update("UPDATE plan_review_operation SET executing_credential_id='opaque-reference'");
        assertEquals("CREDENTIAL_CONTEXT_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.resume("project","operation",0,"key","old"))).code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan_functional_case",Integer.class));
        String id=approved();run(()->service.takeover("project",id,0,"takeover","new"));
        assertNull(jdbc.queryForObject("SELECT executing_credential_id FROM plan_review_operation",String.class));
        assertEquals("TARGET_PENDING",run(()->service.resume("project","operation",1,"resume","new")).get("stage"));
    }
    @Test void importChildCannotBeTakenOverWithoutItsParentWorkerIdentity(){
        jdbc.update("UPDATE plan_review_operation SET request_key='import:job'");
        assertEquals("RECOVERY_CONTEXT_REQUIRED",assertThrows(PlanReviewFailure.class,()->run(()->service.takeoverPreview("project","operation",0,"new","old"))).code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_takeover",Integer.class));
    }
    @Test void takeoverRecordsAreVisibleOnlyToParticipantsOrAuthorizedProjectApprovers(){
        approved();
        assertEquals(0,((List<?>)run(()->service.takeovers("project","third")).get("items")).size());
        assertEquals(1,((List<?>)run(()->service.takeovers("project","new")).get("items")).size());
        assertEquals(1,((List<?>)run(()->service.takeovers("project","approver")).get("items")).size());
    }
}
