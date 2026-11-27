package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class DataAssetRecordMySqlTests {
 private static void barrier(java.util.concurrent.CyclicBarrier barrier){try{barrier.await(30,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}}
 private static DriverManagerDataSource source(String schema){return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true","root","quality-test-only");}
 @Test void realTransactionsPreserveHundredThousandRecordsAndConcurrentIdempotency() throws Exception {
  assertNotNull(System.getProperty("quality.mysql.datadir"),"isolated harness datadir must be supplied");
  String schema="record_quality_"+UUID.randomUUID().toString().replace("-","");
  var admin=new JdbcTemplate(source("quality_verify"));
  String datadir=admin.queryForObject("SELECT @@datadir",String.class);
  assertEquals(Path.of(System.getProperty("quality.mysql.datadir")).toRealPath(),Path.of(datadir).toRealPath());
  admin.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
  var pool=Executors.newFixedThreadPool(2);
  try {
   var ds1=source(schema);var ds2=source(schema);var jdbc=spy(new JdbcTemplate(ds1));
   Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("backend/framework/domain")))root=root.getParent();
   for(String migration:List.of("160__data_assets","167__data_asset_record_usage"))for(String sql:Files.readString(root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_"+migration+".sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
   jdbc.execute("CREATE TABLE test_asset_relation(source_asset_type varchar(32),target_asset_type varchar(32),source_asset_id varchar(64),target_asset_id varchar(64),project_id varchar(64))");
   jdbc.execute("CREATE TABLE project(id varchar(64),name varchar(255),organization_id varchar(64),deleted boolean,enable boolean)");
   String text="value\n".repeat(100000);var json=new com.fasterxml.jackson.databind.ObjectMapper();
   jdbc.update("INSERT INTO data_asset(id,organization_id,name,format,revision,current_version_id,update_time) VALUES('asset','org','records','TXT',1,'v1',0)");
   jdbc.update("INSERT INTO data_asset_version(id,asset_id,version_no,content_json,create_time,actor_id,metadata_json) VALUES('v1','asset',1,?,0,'actor',?)",json.writeValueAsString(text),"{\"format\":\"TXT\"}");
   var access=mock(GlobalAssetAccess.class);when(access.authorize(any(),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","actor"));
   var asset1=new DataAssetService(jdbc,access);
   var records1=new DataAssetRecordService(jdbc,asset1,access);
   var jdbc2=spy(new JdbcTemplate(ds2));var asset2=new DataAssetService(jdbc2,access);var records2=new DataAssetRecordService(jdbc2,asset2,access);
   var projectionRows=new java.util.concurrent.atomic.AtomicLong();
   for(var observed:List.of(jdbc,jdbc2)) doAnswer(invocation->{projectionRows.addAndGet(((java.util.List<?>)invocation.getArgument(1)).size());return invocation.callRealMethod();}).when(observed).batchUpdate(startsWith("INSERT INTO data_asset_record_projection"),org.mockito.ArgumentMatchers.<Object[]>anyList());
   var tx1=new TransactionTemplate(new DataSourceTransactionManager(ds1));var tx2=new TransactionTemplate(new DataSourceTransactionManager(ds2));
   var start=new java.util.concurrent.CyclicBarrier(2);
   var first=pool.submit(()->{return tx1.execute(s->{asset1.detail(null,"asset");barrier(start);return records1.page(null,"asset","v1",1000,100);});});
   var second=pool.submit(()->{return tx2.execute(s->{asset2.detail(null,"asset");barrier(start);return records2.page(null,"asset","v1",1,100);});});
   var last=first.get(120,TimeUnit.SECONDS);second.get(120,TimeUnit.SECONDS);
   assertEquals(100000L,((Number)last.get("total")).longValue());assertEquals(100,((List<?>)last.get("items")).size());
   assertEquals(100001L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_record_projection",Long.class));
   assertEquals(200,java.util.stream.Stream.concat(org.mockito.Mockito.mockingDetails(jdbc).getInvocations().stream(),org.mockito.Mockito.mockingDetails(jdbc2).getInvocations().stream()).filter(i->i.getMethod().getName().equals("batchUpdate") && i.getArguments().length==2 && i.getArguments()[1] instanceof java.util.List<?>).count());
   assertEquals(100000L,projectionRows.get());
   var keys=jdbc.queryForList("SELECT record_key FROM data_asset_record_projection WHERE record_index>=0 ORDER BY record_index",String.class);
   assertEquals(text.stripTrailing(),tx1.execute(s->records1.copy(null,"asset","v1",keys)).get("content"));
   var mark1=pool.submit(()->tx1.execute(s->records1.used(null,"asset","v1",keys,"same")));
   var mark2=pool.submit(()->tx2.execute(s->records2.used(null,"asset","v1",keys,"same")));
   mark1.get(120,TimeUnit.SECONDS);mark2.get(120,TimeUnit.SECONDS);
   assertEquals(100000L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_record_usage",Long.class));
   assertThrows(io.metersphere.sdk.exception.MSException.class,()->tx1.execute(s->records1.used(null,"asset","v1",keys.subList(0,1),"same")));
   jdbc.update("INSERT INTO data_asset_version(id,asset_id,version_no,content_json,create_time,actor_id,metadata_json) SELECT 'v2',asset_id,2,content_json,0,actor_id,metadata_json FROM data_asset_version WHERE id='v1'");
   jdbc.update("UPDATE data_asset SET current_version_id='v2',name='renamed',revision=2 WHERE id='asset'");
   var page=tx2.execute(s->records2.page(null,"asset","v2",1,100));
   assertTrue(((List<Map<String,Object>>)page.get("items")).stream().allMatch(r->Boolean.TRUE.equals(r.get("used"))));
  } finally {pool.shutdownNow();pool.awaitTermination(30,TimeUnit.SECONDS);admin.execute("DROP DATABASE `"+schema+"`");}
 }
}
