package io.metersphere.functional.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FunctionalCaseExportTempFilesTests {
    @TempDir Path temp;
    private JdbcTemplate jdbc;
    private FunctionalCaseExportTempFiles service;

    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class);
        service = configure(new FunctionalCaseExportTempFiles());
    }

    private <T extends FunctionalCaseExportTempFiles> T configure(T instance) {
        ReflectionTestUtils.setField(instance, "directory", temp);
        ReflectionTestUtils.setField(instance, "jdbc", jdbc);
        return instance;
    }

    private Path fixture(String task) throws IOException {
        var dir = service.create(task).toPath();
        Files.createDirectory(dir.resolve("nested"));
        Files.writeString(dir.resolve("nested/export.xlsx"), "fixture bytes");
        return dir;
    }

    @Test void immediateCleanupDeletesOnlyOwnedDirectoryAndItsBytes() throws Exception {
        Path owned = fixture("task-1");
        Path unrelated = Files.createDirectory(temp.resolve("tmp-user-file"));
        Files.writeString(unrelated.resolve("keep"), "keep");
        service.cleanup(owned.toFile());
        service.cleanup(unrelated.toFile());
        service.cleanup(temp.toFile());
        assertFalse(Files.exists(owned));
        assertEquals("keep", Files.readString(unrelated.resolve("keep")));
    }

    @Test void recoveryProtectsRunningExportsAndRemovesTerminalExportsAfterRestart() throws Exception {
        Path active = fixture("active"), success = fixture("success"), failed = fixture("failed"), stopped = fixture("stopped");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("active"))).thenReturn(List.of("PREPARED"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("success"))).thenReturn(List.of("SUCCESS"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("failed"))).thenReturn(List.of("ERROR"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("stopped"))).thenReturn(List.of("STOP"));
        configure(new FunctionalCaseExportTempFiles()).recover();
        assertTrue(Files.exists(active.resolve("nested/export.xlsx")));
        assertFalse(Files.exists(success)); assertFalse(Files.exists(failed)); assertFalse(Files.exists(stopped));
    }

    @Test void orphanDirectoriesHaveGracePeriodAndUnknownTaskStatesAreProtected() throws Exception {
        Path fresh = fixture("fresh"), old = fixture("old"), unknown = fixture("unknown");
        Files.setLastModifiedTime(old, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(25).toMillis()));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("unknown"))).thenReturn(List.of("UNRECOGNIZED"));
        service.recover();
        assertTrue(Files.exists(fresh)); assertTrue(Files.exists(unknown)); assertFalse(Files.exists(old));
    }

    @Test void deletionFailureLeavesDurableRetryAndDoesNotFailTheExport() throws Exception {
        Path folder = fixture("retry");
        FunctionalCaseExportTempFiles failing = configure(new FunctionalCaseExportTempFiles() {
            @Override void deleteDirectory(Path path) throws IOException { throw new IOException("temporarily locked"); }
        });
        assertDoesNotThrow(() -> failing.cleanup(folder.toFile()));
        assertTrue(Files.exists(folder.resolve("nested/export.xlsx")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("retry"))).thenReturn(List.of("SUCCESS"));
        configure(new FunctionalCaseExportTempFiles()).recover();
        assertFalse(Files.exists(folder));
    }

    @Test void databaseFailureCannotDeleteFilesAndBadIdentityCannotEscapeRoot() throws Exception {
        Path folder = fixture("db-failure");
        when(jdbc.queryForList(anyString(), eq(String.class), eq("db-failure"))).thenThrow(new IllegalStateException("unavailable"));
        assertDoesNotThrow(service::recover);
        assertTrue(Files.exists(folder));
        assertThrows(IllegalArgumentException.class, () -> service.create("../outside"));
    }
}
