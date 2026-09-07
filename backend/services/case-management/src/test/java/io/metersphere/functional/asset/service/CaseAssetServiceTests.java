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
}
