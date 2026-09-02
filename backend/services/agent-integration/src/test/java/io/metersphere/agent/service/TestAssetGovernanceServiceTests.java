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
}
