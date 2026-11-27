package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.security.SharedConfigurationCipher;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class GlobalAssetMigrationMySqlTests {
  @Test
  void actualMigrationPreservesDuplicateAssetsSourceCiphertextAndOrganizationBoundary()
      throws Exception {
    var ds =
        new SingleConnectionDataSource(
            "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true",
            "root",
            "quality-test-only",
            true);
    var jdbc = new JdbcTemplate(ds);
    String schema = "global_asset_" + UUID.randomUUID().toString().replace("-", "");
    try {
      jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      jdbc.execute("USE `" + schema + "`");
      jdbc.execute("CREATE TABLE project(id VARCHAR(64) PRIMARY KEY,organization_id VARCHAR(64))");
      jdbc.update("INSERT INTO project VALUES('p1','org'),('p2','org'),('p3','outside')");
      Path root = Path.of("").toAbsolutePath();
      while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
      Path ddl = root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl");
      execute(
          jdbc,
          Files.readString(ddl.resolve("V3.7.2_150__business_understanding_assets.sql"))
              .split("-- Independent")[0]);
      execute(
          jdbc,
          Files.readString(ddl.resolve("V3.7.2_152__shared_configurations.sql"))
              .split("INSERT IGNORE")[0]);
      execute(
          jdbc, Files.readString(ddl.resolve("V3.7.2_157__shared_configuration_secret_type.sql")));
      execute(
          jdbc, Files.readString(ddl.resolve("V3.7.2_158__shared_configuration_audit_paging.sql")));
      var cipher = new SharedConfigurationCipher(Base64.getEncoder().encodeToString(new byte[32]));
      String encrypted =
          cipher.encrypt("migration fixture".getBytes(StandardCharsets.UTF_8), "p1:config:1");
      jdbc.update(
          "INSERT INTO shared_configuration"
              + " VALUES('config','p1','system','test','name',1),('duplicate','p2','system','test','name',1)");
      jdbc.update(
          "INSERT INTO"
              + " shared_configuration_version(configuration_id,version_no,kind,encrypted_value,file_name,parse_status,actor_id,create_time,secret_type)"
              + " VALUES('config',1,'PLAIN',?,'','NOT_APPLICABLE','actor',1,'')",
          encrypted);
      jdbc.update(
          "INSERT INTO business_asset"
              + " VALUES('a1','p1','identity',NULL,0,FALSE,1,1),('a2','p2','identity',NULL,0,FALSE,1,1)");
      execute(jdbc, Files.readString(ddl.resolve("V3.7.2_160__data_assets.sql")));
      execute(
          jdbc,
          Files.readString(
              ddl.resolve("V3.7.2_162__organization_global_business_configuration.sql")));
      jdbc.execute("CREATE TABLE user_role(id VARCHAR(64),type VARCHAR(32))");
      jdbc.execute(
          "CREATE TABLE user_role_permission(id VARCHAR(64),role_id VARCHAR(64),permission_id"
              + " VARCHAR(128))");
      jdbc.update(
          "INSERT INTO user_role"
              + " VALUES('admin','SYSTEM'),('org_admin','ORGANIZATION'),('project_admin','PROJECT')");
      execute(
          jdbc,
          Files.readString(ddl.getParent().resolve("dml/V3.7.2_163__data_asset_permissions.sql")));
      assertEquals(
          4, jdbc.queryForObject("SELECT COUNT(*) FROM user_role_permission", Integer.class));
      String roleMigration =
          Files.readString(
              ddl.getParent().resolve("dml/V3.7.2_164__global_asset_role_permissions.sql"));
      execute(jdbc, roleMigration);
      execute(jdbc, roleMigration);
      assertEquals(
          16, jdbc.queryForObject("SELECT COUNT(*) FROM user_role_permission", Integer.class));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM user_role_permission WHERE role_id='project_admin'",
              Integer.class));
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM business_asset WHERE organization_id='org'", Integer.class));
      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM shared_configuration WHERE organization_id='org'",
              Integer.class));
      assertEquals(
          encrypted,
          jdbc.queryForObject(
              "SELECT encrypted_value FROM shared_configuration_version WHERE"
                  + " configuration_id='config'",
              String.class));
      assertEquals(
          "p1",
          jdbc.queryForObject(
              "SELECT project_id FROM shared_configuration WHERE id='config'", String.class));
      jdbc.execute(
          "CREATE TABLE permission_resource(code VARCHAR(128) PRIMARY KEY,scope_type"
              + " VARCHAR(32),permission_id VARCHAR(128))");
      jdbc.update(
          "INSERT INTO permission_resource VALUES"
              + " ('TEST_ASSET_MENU','PROJECT',NULL),('TEST_ASSET_DATASETS_PAGE','PROJECT','PROJECT_FILE_MANAGEMENT:READ'),('TEST_ASSET_DOCUMENTS_PAGE','PROJECT','FUNCTIONAL_CASE_AI:READ'),('TEST_ASSET_SHARED_CONFIGURATION_PAGE','PROJECT',NULL),('TEST_ASSET_VERSIONS_PAGE','PROJECT',NULL),('TEST_ASSET_RELATIONS_PAGE','PROJECT','AI_EXECUTION:READ'),('TEST_ASSET_APIS_PAGE','PROJECT','PROJECT_API_DEFINITION:READ')");
      String uiMigration =
          Files.readString(
              ddl.getParent().resolve("dml/V3.7.2_165__global_asset_page_permissions.sql"));
      execute(jdbc, uiMigration);
      execute(jdbc, uiMigration);
      for (String code :
          List.of(
              "TEST_ASSET_MENU",
              "TEST_ASSET_DATASETS_PAGE",
              "TEST_ASSET_DOCUMENTS_PAGE",
              "TEST_ASSET_SHARED_CONFIGURATION_PAGE",
              "TEST_ASSET_VERSIONS_PAGE"))
        assertEquals(
            "ORGANIZATION",
            jdbc.queryForObject(
                "SELECT scope_type FROM permission_resource WHERE code=?", String.class, code));
      assertEquals(
          "TEST_DATA_ASSET:READ",
          jdbc.queryForObject(
              "SELECT permission_id FROM permission_resource WHERE code='TEST_ASSET_DATASETS_PAGE'",
              String.class));
      assertEquals(
          "BUSINESS_ASSET:READ",
          jdbc.queryForObject(
              "SELECT permission_id FROM permission_resource WHERE"
                  + " code='TEST_ASSET_DOCUMENTS_PAGE'",
              String.class));
      assertEquals(
          "SHARED_CONFIGURATION:READ",
          jdbc.queryForObject(
              "SELECT permission_id FROM permission_resource WHERE"
                  + " code='TEST_ASSET_SHARED_CONFIGURATION_PAGE'",
              String.class));
      assertNull(
          jdbc.queryForObject(
              "SELECT permission_id FROM permission_resource WHERE code='TEST_ASSET_VERSIONS_PAGE'",
              String.class));
      assertEquals(
          "PROJECT",
          jdbc.queryForObject(
              "SELECT scope_type FROM permission_resource WHERE code='TEST_ASSET_RELATIONS_PAGE'",
              String.class));
      assertEquals(
          "PROJECT_API_DEFINITION:READ",
          jdbc.queryForObject(
              "SELECT permission_id FROM permission_resource WHERE code='TEST_ASSET_APIS_PAGE'",
              String.class));
      var auth = mock(BusinessAssetService.class);
      var global = mock(GlobalAssetAccess.class);
      when(auth.globalAccess()).thenReturn(global);
      when(global.authorize(any(), anyString()))
          .thenAnswer(
              call ->
                  new GlobalAssetAccess.Scope(
                      "p3".equals(call.getArgument(0)) ? "outside" : "org", "actor"));
      var service = new SharedConfigurationService(jdbc, auth, cipher);
      assertEquals("migration fixture", service.read("p2", "config", 1, false).get("value"));
      assertThrows(BusinessGovernanceException.class, () -> service.read("p3", "config", 1, false));
      assertTrue(jdbc.queryForList("SHOW TABLES LIKE 'data_asset_version'").size() == 1);
    } finally {
      jdbc.execute("USE quality_verify");
      jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      ds.destroy();
    }
  }

  private static void execute(JdbcTemplate jdbc, String sql) {
    for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
  }
}
