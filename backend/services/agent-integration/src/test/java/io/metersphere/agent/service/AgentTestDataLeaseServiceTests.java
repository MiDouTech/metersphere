package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AgentTestDataLeaseServiceTests {
    @Test
    void acquireRejectsDatasetOutsideFrozenPreflightScope(){
        AgentTestDataLeaseService service=new AgentTestDataLeaseService();JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);
        AgentExecutionPreflightService preflight=Mockito.mock(AgentExecutionPreflightService.class);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"preflightService",preflight);
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("project_id","p1","current_execution_id","e1","status","RUNNING","preflight_id","pf1"));
        when(preflight.assertFrozenExecutableAsset("pf1","DATASET","d2")).thenThrow(new MSException("TEST_DATASET_NOT_IN_FROZEN_SCOPE"));
        MSException error=Assertions.assertThrows(MSException.class,()->service.acquire("t1","d2","row-1",60000));
        Assertions.assertEquals("TEST_DATASET_NOT_IN_FROZEN_SCOPE",error.getMessage());
    }

    @Test
    void contentReturnsImmutableSnapshotWithNamespaceAfterIntegrityCheck(){
        AgentTestDataLeaseService service=new AgentTestDataLeaseService();JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);ReflectionTestUtils.setField(service,"jdbc",jdbc);
        byte[] bytes="id,name\n1,alice".getBytes(java.nio.charset.StandardCharsets.UTF_8);String token="lease-token";
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(Map.of("execution_id","e1","status","ACTIVE","expires_at",System.currentTimeMillis()+60000,
                "lease_token_hash",DigestUtils.sha256Hex(token),"content_snapshot",bytes,"content_sha256",DigestUtils.sha256Hex(bytes),"content_type","text/csv","namespace","ai/t1/e1/key","data_key","row-1"));
        var response=service.content("l1",token,"e1");
        Assertions.assertArrayEquals(bytes,response.getBody());
        Assertions.assertEquals("ai/t1/e1/key",response.getHeaders().getFirst("X-Test-Data-Namespace"));
    }
}
