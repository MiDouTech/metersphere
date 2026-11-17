package io.metersphere.functional.planreview;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewNoticeTests {
    JdbcTemplate jdbc;TransactionTemplate tx;PlanReviewNoticeService service;
    final Set<String> eligible=new HashSet<>(Set.of("creator","owner"));
    final Set<String> owners=new HashSet<>(Set.of("owner"));
    boolean fail;
    PlanReviewNoticeChannel channel;
    @BeforeEach void setup() throws Exception {
        var db=PlanReviewTestDatabase.create();jdbc=db.jdbc();tx=db.tx();
        jdbc.execute("CREATE TABLE user(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE project(id VARCHAR(50),enable BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50),type VARCHAR(20),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))");
        jdbc.update("INSERT INTO user VALUES('manager',true,false),('reader',true,false)");jdbc.update("INSERT INTO project VALUES('project',true,false)");
        jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',true),('reader','PROJECT',true)");
        jdbc.update("INSERT INTO user_role_relation VALUES('manager','admin','system'),('reader','reader','project')");jdbc.update("INSERT INTO user_role_permission VALUES('reader','PROJECT_TEST_PLAN:READ')");
        jdbc.update("INSERT INTO plan_review_grant(project_id,enabled,update_time) VALUES('project',true,1)");
        jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_version,applied_version,source_changed_at,original_actor,create_time,update_time) VALUES('relation','project','plan','review','g','plan','review','ACTIVE','PENDING',1,0,?,'creator',1,1)",System.currentTimeMillis()-40000);
        jdbc.execute("CREATE TABLE test_inbox(id BIGINT AUTO_INCREMENT PRIMARY KEY,notice_id VARCHAR(50) UNIQUE,recipient VARCHAR(50),recovered BOOLEAN,ended BOOLEAN,type VARCHAR(20))");
        channel=new PlanReviewNoticeChannel(){
            public Set<String> recipients(Map<String,Object> relation,String reason){var result=new HashSet<>(owners);result.add("creator");return result;}
            public Set<String> responsibles(String project,String reason){return Set.copyOf(owners);}
            public String eligibility(String project,String recipient){return eligible.contains(recipient)?"CURRENT":"ACCOUNT_OR_PROJECT_ACCESS_UNAVAILABLE";}
            public long publish(String recipient,String notice,long detected,boolean recovered,boolean ended,String type){
                jdbc.update("INSERT INTO test_inbox(notice_id,recipient,recovered,ended,type) VALUES(?,?,?,?,?)",notice,recipient,recovered,ended,type);
                if(fail)throw new IllegalStateException("Injected inbox outage after insert");
                return jdbc.queryForObject("SELECT id FROM test_inbox WHERE notice_id=?",Long.class,notice);
            }
        };
        service=new PlanReviewNoticeService(jdbc,new PlanReviewAuthorization(jdbc),channel,db.manager());
    }
    void collect(){tx.executeWithoutResult(status->service.collect("project","relation","g"));}
    void deliver(){for(var row:jdbc.queryForList("SELECT id,project_id,relation_id,generation FROM plan_review_notice WHERE state IN ('PENDING','WAITING_RETRY')"))tx.executeWithoutResult(status->service.deliver(row));}
    String fault(){return jdbc.queryForObject("SELECT id FROM plan_review_fault ORDER BY fault_round LIMIT 1",String.class);}
    void recovered(){jdbc.update("UPDATE plan_review_relation SET sync_state='SYNCED',applied_version=source_version,approval_pending=0");collect();}
    void due(){jdbc.update("UPDATE plan_review_notice SET next_retry_at=0 WHERE readable_at IS NULL");}
    @Test void healthyInboxIsReadableOnceAndRepeatedDetectionKeepsSameFaultCohort(){
        collect();deliver();collect();deliver();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_fault",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE state='READABLE'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE readable_at-detected_at>60000",Integer.class));
    }
    @Test void independentFailureBudgetStopsAfterInitialAndThreeRetries(){
        fail=true;
        for(int attempt=1;attempt<=4;attempt++){
            due();service.drain();
            assertEquals(attempt,jdbc.queryForObject("SELECT attempts FROM plan_review_notice LIMIT 1",Integer.class));
            if(attempt<4){long interval=new long[]{10000,30000,60000}[attempt-1];assertEquals(interval,jdbc.queryForObject("SELECT next_retry_at-last_finished_at FROM plan_review_notice LIMIT 1",Long.class));}
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE state='FAILED_MANUAL'",Integer.class));
        fail=false;jdbc.update("UPDATE plan_review_relation SET source_version=2");service.drain();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT attempts FROM plan_review_notice LIMIT 1",Integer.class));
        assertEquals(0L,jdbc.queryForObject("SELECT applied_version FROM plan_review_relation",Long.class));
    }
    @Test void recoveryWaitsForEachOriginalFaultAndDoesNotResetExhaustedBudgets(){
        collect();jdbc.update("UPDATE plan_review_notice SET attempts=4,state='FAILED_MANUAL'");recovered();deliver();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        var preview=tx.execute(status->service.preview("project",fault(),"manager"));
        tx.executeWithoutResult(status->service.confirm("project",(String)preview.get("previewId"),true,"manager"));
        due();deliver();due();deliver();
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox r JOIN test_inbox f ON r.recipient=f.recipient AND f.type='FAULT' WHERE r.type='RECOVERY' AND r.id<f.id",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox WHERE type='FAULT' AND recovered=true",Integer.class));
        assertEquals(Boolean.TRUE,tx.execute(status->service.confirm("project",(String)preview.get("previewId"),true,"manager")).get("replayed"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_audit WHERE action='NOTICE_RECOVER'",Integer.class));
    }
    @Test void accountEligibilityIsRecheckedAtDeliveryAndReplacementIsNeverAutomatic(){
        collect();eligible.remove("owner");deliver();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE state='INELIGIBLE'",Integer.class));
        owners.clear();owners.add("new-owner");eligible.add("new-owner");collect();deliver();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE recipient_id='new-owner'",Integer.class));
        var preview=tx.execute(status->service.preview("project",fault(),"manager"));
        tx.executeWithoutResult(status->service.confirm("project",(String)preview.get("previewId"),true,"manager"));deliver();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox WHERE recipient='new-owner'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox WHERE recipient='creator'",Integer.class));
    }
    @Test void previewDriftAndMissingRecoveryPermissionProduceZeroDeliveryOrBudgetReset(){
        collect();jdbc.update("UPDATE plan_review_notice SET attempts=4,state='FAILED_MANUAL'");
        assertThrows(PlanReviewFailure.class,()->tx.executeWithoutResult(status->service.preview("project",fault(),"reader")));
        var preview=tx.execute(status->service.preview("project",fault(),"manager"));eligible.remove("owner");
        assertEquals("PREVIEW_STALE",assertThrows(PlanReviewFailure.class,()->tx.executeWithoutResult(status->service.confirm("project",(String)preview.get("previewId"),true,"manager"))).code());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE attempts=4",Integer.class));
        assertThrows(PlanReviewFailure.class,()->service.list("other","relation","manager"));
    }
    @Test void endedGenerationAllowsHistoricalFaultOnlyAndNeverRestartsMembers(){
        collect();jdbc.update("UPDATE plan_review_relation SET state='ENDED',sync_state='ENDED',active_plan_id=NULL,active_review_id=NULL");collect();deliver();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM test_inbox WHERE ended=true",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE type='RECOVERY'",Integer.class));
        assertEquals("ENDED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_review_functional_case",Integer.class));
    }
    @Test void disabledLegacyGrantDoesNotCreateAGrantFaultAndDelayStillRecovers(){
        jdbc.update("UPDATE plan_review_grant SET enabled=false");collect();
        assertEquals("DELAYED",jdbc.queryForObject("SELECT reason FROM plan_review_fault",String.class));
        jdbc.update("UPDATE plan_review_grant SET enabled=true");collect();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE type='RECOVERY'",Integer.class));
        recovered();assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice WHERE type='RECOVERY' AND reason='DELAYED'",Integer.class));
    }
    @Test void disabledProjectPausesWithoutCreatingAnEditableDelayFault(){
        jdbc.update("UPDATE project SET enable=false");collect();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_fault",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_notice",Integer.class));
    }
    @Test void nativeStorageStateUsesTheExistingBusinessStateProviderRatherThanAssumingEditable(){
        jdbc.update("UPDATE test_plan SET status='NOT_ARCHIVED'");
        service=new PlanReviewNoticeService(jdbc,new PlanReviewAuthorization(jdbc),channel,tx.getTransactionManager(),new PlanReviewPlanStateResolver(List.of(new PlanReviewPlanStateProvider(){public String businessState(String plan){return "UNDERWAY";}})));
        collect();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_fault",Integer.class));
    }
    @Test void anUnknownPlanStateDoesNotProduceAFalseEditableDelayFault(){
        jdbc.update("UPDATE test_plan SET status=NULL");collect();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_fault",Integer.class));
    }
}
