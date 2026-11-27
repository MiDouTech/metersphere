package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.metersphere.system.utils.Pager;
import java.util.*;
import org.junit.jupiter.api.Test;

class GlobalAssetCatalogServiceTests {
  @Test
  void sourceVersionReadUsesTargetProjectRoleWithoutCurrentShiroPermission() {
    var context = mock(GlobalAssetAccess.class);
    var permissions = mock(AgentBusinessAccess.class);
    var mapper = mock(io.metersphere.agent.mapper.TestAssetMapper.class);
    when(context.context("hint")).thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    when(context.context("p2")).thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    when(permissions.userHasPermission("actor", "p2", "PROJECT_ENVIRONMENT:READ")).thenReturn(true);
    var service = new TestAssetCatalogService();
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "globalAssetAccess", context);
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "sourceBusinessAccess", permissions);
    org.springframework.test.util.ReflectionTestUtils.setField(service, "mapper", mapper);
    assertTrue(
        service
            .sourceVersions("hint", "p2", "ENVIRONMENT", null, null, null, null, false, 1, 20)
            .getList()
            .isEmpty());
    verify(mapper)
        .countVersions(
            "p2", List.of("ENVIRONMENT"), "ENVIRONMENT", null, null, List.of(), null, false);
    assertThrows(
        RuntimeException.class,
        () -> service.sourceVersions("hint", "p2", "CASE", null, null, null, null, false, 1, 20));
  }

  @Test
  void defaultVersionsChecksEachSourceTypeRatherThanCurrentProjectPermissions() {
    var access = mock(GlobalAssetAccess.class);
    var source = mock(AgentBusinessAccess.class);
    var catalog = mock(TestAssetCatalogService.class);
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    when(access.context("hint")).thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    when(jdbc.queryForList(
            "SELECT id FROM project WHERE organization_id=? AND deleted=0 ORDER BY id",
            String.class,
            "org"))
        .thenReturn(List.of("p1", "p2", "p3"));
    when(source.userHasPermission("actor", "p1", "FUNCTIONAL_CASE:READ")).thenReturn(true);
    when(source.userHasPermission("actor", "p2", "PROJECT_ENVIRONMENT:READ")).thenReturn(true);
    var a = new io.metersphere.agent.dto.TestAssetVersionDTO();
    a.setId("case");
    a.setCreatedAt(2L);
    var b = new io.metersphere.agent.dto.TestAssetVersionDTO();
    b.setId("env");
    b.setCreatedAt(1L);
    when(catalog.sourceVersions("hint", "p1", "CASE", null, null, null, null, false, 1, 100))
        .thenReturn(new Pager<>(List.of(a), 1, 100, 1));
    when(catalog.sourceVersions("hint", "p2", "ENVIRONMENT", null, null, null, null, false, 1, 100))
        .thenReturn(new Pager<>(List.of(b), 1, 100, 1));
    // Functional-case readers also retain their source project's evidence access.
    when(catalog.sourceVersions("hint", "p1", "EVIDENCE", null, null, null, null, false, 1, 100))
        .thenReturn(new Pager<>(List.of(), 0, 100, 1));
    var result =
        new GlobalAssetCatalogService(access, source, catalog, jdbc)
            .versions("hint", null, null, null, null, null, false, 1, 20);
    assertEquals(
        List.of("case", "env"),
        result.getList().stream()
            .map(io.metersphere.agent.dto.TestAssetVersionDTO::getId)
            .toList());
    verify(catalog, times(3))
        .sourceVersions(
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyInt(),
            anyInt());
    verifyNoMoreInteractions(catalog);
  }

  @Test
  void projectHintDoesNotFilterOrganizationCatalogAndSourcePermissionsRemainEffective() {
    var access = mock(GlobalAssetAccess.class);
    var source = mock(AgentBusinessAccess.class);
    var catalog = mock(TestAssetCatalogService.class);
    var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    when(access.context("p1")).thenReturn(new GlobalAssetAccess.Scope("org", "actor"));
    when(jdbc.queryForList(
            "SELECT id FROM project WHERE organization_id=? AND deleted=0 ORDER BY id",
            String.class,
            "org"))
        .thenReturn(List.of("p1", "p2", "p3"));
    when(source.userHasPermission("actor", "p1", "PROJECT_ENVIRONMENT:READ")).thenReturn(true);
    when(source.userHasPermission("actor", "p2", "PROJECT_ENVIRONMENT:READ")).thenReturn(true);
    for (String project : List.of("p1", "p2")) {
      var row = new io.metersphere.agent.dto.TestAssetCatalogItemDTO();
      row.setId(project);
      row.setProjectId(project);
      row.setUpdateTime(1L);
      when(catalog.sourceCatalog(
              "p1", project, "ENVIRONMENT", null, null, null, null, null, false, 1, 100))
          .thenReturn(new Pager<>(List.of(row), 1, 100, 1));
    }
    var result =
        new GlobalAssetCatalogService(access, source, catalog, jdbc)
            .catalog("p1", "ENVIRONMENT", null, null, null, null, null, false, 1, 20);
    assertEquals(
        List.of("p1", "p2"),
        result.getList().stream()
            .map(io.metersphere.agent.dto.TestAssetCatalogItemDTO::getId)
            .toList());
    assertEquals(2, result.getTotal());
    verify(catalog, never())
        .sourceCatalog("p1", "p3", "ENVIRONMENT", null, null, null, null, null, false, 1, 100);
  }

  @Test
  void mergeSortsAcrossSourceProjectsBeforePaging() {
    var result =
        GlobalAssetCatalogService.merge(
            List.of("a", "b"),
            2,
            2,
            id -> new Pager<>(id.equals("a") ? List.of(1, 5, 7) : List.of(2, 4, 6), 3, 100, 1),
            (id, page) -> {
              throw new AssertionError("unneeded page");
            },
            Comparator.<Integer>naturalOrder());
    assertEquals(List.of(4, 5), result.getList());
    assertEquals(6, result.getTotal());
  }

  @Test
  void mergeFetchesEnoughSourcePagesForDeepGlobalPage() {
    var result =
        GlobalAssetCatalogService.merge(
            List.of("a"),
            11,
            10,
            id ->
                new Pager<>(java.util.stream.IntStream.range(0, 100).boxed().toList(), 110, 100, 1),
            (id, page) ->
                new Pager<>(
                    java.util.stream.IntStream.range(100, 110).boxed().toList(), 110, 100, page),
            Comparator.<Integer>naturalOrder());
    assertEquals(100, result.getList().getFirst());
    assertEquals(10, result.getList().size());
  }

  @Test
  void invalidRangeCannotTriggerUnboundedSourceScan() {
    assertThrows(
        RuntimeException.class,
        () ->
            GlobalAssetCatalogService.merge(
                List.of("a"),
                1001,
                100,
                id -> {
                  throw new AssertionError();
                },
                (id, page) -> {
                  throw new AssertionError();
                },
                Comparator.<Integer>naturalOrder()));
  }
}
