package io.metersphere.system.service;

import io.metersphere.sdk.util.Translator;
import io.metersphere.system.config.PermissionCache;
import io.metersphere.system.domain.UserRole;
import io.metersphere.system.dto.permission.PermissionDefinitionItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BaseUserRoleServicePermissionTreeTests {
    @Test
    void absentOptionalRootOrderIsSortedDeterministicallyWithoutFailingAuthorizationTree() {
        var known = root("known", 0);
        var z = root("z", null);
        var a = root("a", null);
        PermissionCache cache = new PermissionCache();
        cache.setPermissionDefinition(List.of(z, known, a));
        BaseUserRolePermissionService grants = mock(BaseUserRolePermissionService.class);
        when(grants.getPermissionIdSetByRoleId("admin")).thenReturn(Set.of());
        BaseUserRoleService service = new BaseUserRoleService();
        ReflectionTestUtils.setField(service, "permissionCache", cache);
        ReflectionTestUtils.setField(service, "baseUserRolePermissionService", grants);
        UserRole role = new UserRole();
        role.setId("admin");
        role.setType("SYSTEM");
        try (var translator = mockStatic(Translator.class)) {
            translator.when(() -> Translator.get(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
            assertEquals(List.of("known", "a", "z"), service.getPermissionSetting(role).stream()
                    .map(PermissionDefinitionItem::getId).toList());
        }
    }

    private PermissionDefinitionItem root(String id, Integer order) {
        PermissionDefinitionItem root = new PermissionDefinitionItem();
        root.setId(id);
        root.setName(id);
        root.setType("SYSTEM");
        root.setOrder(order);
        root.setChildren(List.of());
        return root;
    }
}
