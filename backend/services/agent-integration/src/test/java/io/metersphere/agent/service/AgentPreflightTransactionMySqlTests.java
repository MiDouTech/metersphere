package io.metersphere.agent.service;

import io.metersphere.agent.dto.TestAssetCatalogItemDTO;
import io.metersphere.agent.dto.TestAssetRefDTO;
import io.metersphere.agent.mapper.TestAssetMapper;
import io.metersphere.sdk.exception.MSException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "quality.mysql", matches = "true")
class AgentPreflightTransactionMySqlTests {
    private final String schema = "preflight_fix_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate admin, jdbc;
    private DataSourceTransactionManager transactions;
    private TestAssetCatalogService catalog;
    private TestAssetMapper mapper;
    private boolean explicitVersion;

    @BeforeEach void setup() {
        admin = new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only"));
        admin.execute("CREATE DATABASE " + schema);
        var dataSource = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13326/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true", "root", "quality-test-only");
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        jdbc.execute("CREATE TABLE preflight_result(status VARCHAR(32))");
        mapper = mock(TestAssetMapper.class);
        var projects = mock(AgentProjectService.class);
        when(projects.resolveProjectId("p1")).thenReturn("p1");
        var versions = new TestAssetVersionService();
        ReflectionTestUtils.setField(versions, "mapper", mapper);
        var target = new TestAssetCatalogService();
        ReflectionTestUtils.setField(target, "mapper", mapper);
        ReflectionTestUtils.setField(target, "agentProjectService", projects);
        ReflectionTestUtils.setField(target, "versionService", transactional(versions));
        catalog = transactional(target);
    }

    @AfterEach void cleanup() { if (admin != null) admin.execute("DROP DATABASE IF EXISTS " + schema); }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    private void validateAndSaveBlocked() {
        var subject = mock(Subject.class);
        when(subject.isPermitted(anyString())).thenReturn(true);
        var ref = new TestAssetRefDTO();
        ref.setAssetType("ENVIRONMENT");
        ref.setAssetId("environment-1");
        if (explicitVersion) ref.setVersionId("published-version-1");
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getSubject).thenReturn(subject);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("INSERT INTO preflight_result(status) VALUES ('STARTED')");
                try {
                    catalog.resolveContext("p1", List.of(ref));
                    fail("Fixture must reject the asset");
                } catch (MSException rejected) {
                    jdbc.update("UPDATE preflight_result SET status='BLOCKED'");
                }
            });
        }
    }

    @Test void missingAssetDoesNotRollBackTheBlockedResult() {
        assertDoesNotThrow(this::validateAndSaveBlocked);
        assertEquals("BLOCKED", jdbc.queryForObject("SELECT status FROM preflight_result", String.class));
    }

    @Test void nestedPublishedVersionRejectionDoesNotPoisonOuterTransaction() {
        when(mapper.selectCatalogItem("p1", "ENVIRONMENT", "environment-1")).thenReturn(new TestAssetCatalogItemDTO());
        assertDoesNotThrow(this::validateAndSaveBlocked);
        assertEquals("BLOCKED", jdbc.queryForObject("SELECT status FROM preflight_result", String.class));
    }

    @Test void unexpectedTechnicalErrorsStillPropagateAndDoNotSaveSuccess() {
        when(mapper.selectCatalogItem("p1", "ENVIRONMENT", "environment-1")).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, this::validateAndSaveBlocked);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM preflight_result", Integer.class));
    }

    @Test void explicitPublishedVersionRejectionCanBeRecorded() {
        explicitVersion = true;
        when(mapper.selectCatalogItem("p1", "ENVIRONMENT", "environment-1")).thenReturn(new TestAssetCatalogItemDTO());
        assertDoesNotThrow(this::validateAndSaveBlocked);
        assertEquals("BLOCKED", jdbc.queryForObject("SELECT status FROM preflight_result", String.class));
    }
}
