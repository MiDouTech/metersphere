package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentAssetExecutionViewTests {
    @Test void assetReadsTheCanonicalSourceReferenceWithoutCreatingAHistoricalHubSubmission() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM case_asset_source_relation"),eq("hub"),eq("asset"))).thenReturn(List.of(Map.of(
                "id","source","project_id","business","last_execute_time",123L,"last_execute_result","ERROR","last_execute_user","tester")));
        when(jdbc.queryForList(startsWith("SELECT submission_id"),eq(String.class),eq("source"))).thenReturn(List.of("submission"));
        when(jdbc.queryForList(contains("JOIN agent_case_submission"),eq("source"),eq("business"),eq(123L),eq("ERROR"),eq("tester")))
                .thenReturn(List.of(Map.of("id","submission","facts","{\"steps\":[]}")));
        var asset=new FunctionalCaseDetailDTO();asset.setId("asset");asset.setProjectId("hub");
        var result=new AgentDirectExecutionViewProvider(jdbc,new AgentFactCipher()).current(asset).orElseThrow();
        assertEquals("submission",result.id());assertTrue(result.facts().containsKey("steps"));
        assertEquals("asset",asset.getId());
    }
}
