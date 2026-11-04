package io.metersphere.agent.service;
import io.metersphere.agent.dto.ImportFollowupRequest;
import io.metersphere.functional.service.CaseReviewService;
import io.metersphere.functional.domain.CaseReview;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.service.TestPlanService;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.dto.user.UserDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real coordinator transactions; native plan/review services are contract doubles. */
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class ImportFollowupCoordinatorMySqlTests {
 final String schema="msp12_pr_"+UUID.randomUUID().toString().replace("-","");
 JdbcTemplate admin,jdbc;TransactionTemplate tx;ImportFollowupCoordinator coordinator;
 TestPlanService plans;CaseReviewService reviews;TestPlanFunctionalCaseService planCases;
 @BeforeEach void setup() throws Exception {
  String config=System.getProperty("plan.review.mysql.config");
  if(config==null)throw new IllegalStateException("An explicitly isolated test instance is required");
  var properties=new java.util.Properties();
  try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(config))){properties.load(reader);}
  String server=properties.getProperty("url","");
  if(!server.startsWith("jdbc:mysql://127.0.0.1:") || !"true".equals(properties.getProperty("isolated")))throw new IllegalStateException("Only isolated loopback test instances are permitted");
  admin=new JdbcTemplate(new DriverManagerDataSource(server,properties.getProperty("user"),properties.getProperty("password")));
  if(!Objects.equals(properties.getProperty("serverUuid"),admin.queryForObject("SELECT @@server_uuid",String.class)))throw new IllegalStateException("Test instance identity mismatch");
  admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
  int options=server.indexOf('?');
  var source=new DriverManagerDataSource(server.substring(0,options)+schema+server.substring(options),properties.getProperty("user"),properties.getProperty("password"));jdbc=new JdbcTemplate(source);
  var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
  try(var connection=source.getConnection()) {for(String migration:List.of("112__functional_import_job","123__functional_import_followup","130__asset_import_followup_adapter","137__plan_review_relation","138__plan_review_followup_v2","140__plan_review_takeover","141__plan_review_copy_request","142__plan_review_notice_round","143__plan_review_legacy_migration"))ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_"+migration+".sql"));}
  jdbc.execute("CREATE TABLE project(id VARCHAR(50),organization_id VARCHAR(50),deleted BOOLEAN,enable BOOLEAN)");jdbc.update("INSERT INTO project VALUES ('project','org',0,1)");
  jdbc.execute("CREATE TABLE test_plan(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),status VARCHAR(30),name VARCHAR(100),type VARCHAR(32) DEFAULT 'TEST_PLAN')");
  jdbc.execute("CREATE TABLE case_review(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),status VARCHAR(30),name VARCHAR(100))");
  jdbc.execute("CREATE TABLE case_review_user(review_id VARCHAR(50),user_id VARCHAR(50))");
  jdbc.execute("CREATE TABLE default_hub_sync_job(id VARCHAR(50) PRIMARY KEY,scope_project_id VARCHAR(50),create_user VARCHAR(50),job_type VARCHAR(30),status VARCHAR(30))");
  jdbc.execute("CREATE TABLE case_asset_import_result(job_id VARCHAR(50),target_case_id VARCHAR(50),status VARCHAR(30))");
  var imports=mock(AgentCaseImportService.class);var access=mock(AgentBusinessAccess.class);
  when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
  plans=mock(TestPlanService.class);reviews=mock(CaseReviewService.class);planCases=mock(TestPlanFunctionalCaseService.class);
  var users=mock(UserLoginService.class);var user=new UserDTO();user.setId("user");when(users.getUserDTO(anyString())).thenAnswer(call->{var dto=new UserDTO();dto.setId(call.getArgument(0));return dto;});
  coordinator=new ImportFollowupCoordinator(jdbc,manager,imports,access,plans,planCases,reviews,users);
  var authorization=mock(io.metersphere.functional.planreview.PlanReviewAuthorization.class);
  when(authorization.current(anyString(),anyString(),eq(true))).thenReturn(new io.metersphere.functional.planreview.PlanReviewPolicy.Access(java.util.EnumSet.allOf(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.class),true));
  ReflectionTestUtils.setField(coordinator,"relationAuthorization",authorization);
  var relations=mock(io.metersphere.functional.planreview.PlanReviewApplicationService.class);
  when(relations.summary(anyString(),anyString(),anyString())).thenReturn(Map.of("sync",Map.of("editable",true)));
  ReflectionTestUtils.setField(coordinator,"relations",relations);
  jdbc.update("INSERT INTO plan_review_grant(project_id,enabled,update_time) VALUES('project',1,1)");
  when(plans.addFromReservation(any(),anyString(),anyString(),anyString())).thenAnswer(call->{
   jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES ('plan','project','PREPARED','plan')");
   jdbc.update("INSERT INTO case_review VALUES ('review','project','PREPARED','review')");
   jdbc.update("INSERT INTO case_review_user VALUES('review','reviewer')");
   jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_changed_at,original_actor,create_time,update_time) VALUES('relation','project','plan','review','generation','plan','review','ACTIVE','PENDING',1,'user',1,1)");
   var plan=new TestPlan();plan.setId("plan");return plan;});
  doAnswer(call->{jdbc.update("UPDATE plan_review_relation SET source_version=1,applied_version=1,sync_state='SYNCED'");return null;}).when(planCases).associateCases(any(),any());
  when(reviews.addCaseReview(any(),eq("user"))).thenAnswer(call->{jdbc.update("INSERT INTO case_review VALUES ('review','project','PREPARED','review')");var review=new CaseReview();review.setId("review");return review;});
 }
 @AfterEach void cleanup(){if(admin!=null&&schema.matches("msp12_pr_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
 ImportFollowupRequest decision(){var r=new ImportFollowupRequest();r.setPolicy("CREATE_NEW_PAIR");r.setReviewers(List.of("reviewer"));r.setReviewPassRule("SINGLE");return r;}
 void file(){long now=System.currentTimeMillis();jdbc.update("INSERT INTO functional_import_job(id,project_id,user_id,token_id,request_id,request_hash,request_json,template_hash,source_hash,source_name,source_bytes,create_time,update_time,expires_at) VALUES('file','project','user','WEB','r','hash','{}','hash','hash','cases.xlsx',X'',?,?,?)",now,now,now+86400000);}
 void process(String id){ReflectionTestUtils.invokeMethod(coordinator,"prepareImportReservation",id);tx.executeWithoutResult(status->ReflectionTestUtils.invokeMethod(coordinator,"process",id));}
 @Test void unrelatedLegacyImportOnlyCompletesWithoutCreatingAnyNewObjectsOrOperations(){
  file();jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  jdbc.update("INSERT INTO functional_import_followup(job_id,project_id,user_id,policy,request_json,group_id,state,create_time,update_time) VALUES('file','project','user','IMPORT_ONLY','{}','legacy-group','PENDING',1,1)");
  coordinator.drain();coordinator.drain();
  assertEquals("SUCCEEDED",coordinator.status("file","user").get("state"));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_import_followup",Integer.class));
  verifyNoInteractions(plans,reviews,planCases);
  assertThrows(io.metersphere.functional.planreview.PlanReviewFailure.class,()->coordinator.retry("file","user"));
 }
 @Test void legacyPairedJobIsClearlyBlockedInsteadOfReplayedAsANewOperation(){
  file();jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  jdbc.update("INSERT INTO functional_import_followup(job_id,project_id,user_id,policy,request_json,group_id,state,create_time,update_time) VALUES('file','project','user','CREATE_NEW_PAIR','{}','legacy-group','PENDING',1,1)");
  coordinator.drain();
  assertEquals("LEGACY_ADOPTION_REQUIRED",coordinator.status("file","user").get("state"));
  assertThrows(io.metersphere.functional.planreview.PlanReviewFailure.class,()->coordinator.retry("file","user"));
  assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM functional_import_followup",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));verifyNoInteractions(plans,reviews,planCases);
 }
 @Test void noSuccessfulItemsNeverCreatesEmptyPlanOrReview(){file();coordinator.prepare("file","user",decision());jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");process("file");assertEquals("SUCCEEDED",coordinator.status("file","user").get("state"));verifyNoInteractions(plans,reviews,planCases);}
 @Test void replayUsesOnlyCommittedSuccessIdsAndDoesNotRecreateTargets(){
  file();var request=decision();coordinator.prepare("file","user",request);coordinator.prepare("file","user",request);
  jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','success'),('file',2,'CREATE','FAILED',NULL),('file',3,'SKIP','SKIPPED','skipped')");jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  for(int i=0;i<5;i++)process("file");
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
  assertEquals(List.of("success"),jdbc.queryForList("SELECT case_id FROM plan_review_import_followup_item",String.class));
  verify(planCases,times(1)).associateCases(argThat(r->r.getSelectIds().equals(List.of("success"))),any());
  verify(reviews,never()).associateCase(any(),anyString());
  assertEquals("SUCCEEDED",coordinator.status("file","user").get("state"));
 }
 @Test void assetAdapterUsesRealAssetImportResults(){
  jdbc.update("INSERT INTO default_hub_sync_job VALUES ('asset','project','user','IMPORT_CASE','PENDING')");coordinator.prepare("asset","user",decision());
  jdbc.update("INSERT INTO case_asset_import_result VALUES ('asset','asset-success','SUCCESS'),('asset','asset-skipped','SKIPPED')");jdbc.update("UPDATE default_hub_sync_job SET status='SUCCESS'");
  for(int i=0;i<5;i++)process("asset");
  assertEquals(List.of("asset-success"),jdbc.queryForList("SELECT case_id FROM plan_review_import_followup_item",String.class));
  assertEquals("SUCCEEDED",coordinator.status("asset","user").get("state"));
 }
 @Test void appendUsesExistingReviewersWithoutAskingForNewOnes() {
  file();
  jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES ('existing-plan','project','PREPARED','plan')");
  jdbc.update("INSERT INTO case_review VALUES ('existing-review','project','PREPARED','review')");
  jdbc.update("INSERT INTO case_review_user VALUES ('existing-review','existing-reviewer')");
  jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_version,source_changed_at,original_actor,create_time,update_time) VALUES('group','project','existing-plan','existing-review','generation','existing-plan','existing-review','ACTIVE','SYNCED',1,1,'user',1,1)");
  var request=new ImportFollowupRequest();request.setPolicy("APPEND_EXISTING");request.setTargetGroupId("group");request.setExpectedGroupRevision(1L);
  assertDoesNotThrow(()->coordinator.prepare("file","user",request));
  assertEquals("existing-review",jdbc.queryForObject("SELECT review_id FROM plan_review_import_followup WHERE job_id='file'",String.class));
  verifyNoInteractions(plans,reviews,planCases);
 }
 @Test void creationReservationRunsOutsideTheFollowupBusinessTransaction(){
  file();coordinator.prepare("file","user",decision());
  jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','success')");
  jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  doAnswer(call->{assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());return null;}).when(plans).reserveCreation(any(),eq("user"));
  process("file");
  verify(plans).reserveCreation(any(),eq("user"));
  verify(plans).addFromReservation(any(),eq("user"),anyString(),anyString());
  verify(plans,never()).add(any(),anyString(),anyString(),anyString());
 }
 @Test void springNeverBoundaryRejectsReservationWhileHoldingAnOuterTransaction(){
  var target=new TestPlanService();
  var journal=mock(io.metersphere.plan.service.PlanReviewCreationJournal.class);
  ReflectionTestUtils.setField(target,"planReviewCreationJournal",journal);
  var proxy=transactional(target);
  var request=new io.metersphere.plan.dto.request.TestPlanCreateRequest();
  assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->tx.executeWithoutResult(status->proxy.reserveCreation(request,"user")));
  verifyNoInteractions(journal);
 }
 @Test void springRequiresNewReservationSurvivesOuterRollbackAndReusesTheSameIds(){
  var target=new io.metersphere.plan.service.PlanReviewCreationJournal();
  ReflectionTestUtils.setField(target,"jdbc",jdbc);
  var authorization=mock(io.metersphere.functional.planreview.PlanReviewAuthorization.class);
  when(authorization.current(anyString(),anyString(),eq(true))).thenReturn(new io.metersphere.functional.planreview.PlanReviewPolicy.Access(EnumSet.allOf(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.class),true));
  ReflectionTestUtils.setField(target,"authorization",authorization);
  var journal=transactional(target);
  var settings=new io.metersphere.functional.planreview.PlanReviewSettings("review","root",List.of("reviewer"),"SINGLE",false);
  var create=new io.metersphere.plan.dto.request.TestPlanCreateRequest();create.setProjectId("project");create.setName("plan");create.setRequestKey("stable");create.setReviewSettings(settings);String payload=io.metersphere.sdk.util.JSON.toJSONString(create);
  var reserved=new java.util.concurrent.atomic.AtomicReference<io.metersphere.plan.service.PlanReviewCreationJournal.Reservation>();
  assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
   jdbc.update("INSERT INTO default_hub_sync_job VALUES('rolled-back','project','user','IMPORT_CASE','PENDING')");
   reserved.set(journal.prepare("project","user","PLAN_SERVICE","CREATE_PAIR","stable",payload,settings,0));
   throw new IllegalStateException("Injected outer rollback");
  }));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM default_hub_sync_job WHERE id='rolled-back'",Integer.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_operation",Integer.class));
  var replay=journal.prepare("project","user","PLAN_SERVICE","CREATE_PAIR","stable",payload,settings,0);
  assertEquals(reserved.get().id(),replay.id());assertEquals(reserved.get().planId(),replay.planId());assertEquals(reserved.get().reviewId(),replay.reviewId());
 }
 @SuppressWarnings("unchecked") <T>T transactional(T target){
  var proxy=new org.springframework.aop.framework.ProxyFactory(target);proxy.setProxyTargetClass(true);
  proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx.getTransactionManager(),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
  return (T)proxy.getProxy();
 }
 AgentPlanReviewExecutionCredential credentialProvider(){
  jdbc.execute("CREATE TABLE agent_token(id VARCHAR(50) PRIMARY KEY,user_id VARCHAR(50),enable BOOLEAN,status VARCHAR(20),revoked_at BIGINT,expire_time BIGINT,project_ids TEXT,project_id VARCHAR(50),scopes TEXT)");
  jdbc.update("INSERT INTO agent_token VALUES('opaque-reference','user',1,'ACTIVE',NULL,NULL,'[\"project\"]',NULL,'[\"PLAN_WRITE\",\"REVIEW_WRITE\"]')");
  var provider=new AgentPlanReviewExecutionCredential();ReflectionTestUtils.setField(provider,"jdbc",jdbc);return provider;
 }
 @Test void followupRecoveryRequiresCurrentImportPermissionAndLeavesFailedStateUntouched(){
  file();coordinator.prepare("file","user",decision());jdbc.update("UPDATE plan_review_import_followup SET state='FAILED'");
  var authorization=mock(io.metersphere.functional.planreview.PlanReviewAuthorization.class);
  var permissions=EnumSet.allOf(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.class);permissions.remove(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.IMPORT);
  when(authorization.current(anyString(),anyString(),eq(true))).thenReturn(new io.metersphere.functional.planreview.PlanReviewPolicy.Access(permissions,true));
  ReflectionTestUtils.setField(coordinator,"relationAuthorization",authorization);
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->coordinator.retry("file","user"));
  assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
  verifyNoInteractions(plans,reviews,planCases);
 }
 @Test void completedFollowupCannotBeReopenedByRetry(){
  file();coordinator.prepare("file","user",decision());jdbc.update("UPDATE plan_review_import_followup SET state='SUCCEEDED'");
  assertEquals("OPERATION_NOT_FAILED",assertThrows(io.metersphere.functional.planreview.PlanReviewFailure.class,()->coordinator.retry("file","user")).code());
  assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
 }
 io.metersphere.functional.planreview.PlanReviewOperationService operationService(){
  var authorization=mock(io.metersphere.functional.planreview.PlanReviewAuthorization.class);
  when(authorization.current(anyString(),anyString(),anyBoolean())).thenReturn(new io.metersphere.functional.planreview.PlanReviewPolicy.Access(EnumSet.allOf(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.class),true));
  var resumer=new ImportFollowupOperationResumer();ReflectionTestUtils.setField(resumer,"followups",coordinator);
  return new io.metersphere.functional.planreview.PlanReviewOperationService(jdbc,authorization,List.of(resumer));
 }
 String rootId(){return jdbc.queryForObject("SELECT operation_id FROM plan_review_import_followup WHERE job_id='file'",String.class);}
 String approveImport(io.metersphere.functional.planreview.PlanReviewOperationService operations){
  var preview=tx.execute(status->operations.takeoverPreview("project",rootId(),0,"new","user"));
  String id=(String)preview.get("previewId");tx.executeWithoutResult(status->operations.approve("project",id,true,"approver"));return id;
 }
 @Test void approvedImportTakeoverResumesWithNewActorWithoutRequiringTheOriginalAccount(){
  file();coordinator.prepare("file","user",decision());
  jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','success')");jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  jdbc.update("UPDATE plan_review_import_followup SET state='FAILED'");jdbc.update("UPDATE plan_review_operation SET state='FAILED' WHERE id=?",rootId());
  jdbc.update("UPDATE plan_review_operation SET executing_credential_id='original-agent-reference',original_credential_id='original-agent-reference' WHERE id=?",rootId());
  jdbc.update("UPDATE functional_import_job SET token_id='original-agent-reference' WHERE id='file'");
  var operations=operationService();String approval=approveImport(operations);
  tx.executeWithoutResult(status->operations.takeover("project",approval,0,"takeover","new"));
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->coordinator.retry("file","user"));
  var authorization=mock(io.metersphere.functional.planreview.PlanReviewAuthorization.class);
  when(authorization.current(eq("new"),anyString(),eq(true))).thenReturn(new io.metersphere.functional.planreview.PlanReviewPolicy.Access(EnumSet.allOf(io.metersphere.functional.planreview.PlanReviewPolicy.Permission.class),true));
  when(authorization.current(eq("user"),anyString(),eq(true))).thenThrow(new io.metersphere.functional.planreview.PlanReviewFailure("RESOURCE_NOT_ACCESSIBLE",404,"Original account revoked"));
  ReflectionTestUtils.setField(coordinator,"relationAuthorization",authorization);
  tx.executeWithoutResult(status->operations.resume("project",rootId(),1,"resume","new"));
  for(int i=0;i<5;i++)process("file");
  assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
  assertEquals("COMPLETED",jdbc.queryForObject("SELECT state FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertEquals("user",jdbc.queryForObject("SELECT original_actor FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertEquals("new",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertNull(jdbc.queryForObject("SELECT executing_credential_id FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertEquals("original-agent-reference",jdbc.queryForObject("SELECT original_credential_id FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
  verify(plans).addFromReservation(any(),eq("new"),anyString(),anyString());
  verify(planCases).associateCases(any(),argThat(session->"new".equals(session.getId())));
 }
 void reserveImportChild(String actor){
  jdbc.update("INSERT INTO plan_review_operation(id,project_id,source_kind,operation_type,request_key,payload_hash,request_json,original_actor,executing_actor,plan_id,review_id,stage,state,create_time,update_time) VALUES('child','project','PLAN_SERVICE','CREATE_PAIR','import:file','hash','{}','user',?,'reserved-plan','reserved-review','VALIDATED','PENDING',1,1)",actor);
 }
 @Test void terminatingParentAlsoStopsOnlyItsUncreatedChildAndOriginalQueue(){
  file();coordinator.prepare("file","user",decision());reserveImportChild("user");
  var operations=operationService();tx.executeWithoutResult(status->operations.terminate("project",rootId(),0,"user"));
  assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
  assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM plan_review_operation WHERE id='child'",String.class));
  assertEquals("reserved-plan",jdbc.queryForObject("SELECT plan_id FROM plan_review_operation WHERE id='child'",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));verifyNoInteractions(plans,reviews,planCases);
 }
 @Test void conflictingChildMakesTerminationRollBackWithoutStoppingSomeoneElsesReservation(){
  file();coordinator.prepare("file","user",decision());reserveImportChild("third");
  var operations=operationService();assertThrows(io.metersphere.functional.planreview.PlanReviewFailure.class,()->tx.executeWithoutResult(status->operations.terminate("project",rootId(),0,"user")));
  assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
  assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM plan_review_operation WHERE id='child'",String.class));
  assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM plan_review_operation WHERE id=?",String.class,rootId()));
 }
 @Test void importTakeoverChangesPendingChildOwnershipAtomicallyAndPreservesReservedIds(){
  file();coordinator.prepare("file","user",decision());reserveImportChild("user");
  var operations=operationService();String approval=approveImport(operations);
  tx.executeWithoutResult(status->operations.takeover("project",approval,0,"takeover","new"));
  assertEquals(List.of("new"),jdbc.queryForList("SELECT DISTINCT executing_actor FROM plan_review_operation",String.class));
  assertEquals("reserved-plan",jdbc.queryForObject("SELECT plan_id FROM plan_review_operation WHERE id='child'",String.class));
  assertEquals("reserved-review",jdbc.queryForObject("SELECT review_id FROM plan_review_operation WHERE id='child'",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
 }
 @Test void conflictingChildOwnershipRollsBackParentTakeoverAndApprovalConsumption(){
  file();coordinator.prepare("file","user",decision());reserveImportChild("third");
  var operations=operationService();String approval=approveImport(operations);
  assertEquals("CHILD_OPERATION_CHANGED",assertThrows(io.metersphere.functional.planreview.PlanReviewFailure.class,()->tx.executeWithoutResult(status->operations.takeover("project",approval,0,"takeover","new"))).code());
  assertEquals("user",jdbc.queryForObject("SELECT executing_actor FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertNull(jdbc.queryForObject("SELECT consumed_at FROM plan_review_takeover WHERE id=?",Long.class,approval));
  assertEquals(0L,jdbc.queryForObject("SELECT revision FROM plan_review_operation WHERE id=?",Long.class,rootId()));
 }
 @Test void endedImportGenerationStopsWorkerWithoutAppendingOrCreatingTargets(){
  file();
  jdbc.update("INSERT INTO test_plan(id,project_id,status,name) VALUES('existing-plan','project','PREPARED','plan')");jdbc.update("INSERT INTO case_review VALUES('existing-review','project','PREPARED','review')");
  jdbc.update("INSERT INTO case_review_user VALUES('existing-review','reviewer')");
  jdbc.update("INSERT INTO plan_review_relation(id,project_id,plan_id,review_id,generation,active_plan_id,active_review_id,state,sync_state,source_changed_at,original_actor,create_time,update_time) VALUES('group','project','existing-plan','existing-review','g1','existing-plan','existing-review','ACTIVE','SYNCED',1,'user',1,1)");
  var request=new ImportFollowupRequest();request.setPolicy("APPEND_EXISTING");request.setTargetGroupId("group");request.setExpectedGroupRevision(0L);
  coordinator.prepare("file","user",request);jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','success')");
  jdbc.update("UPDATE plan_review_relation SET state='ENDED',active_plan_id=NULL,active_review_id=NULL");process("file");
  assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
  assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM plan_review_operation WHERE id=?",String.class,rootId()));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
  verifyNoInteractions(plans,reviews,planCases);
 }
 @Test void completedTargetSignalFinalizesItemMarkersWithoutReplayingSourceWrites(){
  file();coordinator.prepare("file","user",decision());jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','success')");
  process("file");process("file");jdbc.update("UPDATE plan_review_relation SET sync_state='PENDING',applied_version=0");process("file");
  assertEquals("TARGET_PENDING",jdbc.queryForObject("SELECT stage FROM plan_review_operation WHERE id=?",String.class,rootId()));
  jdbc.update("UPDATE plan_review_relation SET sync_state='SYNCED',applied_version=source_version");
  jdbc.update("UPDATE plan_review_operation SET stage='COMPLETED',state='COMPLETED',revision=revision+1 WHERE id=?",rootId());process("file");
  assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM plan_review_import_followup",String.class));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM plan_review_import_followup_item WHERE plan_done=1 AND review_done=1",Integer.class));
  verify(planCases,times(1)).associateCases(any(),any());
 }
 @Test void credentialRecoveryRechecksRevocationScopesAndProjectInsteadOfTrustingRequestContext(){
  var provider=credentialProvider();var context=new io.metersphere.system.domain.AgentToken();context.setId("opaque-reference");context.setUserId("user");
  io.metersphere.agent.security.AgentTokenContext.set(context);
  try{
   assertEquals(Boolean.TRUE,tx.execute(status->provider.validate("opaque-reference","project","user")));
   assertEquals(Boolean.FALSE,tx.execute(status->provider.validate("opaque-reference","other","user")));
   // Existing scope semantics: PLAN_WRITE covers PLAN_READ; recovery must retain that compatibility.
   assertEquals(Boolean.TRUE,tx.execute(status->provider.validateLinkedReview("opaque-reference","project","user")));
   jdbc.update("UPDATE agent_token SET scopes='[\"PLAN_READ\",\"REVIEW_WRITE\"]'");
   assertEquals(Boolean.FALSE,tx.execute(status->provider.validateLinkedReview("opaque-reference","project","user")));
   jdbc.update("UPDATE agent_token SET scopes='[\"PLAN_WRITE\",\"REVIEW_WRITE\",\"PLAN_READ\"]'");
   assertEquals(Boolean.TRUE,tx.execute(status->provider.validateLinkedReview("opaque-reference","project","user")));
   jdbc.update("UPDATE agent_token SET scopes='[\"PLAN_WRITE\"]'");
   assertEquals(Boolean.FALSE,tx.execute(status->provider.validate("opaque-reference","project","user")));
   jdbc.update("UPDATE agent_token SET scopes='[\"PLAN_WRITE\",\"REVIEW_WRITE\"]',revoked_at=1");
   assertEquals(Boolean.FALSE,tx.execute(status->provider.validate("opaque-reference","project","user")));
  }finally{io.metersphere.agent.security.AgentTokenContext.clear();}
  assertEquals(Boolean.FALSE,tx.execute(status->provider.validate("opaque-reference","project","user")));
 }
 @Test void repeatableReadCannotRecoverWithATokenRevokedAfterItsSnapshot() throws Exception {
  var provider=credentialProvider();var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  var read=new java.util.concurrent.CountDownLatch(1);var proceed=new java.util.concurrent.CountDownLatch(1);
  try{
   var future=executor.submit(()->{
    var context=new io.metersphere.system.domain.AgentToken();context.setId("opaque-reference");context.setUserId("user");
    io.metersphere.agent.security.AgentTokenContext.set(context);
    try{
     var repeatable=new TransactionTemplate(tx.getTransactionManager());repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);repeatable.setTimeout(15);
     return repeatable.execute(status->{
      jdbc.queryForList("SELECT enable FROM agent_token");read.countDown();
      try{if(!proceed.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Test coordination timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
      jdbc.queryForList("SELECT project_id FROM plan_review_grant WHERE project_id='project' FOR UPDATE");
      return provider.validate("opaque-reference","project","user");
     });
    }finally{io.metersphere.agent.security.AgentTokenContext.clear();}
   });
   assertTrue(read.await(10,java.util.concurrent.TimeUnit.SECONDS));jdbc.update("UPDATE agent_token SET revoked_at=1");proceed.countDown();
   assertFalse(future.get(20,java.util.concurrent.TimeUnit.SECONDS));
  }finally{proceed.countDown();executor.shutdownNow();}
 }
}
