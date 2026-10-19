package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentFrozenCaseContentTests {
    private org.mockito.MockedStatic<io.metersphere.system.uid.IDGenerator> ids;
    @org.junit.jupiter.api.BeforeEach void ids(){ids=mockStatic(io.metersphere.system.uid.IDGenerator.class);ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenAnswer(call->java.util.UUID.randomUUID().toString());}
    @org.junit.jupiter.api.AfterEach void closeIds(){ids.close();}

    @Test void creationUsesFrozenPublicationWithoutReadingChangedWorkingCopy(){
        var search=mock(AgentFunctionalCaseSearchService.class);var service=new AgentExecutionSnapshotService();
        ReflectionTestUtils.setField(service,"functionalCaseSearchService",search);ReflectionTestUtils.setField(service,"contentReader",new AgentCaseContentReader());
        var version=new TestAssetVersionDTO();version.setContentSnapshot("{\"editType\":\"STEP\",\"steps\":[{\"desc\":\"published action\",\"result\":\"published expected\"}]}");version.setContentHash(DigestUtils.sha256Hex(version.getContentSnapshot()));
        var c=new AgentExecutionCaseDTO();c.setCaseId("row1");c.setId("ec1");c.setTaskId("t1");
        var steps=service.prepareSnapshot(c,version,1L);
        assertEquals("published action",steps.getFirst().getInstruction());assertEquals(version.getContentSnapshot(),c.getCaseSnapshot());verifyNoInteractions(search);
    }
    @Test void frozenVersionIsNotReplacedByLatestAndOldCheckerIsRejected(){
        var jdbc=mock(JdbcTemplate.class);var versions=mock(TestAssetVersionService.class);var service=new AgentExecutionPreflightService();
        ReflectionTestUtils.setField(service,"jdbcTemplate",jdbc);ReflectionTestUtils.setField(service,"testAssetVersionService",versions);
        var version=new TestAssetVersionDTO();version.setId("old-publication");version.setContentSnapshot("{}");version.setContentHash(DigestUtils.sha256Hex("{}"));
        Map<String,Object> ref=new HashMap<>(Map.of("projectId","p1","caseRowId","row1","assetId","stable1","versionId",version.getId(),"contentHash",version.getContentHash(),"checkerVersion",AgentCaseContentReader.CHECKER_VERSION));
        when(jdbc.queryForMap(anyString(),eq("pf1"))).thenAnswer(call->Map.of("project_id","p1","snapshot_json",JSON.toJSONString(Map.of("caseAssets",List.of(ref)))));
        when(versions.getFrozen("old-publication","p1","CASE","stable1")).thenReturn(version);
        assertEquals(version,service.frozenCaseVersion("pf1","row1","stable1"));verify(versions,never()).latestPublished(anyString(),anyString(),anyString());
        ref.put("checkerVersion","v1");assertEquals("PREFLIGHT_CHECKER_VERSION_EXPIRED",assertThrows(MSException.class,()->service.frozenCaseVersion("pf1","row1","stable1")).getMessage());
    }
}
