package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetSourceGovernanceRequest;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAssetGovernanceServiceTests {

    private final TestAssetGovernanceService service = new TestAssetGovernanceService();

    @Test
    void trustedSourceMustBeConfirmedAndAutomationIsSupported() {
        Assertions.assertTrue(TestAssetGovernanceService.SOURCES.contains("AUTOMATION"));
        Assertions.assertThrows(MSException.class, () -> service.recordTrustedSource(
                "project-1", "CASE", "case-1", "UNKNOWN", null, null, "SYSTEM", null));
    }

    @Test
    void sourceGovernanceCannotConfirmUnknownAsUnknown() {
        TestAssetSourceGovernanceRequest request = new TestAssetSourceGovernanceRequest();
        request.setProjectId("project-1");
        request.setAssetType("CASE");
        request.setAssetId("case-1");
        request.setCreationSource("UNKNOWN");
        request.setEvidence("legacy record");

        Assertions.assertThrows(MSException.class, () -> service.governSource(request));
    }

    @Test
    void defaultCategoryCannotBeChangedDeletedReorderedOrImitated() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "jdbc", jdbc);
        try (var session = org.mockito.Mockito.mockStatic(io.metersphere.system.utils.SessionUtils.class)) {
            session.when(io.metersphere.system.utils.SessionUtils::getCurrentOrganizationId).thenReturn("org");
            session.when(io.metersphere.system.utils.SessionUtils::getUserId).thenReturn("actor");
            var request = new io.metersphere.agent.dto.TestAssetCategorySaveRequest();
            request.setName("changed");
            Assertions.assertThrows(MSException.class, () -> service.update("__uncategorized__", request));
            Assertions.assertThrows(MSException.class, () -> service.delete("__uncategorized__", new io.metersphere.agent.dto.TestAssetCategoryDeleteRequest()));
            var reorder = new io.metersphere.agent.dto.TestAssetCategoryReorderRequest();
            reorder.setIds(java.util.List.of("__uncategorized__"));
            Assertions.assertThrows(MSException.class, () -> service.reorder(reorder));
            request.setParentId("__uncategorized__");
            Assertions.assertThrows(MSException.class, () -> service.create(request));
            request.setParentId(null); request.setName("默认分类");
            Assertions.assertThrows(MSException.class, () -> service.create(request));
            org.mockito.Mockito.verifyNoInteractions(jdbc);
        }
    }
}
