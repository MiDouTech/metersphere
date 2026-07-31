package io.metersphere.agent.constants;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AgentAttachmentPurposeTests {

    @Test
    void shouldParseKnownPurposes() {
        Assertions.assertEquals(AgentAttachmentPurpose.CASE_DETAIL, AgentAttachmentPurpose.from("case_detail"));
        Assertions.assertEquals(AgentAttachmentPurpose.BUG_COMMENT, AgentAttachmentPurpose.from("BUG_COMMENT"));
        Assertions.assertEquals(AgentAttachmentPurpose.EXECUTION, AgentAttachmentPurpose.from("EXECUTION"));
    }

    @Test
    void shouldRejectUnknownPurpose() {
        Assertions.assertNull(AgentAttachmentPurpose.from("UNKNOWN"));
        Assertions.assertNull(AgentAttachmentPurpose.from(null));
    }
}
