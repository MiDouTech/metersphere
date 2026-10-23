package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.mapper.BaseUserMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real migration, paging, row locks and rollback in an isolated disposable database. */
@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class FunctionalCaseCommentLifecycleMySqlTests {
    static JdbcTemplate admin, jdbc;
    static String database;
    FunctionalCaseCommentLifecycleService service;
    FunctionalCaseCommentAccess access;
    TransactionTemplate tx;

    @BeforeAll static void database() throws Exception {
        String server = System.getProperty("quality.mysql.server", "jdbc:mysql://127.0.0.1:13326/");
        if (!server.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/")) throw new IllegalArgumentException("Dedicated loopback MySQL required");
        String user = System.getProperty("quality.mysql.user", "root");
        String password = System.getProperty("quality.mysql.password", "quality-test-only");
        String options = "?useSSL=false&allowPublicKeyRetrieval=true";
        admin = new JdbcTemplate(new DriverManagerDataSource(server + options, user, password));
        database = "case_comment_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        var source = new DriverManagerDataSource(server + database + options, user, password);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE functional_case(id VARCHAR(50) PRIMARY KEY,deleted BOOLEAN)");
        jdbc.update("INSERT INTO functional_case VALUES ('case',0)");
        jdbc.execute("""
                CREATE TABLE functional_case_comment(id VARCHAR(50) PRIMARY KEY,case_id VARCHAR(50),
                parent_id VARCHAR(50),reply_user VARCHAR(50),notifier TEXT,content TEXT,
                create_user VARCHAR(50),create_time BIGINT,update_time BIGINT)
                """);
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_107__case_comment_lifecycle.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("migration/3.7.2/ddl/V3.7.2_108__case_comment_notice_outbox.sql"));
        }
    }
    @AfterAll static void cleanup() {
        if (admin != null && database != null && database.matches("case_comment_test_[a-f0-9]{32}")) admin.execute("DROP DATABASE " + database);
    }
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM functional_case_comment_audit");
        jdbc.update("DELETE FROM functional_case_comment_notice");
        jdbc.update("DELETE FROM functional_case_comment");
        service = new FunctionalCaseCommentLifecycleService();
        access = mock(FunctionalCaseCommentAccess.class);
        var owner = new FunctionalCase(); owner.setId("case"); owner.setProjectId("project");
        when(access.requireCase(eq("case"), anyString(), anyBoolean())).thenReturn(owner);
        when(access.hasPermission(anyString(), eq("project"), anyString())).thenReturn(true);
        var users = mock(BaseUserMapper.class);
        when(users.getCommentUserInfoByIds(anyList())).thenReturn(List.of());
        var audit = new FunctionalCaseCommentAudit();
        ReflectionTestUtils.setField(audit, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "audit", audit);
        ReflectionTestUtils.setField(service, "users", users);
        tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }
    void comment(String id, String parent) {
        jdbc.update("INSERT INTO functional_case_comment(id,case_id,parent_id,content,create_user,create_time,update_time) VALUES (?,'case',?,'private body','author',10,10)", id, parent);
    }
    @Test void rootsAndRepliesPageIndependentlyWithStableTies() {
        for (int i = 0; i < 25; i++) { comment("root" + String.format("%02d", i), ""); comment("child" + String.format("%02d", i), "root24"); }
        var first = service.page("case", "author", null, 1, 10);
        assertEquals(25, first.total()); assertEquals(10, first.list().size());
        assertEquals("root24", first.list().getFirst().getId());
        assertEquals(25, first.list().getFirst().getChildTotal());
        assertEquals(10, first.list().getFirst().getChildComments().size());
        Set<String> ids = new HashSet<>();
        for (int page = 1; page <= 3; page++) service.page("case", "author", "root24", page, 10).list().forEach(row -> assertTrue(ids.add(row.getId())));
        assertEquals(25, ids.size());
        var last = service.page("case", "author", null, 99, 10);
        assertEquals(3, last.current()); assertEquals(5, last.list().size());
    }
    @Test void rejectsInvalidPagingAndForeignOrNonRootParent() {
        comment("root", ""); comment("child", "root");
        for (String parent : List.of("missing", "child")) assertThrows(MSException.class, () -> service.page("case", "author", parent, 1, 10));
        assertThrows(MSException.class, () -> service.page("case", "author", null, 0, 10));
        assertThrows(MSException.class, () -> service.page("case", "author", null, 1, 101));
        jdbc.update("UPDATE functional_case_comment SET case_id='other' WHERE id='root'");
        assertThrows(MSException.class, () -> service.page("case", "author", "root", 1, 10));
    }
    @Test void tombstoneKeepsChildrenAndHashesAuditWithoutBody() {
        comment("root", ""); comment("reply", "root");
        var deleted = tx.execute(status -> service.delete("case", "root", "author", 10L));
        assertNotNull(deleted); assertTrue(deleted.deleted()); assertTrue(deleted.updateTime() > 10);
        var root = service.page("case", "author", null, 1, 10).list().getFirst();
        assertEquals("", root.getContent()); assertTrue(root.getDeleted());
        assertFalse(root.isCanEdit()); assertFalse(root.isCanDelete());
        assertEquals("reply", root.getChildComments().getFirst().getId());
        assertEquals("private body", root.getChildComments().getFirst().getContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_audit", Integer.class));
        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex("private body"), jdbc.queryForObject("SELECT before_hash FROM functional_case_comment_audit", String.class));
        assertEquals(deleted, tx.execute(status -> service.delete("case", "root", "author", deleted.updateTime())));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_audit", Integer.class));
    }
    @Test void authorModeratorAndRevokedPermissionsAreEnforced() {
        comment("root", "");
        assertThrows(MSException.class, () -> tx.execute(status -> service.delete("case", "root", "other", 10L)));
        assertFalse(service.page("case", "other", null, 1, 10).list().getFirst().isCanDelete());
        when(access.mayModerate("moderator", "project")).thenReturn(true);
        var visible = service.page("case", "moderator", null, 1, 10).list().getFirst();
        assertTrue(visible.isCanDelete()); assertFalse(visible.isCanEdit());
        when(access.requireCase("case", "author", true)).thenThrow(FunctionalCaseCommentAccess.denied());
        assertThrows(MSException.class, () -> tx.execute(status -> service.delete("case", "root", "author", 10L)));
        assertTrue(tx.execute(status -> service.delete("case", "root", "moderator", 10L)).deleted());
    }
    @Test void missingStaleAndConcurrentVersionsCannotDeleteTwice() throws Exception {
        comment("root", "");
        assertThrows(MSException.class, () -> tx.execute(status -> service.delete("case", "root", "author", null)));
        assertThrows(MSException.class, () -> tx.execute(status -> service.delete("case", "root", "author", 9L)));
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2);
        try {
            Callable<Boolean> call = () -> { ready.countDown(); assertTrue(ready.await(3, TimeUnit.SECONDS));
                try { tx.execute(status -> service.delete("case", "root", "author", 10L)); return true; }
                catch (MSException conflict) { return false; }
            };
            var results = pool.invokeAll(List.of(call, call));
            assertNotEquals(results.get(0).get(), results.get(1).get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_audit", Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void auditFailureRollsBackTombstone() {
        comment("root", ""); var brokenAudit = mock(FunctionalCaseCommentAudit.class);
        doThrow(new IllegalStateException("audit unavailable")).when(brokenAudit).record(anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        ReflectionTestUtils.setField(service, "audit", brokenAudit);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> service.delete("case", "root", "author", 10L)));
        assertEquals("private body", jdbc.queryForObject("SELECT content FROM functional_case_comment WHERE id='root'", String.class));
        assertFalse(jdbc.queryForObject("SELECT deleted FROM functional_case_comment WHERE id='root'", Boolean.class));
    }
    @Test void legacyArrayNeverSilentlyTruncates() {
        for (int i = 0; i < 100; i++) comment("r" + i, "");
        assertEquals(100, service.legacyList("case", "author").size());
        comment("overflow", "");
        assertThrows(MSException.class, () -> service.legacyList("case", "author"));
    }

    @Test void noticeOutboxRollsBackWithCommentAndDeduplicatesDelivery() {
        comment("root", "");
        var sender = mock(io.metersphere.system.service.NoticeSendService.class);
        var detail = new io.metersphere.system.notice.MessageDetail();
        detail.setId("channel"); detail.setProjectId("project");
        detail.setType(io.metersphere.system.notice.constants.NoticeConstants.Type.IN_SITE);
        var model = io.metersphere.system.notice.NoticeModel.builder().event("COMMENT").frozenRecipients(true)
                .paramMap(Map.of("projectId", "project")).receivers(List.of(new io.metersphere.system.notice.Receiver("author", "SYSTEM_NOTICE"))).build();
        when(sender.prepareDeliveries(anyString(), any())).thenReturn(List.of(new io.metersphere.system.service.NoticeSendService.Delivery(detail, model)));
        var outbox = new FunctionalCaseCommentOutbox();
        ReflectionTestUtils.setField(outbox, "jdbc", jdbc);
        ReflectionTestUtils.setField(outbox, "sender", sender);
        ReflectionTestUtils.setField(outbox, "access", access);
        ReflectionTestUtils.setField(outbox, "transactionManager", new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(status -> { outbox.enqueue("root", 10, "case", model); status.setRollbackOnly(); });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_notice", Integer.class));
        tx.executeWithoutResult(status -> { outbox.enqueue("root", 10, "case", model); outbox.enqueue("root", 10, "case", model); });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM functional_case_comment_notice", Integer.class));
        outbox.drain(); outbox.drain();
        verify(sender, times(1)).deliver(any());
        assertEquals("SENT", jdbc.queryForObject("SELECT state FROM functional_case_comment_notice", String.class));
    }
}
