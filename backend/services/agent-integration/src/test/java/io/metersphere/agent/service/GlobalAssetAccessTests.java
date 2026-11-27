package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GlobalAssetAccessTests {
  @AfterEach
  void clear() {
    AgentTokenContext.clear();
  }

  @Test
  void tokenRequiresWhitelistedContextBeforeOrganizationLookup() {
    var jdbc = mock(JdbcTemplate.class);
    var access = mock(AgentBusinessAccess.class);
    var token = new AgentToken();
    token.setId("token");
    token.setUserId("actor");
    AgentTokenContext.set(token);
    when(access.activeToken()).thenReturn(token);
    assertThrows(
        RuntimeException.class,
        () -> new GlobalAssetAccess(jdbc, access).authorize(null, "BUSINESS_ASSET:READ"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void currentOrganizationRoleAndTokenWhitelistBothRequired() {
    var jdbc =
        new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:"
                    + java.util.UUID.randomUUID()
                    + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa",
                ""));
    jdbc.execute(
        "CREATE TABLE project(id VARCHAR(64),organization_id VARCHAR(64),deleted BOOLEAN,enable"
            + " BOOLEAN)");
    jdbc.execute("CREATE TABLE organization(id VARCHAR(64),enable BOOLEAN,deleted BOOLEAN)");
    jdbc.execute("CREATE TABLE user(id VARCHAR(64),enable BOOLEAN,deleted BOOLEAN)");
    jdbc.execute("CREATE TABLE user_role(id VARCHAR(64),type VARCHAR(64),enabled BOOLEAN)");
    jdbc.execute(
        "CREATE TABLE user_role_relation(user_id VARCHAR(64),role_id VARCHAR(64),source_id"
            + " VARCHAR(64))");
    jdbc.execute(
        "CREATE TABLE user_role_permission(role_id VARCHAR(64),permission_id VARCHAR(64))");
    jdbc.update("INSERT INTO project VALUES('p','org',FALSE,TRUE),('outside','org',FALSE,TRUE)");
    jdbc.update("INSERT INTO organization VALUES('org',TRUE,FALSE)");
    jdbc.update("INSERT INTO user VALUES('actor',TRUE,FALSE)");
    jdbc.update(
        "INSERT INTO user_role"
            + " VALUES('project_admin','PROJECT',TRUE),('org_admin','ORGANIZATION',TRUE)");
    jdbc.update("INSERT INTO user_role_relation VALUES('actor','project_admin','p')");
    var access = mock(AgentBusinessAccess.class);
    var token = new AgentToken();
    token.setId("token");
    token.setUserId("actor");
    token.setProjectId("p");
    AgentTokenContext.set(token);
    when(access.activeToken()).thenReturn(token);
    var global = new GlobalAssetAccess(jdbc, access);
    assertEquals(new GlobalAssetAccess.Scope("org", "actor"), global.context("p"));
    assertThrows(RuntimeException.class, () -> global.authorize("p", "BUSINESS_ASSET:READ"));
    jdbc.update("INSERT INTO project VALUES('foreign','foreign-org',FALSE,TRUE)");
    jdbc.update("INSERT INTO user_role_relation VALUES('actor','project_admin','foreign')");
    var catalog = mock(TestAssetCatalogService.class);
    when(catalog.sourceCatalog(
            "p", "p", "ENVIRONMENT", null, null, null, null, null, false, 1, 100))
        .thenReturn(new io.metersphere.system.utils.Pager<>(java.util.List.of(), 0, 100, 1));
    var index = new GlobalAssetCatalogService(global, new AgentBusinessAccess(jdbc), catalog, jdbc);
    assertTrue(
        index
            .catalog("p", "ENVIRONMENT", null, null, null, null, null, false, 1, 20)
            .getList()
            .isEmpty());
    verify(catalog)
        .sourceCatalog("p", "p", "ENVIRONMENT", null, null, null, null, null, false, 1, 100);
    verify(catalog, never())
        .sourceCatalog(
            eq("p"),
            eq("foreign"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyInt(),
            anyInt());
    clearInvocations(catalog);
    jdbc.update("DELETE FROM user_role_relation WHERE source_id='p'");
    assertEquals(
        0,
        index.catalog("p", "ENVIRONMENT", null, null, null, null, null, false, 1, 20).getTotal());
    verifyNoInteractions(catalog);
    jdbc.update("INSERT INTO user_role_relation VALUES('actor','project_admin','p')");
    jdbc.update("INSERT INTO user_role_relation VALUES('actor','org_admin','org')");
    assertEquals(
        new GlobalAssetAccess.Scope("org", "actor"), global.authorize("p", "BUSINESS_ASSET:READ"));
    assertThrows(RuntimeException.class, () -> global.authorize("outside", "BUSINESS_ASSET:READ"));
    jdbc.update("UPDATE organization SET enable=FALSE");
    assertThrows(RuntimeException.class, () -> global.authorize("p", "BUSINESS_ASSET:READ"));
    jdbc.update("UPDATE organization SET enable=TRUE");
    jdbc.update("UPDATE user_role SET enabled=FALSE WHERE id='org_admin'");
    assertThrows(RuntimeException.class, () -> global.authorize("p", "BUSINESS_ASSET:READ"));
  }
}
