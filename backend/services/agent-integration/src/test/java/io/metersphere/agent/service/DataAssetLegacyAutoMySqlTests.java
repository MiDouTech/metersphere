package io.metersphere.agent.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
@EnabledIfSystemProperty(named="quality.mysql",matches="true")
class DataAssetLegacyAutoMySqlTests {
 private static DriverManagerDataSource source(String schema){return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root","quality-test-only");}
 @Test void mixedCollationPersistentSchedulingRespectsSourcePermissionsAndRetry() throws Exception {
  assertNotNull(System.getProperty("quality.mysql.datadir"));
  var admin=new JdbcTemplate(source("quality_verify"));
  assertEquals(Path.of(System.getProperty("quality.mysql.datadir")).toRealPath(),Path.of(admin.queryForObject("SELECT @@datadir",String.class)).toRealPath());
  String schema="legacy_quality_"+UUID.randomUUID().toString().replace("-","");
  admin.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
  try {
   var jdbc=new JdbcTemplate(source(schema));Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("backend/framework/domain")))root=root.getParent();
   for(String migration:List.of("160__data_assets","166__data_asset_legacy_auto"))for(String sql:Files.readString(root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_"+migration+".sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
   jdbc.execute("CREATE TABLE project(id varchar(64),organization_id varchar(64),deleted boolean,enable boolean) COLLATE utf8mb4_general_ci");
   jdbc.execute("CREATE TABLE file_metadata(id varchar(64),ref_id varchar(64),project_id varchar(64),type varchar(16)) COLLATE utf8mb4_general_ci");
   jdbc.update("INSERT INTO project VALUES('p1','org',0,1),('p2','org',0,1),('private','org',0,1)");
   jdbc.update("INSERT INTO file_metadata VALUES('f1',NULL,'p1','CSV'),('f2',NULL,'p2','CSV'),('secret',NULL,'private','CSV')");
   var global=mock(GlobalAssetAccess.class);var access=mock(AgentBusinessAccess.class);var legacy=mock(DataAssetLegacyService.class);var scope=new GlobalAssetAccess.Scope("org","actor");
   when(global.authorize(any(),anyString())).thenReturn(scope);when(global.context(anyString())).thenReturn(scope);when(global.context("private")).thenThrow(AgentBusinessAccess.denied());
   when(access.userHasPermission(eq("actor"),anyString(),eq("PROJECT_FILE_MANAGEMENT:READ"))).thenReturn(true);
   var service=new DataAssetLegacyAutoService(jdbc,global,access,legacy);
   var status=service.status(null);assertEquals(2L,((Map<?,?>)status.get("counts")).get("pending"));assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_legacy_job WHERE project_id='private'",Long.class));
   when(legacy.importFile(anyString(),anyString())).thenAnswer(inv->{String project=inv.getArgument(0),family=inv.getArgument(1);for(String id:jdbc.queryForList("SELECT id FROM file_metadata WHERE project_id=? AND COALESCE(NULLIF(ref_id,''),id)=?",String.class,project,family))jdbc.update("INSERT IGNORE INTO data_asset_source VALUES(?,?,?,?)",id,project,"asset-"+family,"version-"+id);return Map.of();});
   service.run(null,false);assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_legacy_job WHERE status='DONE'",Long.class));verify(legacy,times(1)).importFile(anyString(),anyString());
   jdbc.update("INSERT INTO file_metadata VALUES('f1-v2','f1','p1','CSV')");service.status(null);assertEquals("QUEUED",jdbc.queryForObject("SELECT status FROM data_asset_legacy_job WHERE project_id='p1'",String.class));
   service.run(null,false);service.run(null,false);assertEquals(2L,jdbc.queryForObject("SELECT COUNT(*) FROM data_asset_legacy_job WHERE status='DONE'",Long.class));
   jdbc.update("INSERT INTO file_metadata VALUES('failure',NULL,'p1','CSV')");when(legacy.importFile("p1","failure")).thenThrow(new IllegalStateException("private-secret-content"));
   service.run(null,false);var failed=jdbc.queryForMap("SELECT error_code,trace_id,status FROM data_asset_legacy_job WHERE family_id='failure'");assertEquals("FAILED",failed.get("status"));assertNotNull(failed.get("trace_id"));assertFalse(failed.toString().contains("private-secret-content"));
   service.run(null,false);verify(legacy,times(1)).importFile("p1","failure");service.run(null,true);verify(legacy,times(2)).importFile("p1","failure");
  } finally {admin.execute("DROP DATABASE `"+schema+"`");}
 }
}
