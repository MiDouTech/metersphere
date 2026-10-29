package io.metersphere.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentCaseImportStageTests {
    @Test void workerDoesNotTrustQueuedStateWithoutACommitRecord() {
        var jdbc=mock(JdbcTemplate.class);var service=new AgentCaseImportService();
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        when(jdbc.queryForList(contains("FOR UPDATE SKIP LOCKED"),eq("job")))
                .thenReturn(List.of(Map.of("id","job","state","QUEUED")));
        org.junit.jupiter.api.Assertions.assertNull(service.processOne("job"));
        verify(jdbc).update(contains("state='STOPPED'"),anyLong(),eq("job"));
        verify(jdbc,never()).queryForList(startsWith("SELECT * FROM functional_import_item"),eq("job"));
    }
    private JdbcTemplate exercise(Long committedAt) {
        var jdbc=mock(JdbcTemplate.class);var access=mock(AgentBusinessAccess.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var row=new HashMap<String,Object>();row.put("state","STOPPED");row.put("project_id","project");
        row.put("source_name","fixture.xlsx");row.put("expires_at",System.currentTimeMillis()+60000);
        row.put("committed_at",committedAt);
        when(jdbc.queryForList(startsWith("SELECT * FROM functional_import_job"),eq("job"),eq("user"),eq("WEB"))).thenReturn(List.of(row));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM project"),eq(Long.class),eq("project"))).thenReturn(1L);
        when(access.userHasPermission(eq("user"),eq("project"),anyString())).thenReturn(true);
        var service=new AgentCaseImportService();
        ReflectionTestUtils.setField(service,"jdbc",jdbc);ReflectionTestUtils.setField(service,"access",access);
        ReflectionTestUtils.setField(service,"transactionManager",manager);
        service.action("job","retry","user","WEB");
        return jdbc;
    }

    @Test void stoppedPrecheckReturnsToConfirmationWithoutQueueingOrResettingItems() {
        var jdbc=exercise(null);
        verify(jdbc).update(contains("state='PRECHECKED'"),anyLong(),eq("job"));
        verify(jdbc,never()).update(contains("state='QUEUED'"),anyLong(),eq("job"));
        verify(jdbc,never()).update(startsWith("UPDATE functional_import_item"),eq("job"));
    }

    @Test void committedStoppedImportRetriesFailedItemsAndKeepsSuccessfulWrites() {
        var jdbc=exercise(123L);
        verify(jdbc).update(contains("state='QUEUED'"),anyLong(),eq("job"));
        verify(jdbc).update(contains("WHERE job_id=? AND state='FAILED'"),eq("job"));
        verify(jdbc,never()).update(contains("state='PRECHECKED'"),anyLong(),eq("job"));
    }
}
