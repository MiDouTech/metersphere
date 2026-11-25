package io.metersphere.functional.asset.service;
import io.metersphere.functional.asset.dto.CaseAssetSystemRequest;
import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetSystemServiceTests {
 JdbcTemplate db;CaseAssetSystemService service;MockedStatic<SessionUtils> session;MockedStatic<io.metersphere.system.uid.IDGenerator> ids;
 @BeforeEach void setup(){
  ids=mockStatic(io.metersphere.system.uid.IDGenerator.class);ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenAnswer(invocation->UUID.randomUUID().toString());
  db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  db.execute("CREATE TABLE project(id varchar primary key,organization_id varchar,deleted int,enable int)");
  db.execute("CREATE TABLE case_asset_catalog(id varchar,organization_id varchar,hub_module_id varchar,deleted int)");
  db.execute("CREATE TABLE functional_case_module(id varchar,project_id varchar,parent_id varchar)");
  db.execute("CREATE TABLE functional_case(id varchar primary key,project_id varchar,module_id varchar,system_id varchar,system_module_id varchar,deleted int,update_user varchar,update_time bigint)");
  db.execute("CREATE TABLE functional_business_system(id varchar primary key,workspace_id varchar,name varchar,enabled int,create_user varchar,update_user varchar,create_time bigint,update_time bigint,UNIQUE(workspace_id,name))");
  db.execute("CREATE TABLE functional_system_module(id varchar primary key,workspace_id varchar,system_id varchar,name varchar,parent_id varchar,pos bigint,create_user varchar,update_user varchar,create_time bigint,update_time bigint,UNIQUE(system_id,parent_id,name))");
  db.execute("CREATE TABLE functional_case_system_relation(id varchar primary key,case_id varchar,system_id varchar,system_module_id varchar,create_user varchar,create_time bigint,UNIQUE(case_id,system_id))");
  db.update("INSERT INTO project VALUES('hub','org',0,1)");db.update("INSERT INTO case_asset_catalog VALUES('catalog','org','root',0)");
  db.update("INSERT INTO functional_case_module VALUES('root','hub','NONE'),('child','hub','root')");
  db.update("INSERT INTO functional_case VALUES('asset','hub','child',null,null,0,null,0),('foreign','other','child',null,null,0,null,0)");
  service=new CaseAssetSystemService();var hub=mock(DefaultHubProjectService.class);when(hub.getDefaultProjectId()).thenReturn("hub");
  var resolver=new DefaultHubModuleResolver();ReflectionTestUtils.setField(resolver,"jdbcTemplate",db);
  ReflectionTestUtils.setField(service,"jdbcTemplate",db);ReflectionTestUtils.setField(service,"defaultHubProjectService",hub);ReflectionTestUtils.setField(service,"defaultHubModuleResolver",resolver);
  session=mockStatic(SessionUtils.class);session.when(SessionUtils::getCurrentOrganizationId).thenReturn("org");session.when(SessionUtils::getUserId).thenReturn("user");
 }
 @AfterEach void close(){session.close();ids.close();}
 CaseAssetSystemRequest named(String name){var r=new CaseAssetSystemRequest();r.setName(name);return r;}
 String sys(String name){return (String)service.saveSystem(null,named(name)).get("id");}
 @Test void crudClassificationMoveAndUnclassificationPreserveAssetIdentity(){
  String a=sys("A"),b=sys("B");String m=(String)service.saveModule(a,null,named("module")).get("id");
  service.saveSystem(a,named("renamed"));service.saveModule(a,m,named("renamed module"));
  var r=new CaseAssetSystemRequest();r.setIds(List.of("asset"));r.setSystemId(a);r.setModuleId(m);assertEquals(1,service.classify(r).get("count"));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM functional_case_system_relation WHERE case_id='asset'",Integer.class));
  assertThrows(RuntimeException.class,()->service.deleteModule(a,m));assertThrows(RuntimeException.class,()->service.deleteSystem(a));
  r.setSystemId(b);r.setModuleId(null);service.classify(r);
  assertEquals(b,db.queryForObject("SELECT system_id FROM functional_case_system_relation WHERE case_id='asset'",String.class));
  r.setSystemId(null);service.classify(r);
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM functional_case_system_relation",Integer.class));
  assertEquals("hub",db.queryForObject("SELECT project_id FROM functional_case WHERE id='asset'",String.class));assertEquals("child",db.queryForObject("SELECT module_id FROM functional_case WHERE id='asset'",String.class));
  assertEquals(1L,service.list().get("unclassifiedCount"));service.deleteModule(a,m);service.deleteSystem(a);service.deleteSystem(b);
 }
 @Test void rejectsInvalidIdsCrossOrganizationAndWrongModule(){
  String a=sys("A"),b=sys("B"),m=(String)service.saveModule(a,null,named("m")).get("id");
  var r=new CaseAssetSystemRequest();assertThrows(RuntimeException.class,()->service.classify(r));
  r.setIds(List.of("foreign"));assertThrows(RuntimeException.class,()->service.classify(r));r.setIds(List.of("bad"));assertThrows(RuntimeException.class,()->service.classify(r));
  r.setIds(List.of("asset"));r.setSystemId(b);r.setModuleId(m);assertThrows(RuntimeException.class,()->service.classify(r));
  db.update("UPDATE functional_business_system SET workspace_id='other' WHERE id=?",b);r.setModuleId(null);assertThrows(RuntimeException.class,()->service.classify(r));
  db.update("UPDATE functional_business_system SET enabled=0 WHERE id=?",a);assertThrows(RuntimeException.class,()->service.saveModule(a,null,named("x")));
  db.update("UPDATE project SET organization_id='other' WHERE id='hub'");assertThrows(RuntimeException.class,()->service.saveSystem(null,named("x")));
 }
 @Test void untrustedCatalogRootsCannotExposeOrClassifyAssets(){
  db.update("UPDATE functional_case_module SET project_id='foreign' WHERE id='root'");
  var r=new CaseAssetSystemRequest();r.setIds(List.of("asset"));assertThrows(RuntimeException.class,()->service.classify(r));
  assertEquals(0L,service.list().get("unclassifiedCount"));
  db.update("UPDATE project SET organization_id='foreign' WHERE id='hub'");assertThrows(RuntimeException.class,()->service.list());
 }
 @Test void summaryAggregatesInSqlAndReadsModulesOnceAcrossSystems(){
  String a=sys("A"),b=sys("B");String am=(String)service.saveModule(a,null,named("a-module")).get("id"),bm=(String)service.saveModule(b,null,named("b-module")).get("id");
  for(int i=0;i<12;i++)db.update("INSERT INTO functional_case VALUES(?, 'hub','child',?,?,0,null,0)","count-"+i,i<8?a:b,i<8?am:bm);
  db.update("INSERT INTO functional_case VALUES('deleted','hub','child',?,?,1,null,0)",a,am);
  db.update("INSERT INTO functional_case VALUES('outside','hub','outside',?,?,0,null,0)",a,am);
  var observed=spy(db);ReflectionTestUtils.setField(service,"jdbcTemplate",observed);
  var result=service.list();assertEquals(1L,result.get("unclassifiedCount"));
  @SuppressWarnings("unchecked") var systems=(List<Map<String,Object>>)result.get("systems");assertEquals(2,systems.size());
  for(var system:systems){long expected=system.get("id").equals(a)?8L:4L;assertEquals(expected,system.get("caseCount"));
   @SuppressWarnings("unchecked") var modules=(List<Map<String,Object>>)system.get("modules");assertEquals(1,modules.size());assertEquals(expected,modules.getFirst().get("caseCount"));}
  var statements=mockingDetails(observed).getInvocations().stream().filter(call->call.getMethod().getName().equals("queryForList")).map(call->(String)call.getArgument(0)).toList();
  assertEquals(1,statements.stream().filter(sql->sql.contains("FROM functional_system_module")).count(),"module reads must remain constant across systems");
  var caseQuery=statements.stream().filter(sql->sql.contains("FROM functional_case WHERE")).findFirst().orElseThrow();assertTrue(caseQuery.contains("COUNT(*)"));assertTrue(caseQuery.contains("GROUP BY system_id,system_module_id"));
 }
 @Test void duplicateNamesAreSafeAndChildrenProtectDeletion(){
  String a=sys("A");var e=assertThrows(RuntimeException.class,()->sys("A"));assertEquals("当前目录已存在同名系统或模块",e.getMessage());
  String m=(String)service.saveModule(a,null,named("parent")).get("id");var child=named("child");child.setParentId(m);service.saveModule(a,null,child);assertThrows(RuntimeException.class,()->service.deleteModule(a,m));
 }
}
