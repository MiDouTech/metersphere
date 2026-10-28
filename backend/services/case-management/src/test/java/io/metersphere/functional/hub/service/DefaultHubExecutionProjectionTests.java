package io.metersphere.functional.hub.service;

import io.metersphere.functional.domain.FunctionalCase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DefaultHubExecutionProjectionTests {
    @Test void executionChangesInvalidateTheMirrorHash() {
        var service=new DefaultHubCaseSyncService();
        ReflectionTestUtils.setField(service,"jdbcTemplate",mock(JdbcTemplate.class));
        var source=new FunctionalCase();source.setId("source");source.setLastExecuteResult("PENDING");
        String before=service.computeHash(source,null);
        source.setLastExecuteResult("ERROR");source.setLastExecuteUser("tester");source.setLastExecuteTime(123L);
        assertNotEquals(before,service.computeHash(source,null));
        String first=service.computeHash(source,null);source.setLastExecuteTime(124L);
        assertNotEquals(first,service.computeHash(source,null));
    }
}
