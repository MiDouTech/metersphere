package io.metersphere.functional.hub.service;

import io.metersphere.functional.hub.dto.DefaultHubCaseMapRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class CaseAssetSourceBindingGuardTests {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    CaseAssetSourceBindingGuard guard;
    DefaultHubCaseMapRow observed;

    @BeforeEach
    void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        initializeFixture();
    }

    void initializeFixture() {
        jdbc.execute("CREATE TABLE functional_case(id varchar(50) primary key,name varchar(255))");
        jdbc.execute("CREATE TABLE default_hub_case_map(id varchar(50) primary key,biz_case_id varchar(50) unique,hub_case_id varchar(50))");
        jdbc.execute("CREATE TABLE case_asset_source_relation(source_project_id varchar(50),source_case_id varchar(50),asset_case_id varchar(50),UNIQUE(source_project_id,source_case_id))");
        jdbc.update("INSERT INTO functional_case VALUES('asset','source-name'),('replacement','new-mirror')");
        jdbc.update("INSERT INTO default_hub_case_map VALUES('mapping','source','asset')");
        jdbc.update("INSERT INTO case_asset_source_relation VALUES('project','ref','asset')");
        guard = new CaseAssetSourceBindingGuard();
        ReflectionTestUtils.setField(guard, "jdbcTemplate", jdbc);
        observed = new DefaultHubCaseMapRow();
        observed.setId("mapping"); observed.setBizCaseId("source"); observed.setHubCaseId("asset");
    }

    void syncUsingOldBinding() {
        tx.executeWithoutResult(status -> {
            guard.lockAndValidate("project", "source", "ref", observed, List.of("asset"));
            jdbc.update("UPDATE functional_case SET name='late-sync' WHERE id='asset'");
        });
    }

    void detach() {
        jdbc.queryForList("SELECT id FROM functional_case WHERE id='asset' FOR UPDATE");
        jdbc.update("DELETE FROM default_hub_case_map WHERE hub_case_id='asset'");
        jdbc.update("DELETE FROM case_asset_source_relation WHERE asset_case_id='asset'");
        jdbc.update("UPDATE functional_case SET name='independent' WHERE id='asset'");
    }

    @Test
    void lateSyncWaitsForDetachAndCannotOverwriteOrRebindIndependentAsset() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var detached = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var syncStarted = new CountDownLatch(1);
        try {
            var editing = pool.submit(() -> tx.executeWithoutResult(status -> {
                detach(); detached.countDown(); await(commit);
            }));
            assertTrue(detached.await(2, TimeUnit.SECONDS));
            var syncing = pool.submit(() -> { syncStarted.countDown(); syncUsingOldBinding(); });
            assertTrue(syncStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> syncing.get(200, TimeUnit.MILLISECONDS));
            commit.countDown(); editing.get(3, TimeUnit.SECONDS);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> syncing.get(3, TimeUnit.SECONDS));
            assertInstanceOf(CaseAssetBindingChangedException.class, failure.getCause());
            assertIndependent();
        } finally {
            commit.countDown(); pool.shutdownNow();
        }
    }

    @Test
    void syncFirstSerializesDetachAndFinalStateRemainsIndependent() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var editStarted = new CountDownLatch(1);
        try {
            var syncing = pool.submit(() -> tx.executeWithoutResult(status -> {
                guard.lockAndValidate("project", "source", "ref", observed, List.of("asset"));
                jdbc.update("UPDATE functional_case SET name='synced' WHERE id='asset'");
                locked.countDown(); await(commit);
            }));
            assertTrue(locked.await(2, TimeUnit.SECONDS));
            var editing = pool.submit(() -> { editStarted.countDown(); tx.executeWithoutResult(status -> detach()); });
            assertTrue(editStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> editing.get(200, TimeUnit.MILLISECONDS));
            commit.countDown(); syncing.get(3, TimeUnit.SECONDS); editing.get(3, TimeUnit.SECONDS);
            assertIndependent();
        } finally {
            commit.countDown(); pool.shutdownNow();
        }
    }

    @Test
    void normalBindingAndFreshRetryAfterDetachRemainSupported() {
        syncUsingOldBinding();
        assertEquals("late-sync", jdbc.queryForObject("SELECT name FROM functional_case WHERE id='asset'", String.class));
        tx.executeWithoutResult(status -> detach());
        tx.executeWithoutResult(status -> guard.lockAndValidate("project", "source", "ref", null, List.of()));
        assertIndependent();
    }

    @Test
    void legacySingleBindingFormsRemainValid() {
        jdbc.update("DELETE FROM case_asset_source_relation");
        tx.executeWithoutResult(status -> guard.lockAndValidate("project", "source", "ref", observed, List.of()));
        jdbc.update("DELETE FROM default_hub_case_map");
        jdbc.update("INSERT INTO case_asset_source_relation VALUES('project','ref','asset')");
        tx.executeWithoutResult(status -> guard.lockAndValidate("project", "source", "ref", null, List.of("asset")));
    }

    @Test
    void lateDeleteCannotTombstoneDetachedAsset() {
        tx.executeWithoutResult(status -> guard.lockAndValidateMapping(observed));
        tx.executeWithoutResult(status -> detach());
        assertThrows(CaseAssetBindingChangedException.class, () -> tx.executeWithoutResult(status -> {
            guard.lockAndValidateMapping(observed);
            jdbc.update("UPDATE functional_case SET name='deleted-by-late-event' WHERE id='asset'");
        }));
        assertIndependent();
    }

    @Test
    void changedMappingOrHistoricalOnlyBindingIsRejected() {
        jdbc.update("UPDATE default_hub_case_map SET hub_case_id='replacement'");
        assertThrows(CaseAssetBindingChangedException.class, this::syncUsingOldBinding);
        jdbc.update("DELETE FROM default_hub_case_map");
        jdbc.update("DELETE FROM case_asset_source_relation");
        assertThrows(CaseAssetBindingChangedException.class, () -> tx.executeWithoutResult(status ->
                guard.lockAndValidate("project", "source", "ref", null, List.of("asset"))));
        assertEquals("source-name", jdbc.queryForObject("SELECT name FROM functional_case WHERE id='asset'", String.class));
    }

    private void assertIndependent() {
        assertEquals("independent", jdbc.queryForObject("SELECT name FROM functional_case WHERE id='asset'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM default_hub_case_map", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM case_asset_source_relation", Integer.class));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("bounded fixture wait");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(e);
        }
    }
}
