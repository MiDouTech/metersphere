package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRecoveryScanHealthTests {
    @Test void classifiesSchemaAndConnectionFailuresAndCapsBackoff() {
        assertEquals("STRUCTURE",AgentRecoveryScanHealth.category(new SQLException("safe","HY000",1267)));
        assertEquals("STRUCTURE",AgentRecoveryScanHealth.category(new SQLException("safe","42S02",1146)));
        assertEquals("CONNECTION",AgentRecoveryScanHealth.category(new SQLException("safe","08001")));
        assertEquals(300000,AgentRecoveryScanHealth.retryDelay("STRUCTURE",1));
        assertEquals(30000,AgentRecoveryScanHealth.retryDelay("CONNECTION",1));
        assertEquals(900000,AgentRecoveryScanHealth.retryDelay("CONNECTION",100));
    }
    @Test void failedPersistenceStillPreventsHotRetryLoop() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new DataAccessResourceFailureException("offline"));
        var health=new AgentRecoveryScanHealth(jdbc);
        assertTrue(health.failure(new SQLException("offline","08001")));
        assertFalse(health.begin());
        verify(jdbc,times(1)).update(anyString(),any(Object[].class));
    }
    @Test void scannerCatchesCandidateQueryFailureBeforeLoop() {
        var scanner=new AgentExecutionWritebackService();
        var health=mock(AgentRecoveryScanHealth.class);
        var finalization=mock(AgentExecutionFinalizationService.class);
        ReflectionTestUtils.setField(scanner,"recoveryHealth",health);
        ReflectionTestUtils.setField(scanner,"finalization",finalization);
        ReflectionTestUtils.setField(scanner,"recoveryAlerts",mock(AgentExecutionAlertService.class));
        when(health.begin()).thenReturn(true);
        var error=new DataAccessResourceFailureException("offline");
        when(finalization.recoverableTasks()).thenThrow(error);
        assertDoesNotThrow(scanner::recoverPendingSync);
        verify(health).failure(error);
        verify(health,never()).success();
    }
}
