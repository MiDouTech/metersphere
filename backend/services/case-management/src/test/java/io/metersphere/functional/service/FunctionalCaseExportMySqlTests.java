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
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_104__case_export_recovery.sql"));
        }
    }

    @AfterAll static void cleanupDatabase() {
        if (admin != null && database != null && database.matches("case_export_test_[a-f0-9]{32}")) admin.execute("DROP DATABASE " + database);
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM export_task");
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
        String id = service.start(request("same"), "u1", "xlsx", task -> new FunctionalCaseExportTaskService.Result("xlsx", 2));
        awaitState(id, "SUCCEEDED");
        String replay = service.start(request("same"), "u1", "xlsx", task -> { fail("must not execute replay"); return null; });
        assertEquals(id, replay);
        assertEquals(2, service.get(id, "u1").getCount());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM export_task", Integer.class));
        assertThrows(Exception.class, () -> service.get(id, "another-user"));
        assertThrows(Exception.class, () -> service.stop(id, "another-user"));
        assertThrows(Exception.class, () -> service.start(request("same"), "u1", "xmind", task -> null));
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
}
