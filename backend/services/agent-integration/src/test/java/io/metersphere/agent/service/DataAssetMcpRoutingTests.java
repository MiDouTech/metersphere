package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DataAssetMcpRoutingTests {
 @Test void nativeBoundaryPreservesOnlyExplicitCanonicalIdempotencyErrors() {
  for(String code:List.of("IDEMPOTENCY_CONFLICT","IDEMPOTENCY_KEY_REQUIRED","IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED")) {
    var failure=assertThrows(DataAssetFailure.class,()->DataAssetErrors.nativeBoundary(()->{throw new io.metersphere.sdk.exception.MSException(code);}));
    assertEquals(code,failure.code());assertEquals(code.equals("IDEMPOTENCY_KEY_REQUIRED")?400:409,failure.httpStatus());
    assertEquals(new AgentSafeErrorMapper().toApiError(new io.metersphere.sdk.exception.MSException(code),"trace").getMessage(),failure.getMessage());
    assertNotEquals(code,failure.getMessage());assertFalse(failure.getMessage().contains(code));
  }
  var unknown=assertThrows(DataAssetFailure.class,()->DataAssetErrors.nativeBoundary(()->{throw new io.metersphere.sdk.exception.MSException("PRIVATE_UNKNOWN_FAILURE");}));
  assertEquals("DATA_ASSET_INTERNAL_ERROR",unknown.code());assertEquals(500,unknown.httpStatus());
  assertFalse(unknown.getMessage().contains("PRIVATE_UNKNOWN_FAILURE"));
 }
 @Test void readActionsDoNotEnterWriteProtocolOrRequireRequestKey() {
  var operations=mock(AgentOperationExecutor.class);var global=mock(GlobalAssetAccess.class);
  var router=new AgentDataAssetMcpWriteService(operations,global,mock(AgentBusinessAccess.class),mock(JdbcTemplate.class));
  for(String action:List.of("list","detail","content","versions","job","download","legacy_inventory","records","copy_records"))
    assertEquals(Map.of("read",true),router.execute(Map.of("sourceProjectId","p","action",action),()->Map.of("read",true)));
  verifyNoInteractions(operations,global);
 }
 @Test void disabledBusinessWritesAllowReadsButDoNotInvokeMutationActions() {
  var operations=mock(AgentOperationExecutor.class);var global=mock(GlobalAssetAccess.class);var access=mock(AgentBusinessAccess.class);var jdbc=mock(JdbcTemplate.class);
  when(global.authorize(eq("p"),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","actor"));
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(1L);
  when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any(),any(),any(),any(),any())).thenReturn(1L);
  doAnswer(i->{((Runnable)i.getArgument(4)).run();return ((java.util.function.Supplier<?>)i.getArgument(5)).get();})
    .when(operations).execute(anyString(),anyString(),any(),eq(Map.class),any(Runnable.class),any(java.util.function.Supplier.class));
  var router=new AgentDataAssetMcpWriteService(operations,global,access,jdbc);
  var generic=new AgentGenericMcpWriteService(operations,access,mock(AgentProjectService.class),jdbc);
  org.springframework.test.util.ReflectionTestUtils.setField(generic,"dataAssetWrites",router);
  org.springframework.test.util.ReflectionTestUtils.setField(generic,"writesEnabled",false);
  var calls=new java.util.concurrent.atomic.AtomicInteger();
  assertEquals(Map.of("read",true),generic.execute("metersphere.data_asset.manage",Map.of("action","list"),()->Map.of("read",true)));
  for(String kind:List.of("create","update","delete","upload","replace_upload","mark_records_used","batch","retry_job")) {
    var args=new LinkedHashMap<String,Object>(Map.of("action",kind,"sourceProjectId","p","id","a","requestId","key","items",List.of(Map.of("id","a"))));
    var error=assertThrows(io.metersphere.sdk.exception.MSException.class,()->generic.execute("metersphere.data_asset.manage",args,()->{calls.incrementAndGet();return Map.of();}));
    assertEquals("MCP_BUSINESS_WRITE_DISABLED",error.getMessage());
  }
  assertEquals(0,calls.get());
 }
 @Test void batchRequestReplayPreservesPartialSuccessAndRejectsChangedItemsAndRevocation() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE data_asset_batch_job(id varchar(64) PRIMARY KEY,organization_id varchar(64),actor_id varchar(64),status varchar(32),request_json longtext,result_json longtext,create_time bigint,update_time bigint,retry_job_id varchar(64))");
  var assets=mock(DataAssetService.class);var global=mock(GlobalAssetAccess.class);
  when(global.authorize(eq("p"),anyString())).thenReturn(new GlobalAssetAccess.Scope("org","actor"));
  when(assets.save(eq("p"),eq("a"),anyMap())).thenReturn(Map.of());
  when(assets.save(eq("p"),eq("b"),anyMap())).thenThrow(new io.metersphere.sdk.exception.MSException("failed"));
  var batches=new DataAssetBatchService(assets,jdbc,global);
  var items=List.of(Map.<String,Object>of("id","a","expectedRevision",1),Map.<String,Object>of("id","b","expectedRevision",1));
  var first=batches.submit("p",items,"request");var replay=batches.submit("p",items,"request");
  assertEquals(first.get("id"),replay.get("id"));assertEquals("FAILED",replay.get("status"));
  verify(assets,times(1)).save(eq("p"),eq("a"),anyMap());verify(assets,times(1)).save(eq("p"),eq("b"),anyMap());
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->batches.submit("p",List.of(Map.of("id","a","expectedRevision",2)),"request"));
  when(global.authorize(eq("p"),anyString())).thenThrow(AgentBusinessAccess.denied());
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->batches.submit("p",items,"request"));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_batch_job",Integer.class));
 }
}
