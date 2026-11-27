package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class DataAssetLegacyConcurrencyMySqlTests {
 @Test void defaultRepeatableReadHttpAndReadCommittedMcpSerializeManualCurrent() throws Exception {
  for(int mode:List.of(0,1,2,3))scenario(mode,false,false);
 }
 @Test void manualCommitBeforeTargetLockSurvivesEarlierDiscoverySnapshot() throws Exception {
  for(int mode:List.of(0,1,2,3))scenario(mode,true,false);
 }
 @Test void alreadyManualCurrentAndMixedFormatImportPreserveManualMetadata() throws Exception {
  for(int mode:List.of(0,1,2,3))scenario(mode,true,true);
 }
 private static DriverManagerDataSource source(String schema) {return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");}
 private static void await(CountDownLatch latch) {try {assertTrue(latch.await(20,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
 private void scenario(int mode,boolean beforeLock,boolean alreadyManual) throws Exception {
  var admin=new JdbcTemplate(source("quality_verify"));
  assertNotNull(System.getProperty("quality.mysql.datadir"));
  assertEquals(Path.of(System.getProperty("quality.mysql.datadir")).toRealPath(),Path.of(admin.queryForObject("SELECT @@datadir",String.class)).toRealPath());
  String schema="legacy_race_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
  var pool=Executors.newFixedThreadPool(2);
  try {
   var ds=source(schema);var jdbc=spy(new JdbcTemplate(ds));var manualJdbc=new JdbcTemplate(source(schema));
   Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("backend/framework/domain")))root=root.getParent();
   for(String sql:Files.readString(root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_160__data_assets.sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
   jdbc.execute("CREATE TABLE agent_idempotency_record(id varchar(64) PRIMARY KEY,token_id varchar(64),tool_name varchar(128),request_id varchar(128),request_hash varchar(64),create_time bigint,status varchar(32),hash_version varchar(32),identity_version varchar(32),response_json longtext,completed_time bigint, UNIQUE KEY replay_key(token_id,tool_name,request_id))");
   jdbc.execute("CREATE TABLE test_asset_relation(source_asset_type varchar(32),target_asset_type varchar(32),source_asset_id varchar(64),target_asset_id varchar(64),project_id varchar(64))");
   jdbc.execute("CREATE TABLE project(id varchar(64),organization_id varchar(64),deleted boolean,enable boolean)");jdbc.update("INSERT INTO project VALUES('p','org',false,true)");
   jdbc.execute("CREATE TABLE data_asset_legacy_job(project_id varchar(64),family_id varchar(64),status varchar(32),update_time bigint,error_code varchar(64),trace_id varchar(64),PRIMARY KEY(project_id,family_id))");
   jdbc.execute("CREATE TABLE file_metadata(id varchar(64),name varchar(255),description varchar(2000),type varchar(16),size bigint,project_id varchar(64),storage varchar(16),create_time bigint,ref_id varchar(64),original_name varchar(255))");
   jdbc.update("INSERT INTO file_metadata VALUES('f1','source1','legacy','JSON',2,'p','MINIO',1,'family','source.json'),('f2','source2','legacy','CSV',4,'p','MINIO',2,'family','source.csv')");
   var global=mock(GlobalAssetAccess.class);when(global.authorize(any(),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","actor"));
   when(global.context(anyString())).thenReturn(new GlobalAssetAccess.Scope("org","actor"));
   var access=mock(AgentBusinessAccess.class);when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
   var assets=spy(new DataAssetService(jdbc,global));var manualAssets=new DataAssetService(manualJdbc,global);
   var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
   var original=tx.execute(s->assets.save("p",null,Map.of("name","initial","format","JSON","content",Map.of("old",true))));String id=original.get("id").toString();
   jdbc.update("INSERT INTO data_asset_source VALUES('f1','p',?,?)",id,original.get("currentVersionId"));
   if(alreadyManual)tx.execute(status->assets.save("p",id,Map.of("expectedRevision",1,"name","earlier manual","description","earlier description","content",Map.of("earlier",true))));
   int expectedManualRevision=alreadyManual?2:1;
   var checkpoint=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new AtomicBoolean();
   if(beforeLock)doAnswer(i->{Object result=i.callRealMethod();if(once.compareAndSet(false,true)){checkpoint.countDown();await(release);}return result;}).when(jdbc).queryForList(startsWith("SELECT DISTINCT s.asset_id"),eq("p"),eq("family"));
   else doAnswer(i->{Object result=i.callRealMethod();if(once.compareAndSet(false,true)){checkpoint.countDown();await(release);}return result;}).when(assets).detail("p",id);
   var files=mock(io.metersphere.project.service.FileManagementService.class);when(files.imageReadRequest(any())).thenReturn(new io.metersphere.sdk.file.FileRequest("source","MINIO","f2"));
   var legacy=new DataAssetLegacyService(jdbc,access,global,assets,new DataAssetParser(),files);
   var proxyFactory=new org.springframework.aop.framework.ProxyFactory(legacy);
   proxyFactory.setProxyTargetClass(true);
   proxyFactory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new DataSourceTransactionManager(ds),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
   var proxy=(DataAssetLegacyService)proxyFactory.getProxy();
   var firstRepeatableRead=new AtomicBoolean(true);
   java.util.concurrent.Callable<Map<String,Object>> importCall=()->{
    try(var center=mockStatic(io.metersphere.sdk.file.FileCenter.class)) {
     var repo=mock(io.metersphere.sdk.file.FileRepository.class);when(repo.getFileAsStream(any())).thenAnswer(i->new java.io.ByteArrayInputStream("a,b\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
     center.when(()->io.metersphere.sdk.file.FileCenter.getRepository("MINIO")).thenReturn(repo);
     if(mode==1) {
       var token=new io.metersphere.system.domain.AgentToken();token.setId("token");token.setUserId("actor");io.metersphere.agent.security.AgentTokenContext.set(token);
       try { return new AgentDataAssetMcpWriteService(new AgentOperationExecutor(jdbc,new DataSourceTransactionManager(ds)),global,access,jdbc).execute(Map.of("sourceProjectId","p","action","legacy_import","id","family","requestId","stable"),()->proxy.importFile("p","family")); }
       finally {io.metersphere.agent.security.AgentTokenContext.clear();}
     }
     if(mode==2 && firstRepeatableRead.compareAndSet(true,false)) {
       var outer=new TransactionTemplate(new DataSourceTransactionManager(ds));outer.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
       return outer.execute(status->proxy.importFile("p","family"));
     }
     if(mode==3)return new DataAssetLegacyAutoService(jdbc,global,access,proxy).run("p",false);
     return proxy.importFile("p","family");
    }
   };
   var importer=pool.submit(importCall);
   await(checkpoint);
   var manualTx=new TransactionTemplate(new DataSourceTransactionManager(manualJdbc.getDataSource()));
   var writerStarted=new CountDownLatch(1);
   var writer=pool.submit(()->manualTx.execute(s->{manualAssets.detail("p",id);writerStarted.countDown();return manualAssets.save("p",id,Map.of("expectedRevision",expectedManualRevision,"name","manual title","description","manual description","content",Map.of("manual",true)));}));
   await(writerStarted);
   if(beforeLock) {writer.get(20,TimeUnit.SECONDS);release.countDown();}
   else {assertThrows(TimeoutException.class,()->writer.get(300,TimeUnit.MILLISECONDS));release.countDown();}
   if(mode==2 && beforeLock) {
     var conflict=assertThrows(ExecutionException.class,()->importer.get(30,TimeUnit.SECONDS));assertInstanceOf(io.metersphere.sdk.exception.MSException.class,conflict.getCause());
     assertEquals(1L,manualJdbc.queryForObject("SELECT COUNT(*) FROM data_asset_source WHERE asset_id=?",Long.class,id));
     pool.submit(importCall).get(30,TimeUnit.SECONDS);
   } else importer.get(30,TimeUnit.SECONDS);
   if(mode==1) {
     long versions=manualJdbc.queryForObject("SELECT COUNT(*) FROM data_asset_version WHERE asset_id=?",Long.class,id);
     pool.submit(importCall).get(30,TimeUnit.SECONDS);
     assertEquals(versions,manualJdbc.queryForObject("SELECT COUNT(*) FROM data_asset_version WHERE asset_id=?",Long.class,id));
     when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(false);
     var denied=assertThrows(ExecutionException.class,()->pool.submit(importCall).get(30,TimeUnit.SECONDS));
     assertInstanceOf(io.metersphere.sdk.exception.MSException.class,denied.getCause());
     when(access.userHasPermission(anyString(),anyString(),anyString())).thenReturn(true);
   }
   if(!beforeLock) {
    var conflict=assertThrows(ExecutionException.class,()->writer.get(20,TimeUnit.SECONDS));assertInstanceOf(io.metersphere.sdk.exception.MSException.class,conflict.getCause());
    int revision=((Number)manualAssets.detail("p",id).get("revision")).intValue();
    manualTx.execute(s->manualAssets.save("p",id,Map.of("expectedRevision",revision,"name","manual title","description","manual description","format","CSV","content",Map.of("rows",List.of(List.of("manual"))))));
   }
   var current=manualAssets.detail("p",id);
   assertEquals("manual title",current.get("name"));assertEquals("manual description",current.get("description"));
   assertEquals(beforeLock?"JSON":"CSV",current.get("format"));
   assertEquals(2L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_source WHERE asset_id=?",Long.class,id));
   assertEquals(alreadyManual?4L:3L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_version WHERE asset_id=?",Long.class,id));
   var metadata=(Map<?,?>)manualAssets.content("p",id,null).get("metadata");assertEquals("manual title",metadata.get("name"));
   assertNotEquals(jdbc.queryForObject("SELECT source_version_id FROM data_asset_source WHERE source_file_id='f2'",String.class),current.get("currentVersionId"));
   assertEquals(2L,jdbc.queryForObject("SELECT COUNT(*) FROM file_metadata",Long.class));
  } finally {pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);admin.execute("DROP DATABASE "+schema);}
 }
}
