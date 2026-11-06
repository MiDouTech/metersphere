package io.metersphere.agent.service;

import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.project.domain.FileMetadata;
import io.metersphere.project.dto.filemanagement.request.FileReUploadRequest;
import io.metersphere.project.dto.filemanagement.request.FileUploadRequest;
import io.metersphere.project.service.FileManagementService;
import io.metersphere.project.service.FileMetadataService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Transaction and adapter tests on H2 MySQL mode; not a substitute for real MySQL/storage integration. */
class AgentProjectFileServiceTests {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AgentProjectFileService service;
    private AgentBusinessAccess access;
    private FileMetadataService files;
    private AgentExecLogService audit;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private byte[] bytes;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        String ddl = new ClassPathResource("migration/3.7.2/ddl/V3.7.2_144__project_original_upload.sql").getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("COLLATE utf8mb4_bin", "").replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String statement : ddl.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        jdbc.execute("CREATE TABLE project(id VARCHAR(50) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE agent_token(id VARCHAR(50) PRIMARY KEY)");
        jdbc.update("INSERT INTO project VALUES ('p')");
        jdbc.update("INSERT INTO agent_token VALUES ('token')");
        jdbc.execute("CREATE TABLE system_parameter(param_key VARCHAR(100),param_value VARCHAR(100))");
        jdbc.update("INSERT INTO system_parameter VALUES ('upload.file.size','50')");
        jdbc.execute("CREATE TABLE file_metadata(id VARCHAR(50) PRIMARY KEY,ref_id VARCHAR(50),project_id VARCHAR(50),name VARCHAR(255),type VARCHAR(20),storage VARCHAR(20),original_name VARCHAR(255),latest BOOLEAN,module_id VARCHAR(50),size BIGINT,file_version VARCHAR(50),update_time BIGINT,update_user VARCHAR(50))");
        service = new AgentProjectFileService();
        access = mock(AgentBusinessAccess.class);
        files = mock(FileMetadataService.class);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "files", files);
        ReflectionTestUtils.setField(service, "management", mock(FileManagementService.class));
        audit = mock(AgentExecLogService.class);
        ReflectionTestUtils.setField(service, "audit", audit);
        ReflectionTestUtils.setField(service, "environment", "ISOLATED_TEST");
        when(files.selectById(anyString())).thenAnswer(call -> {
            var rows = jdbc.query("SELECT * FROM file_metadata WHERE id=?", BeanPropertyRowMapper.newInstance(FileMetadata.class), (String) call.getArgument(0));
            return rows.isEmpty() ? null : rows.getFirst();
        });
        when(files.upload(any(), anyString(), any())).thenAnswer(call -> store(null, call.getArgument(0), call.getArgument(2)));
        when(files.reUpload(any(), anyString(), any())).thenAnswer(call -> {
            FileReUploadRequest request = call.getArgument(0);
            var old = files.selectById(request.getFileId());
            jdbc.update("UPDATE file_metadata SET latest=0 WHERE ref_id=?", old.getRefId());
            var upload = new FileUploadRequest();
            upload.setProjectId(old.getProjectId());
            upload.setModuleId(old.getModuleId());
            return store(old.getRefId(), upload, call.getArgument(2));
        });
        when(files.getFileByte(any())).thenAnswer(call -> objects.get(((FileMetadata) call.getArgument(0)).getId()));
        bytes = ProjectOriginalFileValidatorTests.zip(Map.of("content.json", ProjectOriginalFileValidatorTests.XMIND_JSON));
        token("token");
        var proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                new DataSourceTransactionManager(source), new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        service = (AgentProjectFileService) proxy.getProxy();
    }

    @AfterEach void cleanup() { AgentTokenContext.clear(); }

    private String store(String ref, FileUploadRequest request, MultipartFile file) throws Exception {
        String id = UUID.randomUUID().toString();
        String name = file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        jdbc.update("INSERT INTO file_metadata VALUES (?,?,?,?,?,'MINIO',?,1,?,?,?,?,?)", id, ref == null ? id : ref, request.getProjectId(), name.substring(0, dot), name.substring(dot + 1), name, request.getModuleId(), file.getSize(), id, System.currentTimeMillis(), "user");
        objects.put(id, file.getBytes());
        return id;
    }

    private void token(String id) {
        var token = new AgentToken(); token.setId(id); token.setUserId("user"); AgentTokenContext.set(token);
    }
    private <T> T transaction(Supplier<T> action) { return tx.execute(status -> action.get()); }
    private Map<String, Object> request(String key) {
        return new HashMap<>(Map.of("projectId", "p", "requestId", key, "fileName", "原件.xmind", "size", bytes.length, "sha256", DigestUtils.sha256Hex(bytes)));
    }
    private String prepare(String key) { return (String) transaction(() -> service.prepare(request(key))).get("uploadId"); }
    private Map<String, Object> act(String action, String upload) {
        return transaction(() -> service.upload(action, Map.of("projectId", "p", "uploadId", upload)));
    }
    private Map<String, Object> chunk(String upload, int offset, byte[] part) {
        return transaction(() -> service.upload("chunk", Map.of("projectId", "p", "uploadId", upload, "offset", offset, "base64", Base64.getEncoder().encodeToString(part), "chunkHash", DigestUtils.sha256Hex(part))));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void outOfOrderReplayAndConcurrentCompleteProduceOneExactVersion() throws Exception {
        String upload = prepare("key"); int half = bytes.length / 2;
        chunk(upload, half, Arrays.copyOfRange(bytes, half, bytes.length));
        chunk(upload, 0, Arrays.copyOf(bytes, half));
        chunk(upload, 0, Arrays.copyOf(bytes, half));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.<Callable<Map<String, Object>>>of(() -> { token("token"); return act("complete", upload); }, () -> { token("token"); return act("complete", upload); });
            var results = pool.invokeAll(tasks);
            assertEquals(results.get(0).get().get("fileRef"), results.get(1).get().get("fileRef"));
        }
        assertEquals(1, count("file_metadata"));
        jdbc.update("UPDATE system_parameter SET param_value='invalid'");
        assertEquals("COMPLETE", transaction(() -> service.prepare(request("key"))).get("state"));
        jdbc.update("UPDATE system_parameter SET param_value='50'");
        assertEquals(1, count("project_file_original"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM project_original_chunk WHERE content IS NOT NULL", Integer.class));
        Map<?, ?> ref = (Map<?, ?>) act("complete", upload).get("fileRef");
        var read = transaction(() -> service.read("download", Map.of("projectId", "p", "fileId", ref.get("fileId"), "versionId", ref.get("versionId"), "offset", 0, "length", 1048576)));
        assertArrayEquals(bytes, Base64.getDecoder().decode((String) read.get("base64")));
        assertEquals(DigestUtils.sha256Hex(bytes), read.get("fullHash"));
        assertEquals(true, read.get("eof"));
        chunk(upload, 0, Arrays.copyOf(bytes, half));
        assertEquals("COMPLETE", act("cancel", upload).get("state"));
        assertEquals(1, count("file_metadata"));
    }

    @Test void rejectsOverlapChangedReplayEmptyOutOfBoundsAndMissingChunksWithoutFiles() {
        String upload = prepare("ranges");
        chunk(upload, 0, Arrays.copyOf(bytes, 10));
        assertThrows(MSException.class, () -> chunk(upload, 5, new byte[]{1, 2}));
        assertThrows(MSException.class, () -> chunk(upload, 0, new byte[10]));
        assertThrows(MSException.class, () -> chunk(upload, 10, new byte[0]));
        assertThrows(MSException.class, () -> chunk(upload, bytes.length, new byte[]{1}));
        assertThrows(MSException.class, () -> act("complete", upload));
        assertEquals(0, count("file_metadata"));
        assertEquals(1, count("project_original_chunk"));
    }

    @Test void cancelledAndExpiredUploadsCannotCreateFilesAndKeepOriginalRequest() {
        String cancelled = prepare("cancel"); chunk(cancelled, 0, bytes);
        assertEquals("CANCELLED", act("cancel", cancelled).get("state"));
        assertThrows(MSException.class, () -> act("complete", cancelled));
        assertEquals("CANCELLED", transaction(() -> service.prepare(request("cancel"))).get("state"));
        String expired = prepare("expired"); chunk(expired, 0, bytes);
        jdbc.update("UPDATE project_original_upload SET expires_at=0 WHERE id=?", expired);
        assertThrows(MSException.class, () -> act("complete", expired));
        transaction(() -> { service.expireChunks(); return null; });
        assertEquals("EXPIRED", transaction(() -> service.prepare(request("expired"))).get("state"));
        assertEquals(0, count("file_metadata"));
        assertEquals(2, count("project_original_upload"));
        assertEquals(0, count("project_original_chunk"));
    }

    @Test void requestCaseAndFingerprintAreStrictAndHashFailureWritesNothing() {
        String upload = prepare("CaseKey");
        assertEquals(upload, prepare("CaseKey"));
        assertNotEquals(upload, prepare("casekey"));
        var different = request("CaseKey"); different.put("sourceLabel", "different");
        assertThrows(MSException.class, () -> transaction(() -> service.prepare(different)));
        byte[] changed = bytes.clone(); changed[changed.length - 1] ^= 1;
        chunk(upload, 0, changed);
        assertThrows(MSException.class, () -> act("complete", upload));
        assertEquals(0, count("file_metadata"));
    }

    @Test void tokenAndProjectIsolationAndRevocationRejectNextChunk() {
        String upload = prepare("owner");
        token("other");
        assertThrows(MSException.class, () -> chunk(upload, 0, bytes));
        token("token");
        assertThrows(MSException.class, () -> transaction(() -> service.upload("complete", Map.of("projectId", "other", "uploadId", upload))));
        doThrow(new MSException("denied")).when(access).project(eq("p"), anyString(), anyBoolean(), any(String[].class));
        assertThrows(MSException.class, () -> chunk(upload, 0, bytes));
        assertEquals(0, count("project_original_chunk"));
        assertEquals(0, count("file_metadata"));
    }

    @Test void cancelAndCompleteRaceLeavesOneDeterminateResult() throws Exception {
        String upload = prepare("race"); chunk(upload, 0, bytes);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.<Callable<String>>of(
                    () -> { token("token"); return (String) act("cancel", upload).get("state"); },
                    () -> { token("token"); try { return (String) act("complete", upload).get("state"); } catch (MSException cancelled) { return "CANCELLED"; } }));
            String state = (String) transaction(() -> service.upload("get", Map.of("projectId", "p", "requestId", "race"))).get("state");
            assertEquals(state, results.get(0).get()); assertEquals(state, results.get(1).get());
            assertEquals("COMPLETE".equals(state) ? 1 : 0, count("file_metadata"));
        }
    }

    @Test void newVersionRequiresExpectedLatestAndOldVersionStaysReadable() {
        String upload = prepare("v1"); chunk(upload, 0, bytes);
        Map<?, ?> first = (Map<?, ?>) act("complete", upload).get("fileRef");
        var conflict = request("different-content"); conflict.put("sha256", "0".repeat(64));
        var collision = assertThrows(ProjectFileException.class, () -> transaction(() -> service.prepare(conflict)));
        assertEquals("FILE_NAME_CONFLICT", collision.getMessage());
        assertEquals(1, ((List<?>) collision.details().get("candidates")).size());
        assertEquals("REUSE_CONFIRMATION_REQUIRED", transaction(() -> service.prepare(request("duplicate"))).get("state"));
        var next = request("v2"); next.put("existingFileId", first.get("fileId")); next.put("expectedVersion", first.get("versionId")); next.put("confirmNewVersion", true);
        String newUpload = (String) transaction(() -> service.prepare(next)).get("uploadId");
        chunk(newUpload, 0, bytes); act("complete", newUpload);
        var stale = new HashMap<>(next); stale.put("requestId", "stale");
        assertThrows(MSException.class, () -> transaction(() -> service.prepare(stale)));
        assertArrayEquals(bytes, transaction(() -> service.readOriginal("p", (String) first.get("fileId"), (String) first.get("versionId"), DigestUtils.sha256Hex(bytes))));
        assertEquals(2, count("file_metadata"));
    }

    @Test void capacityLimitUsesCurrentServerConfigurationAndRejectsUnknownLimit() {
        assertEquals(50L * 1024 * 1024, service.maxFileBytes());
        jdbc.update("UPDATE system_parameter SET param_value='1'");
        var large = request("large"); large.put("size", 1048577);
        assertThrows(MSException.class, () -> transaction(() -> service.prepare(large)));
        large.put("size", 1048576);
        assertEquals("PREPARED", transaction(() -> service.prepare(large)).get("state"));
        jdbc.update("UPDATE system_parameter SET param_value='invalid'");
        assertThrows(MSException.class, () -> service.maxFileBytes());
    }

    @Test void writeVersionsRejectLatestAndFreezeTheSameImmutableVersionUntilCommit() {
        String upload = prepare("first"); chunk(upload, 0, bytes);
        Map<?, ?> first = (Map<?, ?>) act("complete", upload).get("fileRef");
        var request = request("new-a");
        request.put("existingFileId", first.get("fileId"));
        request.put("expectedVersion", "latest");
        request.put("confirmNewVersion", true);
        assertThrows(MSException.class, () -> transaction(() -> service.prepare(request)));
        request.put("expectedVersion", first.get("versionId"));
        String a = (String) transaction(() -> service.prepare(request)).get("uploadId");
        var second = new HashMap<>(request); second.put("requestId", "new-b");
        String b = (String) transaction(() -> service.prepare(second)).get("uploadId");
        chunk(a, 0, bytes); chunk(b, 0, bytes);
        assertEquals("COMPLETE", act("complete", a).get("state"));
        assertEquals("TARGET_CONFLICT", assertThrows(MSException.class, () -> act("complete", b)).getMessage());
        assertEquals(2, count("file_metadata"));
        assertArrayEquals(bytes, transaction(() -> service.readOriginal("p", (String) first.get("fileId"), (String) first.get("versionId"), DigestUtils.sha256Hex(bytes))));
        // A pre-fix pending session containing an alias must also be rejected, not refreshed.
        String frozen = jdbc.queryForObject("SELECT request_json FROM project_original_upload WHERE id=?", String.class, b);
        Map<String, Object> legacy = io.metersphere.sdk.util.JSON.parseMap(frozen);
        legacy.put("expectedVersion", "latest");
        jdbc.update("UPDATE project_original_upload SET request_json=? WHERE id=?", io.metersphere.sdk.util.JSON.toJSONString(legacy), b);
        assertEquals("VALIDATION_ERROR", assertThrows(MSException.class, () -> act("complete", b)).getMessage());
        assertEquals(2, count("file_metadata"));
    }

    private AgentBusinessAccess realAuthorization() {
        jdbc.execute("ALTER TABLE project ADD deleted INT DEFAULT 0");
        jdbc.execute("ALTER TABLE project ADD enable BOOLEAN DEFAULT TRUE");
        jdbc.execute("ALTER TABLE agent_token ADD user_id VARCHAR(50) DEFAULT 'user'");
        jdbc.execute("ALTER TABLE agent_token ADD enable BOOLEAN DEFAULT TRUE");
        jdbc.execute("ALTER TABLE agent_token ADD revoked_at BIGINT");
        jdbc.execute("ALTER TABLE agent_token ADD scopes VARCHAR(255) DEFAULT 'PROJECT_FILE_READ;PROJECT_FILE_WRITE;PROJECT_FILE_DOWNLOAD'");
        jdbc.execute("CREATE TABLE user(id VARCHAR(50) PRIMARY KEY,enable BOOLEAN,deleted INT)");
        jdbc.execute("CREATE TABLE user_role_relation(user_id VARCHAR(50),role_id VARCHAR(50),source_id VARCHAR(50),PRIMARY KEY(user_id,role_id,source_id))");
        jdbc.execute("CREATE TABLE user_role(id VARCHAR(50) PRIMARY KEY,type VARCHAR(50),enabled BOOLEAN)");
        jdbc.execute("CREATE TABLE user_role_permission(role_id VARCHAR(50),permission_id VARCHAR(100),PRIMARY KEY(role_id,permission_id))");
        jdbc.update("INSERT INTO user VALUES('user',TRUE,0)");
        jdbc.update("INSERT INTO user_role VALUES('r','PROJECT',TRUE)");
        jdbc.update("INSERT INTO user_role_relation VALUES('user','r','p')");
        jdbc.update("INSERT INTO user_role_permission VALUES('r','PROJECT_FILE_MANAGEMENT:READ'),('r','PROJECT_FILE_MANAGEMENT:READ+ADD'),('r','PROJECT_FILE_MANAGEMENT:READ+DOWNLOAD')");
        var real = new AgentBusinessAccess(jdbc);
        ReflectionTestUtils.setField(service, "access", real);
        return real;
    }

    private String revocationSql(String kind) {
        return switch (kind) {
            case "RELATION" -> "DELETE FROM user_role_relation WHERE user_id='user'";
            case "PERMISSION" -> "DELETE FROM user_role_permission WHERE permission_id='PROJECT_FILE_MANAGEMENT:READ+ADD'";
            case "USER" -> "UPDATE user SET enable=FALSE WHERE id='user'";
            case "ROLE" -> "UPDATE user_role SET enabled=FALSE WHERE id='r'";
            case "TOKEN" -> "UPDATE agent_token SET revoked_at=1 WHERE id='token'";
            default -> throw new IllegalArgumentException();
        };
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RELATION", "PERMISSION", "USER", "ROLE", "TOKEN"})
    void fileFirstHoldsActualAuthorizationDependenciesUntilCommit(String kind) throws Exception {
        realAuthorization();
        String upload = prepare("write-first"); chunk(upload, 0, bytes);
        var enteredStorage = new CountDownLatch(1); var releaseStorage = new CountDownLatch(1);
        doAnswer(call -> {
            enteredStorage.countDown();
            if (!releaseStorage.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Storage barrier timed out");
            return store(null, call.getArgument(0), call.getArgument(2));
        }).when(files).upload(any(), anyString(), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var completion = pool.submit(() -> { token("token"); return act("complete", upload); });
            assertTrue(enteredStorage.await(10, TimeUnit.SECONDS));
            var attempting = new CountDownLatch(1);
            var revoked = pool.submit(() -> tx.execute(status -> { attempting.countDown(); return jdbc.update(revocationSql(kind)); }));
            assertTrue(attempting.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> revoked.get(200, TimeUnit.MILLISECONDS));
            releaseStorage.countDown();
            assertEquals("COMPLETE", completion.get(10, TimeUnit.SECONDS).get("state"));
            assertEquals(1, revoked.get(10, TimeUnit.SECONDS));
            assertThrows(MSException.class, () -> act("complete", upload));
            assertEquals(1, count("file_metadata"));
            assertEquals("COMPLETE", jdbc.queryForObject("SELECT state FROM project_original_upload WHERE id=?", String.class, upload));
        } finally { releaseStorage.countDown(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RELATION", "PERMISSION", "USER", "ROLE", "TOKEN"})
    void revocationFirstBlocksAndRejectsFileCommitWithoutNewVersions(String kind) throws Exception {
        realAuthorization();
        String upload = prepare("revoke-first"); chunk(upload, 0, bytes);
        var changed = new CountDownLatch(1); var releaseRevocation = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var revocation = pool.submit(() -> tx.execute(status -> {
                jdbc.update(revocationSql(kind)); changed.countDown();
                try { if (!releaseRevocation.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Revocation barrier timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return true;
            }));
            assertTrue(changed.await(10, TimeUnit.SECONDS));
            var completion = pool.submit(() -> { token("token"); return act("complete", upload); });
            assertThrows(TimeoutException.class, () -> completion.get(200, TimeUnit.MILLISECONDS));
            releaseRevocation.countDown();
            assertTrue(revocation.get(10, TimeUnit.SECONDS));
            var denied = assertThrows(ExecutionException.class, () -> completion.get(10, TimeUnit.SECONDS));
            assertInstanceOf(MSException.class, denied.getCause());
            assertEquals(0, count("file_metadata"));
            assertEquals(0, count("project_file_original"));
            assertEquals("UPLOADING", jdbc.queryForObject("SELECT state FROM project_original_upload WHERE id=?", String.class, upload));
        } finally { releaseRevocation.countDown(); }
    }

    @Test void malformedXmindCannotCreateAnOfficialVersion() throws Exception {
        int number = 0;
        for (var invalid : List.of(Map.entry("content.json", "not JSON at all"), Map.entry("content.json", "{}"),
                Map.entry("content.xml", "<x/>"), Map.entry("content.xml", "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///unavailable'>]><x>&e;</x>"))) {
            bytes = ProjectOriginalFileValidatorTests.zip(Map.of(invalid.getKey(), invalid.getValue()));
            String upload = prepare("invalid-" + ++number); chunk(upload, 0, bytes);
            assertEquals("UNSUPPORTED_FORMAT", assertThrows(MSException.class, () -> act("complete", upload)).getMessage());
            assertEquals(0, count("file_metadata"));
            assertEquals(0, count("project_file_original"));
        }
    }

    @Test void bothSupportedXmindFormatsStoreAndReadBackIdenticalBytes() throws Exception {
        for (String content : List.of("content.json", "content.xml")) {
            bytes = ProjectOriginalFileValidatorTests.zip(Map.of(content, content.endsWith("json")
                    ? ProjectOriginalFileValidatorTests.XMIND_JSON : ProjectOriginalFileValidatorTests.XMIND_XML));
            var request = request("roundtrip-" + content);
            request.put("fileName", content + ".xmind");
            String upload = (String) transaction(() -> service.prepare(request)).get("uploadId");
            chunk(upload, 0, bytes);
            Map<?, ?> ref = (Map<?, ?>) act("complete", upload).get("fileRef");
            assertArrayEquals(bytes, transaction(() -> service.readOriginal("p", (String) ref.get("fileId"), (String) ref.get("versionId"), DigestUtils.sha256Hex(bytes))));
        }
        assertEquals(2, count("file_metadata"));
    }

    @Test void rejectionAuditResolvesOwnedOperationsAcrossProjectsAndSurvivesRollback() {
        realAuthorization();
        jdbc.update("INSERT INTO project(id) VALUES ('q')");
        jdbc.update("INSERT INTO user_role_relation VALUES ('user','r','q')");
        String p = prepare("p-request");
        var qRequest = request("q-request"); qRequest.put("projectId", "q");
        String q = (String) transaction(() -> service.prepare(qRequest)).get("uploadId");
        jdbc.execute("CREATE TABLE rejection_audit(resource VARCHAR(50),content TEXT)");
        doAnswer(call -> { jdbc.update("INSERT INTO rejection_audit VALUES (?,?)", (String) call.getArgument(1), (String) call.getArgument(2)); return "audit"; })
                .when(audit).audit(eq("PROJECT_FILE_REJECT"), anyString(), anyString());
        for (String upload : List.of(p, q)) {
            String project = upload.equals(p) ? "p" : "q";
            tx.executeWithoutResult(status -> {
                service.recordRejection(new ProjectFileException("HASH_MISMATCH"), "upload.chunk", Map.of("projectId", project, "uploadId", upload, "base64", "DO_NOT_LOG"), "trace-" + project);
                status.setRollbackOnly();
            });
        }
        assertEquals(2, count("rejection_audit"));
        for (var row : jdbc.queryForList("SELECT * FROM rejection_audit")) {
            Map<String, Object> content = io.metersphere.sdk.util.JSON.parseMap((String) row.get("content"));
            String project = (String) content.get("projectId");
            assertEquals("p".equals(project) ? p : q, content.get("uploadId"));
            assertEquals(row.get("resource"), content.get("operationId"));
            assertEquals(project + "-request", content.get("requestId"));
            assertEquals("trace-" + project, content.get("traceId"));
            assertEquals(DigestUtils.sha256Hex(bytes), content.get("sha256"));
            assertFalse(row.get("content").toString().contains("DO_NOT_LOG"));
        }
        service.recordRejection(new ProjectFileException("HASH_MISMATCH"), "upload.chunk", Map.of("projectId", "other", "uploadId", p), "trace-denied");
        String anonymous = jdbc.queryForObject("SELECT content FROM rejection_audit WHERE resource='AUDIT'", String.class);
        assertFalse(anonymous.contains("projectId")); assertFalse(anonymous.contains("uploadId")); assertFalse(anonymous.contains("sha256"));
        var rejected = assertThrows(ProjectFileException.class, () -> transaction(() -> service.upload("chunk", Map.of(
                "projectId", "p", "uploadId", p, "offset", 0, "base64", Base64.getEncoder().encodeToString(bytes), "chunkHash", "0".repeat(64)))));
        assertEquals(p, rejected.auditContext().get("uploadId"));
        jdbc.update("DELETE FROM user_role_relation WHERE user_id='user'");
        service.recordRejection(rejected, "upload.chunk", Map.of("projectId", "forged", "uploadId", "forged"), "trace-after-revoke");
        var after = jdbc.queryForList("SELECT content FROM rejection_audit WHERE resource=?", String.class, p).stream()
                .filter(value -> value.contains("trace-after-revoke")).findFirst().orElseThrow();
        Map<String, Object> retained = io.metersphere.sdk.util.JSON.parseMap(after);
        assertEquals("p", retained.get("projectId")); assertEquals("p-request", retained.get("requestId"));
        assertFalse(after.contains("forged"));
        var publicError = new AgentSafeErrorMapper().toApiError(rejected, "trace-after-revoke");
        assertFalse(publicError.getDetails().containsKey("uploadId"));
        assertFalse(publicError.getDetails().containsKey("projectId"));
        assertEquals(0, count("file_metadata"));
    }
}
