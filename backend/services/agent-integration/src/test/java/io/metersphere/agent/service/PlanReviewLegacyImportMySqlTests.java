package io.metersphere.agent.service;

import io.metersphere.functional.planreview.*;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Full real-MySQL migration and original import adoption, including a distinct rehearsal schema. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class PlanReviewLegacyImportMySqlTests {
    ImportFollowupCoordinatorMySqlTests fixture;JdbcTemplate jdbc;PlanReviewLegacyMigrationService migration;PlanReviewLegacyInventory inventory;
    final List<String> schemas=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        fixture=new ImportFollowupCoordinatorMySqlTests();fixture.setup();jdbc=fixture.jdbc;
        jdbc.execute("ALTER TABLE test_plan ADD num BIGINT DEFAULT 1,ADD update_time BIGINT DEFAULT 1,ADD group_id VARCHAR(50) DEFAULT 'NONE',ADD module_id VARCHAR(50) DEFAULT 'root'");
        jdbc.execute("ALTER TABLE case_review ADD num BIGINT DEFAULT 1,ADD update_time BIGINT DEFAULT 1,ADD case_count INT DEFAULT 0,ADD pass_rate DECIMAL(5,2) DEFAULT 0");
        for(String ddl:List.of("CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted BOOLEAN)","CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(32),enabled BOOLEAN)","CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50))","CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100))",
                "CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),deleted BOOLEAN)","CREATE TABLE test_plan_functional_case(id VARCHAR(50) PRIMARY KEY,test_plan_id VARCHAR(50),functional_case_id VARCHAR(50))",
                "CREATE TABLE case_review_functional_case(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(64),create_time BIGINT,create_user VARCHAR(50),update_time BIGINT,pos BIGINT)",
                "CREATE TABLE case_review_history(id VARCHAR(50) PRIMARY KEY,review_id VARCHAR(50),case_id VARCHAR(50),status VARCHAR(64),content BLOB,deleted BOOLEAN,abandoned BOOLEAN)","CREATE TABLE case_review_module(id VARCHAR(50),project_id VARCHAR(50))"))jdbc.execute(ddl);
        jdbc.update("INSERT INTO user VALUES('developer',1,0),('user',1,0),('reviewer',1,0),('new',1,0),('owner',1,0)");
        jdbc.update("INSERT INTO user_role VALUES('admin','SYSTEM',1),('members','PROJECT',1),('owner','PROJECT',1)");
        jdbc.update("INSERT INTO user_role_relation VALUES('developer','admin','system'),('user','admin','system'),('new','admin','system'),('user','members','project'),('new','members','project'),('reviewer','members','project'),('owner','owner','project')");
        jdbc.update("INSERT INTO user_role_permission VALUES('members','CASE_REVIEW:READ'),('members','CASE_REVIEW:READ+REVIEW'),('owner','PLAN_REVIEW:TAKEOVER_APPROVE'),('owner','PROJECT_TEST_PLAN:READ'),('owner','CASE_REVIEW:READ')");
        jdbc.update("INSERT INTO functional_case VALUES('a','project',0),('b','project',0)");
        var auth=new PlanReviewAuthorization(jdbc);var store=new PlanReviewPersistence(jdbc);var states=new PlanReviewPlanStateResolver(List.of());
        var app=new PlanReviewApplicationService(jdbc,store,auth,fixture.tx.getTransactionManager(),states);
        inventory=new PlanReviewLegacyInventory(jdbc,auth,states);migration=new PlanReviewLegacyMigrationService(jdbc,auth,store,inventory,new PlanReviewLegacySnapshot(jdbc,states),app);
        migration.setAdopters(List.of(new PlanReviewLegacyImportAdopter(jdbc,states)));
        String config=System.getProperty("plan.review.mysql.config");var properties=new Properties();try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(config))){properties.load(reader);}
        String schema="msp12_pr_"+UUID.randomUUID().toString().replace("-","");schemas.add(schema);fixture.admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
        String url=properties.getProperty("url");int options=url.indexOf('?');var source=new DriverManagerDataSource(url.substring(0,options)+schema+url.substring(options),properties.getProperty("user"),properties.getProperty("password"));
        migration.setSandboxes(List.of(new PlanReviewLegacyJdbcSandbox(jdbc,new JdbcTemplate(source))));
        fixture.file();jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
        jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','a'),('file',2,'CREATE','SUCCESS','b')");
    }
    @AfterEach void cleanup(){if(fixture!=null){for(String schema:schemas)if(schema.matches("msp12_pr_[a-f0-9]{32}"))fixture.admin.execute("DROP DATABASE "+schema);fixture.cleanup();}}
    void old(String plan,String review){
        var request=fixture.decision();request.setReviewName("Original review");
        jdbc.update("INSERT INTO functional_import_followup(job_id,project_id,user_id,policy,request_json,group_id,plan_id,review_id,state,create_time,update_time) VALUES('file','project','user','CREATE_NEW_PAIR',?,'old-group',?,?,'FAILED',1,1)",JSON.toJSONString(request),plan,review);
    }
    Map<String,Object> migrate(Map<String,String> groups,PlanReviewLegacyDecision.Job job){
        var selection=new PlanReviewLegacyDecision("project","migration",(String)inventory.scan("project","developer").get("manifestHash"),groups,Map.of("file",job));
        var preview=fixture.tx.execute(s->migration.preview(selection,"developer"));String change=(String)preview.get("id"),hash=(String)preview.get("snapshot_hash");
        fixture.tx.execute(s->migration.backup("project",change,"developer"));fixture.tx.execute(s->migration.rehearse("project",change,"developer"));
        fixture.tx.execute(s->migration.approve("project",change,hash,"1".repeat(64),"2".repeat(40),true,"owner"));
        var result=fixture.tx.execute(s->migration.execute("project",change,hash,true,"developer"));assertEquals(JSON.toJSONString(result),JSON.toJSONString(fixture.tx.execute(s->migration.execute("project",change,hash,true,"developer"))));return result;
    }
    @Test void noPlanStageReservesOnePairAndPreservesOriginalIdentityAfterApprovedWebTransfer(){
        old(null,null);jdbc.update("UPDATE functional_import_job SET token_id='old-agent-reference'");jdbc.update("UPDATE user SET enable=0 WHERE id='user'");
        migrate(Map.of(),new PlanReviewLegacyDecision.Job("ADOPT","new",null,"WEB"));
        var parent=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE source_kind='IMPORT_FOLLOWUP'");var child=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE source_kind='PLAN_SERVICE'");
        assertEquals("user",parent.get("original_actor"));assertEquals("new",parent.get("executing_actor"));assertEquals("old-agent-reference",parent.get("original_credential_id"));assertNull(parent.get("executing_credential_id"));assertEquals("owner",parent.get("approver"));
        assertEquals(parent.get("plan_id"),child.get("plan_id"));assertEquals(parent.get("review_id"),child.get("review_id"));assertEquals("VALIDATED",child.get("stage"));assertEquals("FAILED",parent.get("state"));
        assertEquals("MIGRATED_V2",jdbc.queryForObject("SELECT state FROM functional_import_followup",String.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup WHERE state='PENDING'",Integer.class));
        assertEquals(0,jdbc.update("UPDATE functional_import_followup SET state='PENDING' WHERE job_id='file' AND state IN ('FAILED','SUCCEEDED')"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));assertEquals(List.of("a","b"),jdbc.queryForList("SELECT case_id FROM plan_review_import_followup_item ORDER BY case_id",String.class));
    }
    @Test void planOnlyStageKeepsTheRealPlanAndItsCommittedFlagsAndOnlyReservesTheMissingReview(){
        jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES('original-plan','project','PREPARED','Original plan')");jdbc.update("INSERT INTO test_plan_functional_case VALUES('original-member','original-plan','a')");old("original-plan",null);
        jdbc.update("INSERT INTO functional_import_followup_item VALUES('file','a',1,0)");
        migrate(Map.of(),new PlanReviewLegacyDecision.Job("ADOPT","user",null));
        var child=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE source_kind='PLAN_SERVICE'");assertEquals("original-plan",child.get("plan_id"));assertEquals("LEGACY_PLAN_CREATED",child.get("stage"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
        assertEquals(true,jdbc.queryForObject("SELECT plan_done FROM plan_review_import_followup_item WHERE job_id='file' AND case_id='a'",Boolean.class));assertEquals(false,jdbc.queryForObject("SELECT plan_done FROM plan_review_import_followup_item WHERE job_id='file' AND case_id='b'",Boolean.class));
        var auth=new PlanReviewAuthorization(jdbc);var states=new PlanReviewPlanStateResolver(List.of());
        var app=new PlanReviewApplicationService(jdbc,new PlanReviewPersistence(jdbc),auth,fixture.tx.getTransactionManager(),states);
        var journalTarget=new io.metersphere.plan.service.PlanReviewCreationJournal();
        org.springframework.test.util.ReflectionTestUtils.setField(journalTarget,"jdbc",jdbc);org.springframework.test.util.ReflectionTestUtils.setField(journalTarget,"authorization",auth);org.springframework.test.util.ReflectionTestUtils.setField(journalTarget,"relations",app);org.springframework.test.util.ReflectionTestUtils.setField(journalTarget,"reviews",fixture.reviews);org.springframework.test.util.ReflectionTestUtils.setField(journalTarget,"planStates",states);
        var journal=fixture.transactional(journalTarget);
        when(fixture.reviews.addReservedCaseReview(any(),eq("user"),anyString())).thenAnswer(call->{String id=call.getArgument(2);jdbc.update("INSERT INTO case_review(id,project_id,status,name) VALUES(?,'project','PREPARED','Original review')",id);jdbc.update("INSERT INTO case_review_user VALUES(?,'reviewer')",id);var review=new io.metersphere.functional.domain.CaseReview();review.setId(id);return review;});
        doAnswer(call->{journal.completeLegacyImportReview(call.getArgument(0),call.getArgument(1));return null;}).when(fixture.plans).completeLegacyImportReview(any(),eq("user"));
        var provider=new ImportFollowupOperationResumer();org.springframework.test.util.ReflectionTestUtils.setField(provider,"followups",fixture.coordinator);
        var operations=new PlanReviewOperationService(jdbc,auth,List.of(provider));var parent=jdbc.queryForMap("SELECT * FROM plan_review_operation WHERE source_kind='IMPORT_FOLLOWUP'");
        fixture.tx.executeWithoutResult(status->operations.resume("project",(String)parent.get("id"),0,"resume-original","user"));fixture.process("file");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan WHERE id='original-plan'",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review WHERE id=?",Integer.class,child.get("review_id")));
        assertEquals(child.get("review_id"),jdbc.queryForObject("SELECT review_id FROM plan_review_import_followup",String.class));
        assertEquals(List.of("a"),jdbc.queryForList("SELECT functional_case_id FROM test_plan_functional_case",String.class));
        verify(fixture.plans,never()).addFromReservation(any(),anyString(),anyString(),anyString());
    }
    @Test void zeroSuccessRetiresTheOldQueueWithoutCreatingAnEmptyPairOrAnUnusedChild(){
        old(null,null);jdbc.update("DELETE FROM functional_import_item");jdbc.update("UPDATE functional_import_followup SET request_json='{\"policy\":\"CREATE_NEW_PAIR\"}'");
        migrate(Map.of(),new PlanReviewLegacyDecision.Job("ADOPT","user",null));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));assertEquals("SKIPPED_ZERO_SUCCESS",jdbc.queryForObject("SELECT stage FROM plan_review_operation",String.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
    }
    @Test void assetAdoptionUsesOnlyTheOriginalAssetSuccessIdsInsteadOfFileRowsWithTheSameJobId(){
        old(null,null);jdbc.update("UPDATE functional_import_followup SET source_kind='ASSET'");jdbc.update("INSERT INTO default_hub_sync_job VALUES('file','project','user','IMPORT_CASE','SUCCESS')");jdbc.update("INSERT INTO case_asset_import_result VALUES('file','b','SUCCESS'),('file','a','SKIPPED')");
        migrate(Map.of(),new PlanReviewLegacyDecision.Job("ADOPT","user",null));
        assertEquals("ASSET",jdbc.queryForObject("SELECT source_kind FROM plan_review_import_followup",String.class));assertEquals(List.of("b"),jdbc.queryForList("SELECT case_id FROM plan_review_import_followup_item",String.class));
    }
    @Test void twoRecordedObjectsWithoutAnOldGroupAreMappedUsingThoseExactIds(){
        jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES('original-plan','project','PREPARED','Plan')");jdbc.update("INSERT INTO case_review(id,project_id,status,name) VALUES('original-review','project','PREPARED','Review')");jdbc.update("INSERT INTO case_review_user VALUES('original-review','reviewer')");old("original-plan","original-review");
        migrate(Map.of(),new PlanReviewLegacyDecision.Job("ADOPT","user",null));
        var relation=jdbc.queryForMap("SELECT * FROM plan_review_relation");assertEquals("original-plan",relation.get("plan_id"));assertEquals("original-review",relation.get("review_id"));assertEquals("RECONFIRM_REQUIRED",relation.get("sync_state"));
        var mapping=jdbc.queryForMap("SELECT * FROM plan_review_legacy_job_map");assertEquals(relation.get("id"),mapping.get("relation_id"));assertEquals(true,mapping.get("created_relation"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM functional_import_followup_group",Integer.class));
    }
    @Test void fixedPairAdoptionLeavesReviewOpinionsAndPerChildProgressUnchangedAndRequiresFreshCatchup(){
        jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES('original-plan','project','PREPARED','Plan')");jdbc.update("INSERT INTO case_review(id,project_id,status,name) VALUES('original-review','project','PREPARED','Review')");jdbc.update("INSERT INTO case_review_user VALUES('original-review','reviewer')");
        jdbc.update("INSERT INTO test_plan_functional_case VALUES('source-a','original-plan','a'),('source-b','original-plan','b')");
        jdbc.update("INSERT INTO case_review_functional_case(id,review_id,case_id,status,create_time,update_time,pos) VALUES('kept','original-review','b','PASS',1,1,1)");jdbc.update("INSERT INTO case_review_history VALUES('opinion','original-review','b','PASS',X'010203',0,0)");
        jdbc.update("INSERT INTO functional_import_followup_group VALUES('old-group','project','original-plan','original-review',1,'user',1)");old("original-plan","original-review");jdbc.update("INSERT INTO functional_import_followup_item VALUES('file','a',1,0),('file','b',1,1)");
        migrate(Map.of("old-group","KEEP"),new PlanReviewLegacyDecision.Job("ADOPT","user",null));
        assertEquals("RECONFIRM_REQUIRED",jdbc.queryForObject("SELECT sync_state FROM plan_review_relation",String.class));assertEquals(List.of("b"),jdbc.queryForList("SELECT case_id FROM case_review_functional_case",String.class));assertArrayEquals(new byte[]{1,2,3},jdbc.queryForObject("SELECT content FROM case_review_history",byte[].class));
        assertEquals(true,jdbc.queryForObject("SELECT review_done FROM plan_review_import_followup_item WHERE case_id='b'",Boolean.class));assertEquals(false,jdbc.queryForObject("SELECT review_done FROM plan_review_import_followup_item WHERE case_id='a'",Boolean.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));
    }
}
