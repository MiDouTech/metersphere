package io.metersphere.agent.service;

import io.metersphere.agent.security.SharedConfigurationCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="quality.mysql", matches="true")
class SharedConfigurationAuditMySqlTests {
  @Test void actualMigrationsAuditPagingReasonsAndNoopUseRealMySql() throws Exception {
    var ds = new SingleConnectionDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only", true);
    String schema = "audit_verify_" + UUID.randomUUID().toString().replace("-", "");
    var jdbc = new JdbcTemplate(ds);
    try {
      jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
      jdbc.execute("USE `" + schema + "`");
      jdbc.execute("CREATE TABLE project(id VARCHAR(64) PRIMARY KEY)");
      jdbc.update("INSERT INTO project VALUES (?)", "project");
      Path root = Path.of("").toAbsolutePath();
      while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
      Path ddl = root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl");
      String base = Files.readString(ddl.resolve("V3.7.2_152__shared_configurations.sql"));
      for (String statement : base.substring(0, base.indexOf("INSERT IGNORE")).split(";"))
        if (!statement.isBlank()) jdbc.execute(statement);
      for (String file : List.of("V3.7.2_157__shared_configuration_secret_type.sql", "V3.7.2_158__shared_configuration_audit_paging.sql"))
        for (String statement : Files.readString(ddl.resolve(file)).split(";"))
          if (!statement.isBlank()) jdbc.execute(statement);
      jdbc.execute("CREATE TABLE organization(id VARCHAR(64) PRIMARY KEY)");
      jdbc.update("INSERT INTO organization VALUES('org')");
      jdbc.execute("ALTER TABLE shared_configuration ADD organization_id VARCHAR(64)");
      var global = mock(GlobalAssetAccess.class);
      when(global.authorize(any(), anyString())).thenAnswer(call -> new GlobalAssetAccess.Scope("project".equals(call.getArgument(0)) ? "org" : "outside", "actor"));
      var auth = mock(BusinessAssetService.class);
      when(auth.globalAccess()).thenReturn(global);
      when(auth.authorize(eq("project"), anyBoolean(), anyString(), anyString())).thenReturn("actor");
      var service = BusinessAssetTransactionProxy.wrap(new SharedConfigurationService(jdbc, auth,
          new SharedConfigurationCipher(Base64.getEncoder().encodeToString(new byte[32]))), jdbc);
      String id = (String) service.save("project", null, "s", "test", "audit fixture", "SECRET", "synthetic-sensitive", "", 0).get("id");
      service.save("project", id, "s", "test", "audit fixture", "SECRET", "synthetic-replacement", "", 1);
      service.select("project", id, 1, 2, "auditable rollback");
      var before = service.audits("project", id, 1, 100, "SELECT_VERSION", 1);
      assertEquals(1L, ((Number) before.get("total")).longValue());
      assertEquals("auditable rollback", ((Map<?, ?>)((List<?>)before.get("items")).getFirst()).get("reason"));
      service.select("project", id, 1, 1, "repeat current");
      assertEquals(before, service.audits("project", id, 1, 100, "SELECT_VERSION", 1));
      var first = service.audits("project", id, 1, 2, null, null);
      var second = service.audits("project", id, 2, 2, null, null);
      assertEquals(3L, ((Number) first.get("total")).longValue());
      assertEquals(2, ((List<?>) first.get("items")).size());
      assertEquals(1, ((List<?>) second.get("items")).size());
      assertFalse(first.toString().contains("synthetic-sensitive"));
      assertThrows(io.metersphere.sdk.exception.MSException.class,
          () -> service.audits("another-project", id, 1, 20, null, null));
      assertEquals(3, jdbc.queryForList("SHOW INDEX FROM shared_configuration_audit WHERE Key_name='idx_shared_configuration_audit_page'").size());
    } finally {
      jdbc.execute("USE quality_verify");
      jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      ds.destroy();
    }
  }
}
