package io.metersphere.app;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.config.PermissionConfig;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Full reactor classpath contract, complementary to the system module's scoped HTTP test. */
class PermissionManifestCoverageTests {
    @Test
    void completeApplicationLoadsAllModuleDefinitionsAndLiteralAdminSeedGrants() throws Exception {
        Set<String> runtime = new HashSet<>();
        collect(new PermissionConfig().permissionCache().getPermissionDefinition(), runtime);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("backend/services"))) root = root.getParent();
        assertNotNull(root, "Repository root required for manifest coverage test");
        Set<String> expected = new HashSet<>();
        Set<String> retired = new HashSet<>();
        try (var modules = Files.list(root.resolve("backend/services"))) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path manifest = module.resolve("src/main/resources/permission.json");
                if (Files.exists(manifest)) collect(JSON.parseArray(Files.readString(manifest), PermissionDefinitionItem.class), expected);
                Path retirement = module.resolve("src/main/resources/retired-permissions.json");
                if (Files.exists(retirement)) JSON.parseObject(Files.readString(retirement), java.util.Map.class)
                        .keySet().forEach(id -> retired.add(String.valueOf(id)));
            }
        }
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(runtime);
        assertTrue(missing.isEmpty(), () -> "Definitions absent from full application classpath: " + missing);
        assertTrue(java.util.Collections.disjoint(runtime, retired), "Retired permissions must not reappear in the active authorization tree");
        for (String id : List.of("CASE_ASSET:READ", "CASE_REVIEW:READ", "FUNCTIONAL_CASE:READ",
                "FUNCTIONAL_CASE_SYSTEM:CLASSIFY", "FUNCTIONAL_CASE_WORKSPACE:ADD", "FUNCTIONAL_CASE_WORKSPACE:READ",
                "FUNCTIONAL_CASE_WORKSPACE:UPDATE", "PROJECT_BASE_INFO:READ", "PROJECT_TEST_PLAN_REPORT:READ",
                "TEST_ASSET_CATEGORY:ASSIGN", "TEST_ASSET_CATEGORY:MANAGE", "TEST_ASSET_SOURCE:GOVERN")) {
            assertTrue(runtime.contains(id) || retired.contains(id), () -> "Unclassified CI grant: " + id);
        }
        Pattern seedGrant = Pattern.compile("['\"]admin['\"]\\s*,\\s*['\"]([^'\"]+:[^'\"]+)['\"]");
        try (var migrations = Files.walk(root.resolve("backend/framework/domain/src/main/resources/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                var matcher = seedGrant.matcher(Files.readString(migration));
                while (matcher.find()) {
                    String id = matcher.group(1);
                    assertTrue(runtime.contains(id), () -> "Admin seed permission missing from full manifest: " + id + " in " + migration);
                }
            }
        }
    }

    private void collect(List<PermissionDefinitionItem> items, Set<String> ids) {
        if (items == null) return;
        for (var item : items) {
            if (item.getPermissions() != null) item.getPermissions().forEach(permission -> ids.add(permission.getId()));
            collect(item.getChildren(), ids);
        }
    }
}
