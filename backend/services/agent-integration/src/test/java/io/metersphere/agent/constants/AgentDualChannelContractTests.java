package io.metersphere.agent.constants;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDualChannelContractTests {

    @Test
    void acceptsOnlyTheThreeDefinedOriginChannelPairs() {
        assertDoesNotThrow(() -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_SCHEDULED, AgentExecutorChannel.MODEL_API_RUNNER));
        assertDoesNotThrow(() -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_MANUAL, AgentExecutorChannel.MODEL_API_RUNNER));
        assertDoesNotThrow(() -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PERSONAL_MCP, AgentExecutorChannel.EXTERNAL_MCP_AGENT));

        assertThrows(MSException.class, () -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_SCHEDULED, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
        assertThrows(MSException.class, () -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PLATFORM_MANUAL, AgentExecutorChannel.EXTERNAL_MCP_AGENT));
        assertThrows(MSException.class, () -> AgentExecutorChannel.requireLegal(
                AgentTaskOrigin.PERSONAL_MCP, AgentExecutorChannel.MODEL_API_RUNNER));
    }

    @Test
    void governanceMigrationExtendsExistingTablesWithoutRedefiningLegacySchema() throws IOException {
        String resource = "/migration/3.7.2/ddl/V3.7.2_80__ai_execution_governance_foundation.sql";
        try (InputStream stream = AgentDualChannelContractTests.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "governance migration must be packaged in the domain dependency");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(sql.contains("ADD COLUMN retention_until"));
            assertFalse(sql.contains("ADD INDEX idx_ai_human_request_task_status"));
            assertFalse(sql.contains("CREATE TABLE ai_credential_reference"));
            assertTrue(sql.contains("ALTER TABLE ai_credential_reference"));
            assertTrue(sql.contains("CHANGE COLUMN enable enabled"));
            assertTrue(sql.contains("`sensitive`       BIT(1)"));
        }
    }

    @Test
    void integrityMigrationReplacesLegacyScheduleKeyAndAddsEphemeralDatasetStorage() throws IOException {
        String resource="/migration/3.7.2/ddl/V3.7.2_88__ai_execution_integrity_completion.sql";
        try(InputStream stream=AgentDualChannelContractTests.class.getResourceAsStream(resource)){
            assertNotNull(stream,"integrity migration must be packaged in the domain dependency");
            String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(sql.contains("DROP INDEX uk_ai_trigger_schedule"));
            assertTrue(sql.contains("ADD COLUMN content_snapshot LONGBLOB"));
            assertTrue(sql.contains("ADD COLUMN cleaned_at BIGINT"));
        }
    }
}
