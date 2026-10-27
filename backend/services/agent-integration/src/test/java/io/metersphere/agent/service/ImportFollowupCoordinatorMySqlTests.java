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
 final String schema="import_followup_"+UUID.randomUUID().toString().replace("-","");
 JdbcTemplate admin,jdbc;TransactionTemplate tx;ImportFollowupCoordinator coordinator;
 TestPlanService plans;CaseReviewService reviews;TestPlanFunctionalCaseService planCases;
 @BeforeEach void setup() throws Exception {
  String server="jdbc:mysql://127.0.0.1:13326/",options="?useSSL=false&allowPublicKeyRetrieval=true";
  admin=new JdbcTemplate(new DriverManagerDataSource(server+options,"root","quality-test-only"));admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
  var source=new DriverManagerDataSource(server+schema+options,"root","quality-test-only");jdbc=new JdbcTemplate(source);
  var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
  try(var connection=source.getConnection()) {for(String migration:List.of("112__functional_import_job","123__functional_import_followup","130__asset_import_followup_adapter"))ScriptUtils.executeSqlScript(connection,new ClassPathResource("migration/3.7.2/ddl/V3.7.2_"+migration+".sql"));}
  jdbc.execute("CREATE TABLE project(id VARCHAR(50),organization_id VARCHAR(50),deleted BOOLEAN,enable BOOLEAN)");jdbc.update("INSERT INTO project VALUES ('project','org',0,1)");
  jdbc.execute("CREATE TABLE test_plan(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),status VARCHAR(30),name VARCHAR(100))");
  jdbc.execute("CREATE TABLE case_review(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),status VARCHAR(30),name VARCHAR(100))");
  jdbc.execute("CREATE TABLE default_hub_sync_job(id VARCHAR(50) PRIMARY KEY,scope_project_id VARCHAR(50),create_user VARCHAR(50),job_type VARCHAR(30),status VARCHAR(30))");
  jdbc.execute("CREATE TABLE case_asset_import_result(job_id VARCHAR(50),target_case_id VARCHAR(50),status VARCHAR(30))");
  var imports=mock(AgentCaseImportService.class);var access=mock(AgentBusinessAccess.class);
  when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
  plans=mock(TestPlanService.class);reviews=mock(CaseReviewService.class);planCases=mock(TestPlanFunctionalCaseService.class);
  var users=mock(UserLoginService.class);var user=new UserDTO();user.setId("user");when(users.getUserDTO("user")).thenReturn(user);
  coordinator=new ImportFollowupCoordinator(jdbc,manager,imports,access,plans,planCases,reviews,users);
  when(plans.add(any(),eq("user"),anyString(),anyString())).thenAnswer(call->{jdbc.update("INSERT INTO test_plan VALUES ('plan','project','PREPARED','plan')");var plan=new TestPlan();plan.setId("plan");return plan;});
  when(reviews.addCaseReview(any(),eq("user"))).thenAnswer(call->{jdbc.update("INSERT INTO case_review VALUES ('review','project','PREPARED','review')");var review=new CaseReview();review.setId("review");return review;});
 }
 @AfterEach void cleanup(){if(admin!=null&&schema.matches("import_followup_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
 ImportFollowupRequest decision(){var r=new ImportFollowupRequest();r.setPolicy("CREATE_NEW_PAIR");r.setReviewers(List.of("reviewer"));return r;}
 void file(){long now=System.currentTimeMillis();jdbc.update("INSERT INTO functional_import_job(id,project_id,user_id,token_id,request_id,request_hash,request_json,template_hash,source_hash,source_name,source_bytes,create_time,update_time,expires_at) VALUES('file','project','user','WEB','r','hash','{}','hash','hash','cases.xlsx',X'',?,?,?)",now,now,now+86400000);}
 void process(String id){tx.executeWithoutResult(status->ReflectionTestUtils.invokeMethod(coordinator,"process",id));}
 @Test void noSuccessfulItemsNeverCreatesEmptyPlanOrReview(){file();coordinator.prepare("file","user",decision());jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");process("file");assertEquals("SUCCEEDED",coordinator.status("file","user").get("state"));verifyNoInteractions(plans,reviews,planCases);}
 @Test void replayUsesOnlyCommittedSuccessIdsAndDoesNotRecreateTargets(){
  file();var request=decision();coordinator.prepare("file","user",request);coordinator.prepare("file","user",request);
  jdbc.update("INSERT INTO functional_import_item(job_id,position,action,state,target_id) VALUES('file',1,'CREATE','SUCCESS','success'),('file',2,'CREATE','FAILED',NULL),('file',3,'SKIP','SKIPPED','skipped')");jdbc.update("UPDATE functional_import_job SET state='COMPLETED'");
  for(int i=0;i<5;i++)process("file");
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_plan",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_review",Integer.class));
  assertEquals(List.of("success"),jdbc.queryForList("SELECT case_id FROM functional_import_followup_item",String.class));
  verify(planCases,times(1)).associateCases(argThat(r->r.getSelectIds().equals(List.of("success"))),any());
  verify(reviews,times(1)).associateCase(argThat(r->r.getBaseAssociateCaseRequest().getSelectIds().equals(List.of("success"))),eq("user"));
  assertEquals("SUCCEEDED",coordinator.status("file","user").get("state"));
 }
 @Test void assetAdapterUsesRealAssetImportResults(){
  jdbc.update("INSERT INTO default_hub_sync_job VALUES ('asset','project','user','IMPORT_CASE','PENDING')");coordinator.prepare("asset","user",decision());
  jdbc.update("INSERT INTO case_asset_import_result VALUES ('asset','asset-success','SUCCESS'),('asset','asset-skipped','SKIPPED')");jdbc.update("UPDATE default_hub_sync_job SET status='SUCCESS'");
  for(int i=0;i<5;i++)process("asset");
  assertEquals(List.of("asset-success"),jdbc.queryForList("SELECT case_id FROM functional_import_followup_item",String.class));
  assertEquals("SUCCEEDED",coordinator.status("asset","user").get("state"));
 }
}
