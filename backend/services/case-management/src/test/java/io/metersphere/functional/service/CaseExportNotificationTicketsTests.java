package io.metersphere.functional.service;

import io.metersphere.functional.dto.ExportTaskDTO;
import io.metersphere.sdk.constants.SessionConstants;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.security.SessionLifetime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "quality.redis", matches = "true")
class CaseExportNotificationTicketsTests {
    private org.redisson.api.RedissonClient client;
    private StringRedisTemplate redis;
    private String namespace;
    private MapSessionRepository sessions;
    private MapSession session;
    private FunctionalCaseExportTaskService tasks;
    private static final String ORIGIN = "http://127.0.0.1:15175";

    @BeforeEach void prepare() {
        var config = new org.redisson.config.Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:16479");
        client = org.redisson.Redisson.create(config);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(client));
        namespace = "export-ws-test-" + UUID.randomUUID();
        sessions = new MapSessionRepository(new ConcurrentHashMap<>());
        session = sessions.createSession();
        var userDTO = new io.metersphere.system.dto.user.UserDTO(); userDTO.setId("user");
        var user = SessionUser.fromUser(userDTO, session.getId());
        session.setAttribute(SessionConstants.ATTR_USER, user);
        session.setAttribute(SessionLifetime.EXPIRES_AT, System.currentTimeMillis() + 60000);
        sessions.save(session);
        tasks = mock(FunctionalCaseExportTaskService.class);
        var task = new ExportTaskDTO(); task.setTaskId("task"); task.setProjectId("project");
        when(tasks.get("task", "user")).thenReturn(task);
    }
    @AfterEach void cleanup() {
        if (redis != null) { var keys = redis.keys(namespace + ":*"); if (keys != null && !keys.isEmpty()) redis.delete(keys); }
        if (client != null) client.shutdown();
    }
    private CaseExportNotificationTickets service() {
        var service = new CaseExportNotificationTickets();
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "sessions", sessions);
        ReflectionTestUtils.setField(service, "tasks", tasks);
        ReflectionTestUtils.setField(service, "namespace", namespace);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "configuredOrigins", ORIGIN);
        service.validateConfiguration(); return service;
    }
    @Test void oneTicketCanBeConsumedOnlyOnceAcrossReplicas() {
        var first = service(); var second = service();
        var ticket = first.issue("task", "user", session.getId(), ORIGIN);
        assertEquals(43, ticket.ticket().length());
        var keys = redis.keys(namespace + ":case-export-ws:ticket:*");
        assertNotNull(keys); assertEquals(1, keys.size());
        assertTrue(redis.getExpire(keys.iterator().next()) <= 60);
        assertTrue(redis.getExpire(keys.iterator().next()) > 0);
        long accepted = java.util.stream.IntStream.range(0, 20).parallel().filter(i -> {
            try { (i % 2 == 0 ? first : second).consume(ticket.ticket(), "task", ORIGIN); return true; }
            catch (CaseExportNotificationTickets.TicketException denied) { assertEquals(403, denied.status()); return false; }
        }).count();
        assertEquals(1, accepted);
    }
    @Test void wrongOriginOrTaskCannotSpendTicketAndExpiredTicketIsRejected() {
        var service = service(); var ticket = service.issue("task", "user", session.getId(), ORIGIN);
        assertThrows(CaseExportNotificationTickets.TicketException.class, () -> service.consume(ticket.ticket(), "other", ORIGIN));
        assertThrows(CaseExportNotificationTickets.TicketException.class, () -> service.consume(ticket.ticket(), "task", "https://evil.example"));
        assertEquals("project", service.consume(ticket.ticket(), "task", ORIGIN).projectId());
        var expired = service.issue("task", "user", session.getId(), ORIGIN);
        redis.keys(namespace + ":case-export-ws:ticket:*").forEach(key -> redis.expire(key, java.time.Duration.ZERO));
        assertThrows(CaseExportNotificationTickets.TicketException.class, () -> service.consume(expired.ticket(), "task", ORIGIN));
    }
    @Test void logoutAbsoluteExpiryAndPermissionRevocationInvalidateIssuedTickets() {
        var service = service(); var ticket = service.issue("task", "user", session.getId(), ORIGIN);
        sessions.deleteById(session.getId());
        assertThrows(CaseExportNotificationTickets.TicketException.class, () -> service.consume(ticket.ticket(), "task", ORIGIN));
        sessions.save(session);
        var expiryTicket = service.issue("task", "user", session.getId(), ORIGIN);
        session.setAttribute(SessionLifetime.EXPIRES_AT, 1L); sessions.save(session);
        var expired = expiryTicket;
        assertThrows(CaseExportNotificationTickets.TicketException.class, () -> service.consume(expired.ticket(), "task", ORIGIN));
        session.setAttribute(SessionLifetime.EXPIRES_AT, System.currentTimeMillis() + 60000); sessions.save(session);
        var revoked = service.issue("task", "user", session.getId(), ORIGIN);
        when(tasks.get("task", "user")).thenThrow(CaseExportNotificationTickets.denied());
        assertThrows(CaseExportNotificationTickets.TicketException.class, () -> service.consume(revoked.ticket(), "task", ORIGIN));
    }
    @Test void rateLimitSharedAndStoreOutageFailsClosedWhileDisabledNeedsNoStore() {
        var first = service(); var second = service();
        for (int i = 0; i < 30; i++) (i % 2 == 0 ? first : second).issue("task", "user", session.getId(), ORIGIN);
        assertEquals(429, assertThrows(CaseExportNotificationTickets.TicketException.class,
                () -> second.issue("task", "user", session.getId(), ORIGIN)).status());
        ReflectionTestUtils.setField(first, "redis", mock(StringRedisTemplate.class));
        assertEquals(503, assertThrows(CaseExportNotificationTickets.TicketException.class,
                () -> first.issue("task", "user", session.getId(), ORIGIN)).status());
        ReflectionTestUtils.setField(first, "enabled", false);
        assertFalse(first.issue("task", "user", null, null).enabled());
    }
    @Test void enablingRequiresExplicitValidOrigins() {
        var service = service();
        for (String invalid : List.of("", "*", "https://example.com/path", "https://example.com:99999", "https://user@example.com")) {
            ReflectionTestUtils.setField(service, "configuredOrigins", invalid);
            assertThrows(IllegalArgumentException.class, service::validateConfiguration);
        }
    }
}
