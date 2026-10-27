package io.metersphere.functional.asset.service;
import io.metersphere.system.service.DefaultHubProjectService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class CaseAssetCatalogIdentityMySqlTests {
 final String schema="asset_identity_"+UUID.randomUUID().toString().replace("-","");
 JdbcTemplate admin,jdbc;TransactionTemplate tx;CaseAssetService service;
 org.mockito.MockedStatic<io.metersphere.system.uid.IDGenerator> ids;
 @BeforeEach void setup() throws Exception {
  ids=mockStatic(io.metersphere.system.uid.IDGenerator.class);var counter=new java.util.concurrent.atomic.AtomicLong(1000);ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenAnswer(call->String.valueOf(counter.incrementAndGet()));
  String server="jdbc:mysql://127.0.0.1:13326/",options="?useSSL=false&allowPublicKeyRetrieval=true";
  admin=new JdbcTemplate(new DriverManagerDataSource(server+options,"root","quality-test-only"));admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
  var ds=new DriverManagerDataSource(server+schema+options,"root","quality-test-only");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  String sql=new ClassPathResource("migration/3.7.2/ddl/V3.7.2_61__case_asset_catalog_and_lineage.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  String[] statements=sql.split(";");jdbc.execute(statements[0]);jdbc.execute(statements[1]);
  jdbc.execute("CREATE TABLE organization(id VARCHAR(50) PRIMARY KEY)");jdbc.update("INSERT INTO organization VALUES ('org')");
  jdbc.execute("CREATE TABLE functional_case_module(id VARCHAR(50) PRIMARY KEY,project_id VARCHAR(50),name VARCHAR(255),parent_id VARCHAR(50),module_type VARCHAR(30),ref_project_id VARCHAR(50),pos BIGINT,create_time BIGINT,update_time BIGINT,create_user VARCHAR(50),update_user VARCHAR(50))");
  service=new CaseAssetService();ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);
  var hub=mock(DefaultHubProjectService.class);when(hub.getDefaultProjectId()).thenReturn("hub");ReflectionTestUtils.setField(service,"defaultHubProjectService",hub);
 }
 @AfterEach void cleanup(){if(ids!=null)ids.close();if(admin!=null&&schema.matches("asset_identity_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);}
 Map<String,Object> upsert(String name,String project){return tx.execute(status->ReflectionTestUtils.invokeMethod(service,"upsertCatalog","org",name,"PROJECT",project,"user"));}
 @Test void sameNameProjectsHaveIndependentIdentityAndReplayDoesNotDuplicate(){
  var first=upsert("same","project-000000000001");var second=upsert("same","project-000000000002");
  assertNotEquals(first.get("id"),second.get("id"));assertNotEquals(first.get("hubModuleId"),second.get("hubModuleId"));
  assertEquals("same [000000000002]",second.get("name"));assertEquals(second.get("id"),upsert("same","project-000000000002").get("id"));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM case_asset_catalog",Integer.class));
 }
 @Test void sharedHistoryAndManualNamesArePreserved(){
  var first=upsert("shared","first");jdbc.update("INSERT INTO case_asset_catalog_project_rel(id,catalog_id,project_id,create_time) VALUES('extra',?,'second',0)",first.get("id"));
  assertEquals("shared",upsert("renamed","first").get("name"));
  var single=upsert("single","third");assertEquals("new name",upsert("new name","third").get("name"));
  jdbc.update("UPDATE case_asset_catalog SET manually_renamed=1 WHERE id=?",single.get("id"));assertEquals("new name",upsert("project rename","third").get("name"));
 }
 @Test void untrustedSameNameModuleIsNotAdopted(){
  jdbc.update("INSERT INTO functional_case_module(id,project_id,name,parent_id,module_type) VALUES('legacy','hub','same','NONE','FOLDER')");
  var result=upsert("same","project-000000000001");assertNotEquals("legacy",result.get("hubModuleId"));
  assertNull(jdbc.queryForObject("SELECT ref_project_id FROM functional_case_module WHERE id='legacy'",String.class));
 }
}
