package io.metersphere.functional.asset.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CaseAssetServiceTests {

    @Test
    void referencedProjectQueryUsesPhysicalTestPlanSchema() {
        Assertions.assertTrue(CaseAssetService.REFERENCED_PROJECTS_FROM_SQL.contains("JOIN test_plan tp"));
        Assertions.assertFalse(CaseAssetService.REFERENCED_PROJECTS_FROM_SQL.contains("tp.deleted"));
    }

    @Test
    void historicalSyncNormalizesLegacyCaseEditTypes() {
        Assertions.assertEquals("TEXT", CaseAssetService.normalizeCaseEditType(" text "));
        Assertions.assertEquals("STEP", CaseAssetService.normalizeCaseEditType("STEP"));
        Assertions.assertEquals("STEP", CaseAssetService.normalizeCaseEditType("legacy"));
        Assertions.assertEquals("STEP", CaseAssetService.normalizeCaseEditType(null));
    }
    @Test
    void assetImportCannotCarryAProjectVersionFromTheCaller() {
        var service = new CaseAssetService();
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var hub = org.mockito.Mockito.mock(io.metersphere.system.service.DefaultHubProjectService.class);
        org.mockito.Mockito.when(hub.getDefaultProjectId()).thenReturn("hub");
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("catalog"), org.mockito.ArgumentMatchers.eq("org")))
                .thenReturn(java.util.List.of(java.util.Map.of("id", "catalog", "hubModuleId", "catalog-root")));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "defaultHubProjectService", hub);
        var request = new io.metersphere.functional.request.FunctionalCaseImportRequest();
        request.setProjectId("foreign-project"); request.setWorkspaceId("foreign-org");request.setModuleId("foreign-module");request.setVersionId("foreign-version");
        try (var sessions = org.mockito.Mockito.mockStatic(io.metersphere.system.utils.SessionUtils.class)) {
            sessions.when(io.metersphere.system.utils.SessionUtils::getCurrentOrganizationId).thenReturn("org");
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "prepareImport", "catalog", request);
        }
        Assertions.assertEquals("hub", request.getProjectId());Assertions.assertEquals("org", request.getWorkspaceId());Assertions.assertEquals("catalog-root", request.getModuleId());
        Assertions.assertNull(request.getVersionId(), "The file service must resolve the hub default, not a foreign supplied version");
    }
}
