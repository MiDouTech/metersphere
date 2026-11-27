package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.controller.DataAssetPublicationController;
import io.metersphere.agent.mapper.TestAssetMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class GlobalDataExecutionMySqlTests {
  @Test
  void realMapperPublicationAndLeaseKeepOldGlobalVersionAndBlockActiveDeletion() throws Exception {
    var ds =
        new SingleConnectionDataSource(
            "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true",
            "root",
            "quality-test-only",
            true);
    var jdbc = new JdbcTemplate(ds);
    String schema = "data_execution_" + UUID.randomUUID().toString().replace("-", "");
    try (var ids = mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
      ids.when(io.metersphere.system.uid.IDGenerator::nextStr)
          .thenAnswer(call -> UUID.randomUUID().toString());
      jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      jdbc.execute("USE `" + schema + "`");
      Path root = Path.of("").toAbsolutePath();
      while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
      Path ddl = root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/ddl");
      for (String statement :
          Files.readString(ddl.resolve("V3.7.2_160__data_assets.sql")).split(";"))
        if (!statement.isBlank()) jdbc.execute(statement);
      createTable(
          jdbc,
          ddl.resolve("V3.7.2_44__test_asset_agent_delegation_core.sql"),
          "test_asset_version");
      createTable(
          jdbc,
          ddl.resolve("V3.7.2_44__test_asset_agent_delegation_core.sql"),
          "test_asset_relation");
      createTable(
          jdbc,
          ddl.resolve("V3.7.2_80__ai_execution_governance_foundation.sql"),
          "ai_test_data_lease");
      String integrity =
          Files.readString(ddl.resolve("V3.7.2_88__ai_execution_integrity_completion.sql"));
      int alter = integrity.indexOf("ALTER TABLE ai_test_data_lease");
      jdbc.execute(integrity.substring(alter, integrity.indexOf(';', alter)));
      jdbc.execute(
          "CREATE TABLE project(id varchar(64),organization_id varchar(64),deleted boolean,enable"
              + " boolean,name varchar(255))");
      jdbc.update("INSERT INTO project VALUES('p','org',0,1,'执行项目')");
      jdbc.execute(
          "CREATE TABLE ai_execution_task(id varchar(64),project_id"
              + " varchar(64),current_execution_id varchar(64),status varchar(32),preflight_id"
              + " varchar(64))");
      jdbc.update("INSERT INTO ai_execution_task VALUES('task','p','run','RUNNING','pre')");
      jdbc.execute(
          "CREATE TABLE ai_execution_preflight(id varchar(64),task_id varchar(64),project_id"
              + " varchar(64),actor_id varchar(64),status varchar(32))");
      jdbc.update("INSERT INTO ai_execution_preflight VALUES('pre','task','p','actor','PASSED')");
      jdbc.execute("CREATE TABLE organization(id varchar(64),enable boolean,deleted boolean)");
      jdbc.execute("CREATE TABLE user(id varchar(64),enable boolean,deleted boolean)");
      jdbc.execute("CREATE TABLE user_role(id varchar(64),type varchar(32),enabled boolean)");
      jdbc.execute(
          "CREATE TABLE user_role_relation(user_id varchar(64),role_id varchar(64),source_id"
              + " varchar(64))");
      jdbc.execute(
          "CREATE TABLE user_role_permission(role_id varchar(64),permission_id varchar(128))");
      jdbc.update("INSERT INTO organization VALUES('org',1,0)");
      jdbc.update("INSERT INTO user VALUES('actor',1,0)");
      jdbc.update("INSERT INTO user_role VALUES('reader','ORGANIZATION',1)");
      jdbc.update("INSERT INTO user_role_relation VALUES('actor','reader','org')");
      jdbc.update("INSERT INTO user_role_permission VALUES('reader','TEST_DATA_ASSET:READ')");
      var delegatedGlobal = new GlobalAssetAccess(jdbc, new AgentBusinessAccess(jdbc));
      var global = mock(GlobalAssetAccess.class);
      when(global.authorize(any(), anyString()))
          .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
      var project = mock(AgentBusinessAccess.class);
      when(project.userHasPermission(anyString(), anyString(), anyString())).thenReturn(true);
      var data = new DataAssetService(jdbc, global);
      ReflectionTestUtils.setField(data, "projectAccess", project);
      var factory = new org.mybatis.spring.SqlSessionFactoryBean();
      factory.setDataSource(ds);
      factory.setMapperLocations(
          new org.springframework.core.io.FileSystemResource(
              root.resolve(
                  "backend/services/agent-integration/src/main/java/io/metersphere/agent/mapper/TestAssetMapper.xml")));
      var mapper =
          new org.mybatis.spring.SqlSessionTemplate(Objects.requireNonNull(factory.getObject()))
              .getMapper(TestAssetMapper.class);
      var versions = new TestAssetVersionService();
      ReflectionTestUtils.setField(versions, "mapper", mapper);
      var publication = new DataAssetPublicationController(data, global, project, versions);
      var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
      var first =
          tx.execute(
              s ->
                  data.save(
                      null, Map.of("name", "订单", "format", "JSON", "content", Map.of("金额", 20))));
      String id = first.get("id").toString(), raw = first.get("currentVersionId").toString();
      var published = tx.execute(s -> publication.publish(id, "p", raw));
      assertEquals(raw, published.getSourceVersion());
      tx.execute(s -> data.save(id, Map.of("expectedRevision", 1, "content", Map.of("金额", 99))));
      var preflight = mock(AgentExecutionPreflightService.class);
      when(preflight.assertFrozenExecutableAsset("pre", "DATASET", id))
          .thenReturn(versions.getFrozen(published.getId(), "p", "DATASET", id));
      var files = mock(io.metersphere.project.service.FileMetadataService.class);
      var lease = new AgentTestDataLeaseService();
      ReflectionTestUtils.setField(lease, "jdbc", jdbc);
      ReflectionTestUtils.setField(lease, "preflightService", preflight);
      ReflectionTestUtils.setField(lease, "globalAssetAccess", delegatedGlobal);
      io.metersphere.agent.security.AgentTokenContext.clear();
      jdbc.update("UPDATE ai_execution_preflight SET task_id='wrong-task'");
      assertThrows(
          RuntimeException.class,
          () -> tx.execute(state -> lease.acquire("task", id, "wrong-task", 60000)));
      jdbc.update("UPDATE ai_execution_preflight SET task_id='task',project_id='wrong-project'");
      assertThrows(
          RuntimeException.class,
          () -> tx.execute(state -> lease.acquire("task", id, "wrong-project", 60000)));
      jdbc.update("UPDATE ai_execution_preflight SET project_id='p'");
      jdbc.update("UPDATE user_role SET enabled=0");
      assertThrows(
          RuntimeException.class,
          () -> tx.execute(state -> lease.acquire("task", id, "revoked", 60000)));
      jdbc.update("UPDATE user_role SET enabled=1,type='PROJECT'");
      assertThrows(
          RuntimeException.class,
          () -> tx.execute(state -> lease.acquire("task", id, "project-role", 60000)));
      jdbc.update("UPDATE user_role SET type='ORGANIZATION'");
      jdbc.update("UPDATE user_role_relation SET source_id='outside-org'");
      assertThrows(
          RuntimeException.class,
          () -> tx.execute(state -> lease.acquire("task", id, "cross-org-actor", 60000)));
      jdbc.update("UPDATE user_role_relation SET source_id='org'");
      jdbc.update("UPDATE data_asset SET organization_id='outside-org' WHERE id=?", id);
      assertThrows(
          RuntimeException.class,
          () -> tx.execute(state -> lease.acquire("task", id, "cross-org-asset", 60000)));
      jdbc.update("UPDATE data_asset SET organization_id='org' WHERE id=?", id);

      ReflectionTestUtils.setField(lease, "fileMetadataService", files);
      var independentDs =
          new DriverManagerDataSource(
              "jdbc:mysql://127.0.0.1:13326/"
                  + schema
                  + "?useSSL=false&allowPublicKeyRetrieval=true",
              "root",
              "quality-test-only");
      var independentJdbc = new JdbcTemplate(independentDs);
      var independentTx = new TransactionTemplate(new DataSourceTransactionManager(independentDs));
      var independentLease = new AgentTestDataLeaseService();
      ReflectionTestUtils.setField(independentLease, "jdbc", independentJdbc);
      ReflectionTestUtils.setField(independentLease, "preflightService", preflight);
      ReflectionTestUtils.setField(
          independentLease,
          "globalAssetAccess",
          new GlobalAssetAccess(independentJdbc, new AgentBusinessAccess(independentJdbc)));
      var leased =
          new java.util.concurrent.atomic.AtomicReference<
              io.metersphere.agent.dto.AgentTestDataLeaseDTO>();
      tx.executeWithoutResult(
          state -> {
            data.detail(id);
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_test_data_lease WHERE dataset_id=?",
                    Integer.class,
                    id));
            leased.set(
                independentTx.execute(other -> independentLease.acquire("task", id, "row", 60000)));
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_test_data_lease WHERE dataset_id=?",
                    Integer.class,
                    id));
            assertThrows(RuntimeException.class, () -> data.delete(id, 2));
          });
      var acquired = leased.get();
      String snapshot =
          new String(
              Objects.requireNonNull(
                  lease.content(acquired.getId(), acquired.getLeaseToken(), "run").getBody()),
              java.nio.charset.StandardCharsets.UTF_8);
      assertTrue(snapshot.contains("20"));
      assertFalse(snapshot.contains("99"));
      verifyNoInteractions(files);
      assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> data.delete(id, 2)));
      jdbc.update("UPDATE ai_test_data_lease SET status='RELEASED'");
      jdbc.update(
          "INSERT INTO"
              + " test_asset_relation(id,project_id,relation_type,source_asset_type,source_asset_id,target_asset_type,target_asset_id,created_at)"
              + " VALUES('active','p','USES','TASK','task','DATASET',?,1)",
          id);
      assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> data.delete(id, 2)));
      jdbc.update("UPDATE ai_execution_task SET status='COMPLETED'");
      tx.executeWithoutResult(s -> data.delete(id, 2));
      assertEquals(
          published.getContentSnapshot(),
          versions.getFrozen(published.getId(), "p", "DATASET", id).getContentSnapshot());
    } finally {
      jdbc.execute("USE quality_verify");
      jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      ds.destroy();
    }
  }

  private static void createTable(JdbcTemplate jdbc, Path file, String table) throws Exception {
    String text = Files.readString(file);
    int start = text.indexOf("CREATE TABLE " + table);
    assertTrue(start >= 0);
    jdbc.execute(text.substring(start, text.indexOf(';', start)));
  }
}
