package io.metersphere.agent.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GlobalAssetPermissionRegistryTests {
  @Test
  void organizationRolesCanGrantGlobalAssetsAndExecutionGroupsRemainProjectScoped()
      throws Exception {
    Path root = Path.of("").toAbsolutePath();
    while (!Files.exists(root.resolve("backend/services/agent-integration")))
      root = root.getParent();
    var registry =
        new ObjectMapper()
            .readTree(
                Files.readString(
                    root.resolve(
                        "backend/services/agent-integration/src/main/resources/permission.json")));
    Set<String> global = new HashSet<>();
    for (var group : registry) {
      if (group.path("id").asText().equals("BUSINESS_ASSET_MANAGEMENT")) {
        assertEquals("ORGANIZATION", group.path("type").asText());
        for (var child : group.path("children"))
          for (var permission : child.path("permissions"))
            global.add(permission.path("id").asText());
      }
      if (group.path("id").asText().equals("AGENT_PROJECT_CONFIGURATION"))
        assertEquals("PROJECT", group.path("type").asText());
    }
    assertTrue(
        global.containsAll(
            Set.of(
                "BUSINESS_ASSET:READ",
                "BUSINESS_ASSET:WRITE",
                "SHARED_CONFIGURATION:READ",
                "SHARED_CONFIGURATION:WRITE",
                "SHARED_CONFIGURATION:RESOLVE",
                "TEST_DATA_ASSET:READ",
                "TEST_DATA_ASSET:READ+UPDATE",
                "TEST_ASSET_VERSION:MANAGE")));
    assertFalse(global.contains("AI_EXECUTION:RUN"));
  }

  @Test
  void actualPermissionTreeExposesGlobalAssetGrantsToNewOrganizationRolesAndSystemAdmin()
      throws Exception {
    Path root = Path.of("").toAbsolutePath();
    while (!Files.exists(root.resolve("backend/services/agent-integration")))
      root = root.getParent();
    String json =
        Files.readString(
            root.resolve("backend/services/agent-integration/src/main/resources/permission.json"));
    var cache = new io.metersphere.system.config.PermissionCache();
    cache.setPermissionDefinition(
        io.metersphere.sdk.util.JSON.parseArray(
            json, io.metersphere.system.dto.permission.PermissionDefinitionItem.class));
    var grants =
        org.mockito.Mockito.mock(io.metersphere.system.service.BaseUserRolePermissionService.class);
    org.mockito.Mockito.when(
            grants.getPermissionIdSetByRoleId(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(Set.of());
    var service = new io.metersphere.system.service.BaseUserRoleService();
    org.springframework.test.util.ReflectionTestUtils.setField(service, "permissionCache", cache);
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "baseUserRolePermissionService", grants);
    try (var translator =
        org.mockito.Mockito.mockStatic(io.metersphere.sdk.util.Translator.class)) {
      translator
          .when(
              () ->
                  io.metersphere.sdk.util.Translator.get(org.mockito.ArgumentMatchers.anyString()))
          .thenAnswer(call -> call.getArgument(0));
      var role = new io.metersphere.system.domain.UserRole();
      role.setId("new-custom-org");
      role.setType("ORGANIZATION");
      assertTrue(
          service.getPermissionSetting(role).stream()
              .anyMatch(item -> "BUSINESS_ASSET_MANAGEMENT".equals(item.getId())));
      role.setId("new-project-role");
      role.setType("PROJECT");
      assertFalse(
          service.getPermissionSetting(role).stream()
              .anyMatch(item -> "BUSINESS_ASSET_MANAGEMENT".equals(item.getId())));
      role.setId("admin");
      role.setType("SYSTEM");
      assertTrue(
          service.getPermissionSetting(role).stream()
              .anyMatch(item -> "BUSINESS_ASSET_MANAGEMENT".equals(item.getId())));
    }
  }
}
