package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
class DataAssetLegacyAutoServiceTests {
 @Test void deniedSourceProjectsAreNotDiscoveredOrExposed() {
  var jdbc=mock(JdbcTemplate.class);var global=mock(GlobalAssetAccess.class);var access=mock(AgentBusinessAccess.class);var legacy=mock(DataAssetLegacyService.class);
  var scope=new GlobalAssetAccess.Scope("org","reader");
  when(global.authorize(null,"TEST_DATA_ASSET:READ")).thenReturn(scope);
  when(global.authorize(null,"TEST_DATA_ASSET:READ+UPDATE")).thenThrow(AgentBusinessAccess.denied());
  when(jdbc.queryForList(anyString(),eq(String.class),eq("org"))).thenReturn(List.of("private"));
  when(global.context("private")).thenThrow(AgentBusinessAccess.denied());
  var result=new DataAssetLegacyAutoService(jdbc,global,access,legacy).status(null);
  assertEquals(false,result.get("canRun"));assertEquals(false,result.get("hasMore"));assertEquals(List.of(),result.get("items"));
  verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(legacy);
 }
 @Test void runRequiresWritePermissionBeforeDiscoveringAnything() {
  var jdbc=mock(JdbcTemplate.class);var global=mock(GlobalAssetAccess.class);var access=mock(AgentBusinessAccess.class);var legacy=mock(DataAssetLegacyService.class);
  when(global.authorize(null,"TEST_DATA_ASSET:READ+UPDATE")).thenThrow(AgentBusinessAccess.denied());
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->new DataAssetLegacyAutoService(jdbc,global,access,legacy).run(null,false));
  verifyNoInteractions(jdbc,access,legacy);
 }
 @Test void unknownAuthorizationFailureIsNeverReportedAsReadOnly() {
  var jdbc=mock(JdbcTemplate.class);var global=mock(GlobalAssetAccess.class);var access=mock(AgentBusinessAccess.class);var legacy=mock(DataAssetLegacyService.class);
  when(global.authorize(null,"TEST_DATA_ASSET:READ")).thenReturn(new GlobalAssetAccess.Scope("org","reader"));
  when(jdbc.queryForList(anyString(),eq(String.class),eq("org"))).thenReturn(List.of());
  when(global.authorize(null,"TEST_DATA_ASSET:READ+UPDATE")).thenThrow(new io.metersphere.sdk.exception.MSException("unexpected"));
  assertThrows(io.metersphere.sdk.exception.MSException.class,()->new DataAssetLegacyAutoService(jdbc,global,access,legacy).status(null));
 }
 @Test void discoveryRequeuesDoneOnlyForUnmappedSourcesAndClaimUsesFence() throws Exception {
  // SQL guards are part of the persistent contract, not an in-memory status cache.
  var jdbc=mock(JdbcTemplate.class);var global=mock(GlobalAssetAccess.class);var access=mock(AgentBusinessAccess.class);var legacy=mock(DataAssetLegacyService.class);
  var scope=new GlobalAssetAccess.Scope("org","writer");when(global.authorize(isNull(),anyString())).thenReturn(scope);when(global.context("p")).thenReturn(scope);
  when(access.userHasPermission("writer","p","PROJECT_FILE_MANAGEMENT:READ")).thenReturn(true);
  when(jdbc.queryForList(anyString(),eq(String.class),eq("org"))).thenReturn(List.of("p"));
  when(jdbc.queryForList(contains("SELECT DISTINCT"),eq(String.class),eq("p"))).thenReturn(List.of("f"));
  when(jdbc.queryForList(contains("SELECT family_id,status"),eq("p"),anyLong())).thenReturn(List.of(Map.of("family_id","f","status","RUNNING","update_time",1L)));
  when(jdbc.update(contains("SET status='RUNNING'"),anyLong(),eq("p"),eq("f"),eq("RUNNING"),eq(1L))).thenReturn(0);
  new DataAssetLegacyAutoService(jdbc,global,access,legacy).run(null,false);
  verify(jdbc,atLeastOnce()).update(contains("AND status='DONE' AND EXISTS"),anyLong(),eq("p"),eq("f"),eq("p"),eq("f"));
  verify(jdbc).queryForList(argThat(sql -> sql.contains("status='RUNNING' AND update_time<?") && !sql.contains("OR status='FAILED'")),eq("p"),anyLong());
  verifyNoInteractions(legacy);
 }
}
