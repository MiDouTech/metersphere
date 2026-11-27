package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.dto.*;
import io.metersphere.system.utils.SessionUtils;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class GlobalDataCategoryMySqlTests {
  @Test
  void realCategoryTreeMovesDataWithoutDeletingContentAndRejectsForeignParents() throws Exception {
    var ds =
        new SingleConnectionDataSource(
            "jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true",
            "root",
            "quality-test-only",
            true);
    var jdbc = new JdbcTemplate(ds);
    String schema = "category_" + UUID.randomUUID().toString().replace("-", "");
    try (var session = mockStatic(SessionUtils.class);
        var ids = mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
      ids.when(io.metersphere.system.uid.IDGenerator::nextStr)
          .thenAnswer(call -> UUID.randomUUID().toString());
      jdbc.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      jdbc.execute("USE `" + schema + "`");
      Path root = Path.of("").toAbsolutePath();
      while (!Files.exists(root.resolve("backend/framework/domain"))) root = root.getParent();
      for (String name :
          List.of(
              "V3.7.2_89__test_asset_source_category_governance.sql",
              "V3.7.2_160__data_assets.sql")) {
        var sql =
            Files.readString(
                root.resolve(
                    "backend/framework/domain/src/main/resources/migration/3.7.2/ddl/" + name));
        if (name.contains("_89__"))
          sql =
              sql.split("INSERT IGNORE", 2)[
                  0]; // Existing category schema; historical source tables are not this fixture's
        // data.
        for (String statement : sql.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
      }
      session.when(SessionUtils::getUserId).thenReturn("actor");
      session.when(SessionUtils::getCurrentOrganizationId).thenReturn("org");
      var categories = new TestAssetGovernanceService();
      ReflectionTestUtils.setField(categories, "jdbc", jdbc);
      var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
      var request = new TestAssetCategorySaveRequest();
      request.setName("订单样本");
      var category = tx.execute(s -> categories.create(request));
      var invalid = new TestAssetCategorySaveRequest();
      invalid.setName("invalid");
      invalid.setParentId("foreign-category");
      assertThrows(RuntimeException.class, () -> tx.execute(s -> categories.create(invalid)));
      var access = mock(GlobalAssetAccess.class);
      when(access.authorize(isNull(), anyString()))
          .thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
      var data = new DataAssetService(jdbc, access);
      var asset =
          tx.execute(
              s ->
                  data.save(
                      null,
                      Map.of(
                          "name",
                          "可读数据",
                          "format",
                          "JSON",
                          "content",
                          Map.of("金额", 20.5),
                          "categoryId",
                          category.getId())));
      assertEquals(1L, categories.tree(null).getFirst().getAssetCount());
      var deletion = new TestAssetCategoryDeleteRequest();
      assertThrows(
          RuntimeException.class,
          () -> tx.executeWithoutResult(s -> categories.delete(category.getId(), deletion)));
      deletion.setStrategy("UNCLASSIFY");
      tx.executeWithoutResult(s -> categories.delete(category.getId(), deletion));
      assertNull(data.detail(asset.get("id").toString()).get("categoryId"));
      assertEquals(
          Map.of("金额", 20.5), data.content(asset.get("id").toString(), null).get("content"));
      assertTrue(categories.tree(null).isEmpty());
    } finally {
      jdbc.execute("USE quality_verify");
      jdbc.execute("DROP DATABASE IF EXISTS `" + schema + "`");
      ds.destroy();
    }
  }
}
