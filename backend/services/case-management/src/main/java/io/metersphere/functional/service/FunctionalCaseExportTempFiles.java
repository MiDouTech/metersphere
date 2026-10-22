package io.metersphere.functional.service;

import io.metersphere.sdk.constants.LocalRepositoryDir;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Durable ownership is encoded in the directory name, so retries survive process restarts. */
@Service
public class FunctionalCaseExportTempFiles {
    private static final Logger LOG = LoggerFactory.getLogger(FunctionalCaseExportTempFiles.class);
    private static final String PREFIX = "ms-case-export.";
    private static final Pattern OWNED = Pattern.compile("^ms-case-export\\.([A-Za-z0-9_-]{1,64})\\.[a-f0-9-]{36}$");
    private static final long ORPHAN_GRACE_MS = Duration.ofHours(24).toMillis();
    @Resource private JdbcTemplate jdbc;
    private Path directory;

    private Path root() {
        return (directory == null ? Path.of(LocalRepositoryDir.getSystemTempDir()) : directory).toAbsolutePath().normalize();
    }

    public File create(String taskId) throws IOException {
        if (taskId == null || !taskId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid export task identity");
        Path root = root();
        Files.createDirectories(root);
        return Files.createDirectory(root.resolve(PREFIX + taskId + "." + UUID.randomUUID())).toFile();
    }

    public void cleanup(File folder) {
        if (folder == null) return;
        Path path = folder.toPath().toAbsolutePath().normalize();
        if (!root().equals(path.getParent()) || !OWNED.matcher(path.getFileName().toString()).matches()
                || Files.isSymbolicLink(path)) {
            LOG.warn("Rejected cleanup of a directory not owned by case export");
            return;
        }
        try {
            deleteDirectory(path);
        } catch (IOException failure) {
            // The directory itself remains the durable retry record. Export status is unaffected.
            LOG.warn("Case export temporary cleanup will retry, directory={}", path.getFileName(), failure);
        }
    }

    void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file); // walkFileTree does not follow symbolic links.
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recover() {
        if (!Files.isDirectory(root())) return;
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root(), PREFIX + "*")) {
            for (Path path : directories) {
                var matcher = OWNED.matcher(path.getFileName().toString());
                if (!matcher.matches() || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    var states = jdbc.queryForList("SELECT state FROM export_task WHERE id=? AND type='CASE'", String.class, matcher.group(1));
                    boolean terminal = !states.isEmpty() && Set.of("SUCCESS", "ERROR", "STOP").contains(states.getFirst());
                    boolean orphan = states.isEmpty() && Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()
                            < System.currentTimeMillis() - ORPHAN_GRACE_MS;
                    if (terminal || orphan) cleanup(path.toFile());
                } catch (Exception failure) {
                    LOG.warn("Case export temporary recovery deferred, directory={}", path.getFileName(), failure);
                }
            }
        } catch (IOException failure) {
            LOG.warn("Case export temporary directory scan deferred", failure);
        }
    }
}
