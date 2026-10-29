package io.metersphere.system.config;

import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test-only ownership index; never adds unloaded module permissions to a production role tree. */
public final class RepositoryPermissionDefinitions {
    private RepositoryPermissionDefinitions() { }

    public static Map<String, String> owners() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("backend/services"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Repository root required for cross-module permission contract test");
        Path services = root.resolve("backend/services");
        Map<String, String> result = new HashMap<>();
        try (var modules = Files.list(services)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path manifest = module.resolve("src/main/resources/permission.json");
                if (Files.exists(manifest)) collect(JSON.parseArray(Files.readString(manifest), PermissionDefinitionItem.class),
                        module.getFileName().toString(), result);
                Path retired = module.resolve("src/main/resources/retired-permissions.json");
                if (Files.exists(retired)) {
                    Map<?, ?> entries = JSON.parseObject(Files.readString(retired), Map.class);
                    for (var entry : entries.entrySet()) {
                        String id = String.valueOf(entry.getKey());
                        if (result.putIfAbsent(id, "retired:" + module.getFileName()) != null) {
                            throw new IllegalStateException("Permission cannot be active and retired: " + id);
                        }
                        Path migration = root.resolve("backend/framework/domain/src/main/resources/migration/3.7.2/dml/")
                                .resolve(String.valueOf(entry.getValue()));
                        if (!Files.exists(migration) || !Files.readString(migration).contains("'" + id.replace(':', '_') + "'")
                                || !Files.readString(migration).contains("enabled = b'0'")) {
                            throw new IllegalStateException("Retirement requires a real migration: " + id);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void collect(List<PermissionDefinitionItem> items, String owner, Map<String, String> result) {
        if (items == null) return;
        for (var item : items) {
            if (item.getPermissions() != null) item.getPermissions().forEach(permission -> result.put(permission.getId(), owner));
            collect(item.getChildren(), owner, result);
        }
    }
}
