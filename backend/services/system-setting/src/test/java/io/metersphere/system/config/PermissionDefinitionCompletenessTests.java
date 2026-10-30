package io.metersphere.system.config;

import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionDefinitionCompletenessTests {
    @Test
    void activeAgentPackageApiPermissionsAreRepresentedInRuntimeTree() throws Exception {
        Set<String> ids = new HashSet<>();
        collect(new PermissionConfig().permissionCache().getPermissionDefinition(), ids);
        for (String permission : List.of(PermissionConstants.SYSTEM_AGENT_PACKAGE_READ,
                PermissionConstants.SYSTEM_AGENT_PACKAGE_ADD, PermissionConstants.SYSTEM_AGENT_PACKAGE_UPDATE,
                PermissionConstants.SYSTEM_AGENT_PACKAGE_DELETE)) {
            assertTrue(ids.contains(permission), () -> "Active API permission absent from runtime definitions: " + permission);
        }
    }

    private void collect(List<PermissionDefinitionItem> definitions, Set<String> ids) {
        if (definitions == null) return;
        for (PermissionDefinitionItem item : definitions) {
            if (item.getPermissions() != null) item.getPermissions().forEach(permission -> ids.add(permission.getId()));
            collect(item.getChildren(), ids);
        }
    }
}
