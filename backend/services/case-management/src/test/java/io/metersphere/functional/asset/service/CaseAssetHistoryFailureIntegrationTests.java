package io.metersphere.functional.asset.service;
import io.metersphere.sdk.util.LogUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetHistoryFailureIntegrationTests {
 @Test void realSqlFailureIsCorrelatedAndIsolatedWithoutLeakingSqlOrPrivateMessage(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE case_asset_history_sync_job(id varchar primary key,status varchar,total_count int,success_count int,skipped_count int,failed_count int,case_created_count int,case_updated_count int,case_skipped_count int,update_time bigint,finish_time bigint)");
  jdbc.execute("CREATE TABLE case_asset_history_sync_item(id varchar primary key,job_id varchar,project_id varchar,status varchar,create_time bigint,update_time bigint,case_created_count int,case_updated_count int,case_skipped_count int,failure_reason varchar)");
  jdbc.update("INSERT INTO case_asset_history_sync_job(id,status) VALUES('job','PENDING')");jdbc.update("INSERT INTO case_asset_history_sync_item(id,job_id,project_id,status,create_time,case_created_count,case_updated_count,case_skipped_count) VALUES('item','job','project','PENDING',1,0,0,0)");
  jdbc.execute("CREATE TABLE committed_cases(id varchar primary key)");
  var service=mock(CaseAssetService.class);when(service.prepareHistoricalProject("project","org","operator")).thenReturn(Map.of("caseIds",List.of("bad-case","good-case"),"hubModuleId","module"));
  var sync=mock(CaseAssetHistoryCaseSyncService.class);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  when(sync.sync("project","org","operator","module","bad-case")).thenAnswer(i->tx.execute(s->{jdbc.update("INSERT INTO committed_cases VALUES('rolled-back')");jdbc.queryForList("SELECT secret_password FROM missing_private_table");return "CREATED";}));
  when(sync.sync("project","org","operator","module","good-case")).thenAnswer(i->tx.execute(s->{jdbc.update("INSERT INTO committed_cases VALUES('good')");return "CREATED";}));
  var worker=new CaseAssetHistorySyncWorker();ReflectionTestUtils.setField(worker,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(worker,"caseAssetService",service);ReflectionTestUtils.setField(worker,"caseSyncService",sync);
  try(var logs=mockStatic(LogUtils.class)){
   worker.execute("job","org","operator");
   assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM case_asset_history_sync_item",String.class));assertEquals(1,jdbc.queryForObject("SELECT case_created_count FROM case_asset_history_sync_item",Integer.class));
   assertEquals(List.of("good"),jdbc.queryForList("SELECT id FROM committed_cases",String.class));
   String reason=jdbc.queryForObject("SELECT failure_reason FROM case_asset_history_sync_item",String.class);assertTrue(reason.contains("item:bad-case"));assertFalse(reason.contains("secret_password"));assertFalse(reason.contains("missing_private_table"));
   logs.verify(()->LogUtils.error(argThat((Object text)->text.toString().contains("reference=item:bad-case")&&text.toString().contains("sqlState=42")&&!text.toString().contains("secret_password")&&!text.toString().contains("missing_private_table"))));
  }
 }
}
