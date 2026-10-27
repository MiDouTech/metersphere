package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentIdempotencyRecord;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentIdempotencyRecordMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentIdempotencyCanonicalTests {
    @Test void newRecordsReplayNestedEquivalentObjectsAndExpiredIdentityCannotExecuteAgain() {
        var token=new AgentToken();token.setId("t");AgentTokenContext.set(token);
        try (var ids=org.mockito.Mockito.mockStatic(io.metersphere.system.uid.IDGenerator.class)) {
            ids.when(io.metersphere.system.uid.IDGenerator::nextStr).thenReturn("record");
            var mapper=mock(AgentIdempotencyRecordMapper.class);var service=new AgentIdempotencyService();
            ReflectionTestUtils.setField(service,"agentIdempotencyRecordMapper",mapper);
            var first=new LinkedHashMap<String,Object>();first.put("a",1);first.put("b",2);
            var second=new LinkedHashMap<String,Object>();second.put("b",2);second.put("a",1);
            service.save("tool","key",Map.of("nested",first),Map.of("ok",true));
            var capture=org.mockito.ArgumentCaptor.forClass(AgentIdempotencyRecord.class);verify(mapper).insert(capture.capture());
            var record=capture.getValue();assertEquals("generic-canonical-v1",record.getHashVersion());
            when(mapper.selectByTokenToolRequest("t","tool","key")).thenReturn(record);
            assertEquals(Map.of("ok",true),service.findCachedResponse("tool","key",Map.of("nested",second)).orElseThrow());
            record.setResponseJson(null);
            assertEquals("IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED",assertThrows(MSException.class,()->service.findCachedResponse("tool","key",Map.of("nested",first))).getMessage());
        } finally {AgentTokenContext.clear();}
    }
}
