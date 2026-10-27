package io.metersphere.functional.service;

import io.metersphere.functional.request.FunctionalCaseExportRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Uses a uniquely named database on the dedicated local test MySQL, never the application database. */
@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class FunctionalCaseExportMySqlTests {
    static JdbcTemplate admin;
    static JdbcTemplate jdbc;
    static String database;
    FunctionalCaseExportTaskService service;

    @BeforeAll static void database() throws Exception {
        String server = System.getProperty("quality.mysql.server", "jdbc:mysql://127.0.0.1:13326/");
        if (!server.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")) throw new IllegalArgumentException("Only dedicated loopback test MySQL allowed");
        String user = System.getProperty("quality.mysql.user", "root");
        String password = System.getProperty("quality.mysql.password", "quality-test-only");
        admin = new JdbcTemplate(new DriverManagerDataSource(server + "?useSSL=false&allowPublicKeyRetrieval=true", user, password));
        database = "case_export_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database);
        var source = new DriverManagerDataSource(server + database + "?useSSL=false&allowPublicKeyRetrieval=true", user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE export_task(id VARCHAR(50) PRIMARY KEY,name VARCHAR(255),type VARCHAR(50) NOT NULL,
                file_id VARCHAR(255),file_type VARCHAR(64),project_id VARCHAR(255) NOT NULL,state VARCHAR(50) NOT NULL,
                create_user VARCHAR(50) NOT NULL,create_time BIGINT NOT NULL,update_user VARCHAR(50) NOT NULL,update_time BIGINT NOT NULL)
                """);
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY, project_id VARCHAR(50), deleted BOOLEAN, name VARCHAR(255))");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_104__case_export_recovery.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_106__case_export_retry_lineage.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_134__export_request_identity.sql"));
        }
    }

    @AfterAll static void cleanupDatabase() {
        if (admin != null && database != null && database.matches("case_export_test_[a-f0-9]{32}")) admin.execute("DROP DATABASE " + database);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM export_task");
        jdbc.update("DELETE FROM functional_case");
        service = spy(new FunctionalCaseExportTaskService());
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "transactionManager", new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        ReflectionTestUtils.setField(service, "perUserInflight", 18);
        doNothing().when(service).authorize(anyString(), anyString());
    }

    @AfterEach void close() { service.close(); }

    FunctionalCaseExportRequest request(String key) {
        var request = new FunctionalCaseExportRequest();
        request.setProjectId("p1"); request.setRequestId(key); request.setSelectIds(List.of("c1"));
        return request;
    }

    void awaitState(String id, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!expected.equals(service.get(id, "u1").getState()) && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(expected, service.get(id, "u1").getState());
    }

    @Test void successIsDurableAndReplayDoesNotGenerateAgain() throws Exception {
        String id = service.start(request("audit:export."+"x".repeat(115)), "u1", "xlsx", task -> new FunctionalCaseExportTaskService.Result("xlsx", 2));
        awaitState(id, "SUCCEEDED");
        String replay = service.start(request("audit:export."+"x".repeat(115)), "u1", "xlsx", task -> { fail("must not execute replay"); return null; });
        assertEquals(id, replay);
        assertEquals(2, service.get(id, "u1").getCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM export_task", Integer.class));
        assertThrows(Exception.class, () -> service.get(id, "another-user"));
        assertThrows(Exception.class, () -> service.stop(id, "another-user"));
        assertThrows(Exception.class, () -> service.start(request("audit:export."+"x".repeat(115)), "u1", "xmind", task -> null));
        service.stop(id, "u1");
        assertEquals("SUCCEEDED", service.get(id, "u1").getState());
    }

    @Test void cancellationWinsAgainstLateCompletion() throws Exception {
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        String id = service.start(request("cancel"), "u1", "xlsx", task -> {
            entered.countDown();
            try { finish.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { /* simulates non-interruptible writer */ }
            return new FunctionalCaseExportTaskService.Result("xlsx", 1);
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertEquals("CANCELLED", service.stop(id, "u1").getState());
        finish.countDown();
        awaitState(id, "CANCELLED");
        assertEquals("CANCELLED", service.stop(id, "u1").getState());
        assertThrows(Exception.class, () -> service.forDownload("p1", service.get(id, "u1").getFileId(), "u1"));
    }

    @Test void emptyAndThrownGenerationErrorsTerminate() throws Exception {
        String empty = service.start(request("empty"), "u1", "xmind", task -> { throw new FunctionalCaseExportTaskService.EmptyExportException(); });
        String error = service.start(request("error"), "u1", "xlsx", task -> { throw new IllegalStateException("sensitive database detail"); });
        awaitState(empty, "FAILED"); awaitState(error, "FAILED");
        assertEquals("EXPORT_EMPTY", service.get(empty, "u1").getErrorCode());
        assertFalse(service.get(error, "u1").getMessage().contains("sensitive"));
    }

    @Test void concurrentRetriesCreateOnlyOneTask() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) calls.add(() -> service.start(request("concurrent"), "u1", "xlsx",
                    task -> new FunctionalCaseExportTaskService.Result("xlsx", 1)));
            Set<String> ids = new HashSet<>();
            for (Future<String> result : pool.invokeAll(calls)) ids.add(result.get());
            assertEquals(1, ids.size()); awaitState(ids.iterator().next(), "SUCCEEDED");
        } finally { pool.shutdownNow(); }
    }

    @Test void rejectedQueueHasDurableFailureAndNoInfinitePreparedState() throws Exception {
        service.close();
        service = spy(new FunctionalCaseExportTaskService(1, 1));
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "transactionManager", new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        ReflectionTestUtils.setField(service, "perUserInflight", 18);
        doNothing().when(service).authorize(anyString(), anyString());
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        service.start(request("worker"), "u1", "xlsx", task -> {
            entered.countDown();
            try { finish.await(5, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            return new FunctionalCaseExportTaskService.Result("xlsx",1);
        });
        assertTrue(entered.await(3,TimeUnit.SECONDS));
        service.start(request("queued"), "u1", "xlsx", task -> new FunctionalCaseExportTaskService.Result("xlsx",1));
        String rejected = service.start(request("rejected"), "u1", "xlsx", task -> { fail("rejected worker must not execute"); return null; });
        assertEquals("FAILED",service.get(rejected,"u1").getState());
        assertEquals("EXPORT_BUSY",service.get(rejected,"u1").getErrorCode());
        finish.countDown();
    }

    @Test void otherInstanceCanReadAndCancelWithoutOverwritingItsActiveLease() throws Exception {
        var other = spy(new FunctionalCaseExportTaskService());
        ReflectionTestUtils.setField(other, "jdbc", jdbc);
        doNothing().when(other).authorize(anyString(), anyString());
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        try {
            String id = service.start(request("cross-instance"), "u1", "xlsx", task -> {
                entered.countDown();
                try { finish.await(5, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                return new FunctionalCaseExportTaskService.Result("xlsx",1);
            });
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            other.recover();
            assertEquals("RUNNING",other.get(id,"u1").getState());
            assertEquals("CANCELLED",other.stop(id,"u1").getState());
            finish.countDown();
            awaitState(id,"CANCELLED");
        } finally { finish.countDown(); other.close(); }
    }

    @Test void recoveryOnlyExpiresStaleLeaseAndExpiryBlocksDownloads() throws Exception {
        String id = service.start(request("expires"), "u1", "xlsx", task -> new FunctionalCaseExportTaskService.Result("xlsx", 1));
        awaitState(id, "SUCCEEDED");
        jdbc.update("UPDATE export_task SET expires_at=1 WHERE id=?", id);
        assertEquals("EXPIRED", service.get(id, "u1").getState());
        assertThrows(Exception.class, () -> service.forDownload("p1", service.get(id, "u1").getFileId(), "u1"));
        jdbc.update("UPDATE export_task SET state='PREPARED',worker_id='other-instance',lease_until=? WHERE id=?", System.currentTimeMillis()+60000, id);
        service.recover(); assertEquals("RUNNING", service.get(id, "u1").getState());
        jdbc.update("UPDATE export_task SET lease_until=1 WHERE id=?", id);
        service.recover(); assertEquals("FAILED", service.get(id, "u1").getState());
        assertEquals("EXPORT_TIMEOUT", service.get(id, "u1").getErrorCode());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void lateUploadReregistersCleanupAfterEarlierSweep(boolean generationThrows) throws Exception {
        var files = mock(io.metersphere.system.service.FileService.class);
        ReflectionTestUtils.setField(service, "fileService", files);
        var deletes = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            int count = deletes.incrementAndGet();
            if (!generationThrows && count == 2) throw new java.io.IOException("injected late delete outage");
            return null;
        }).when(files).deleteFile(any());
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        String id = service.start(request("late-upload"), "u1", "xlsx", task -> {
            entered.countDown();
            boolean released = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!released && System.nanoTime() < deadline) {
                try { released = finish.await(50, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) { /* Deliberately simulate the external upload ignoring cancellation. */ }
            }
            if (!released) throw new IllegalStateException("test upload was not released");
            if (generationThrows) throw new IllegalStateException("injected failure after upload");
            return new FunctionalCaseExportTaskService.Result("xlsx", 1);
        });
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            service.stop(id, "u1");
            jdbc.update("UPDATE export_task SET lease_until=1 WHERE id=?", id);
            service.cleanupFiles();
            assertTrue(jdbc.queryForObject("SELECT cleanup_done FROM export_task WHERE id=?", Boolean.class, id));
            finish.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (Boolean.TRUE.equals(jdbc.queryForObject("SELECT cleanup_done FROM export_task WHERE id=?", Boolean.class, id))
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(jdbc.queryForObject("SELECT cleanup_done FROM export_task WHERE id=?", Boolean.class, id));
            service.cleanupFiles();
            assertTrue(jdbc.queryForObject("SELECT cleanup_done FROM export_task WHERE id=?", Boolean.class, id));
            assertEquals(generationThrows ? 2 : 3, deletes.get());
            assertEquals("CANCELLED", service.get(id, "u1").getState());
        } finally { finish.countDown(); }
    }

    @Test void pagedHistoryIncludesOlderTasksAndIsolatesOwnerAndProject() {
        for (int i = 0; i < 25; i++) historyRow("history-" + String.format("%02d", i), "p1", "u1", "ERROR", 100 + i);
        historyRow("old-active", "p1", "u1", "PREPARED", 1);
        historyRow("foreign-user", "p1", "u2", "ERROR", 999);
        historyRow("foreign-project", "p2", "u1", "ERROR", 999);
        var first = service.history("p1", "u1", 1, 10);
        var second = service.history("p1", "u1", 2, 10);
        var last = service.history("p1", "u1", 999, 10);
        assertEquals(26, first.getTotal()); assertEquals(10, first.getList().size());
        assertEquals("history-24", first.getList().getFirst().getTaskId());
        assertEquals("history-14", second.getList().getFirst().getTaskId());
        assertEquals(3, last.getCurrent()); assertEquals(6, last.getList().size());
        assertEquals("old-active", last.getList().getLast().getTaskId());
        assertEquals(26, java.util.stream.Stream.of(first, second, last).flatMap(page -> page.getList().stream())
                .map(io.metersphere.functional.dto.ExportTaskDTO::getTaskId).distinct().count());
        assertEquals("old-active", service.recent("p1", "u1").getFirst().getTaskId());
        assertEquals(1, service.history("p1", "u2", 1, 10).getTotal());
        assertEquals(1, service.history("p2", "u1", 1, 10).getTotal());
        assertEquals(0, service.history("p2", "u2", 1, 10).getTotal());
        assertEquals(1, service.history("p2", "u2", 999, 10).getCurrent());
    }

    @Test void historyRejectsInvalidPaginationInsteadOfRunningAnUnboundedQuery() {
        assertThrows(Exception.class, () -> service.history("p1", "u1", 0, 10));
        assertThrows(Exception.class, () -> service.history("p1", "u1", 1, 0));
        assertThrows(Exception.class, () -> service.history("p1", "u1", 1, 101));
    }

    private void historyRow(String id, String project, String user, String state, long time) {
        jdbc.update("INSERT INTO export_task(id,name,type,project_id,state,create_user,create_time,update_user,update_time) VALUES (?,?,'CASE',?,?,?,?,?,?)",
                id, id, project, state, user, time, user, time);
    }

    @Test void explicitRetryPreservesParametersAndLineageAndReplaysOneTask() throws Exception {
        String source = service.start(request("original"), "u1", "xmind", task -> { throw new IllegalStateException(); });
        awaitState(source, "FAILED");
        var parameters = service.prepareRetry(source, "u1", "retry-stable");
        assertEquals("xmind", parameters.format());
        assertEquals(List.of("c1"), parameters.request().getSelectIds());
        assertEquals(source, parameters.request().getSourceTaskId());
        String retry = service.start(parameters.request(), "u1", parameters.format(), task -> new FunctionalCaseExportTaskService.Result("xmind", 1));
        awaitState(retry, "SUCCEEDED");
        assertNotEquals(source, retry);
        assertEquals(source, service.get(retry, "u1").getSourceTaskId());
        var replay = service.prepareRetry(source, "u1", "retry-stable");
        assertEquals(retry, service.start(replay.request(), "u1", replay.format(), task -> { fail("must not regenerate"); return null; }));
        assertEquals("FAILED", service.get(source, "u1").getState());
        assertThrows(Exception.class, () -> service.prepareRetry(source, "u2", "other"));
        assertThrows(Exception.class, () -> service.prepareRetry(retry, "u1", "success-retry"));
        assertThrows(Exception.class, () -> service.prepareRetry(source, "u1", "invalid key"));
        assertThrows(Exception.class, () -> service.existingRetry(source, "u1", "original"));
        assertThrows(Exception.class, () -> service.existingRetry(source, "u2", "retry-stable"));
        jdbc.update("UPDATE export_task SET request_payload=NULL WHERE id=?", source);
        assertEquals(retry, service.existingRetry(source, "u1", "retry-stable").getTaskId(),
                "A confirmed idempotent replay must not depend on revalidating current fields or old source payload");
    }

    @Test void retryRejectsMissingLegacyParametersAndSupportsStoppedAndExpiredTasks() throws Exception {
        historyRow("legacy", "p1", "u1", "ERROR", 1);
        assertThrows(Exception.class, () -> service.prepareRetry("legacy", "u1", "legacy-retry"));
        String success = service.start(request("expiring"), "u1", "xlsx", task -> new FunctionalCaseExportTaskService.Result("zip", 1));
        awaitState(success, "SUCCEEDED");
        jdbc.update("UPDATE export_task SET expires_at=1 WHERE id=?", success);
        assertEquals("xlsx", service.prepareRetry(success, "u1", "expired-retry").format());
        jdbc.update("UPDATE export_task SET state='STOP' WHERE id=?", success);
        assertEquals(success, service.prepareRetry(success, "u1", "stopped-retry").request().getSourceTaskId());
        jdbc.update("UPDATE export_task SET state='PREPARED' WHERE id=?", success);
        assertThrows(Exception.class, () -> service.prepareRetry(success, "u1", "active-retry"));
    }

    @Test void clientsCannotForgeRetryLineageAndNormalPayloadRemainsCompatible() {
        var original = request("normal");
        String payload = io.metersphere.sdk.util.JSON.toJSONString(original);
        assertFalse(payload.contains("sourceTaskId"));
        var forged = io.metersphere.sdk.util.JSON.parseObject("{\"projectId\":\"p1\",\"sourceTaskId\":\"foreign\"}", FunctionalCaseExportRequest.class);
        assertNull(forged.getSourceTaskId());
    }

    @Test void queuedTaskFreezesDatabaseContentBeforeReturningAndNeverRefreezesReplay() throws Exception {
        service.close();
        service = spy(new FunctionalCaseExportTaskService(1, 2));
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "transactionManager", new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        ReflectionTestUtils.setField(service, "perUserInflight", 18);
        doNothing().when(service).authorize(anyString(), anyString());
        jdbc.update("INSERT INTO functional_case(id,project_id,deleted,name) VALUES ('c1','p1',false,'before')");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var generated = new java.util.concurrent.atomic.AtomicReference<String>();
        try {
            service.start(request("occupied"), "u1", "xlsx", id -> {
                entered.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return new FunctionalCaseExportTaskService.Result("xlsx", 1);
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            String id = service.start(request("frozen"), "u1", "xlsx", task -> {
                String content = service.readSnapshot(() -> jdbc.queryForObject("SELECT name FROM functional_case WHERE id='c1'", String.class));
                service.saveSnapshot(task, List.of("c1"), Map.of("name", content));
                return content;
            }, (task, content) -> {
                generated.set(content);
                return new FunctionalCaseExportTaskService.Result("xlsx", 1);
            });
            assertNull(generated.get(), "Generation must still be queued");
            assertTrue(jdbc.queryForObject("SELECT content_snapshot FROM export_task WHERE id=?", String.class, id).contains("before"));
            jdbc.update("UPDATE functional_case SET name='after' WHERE id='c1'");
            release.countDown();
            awaitState(id, "SUCCEEDED");
            assertEquals("before", generated.get());
            assertEquals(id, service.start(request("frozen"), "u1", "xlsx", task -> {
                fail("An idempotent replay must not refreeze current content"); return null;
            }, (task, content) -> { fail("Replay must not regenerate"); return null; }));
        } finally { release.countDown(); }
    }

    @Test void snapshotFailureAndCancellationAreDurableAndNeverStartGeneration() {
        String empty = service.start(request("freeze-empty"), "u1", "xlsx", task -> {
            throw new FunctionalCaseExportTaskService.EmptyExportException();
        }, (task, content) -> { fail("Empty snapshot must not generate"); return null; });
        assertEquals("FAILED", service.get(empty, "u1").getState());
        assertEquals("EXPORT_EMPTY", service.get(empty, "u1").getErrorCode());
        String cancelled = service.start(request("freeze-stop"), "u1", "xlsx", task -> {
            service.stop(task, "u1");
            return "late frozen content";
        }, (task, content) -> { fail("Cancellation must win against late freezing"); return null; });
        assertEquals("CANCELLED", service.get(cancelled, "u1").getState());
    }
}
