package io.metersphere.functional.hub.service;
import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CaseAssetSourceMappingRepairTests {
 JdbcTemplate jdbc;CaseAssetLockedMirrorSnapshot snapshots;CaseAssetSourceMappingRepair repair;DefaultHubCaseMapRow map;TransactionTemplate tx;
 @BeforeEach void setup(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=VALUE;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  jdbc.execute("CREATE TABLE project(id varchar primary key,organization_id varchar)");jdbc.update("INSERT INTO project VALUES('source-project','org')");
  jdbc.execute("CREATE TABLE functional_case(id varchar primary key,project_id varchar,module_id varchar,ref_id varchar,latest int,deleted int,create_time bigint,update_time bigint,name varchar,case_edit_type varchar,tags varchar,review_status varchar,last_execute_result varchar,execute_user varchar,last_execute_user varchar,last_execute_time bigint)");
  jdbc.update("INSERT INTO functional_case(id,project_id,module_id,ref_id,latest,deleted,create_time,update_time) VALUES('source','source-project','src-module','source-ref',1,0,1,1),('orphan','hub','catalog','orphan',1,0,1,1),('canonical','hub','catalog','canonical',1,0,1,1)");
  jdbc.execute("CREATE TABLE functional_case_blob(id varchar primary key,steps varbinary,text_description varbinary,expected_result varbinary,prerequisite varbinary,description varbinary)");
  jdbc.update("INSERT INTO functional_case_blob(id,steps) VALUES('source',?),('orphan',?),('canonical',?)","source".getBytes(),"same".getBytes(),"same".getBytes());
  jdbc.execute("CREATE TABLE functional_case_custom_field(case_id varchar,field_id varchar,`value` varchar)");
  jdbc.execute("CREATE TABLE functional_case_attachment(id varchar,case_id varchar,file_id varchar,file_name varchar)");jdbc.execute("CREATE TABLE file_association(id varchar,source_id varchar,file_id varchar,deleted int)");
  jdbc.execute("CREATE TABLE default_hub_case_map(id varchar primary key,biz_case_id varchar,hub_case_id varchar,update_time bigint)");jdbc.update("INSERT INTO default_hub_case_map VALUES('map','source','orphan',1)");
  jdbc.execute("CREATE TABLE case_asset_source_relation(source_project_id varchar,source_case_id varchar,asset_case_id varchar)");jdbc.update("INSERT INTO case_asset_source_relation VALUES('source-project','source-ref','canonical')");
  jdbc.execute("CREATE TABLE case_asset_lineage(source_case_id varchar,target_case_id varchar)");jdbc.execute("CREATE TABLE test_plan_functional_case(functional_case_id varchar)");jdbc.execute("CREATE TABLE case_review_functional_case(case_id varchar)");jdbc.execute("CREATE TABLE operation_log(source_id varchar)");jdbc.execute("CREATE TABLE test_asset_version(asset_type varchar,asset_id varchar)");jdbc.execute("CREATE TABLE test_asset_relation(source_asset_type varchar,source_asset_id varchar,target_asset_type varchar,target_asset_id varchar)");
  jdbc.execute("CREATE TABLE functional_case_comment(case_id varchar)");jdbc.execute("CREATE TABLE case_review_functional_case_archive(case_id varchar)");
  jdbc.execute("CREATE TABLE case_asset_source_edit_audit(id varchar,asset_case_id varchar,organization_id varchar,operator_id varchar,edit_mode varchar,source_snapshot varchar,create_time bigint)");
  map=new DefaultHubCaseMapRow();map.setId("map");map.setBizCaseId("source");map.setBizProjectId("source-project");map.setHubCaseId("orphan");map.setContentHash("source-hash");repair=new CaseAssetSourceMappingRepair();ReflectionTestUtils.setField(repair,"jdbcTemplate",jdbc);var hub=mock(io.metersphere.system.service.DefaultHubProjectService.class);when(hub.getDefaultProjectId()).thenReturn("hub");ReflectionTestUtils.setField(repair,"defaultHubProjectService",hub);snapshots=new CaseAssetLockedMirrorSnapshot();ReflectionTestUtils.setField(snapshots,"jdbc",jdbc);ReflectionTestUtils.setField(repair,"lockedSnapshots",snapshots);map.setContentHash(snapshots.lockAndRead(java.util.List.of("source")).get("source").contentHash());
 }
 void run(String oldHash,String canonicalHash){jdbc.update("UPDATE functional_case_blob SET steps=? WHERE id='orphan'",oldHash.getBytes());jdbc.update("UPDATE functional_case_blob SET steps=? WHERE id='canonical'",canonicalHash.getBytes());repairNow();}
 void repairNow(){try(var ids=mockStatic(IDGenerator.class)){ids.when(IDGenerator::nextStr).thenReturn("audit");tx.executeWithoutResult(s->repair.repair(map,"canonical","source-ref","operator","catalog"));}}

 @Test void repairsOnlyUnusedIdenticalMirrorAndRetainsBothRecordsAndAudit(){run("same","same");assertEquals("canonical",jdbc.queryForObject("SELECT hub_case_id FROM default_hub_case_map",String.class));assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM functional_case",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM case_asset_source_edit_audit",Integer.class));}
 @Test void rejectsHistoricalOrReferencedDuplicateWithoutMutation(){jdbc.update("INSERT INTO test_asset_version VALUES('CASE','orphan')");assertThrows(MSException.class,()->run("same","same"));assertEquals("orphan",jdbc.queryForObject("SELECT hub_case_id FROM default_hub_case_map",String.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM test_asset_version",Integer.class));}
 @Test void rejectsDifferentContentAndIndependentEdit(){assertThrows(MSException.class,()->run("old","changed"));jdbc.update("UPDATE functional_case SET update_time=2 WHERE id='orphan'");assertThrows(MSException.class,()->run("same","same"));}
 @Test void rejectsWrongSourceAndDirectory(){map.setBizProjectId("other");assertThrows(MSException.class,()->run("same","same"));map.setBizProjectId("source-project");jdbc.update("UPDATE functional_case SET module_id='other-catalog' WHERE id='orphan'");assertThrows(MSException.class,()->run("same","same"));}
 @Test void rejectsCanonicalBlobCommittedAfterPreLockHashWasCalculated(){
  jdbc.update("UPDATE functional_case_blob SET steps=? WHERE id='canonical'","independently-edited-after-hash".getBytes());
  assertThrows(MSException.class,this::repairNow);
  assertEquals("orphan",jdbc.queryForObject("SELECT hub_case_id FROM default_hub_case_map",String.class));
 }
 @Test void concurrentBlobWriterIsObservedAfterTheLockInsteadOfUsingAnEarlierHash() throws Exception {
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  var changed=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  try {
   var writer=pool.submit(()->tx.executeWithoutResult(status->{jdbc.update("UPDATE functional_case_blob SET steps=? WHERE id='canonical'","concurrent-independent-edit".getBytes());changed.countDown();try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("bounded fixture wait");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
   assertTrue(changed.await(2,java.util.concurrent.TimeUnit.SECONDS));
   var repairing=pool.submit(this::repairNow);
   assertThrows(java.util.concurrent.TimeoutException.class,()->repairing.get(200,java.util.concurrent.TimeUnit.MILLISECONDS));
   release.countDown();writer.get(3,java.util.concurrent.TimeUnit.SECONDS);
   var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->repairing.get(3,java.util.concurrent.TimeUnit.SECONDS));assertInstanceOf(MSException.class,failure.getCause());
   assertEquals("orphan",jdbc.queryForObject("SELECT hub_case_id FROM default_hub_case_map",String.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM case_asset_source_edit_audit",Integer.class));
  } finally {release.countDown();pool.shutdownNow();}
 }
 @Test void currentCustomFieldsAttachmentsAndFileAssociationsParticipateInTheLockedComparison(){
  jdbc.update("INSERT INTO functional_case_custom_field VALUES('canonical','priority','P1')");assertThrows(MSException.class,this::repairNow);jdbc.update("DELETE FROM functional_case_custom_field");
  jdbc.update("INSERT INTO functional_case_attachment VALUES('attachment','canonical','file','proof.png')");assertThrows(MSException.class,this::repairNow);jdbc.update("DELETE FROM functional_case_attachment");
  jdbc.update("INSERT INTO file_association VALUES('association','canonical','file',0)");assertThrows(MSException.class,this::repairNow);
  assertEquals("orphan",jdbc.queryForObject("SELECT hub_case_id FROM default_hub_case_map",String.class));
 }
}
