package io.metersphere.functional.asset.service;
import io.metersphere.functional.asset.dto.CaseAssetSystemRequest;
import io.metersphere.functional.hub.service.DefaultHubModuleResolver;
import io.metersphere.system.service.DefaultHubProjectService;
import io.metersphere.system.utils.SessionUtils;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.aop.framework.ProxyFactory;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class CaseAssetSystemMySqlTests {
 final String schema="msp38_system_"+UUID.randomUUID().toString().replace("-","");
 JdbcTemplate admin,jdbc;TransactionTemplate tx;CaseAssetSystemService service;
 ExecutorService executor;
 @BeforeEach void setup(){
  String server="jdbc:mysql://127.0.0.1:13326/",options="?useSSL=false&allowPublicKeyRetrieval=true";
  admin=new JdbcTemplate(new DriverManagerDataSource(server+options,"root","quality-test-only"));admin.execute("CREATE DATABASE "+schema);
  var ds=new DriverManagerDataSource(server+schema+options,"root","quality-test-only");jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
  jdbc.execute("CREATE TABLE project(id varchar(255) primary key,organization_id varchar(255),deleted int,enable int)");
  jdbc.execute("CREATE TABLE case_asset_catalog(id varchar(255),organization_id varchar(255),hub_module_id varchar(255),deleted int)");
  jdbc.execute("CREATE TABLE functional_case_module(id varchar(255),project_id varchar(255),parent_id varchar(255))");
  jdbc.execute("CREATE TABLE functional_case(id varchar(255) primary key,project_id varchar(255),module_id varchar(255),system_id varchar(255),system_module_id varchar(255),deleted int,update_user varchar(255),update_time bigint)");
  jdbc.execute("CREATE TABLE functional_business_system(id varchar(255) primary key,workspace_id varchar(255),name varchar(255),enabled int,create_user varchar(255),update_user varchar(255),create_time bigint,update_time bigint,UNIQUE(workspace_id,name))");
  jdbc.execute("CREATE TABLE functional_system_module(id varchar(255) primary key,workspace_id varchar(255),system_id varchar(255),name varchar(255),parent_id varchar(255),pos bigint,create_user varchar(255),update_user varchar(255),create_time bigint,update_time bigint,UNIQUE(system_id,parent_id,name))");
  jdbc.execute("CREATE TABLE functional_case_system_relation(id varchar(255) primary key,case_id varchar(255),system_id varchar(255),system_module_id varchar(255),create_user varchar(255),create_time bigint,UNIQUE(case_id,system_id))");
  jdbc.update("INSERT INTO project VALUES('hub','org',0,1)");jdbc.update("INSERT INTO case_asset_catalog VALUES('catalog','org','root',0)");
  jdbc.update("INSERT INTO functional_case_module VALUES('root','hub','NONE'),('child','hub','root')");
  jdbc.update("INSERT INTO functional_case VALUES('asset','hub','child',null,null,0,null,0),('foreign','other','child',null,null,0,null,0)");
  jdbc.update("INSERT INTO functional_business_system VALUES('system','org','system',1,'user','user',0,0)");
  var target=new CaseAssetSystemService();ReflectionTestUtils.setField(target,"jdbcTemplate",jdbc);
  var resolver=new DefaultHubModuleResolver();ReflectionTestUtils.setField(resolver,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(target,"defaultHubModuleResolver",resolver);
  var hub=mock(DefaultHubProjectService.class);when(hub.getDefaultProjectId()).thenReturn("hub");ReflectionTestUtils.setField(target,"defaultHubProjectService",hub);
  var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(CaseAssetSystemService)factory.getProxy();
  executor=Executors.newFixedThreadPool(2);
 }
 @AfterEach void cleanup() throws Exception {
  if(executor!=null){executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
  if(admin!=null && schema.matches("msp38_system_[a-f0-9]{32}"))admin.execute("DROP DATABASE "+schema);
 }
 void identity(Runnable action){
  try(var session=mockStatic(SessionUtils.class);var ids=mockStatic(IDGenerator.class)){
   session.when(SessionUtils::getCurrentOrganizationId).thenReturn("org");session.when(SessionUtils::getUserId).thenReturn("user");ids.when(IDGenerator::nextStr).thenAnswer(i->UUID.randomUUID().toString());action.run();
  }
 }
 CaseAssetSystemRequest request(){var r=new CaseAssetSystemRequest();r.setIds(List.of("asset"));r.setSystemId("system");return r;}
 @Test void relationInsertFailureRollsBackCaseAndPreviousRelation(){
  jdbc.update("UPDATE functional_case SET system_id='old' WHERE id='asset'");jdbc.update("INSERT INTO functional_case_system_relation VALUES('old-relation','asset','old',null,'user',0)");
  jdbc.execute("CREATE TRIGGER fail_relation BEFORE INSERT ON functional_case_system_relation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private-token-sentinel'");
  var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(CaseAssetSystemService.class);
  var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
  try {
   identity(()->{var error=assertThrows(RuntimeException.class,()->service.classify(request()));assertFalse(error.getMessage().contains("private-token-sentinel"));});
   var event=appender.list.stream().filter(e->e.getFormattedMessage().contains("event=asset_system_write_failure")).findFirst().orElseThrow();
   String message=event.getFormattedMessage();assertTrue(message.contains("category=DATABASE_WRITE"));assertTrue(message.contains("sqlState=45000"));assertTrue(message.contains("errorCode=1644"));assertTrue(message.matches(".*time=[0-9]+.*"));assertTrue(message.contains("rootType=SQLException"));assertTrue(message.contains("orgId=org"));assertTrue(message.contains("actorId=user"));
   for(var logged:appender.list){assertFalse(logged.getFormattedMessage().contains("private-token-sentinel"));assertNull(logged.getThrowableProxy());}
  } finally {logger.detachAppender(appender);appender.stop();}
  assertEquals("old",jdbc.queryForObject("SELECT system_id FROM functional_case WHERE id='asset'",String.class));
  assertEquals("old",jdbc.queryForObject("SELECT system_id FROM functional_case_system_relation WHERE case_id='asset'",String.class));
 }
 @Test void deleteWaitsForClassificationCommitThenRejects() throws Exception {
  var classified=new CountDownLatch(1);var commit=new CountDownLatch(1);var deleting=new CountDownLatch(1);
  Future<?> first=executor.submit(()->identity(()->tx.executeWithoutResult(status->{
   assertEquals("REPEATABLE-READ",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));
   service.classify(request());classified.countDown();try{if(!commit.await(10,TimeUnit.SECONDS))throw new AssertionError("commit latch timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
  })));
  try{
   assertTrue(classified.await(10,TimeUnit.SECONDS));
   Future<?> second=executor.submit(()->identity(()->{deleting.countDown();assertThrows(RuntimeException.class,()->service.deleteSystem("system"));}));
   assertTrue(deleting.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->second.get(350,TimeUnit.MILLISECONDS));
   commit.countDown();first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM functional_business_system WHERE id='system'",Integer.class));
   assertEquals("system",jdbc.queryForObject("SELECT system_id FROM functional_case WHERE id='asset'",String.class));
   assertEquals("system",jdbc.queryForObject("SELECT system_id FROM functional_case_system_relation WHERE case_id='asset'",String.class));
  }finally{commit.countDown();}
 }
}
