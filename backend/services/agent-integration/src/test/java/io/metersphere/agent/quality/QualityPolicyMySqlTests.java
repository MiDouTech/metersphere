package io.metersphere.agent.quality;

import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Uses only the disposable local quality_verify database, never an application datasource. */
@EnabledIfSystemProperty(named="quality.mysql", matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QualityPolicyMySqlTests {
    private AnnotationConfigApplicationContext context;
    private QualityPolicyService service;
    private JdbcTemplate jdbc;

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/quality_verify?useSSL=false&allowPublicKeyRetrieval=true",
                    "root", "quality-test-only");
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean QualityPolicyValidator validator() { return new QualityPolicyValidator(); }
        @Bean QualityPolicyAccess access() {
            var access = mock(QualityPolicyAccess.class);
            when(access.require(anyString(), anyString())).thenReturn("reviewer");
            return access;
        }
        @Bean QualityPolicyService service(JdbcTemplate jdbc, QualityPolicyAccess access, QualityPolicyValidator validator) {
            var ids = mock(io.metersphere.system.uid.impl.DefaultUidGenerator.class);
            var counter = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
            when(ids.getUID()).thenAnswer(call -> counter.incrementAndGet());
            return new QualityPolicyService(jdbc, access, validator, ids);
        }
    }

    @BeforeAll void migrate() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(QualityPolicyService.class);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='quality_verify' AND table_name='execution_quality_policy'", Integer.class);
        if (count == 0) new ResourceDatabasePopulator(new ClassPathResource("migration/3.7.2/ddl/V3.7.2_91__execution_quality_policy.sql"))
                .execute(context.getBean(DataSource.class));
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='quality_verify' AND table_name='execution_quality_policy_publication'", Integer.class) == 0)
            new ResourceDatabasePopulator(new ClassPathResource("migration/3.7.2/ddl/V3.7.2_93__execution_quality_policy_publication.sql"))
                    .execute(context.getBean(DataSource.class));
    }
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM execution_quality_policy_publication");
        jdbc.update("DELETE FROM execution_quality_policy");
        jdbc.update("DELETE FROM execution_quality_policy_project");
    }
    @AfterAll void close() {
        if (context != null) context.close();
    }
    private QualityPolicyService.Policy draft(String project) {
        return service.create(new QualityPolicyService.DraftRequest(project, QualityPolicyValidatorTests.VALID, null));
    }
    private QualityPolicyService.PublishRequest publishRequest(QualityPolicyService.Policy policy, String current) {
        return new QualityPolicyService.PublishRequest(policy.projectId(), policy.rowVersion(), current, "Verified change");
    }

    @Test void persistsDraftAndRejectsStaleEditAndPublishedMutation() {
        var first = draft("p1");
        var updated = service.update(first.id(), new QualityPolicyService.DraftRequest("p1",
                QualityPolicyValidatorTests.VALID.replace("Policy", "Updated"), first.rowVersion()));
        assertEquals(1, updated.rowVersion());
        assertThrows(MSException.class, () -> service.update(first.id(), new QualityPolicyService.DraftRequest("p1", QualityPolicyValidatorTests.VALID, 0L)));
        var published = service.publish(updated.id(), publishRequest(updated, null));
        assertEquals("PUBLISHED", published.status());
        assertEquals(published.id(), service.list("p1").currentPolicyId());
        assertThrows(MSException.class, () -> service.update(published.id(), new QualityPolicyService.DraftRequest("p1", QualityPolicyValidatorTests.VALID, published.rowVersion())));
        assertEquals(updated.contentHash(), service.list("p1").items().getFirst().contentHash());
    }
    @Test void crossProjectIdsCannotReadUpdateOrPublish() {
        var policy = draft("p1");
        assertTrue(service.list("p2").items().isEmpty());
        draft("p2");
        assertThrows(MSException.class, () -> service.update(policy.id(), new QualityPolicyService.DraftRequest("p2", QualityPolicyValidatorTests.VALID, 0L)));
        assertThrows(MSException.class, () -> service.publish(policy.id(), new QualityPolicyService.PublishRequest("p2", 0L, null, "Attempt")));
        assertNull(service.list("p1").currentPolicyId());
    }
    @Test void invalidPolicyDoesNotCreateRows() {
        assertThrows(QualityPolicyValidationException.class, () -> service.create(new QualityPolicyService.DraftRequest("p1", "{}", null)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy_project", Integer.class));
    }
    @Test void parallelDraftsGetUniqueVersions() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = pool.invokeAll(List.of(() -> draft("p1"), () -> draft("p1"), () -> draft("p1"), () -> draft("p1")));
            var versions = new java.util.HashSet<Integer>();
            for (var future : futures) versions.add(((QualityPolicyService.Policy)future.get()).versionNo());
            assertEquals(java.util.Set.of(1,2,3,4), versions);
        }
    }
    @Test void concurrentPublishAllowsOnlyOneAndRollbackCreatesNewVersion() throws Exception {
        var a = draft("p1"); var b = draft("p1");
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.function.Function<QualityPolicyService.Policy, Callable<Boolean>> action = policy -> () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                try { service.publish(policy.id(), publishRequest(policy, null)); return true; }
                catch (MSException conflict) { assertEquals("QUALITY_POLICY_VERSION_CONFLICT", conflict.getMessage()); return false; }
            };
            var fa = pool.submit(action.apply(a)); var fb = pool.submit(action.apply(b));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(fa.get(20, TimeUnit.SECONDS), fb.get(20, TimeUnit.SECONDS));
        }
        var listing = service.list("p1");
        var old = listing.items().stream().filter(p -> p.id().equals(listing.currentPolicyId())).findFirst().orElseThrow();
        var rollback = service.create(new QualityPolicyService.DraftRequest("p1", old.rulesJson(), null));
        service.publish(rollback.id(), publishRequest(rollback, old.id()));
        assertEquals(3, rollback.versionNo());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy WHERE status='PUBLISHED'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM execution_quality_policy_publication", Integer.class));
        var event = service.detail(rollback.id(), "p1").publication();
        assertEquals(old.id(), event.previousPolicyId());
        assertEquals(old.contentHash(), event.previousHash());
        assertEquals(rollback.contentHash(), event.publishedHash());
        assertEquals("reviewer", event.actor());
    }
    @Test void pagesAllVersionsAndRetainsCurrentOutsidePage() {
        var first = draft("p1");
        service.publish(first.id(), publishRequest(first, null));
        for (int i = 0; i < 101; i++) draft("p1");
        var page = service.list("p1", 6, 20);
        assertEquals(102, page.total());
        assertEquals(2, page.items().size());
        assertEquals(first.id(), page.currentPolicy().id());
        assertEquals(first.id(), service.detail(first.id(), "p1").policy().id());
        assertThrows(MSException.class, () -> service.detail(first.id(), "p2"));
        assertThrows(MSException.class, () -> service.list("p1", 0, 20));
        assertThrows(MSException.class, () -> service.list("p1", 1, 101));
    }
    @Test void failedAuditInsertRollsBackPublicationAndPointer() {
        var policy = draft("p1");
        jdbc.update("INSERT INTO execution_quality_policy_publication(policy_id,project_id,published_hash,actor,published_at,reason) VALUES (?,?,?,?,?,?)",
                policy.id(), "p1", policy.contentHash(), "fixture", 1L, "Force duplicate key");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.publish(policy.id(), publishRequest(policy, null)));
        assertEquals("DRAFT", service.detail(policy.id(), "p1").policy().status());
        assertNull(service.list("p1").currentPolicyId());
    }
}
