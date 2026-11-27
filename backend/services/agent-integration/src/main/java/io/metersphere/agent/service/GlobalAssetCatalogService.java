package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.security.AgentTokenProjectAccess;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.utils.Pager;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Global index over authorized source projects; source mutations and relations stay scoped. */
@Service
public class GlobalAssetCatalogService {
  private final GlobalAssetAccess access;
  private final AgentBusinessAccess projectAccess;
  private final TestAssetCatalogService catalog;
  private final JdbcTemplate jdbc;

  public GlobalAssetCatalogService(
      GlobalAssetAccess access,
      AgentBusinessAccess projectAccess,
      TestAssetCatalogService catalog,
      JdbcTemplate jdbc) {
    this.access = access;
    this.projectAccess = projectAccess;
    this.catalog = catalog;
    this.jdbc = jdbc;
  }

  private static final Set<String> TYPES =
      Set.of(
          "DOCUMENT",
          "CASE",
          "PLAN",
          "TASK",
          "STEP",
          "DATASET",
          "ENVIRONMENT",
          "COMMON_STEP",
          "API_DEFINITION",
          "EVIDENCE",
          "BUG",
          "BUSINESS_FLOW",
          "PAGE_OBJECT");

  private static void validateType(String type, boolean optional) {
    if (type == null || type.isBlank()) {
      if (optional) return;
      throw new MSException("请选择有效的资产类型");
    }
    if (!TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) throw new MSException("请选择有效的资产类型");
  }

  private static void validateCatalogType(String type) {
    validateType(type, false);
    if (Set.of("TASK", "STEP").contains(type.trim().toUpperCase(Locale.ROOT)))
      throw new MSException("请选择有效的资产类型");
  }

  private List<String> projects(String hint, String type) {
    validateType(type, true);
    type = type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    var permissions = TestAssetCatalogService.sourcePermissions(type);
    GlobalAssetAccess.Scope scope = access.context(hint);
    final var authorizedScope = scope;
    var token = AgentTokenContext.get();
    return jdbc
        .queryForList(
            "SELECT id FROM project WHERE organization_id=? AND deleted=0"
                + (token == null ? "" : " AND enable=1")
                + " ORDER BY id",
            String.class,
            scope.organizationId())
        .stream()
        .filter(id -> token == null || AgentTokenProjectAccess.allows(token, id))
        .filter(
            id ->
                permissions.stream()
                    .anyMatch(
                        p -> projectAccess.userHasPermission(authorizedScope.actorId(), id, p)))
        .toList();
  }

  public Pager<List<TestAssetCatalogItemDTO>> catalog(
      String hint,
      String type,
      String keyword,
      String status,
      Long updatedAfter,
      List<String> sources,
      String category,
      boolean descendants,
      int page,
      int size) {
    validateCatalogType(type);
    return merge(
        projects(hint, type),
        page,
        size,
        id ->
            catalog.sourceCatalog(
                hint,
                id,
                type,
                keyword,
                status,
                updatedAfter,
                sources,
                category,
                descendants,
                1,
                100),
        (id, p) ->
            catalog.sourceCatalog(
                hint,
                id,
                type,
                keyword,
                status,
                updatedAfter,
                sources,
                category,
                descendants,
                p,
                100),
        Comparator.comparing(
                TestAssetCatalogItemDTO::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TestAssetCatalogItemDTO::getProjectId)
            .thenComparing(TestAssetCatalogItemDTO::getId));
  }

  public Pager<List<TestAssetVersionDTO>> versions(
      String hint,
      String type,
      String id,
      String keyword,
      List<String> sources,
      String category,
      boolean descendants,
      int page,
      int size) {
    if (type == null || type.isBlank()) {
      return merge(
          List.of(
              "CASE",
              "DOCUMENT",
              "PLAN",
              "TASK",
              "STEP",
              "DATASET",
              "ENVIRONMENT",
              "COMMON_STEP",
              "API_DEFINITION",
              "EVIDENCE",
              "BUG",
              "BUSINESS_FLOW",
              "PAGE_OBJECT"),
          page,
          size,
          t -> versions(hint, t, id, keyword, sources, category, descendants, 1, 100),
          (t, n) -> versions(hint, t, id, keyword, sources, category, descendants, n, 100),
          Comparator.comparing(
                  TestAssetVersionDTO::getCreatedAt,
                  Comparator.nullsLast(Comparator.reverseOrder()))
              .thenComparing(TestAssetVersionDTO::getId));
    }
    return merge(
        projects(hint, type),
        page,
        size,
        p ->
            catalog.sourceVersions(
                hint, p, type, id, keyword, sources, category, descendants, 1, 100),
        (p, n) ->
            catalog.sourceVersions(
                hint, p, type, id, keyword, sources, category, descendants, n, 100),
        Comparator.comparing(
                TestAssetVersionDTO::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TestAssetVersionDTO::getId));
  }

  interface Next<T> {
    Pager<List<T>> get(String project, int page);
  }

  public Pager<List<TestAssetCatalogItemDTO>> searchByTypes(
      String hint,
      List<String> types,
      String keyword,
      String status,
      Long updatedAfter,
      int page,
      int size) {
    if (types == null || types.isEmpty() || types.size() > 12) throw new MSException("请选择有效的资产类型");
    for (var type : types) validateCatalogType(type);
    return merge(
        types.stream().distinct().toList(),
        page,
        size,
        type -> catalog(hint, type, keyword, status, updatedAfter, null, null, false, 1, 100),
        (type, n) -> catalog(hint, type, keyword, status, updatedAfter, null, null, false, n, 100),
        Comparator.comparing(
                TestAssetCatalogItemDTO::getUpdateTime,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TestAssetCatalogItemDTO::getId));
  }

  public TestAssetCatalogItemDTO detail(String hint, String type, String id) {
    validateCatalogType(type);
    var candidates = projects(hint, type);
    if (hint != null && candidates.contains(hint)) {
      try {
        return catalog.sourceDetail(hint, hint, type, id);
      } catch (MSException ignored) {
        /* Search other authorized sources. */
      }
    }
    TestAssetCatalogItemDTO found = null;
    for (String project : candidates) {
      TestAssetCatalogItemDTO row;
      try {
        row = catalog.sourceDetail(hint, project, type, id);
      } catch (MSException ignored) {
        /* Inaccessible/missing source must disclose no content. */
        continue;
      }
      if (found != null) throw new MSException("资产标识对应多个来源，请明确来源项目后重试");
      found = row;
    }
    if (found == null) throw new MSException("资产不存在或无访问权限");
    return found;
  }

  public TestAssetVersionDTO version(String hint, String id) {
    var rows =
        jdbc.queryForList("SELECT project_id,asset_type FROM test_asset_version WHERE id=?", id);
    if (rows.size() != 1) throw new MSException("资产版本不存在或无访问权限");
    var row = rows.getFirst();
    String project = (String) row.get("project_id");
    if (!projects(hint, (String) row.get("asset_type")).contains(project))
      throw new MSException("资产版本不存在或无访问权限");
    return catalog.sourceVersion(hint, project, id);
  }

  static <T> Pager<List<T>> merge(
      List<String> projects,
      int page,
      int size,
      Function<String, Pager<List<T>>> first,
      Next<T> next,
      Comparator<T> order) {
    if (page < 1 || page > 1000 || size < 1 || size > 100) throw new MSException("分页范围无效，请缩小查询范围");
    long total = 0;
    int needed = page * size;
    var rows = new ArrayList<T>();
    for (String project : projects) {
      var head = first.apply(project);
      total += head.getTotal();
      rows.addAll(head.getList());
      if (rows.size() > 10000) throw new MSException("查询范围过大，请增加筛选条件后重试");
      for (int n = 2; (long) (n - 1) * 100 < Math.min(head.getTotal(), needed); n++) {
        rows.addAll(next.get(project, n).getList());
        if (rows.size() > 10000) throw new MSException("查询范围过大，请增加筛选条件后重试");
      }
    }
    rows.sort(order);
    int start = Math.min((page - 1) * size, rows.size());
    return new Pager<>(
        new ArrayList<>(rows.subList(start, Math.min(start + size, rows.size()))),
        total,
        size,
        page);
  }
}
