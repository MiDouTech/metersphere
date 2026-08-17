package io.metersphere.system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowMigrationServiceTests {
    @Mock
    private JdbcTemplate jdbcTemplate;

    private WorkflowMigrationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowMigrationService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void rollbackRejectsRunningBatchBeforeChangingAnyData() {
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("id", "batch-running");
        batch.put("dryRun", false);
        batch.put("status", "RUNNING");
        when(jdbcTemplate.queryForList(contains("FROM workflow_migration_batch WHERE id=?"), eq("batch-running")))
                .thenReturn(List.of(batch));
        when(jdbcTemplate.queryForList(contains("FROM workflow_migration_exception"), eq("batch-running")))
                .thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.rollback("batch-running"));

        verify(jdbcTemplate, never()).update(contains("ROLLING_BACK"),
                org.mockito.ArgumentMatchers.<Object[]>any());
    }
}
