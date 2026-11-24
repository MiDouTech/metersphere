package io.metersphere.system.sso.miduo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.system.config.LocalLoginProperties;
import io.metersphere.system.config.MiduoSsoProperties;
import io.metersphere.system.controller.LoginController;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.dto.sso.miduo.MiduoSsoStatusDTO;
import io.metersphere.system.dto.sso.miduo.MiduoValidateResult;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.security.MsAuthenticationFilter;
import io.metersphere.system.security.SessionLifetime;
import io.metersphere.system.security.realm.LocalRealm;
import io.metersphere.system.service.UserLoginService;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.session.mgt.ServletContainerSessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP -> Spring Session repository -> Shiro -> callback/controller, synthetic SSO and users only. */
class MspSessionHttpIntegrationTests {
    private MockMvc mvc;
    private MapSessionRepository repository;
    private MiduoSsoClient client;
    private MiduoSsoSessionStore legacy;
    private UserLoginService users;
    private UserDTO user;
    private DefaultWebSecurityManager manager;
    private org.springframework.context.MessageSource previousMessages;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        previousMessages = (org.springframework.context.MessageSource) ReflectionTestUtils.getField(io.metersphere.sdk.util.Translator.class, "messageSource");
        new io.metersphere.sdk.util.Translator().setMessageSource(new org.springframework.context.support.StaticMessageSource());
        users = mock(UserLoginService.class);
        user = new UserDTO(); user.setId("member"); user.setEnable(true);
        when(users.getUserDTO("member")).thenAnswer(call -> user);
        var realm = new LocalRealm();
        ReflectionTestUtils.setField(realm, "userLoginService", users);
        ReflectionTestUtils.setField(realm, "localLoginProperties", new LocalLoginProperties());
        manager = new DefaultWebSecurityManager(realm);
        manager.setSessionManager(new ServletContainerSessionManager());

        repository = new MapSessionRepository(new ConcurrentHashMap<>());
        repository.setDefaultMaxInactiveInterval(Duration.ofSeconds(60)); // legacy server setting
        var sessions = new SessionRepositoryFilter<MapSession>(repository);
        sessions.setHttpSessionIdResolver(new HeaderHttpSessionIdResolver("X-AUTH-TOKEN"));
        var shiro = new ShiroFilterFactoryBean();
        shiro.setSecurityManager(manager);
        shiro.getFilters().put("authc", new MsAuthenticationFilter());
        var chains = new LinkedHashMap<String, String>();
        chains.put("/auth/miduo/**", "anon"); chains.put("/**", "authc");
        shiro.setFilterChainDefinitionMap(chains);

        client = mock(MiduoSsoClient.class);
        legacy = mock(MiduoSsoSessionStore.class);
        var app = spy(new MiduoSsoApplicationService());
        var properties = new MiduoSsoProperties();
        properties.setEnabled(true); properties.setBaseUrl("https://sso.example.test");
        properties.setAppCode("test"); properties.setAppSecret("synthetic");
        properties.setRedirectUri("https://msp.example.test/callback");
        ReflectionTestUtils.setField(app,"properties",properties);
        ReflectionTestUtils.setField(app,"miduoSsoClient",client);
        ReflectionTestUtils.setField(app,"miduoSsoSessionStore",legacy);
        ReflectionTestUtils.setField(app,"miduoSsoStateService",mock(MiduoSsoStateService.class));
        ReflectionTestUtils.setField(app,"userLoginService",users);
        var mapper = mock(ExtUserMapper.class);
        var member = new User(); member.setId("member"); member.setEnable(true);
        when(mapper.selectByWecomUserid("wecom-member")).thenReturn(member);
        ReflectionTestUtils.setField(app,"extUserMapper",mapper);
        var ready = new MiduoSsoStatusDTO(); ready.setReady(true);
        doReturn(ready).when(app).getStatus();
        var identity = new MiduoValidateResult(); identity.setValid(true);
        identity.setWeworkUserid("wecom-member"); identity.setSessionToken("synthetic-upstream");
        identity.setExpiresAt(System.currentTimeMillis() - 1);
        when(client.validateLoginToken(anyString())).thenReturn(identity);
        var callbacks = new MiduoSsoAuthController();
        ReflectionTestUtils.setField(callbacks,"miduoSsoApplicationService",app);
        var login = new LoginController();
        ReflectionTestUtils.setField(login,"userLoginService",users);
        ReflectionTestUtils.setField(login,"projectMapper",mock(ProjectMapper.class));
        ReflectionTestUtils.setField(login,"miduoSsoApplicationService",app);
        ReflectionTestUtils.setField(login,"defaultLocale","zh_CN");
        mvc = MockMvcBuilders.standaloneSetup(callbacks, login, new PermissionProbe())
                .addFilters(sessions, (jakarta.servlet.Filter)shiro.getObject()).build();
    }

    private String login() throws Exception {
        var result = mvc.perform(post("/auth/miduo/callback").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"synthetic-exchange\",\"state\":\"synthetic-state\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("sessionId").asText();
    }

    @AfterEach void cleanup() {
        ThreadContext.remove();
        if (manager != null) manager.destroy();
        ReflectionTestUtils.setField(io.metersphere.sdk.util.Translator.class, "messageSource", previousMessages);
    }

    @org.springframework.web.bind.annotation.RestController
    static class PermissionProbe {
        @org.springframework.web.bind.annotation.GetMapping("/session-permission-probe")
        public org.springframework.http.ResponseEntity<Void> permission() {
            return org.springframework.http.ResponseEntity.status(
                    org.apache.shiro.SecurityUtils.getSubject().isPermitted("SYNTHETIC:READ") ? 200 : 403).build();
        }
    }

    @Test void independentBrowsersSurviveSsoExpiryAndCurrentLogoutDoesNotKillOtherBrowser() throws Exception {
        String a = login(); String b = login();
        assertNotEquals(a, b); assertFalse(a.isBlank());
        assertEquals(Duration.ofDays(14), repository.findById(a).getMaxInactiveInterval());
        assertEquals(Duration.ofDays(14), repository.findById(b).getMaxInactiveInterval());
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", a)).andExpect(status().isOk());
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", b)).andExpect(status().isOk());
        mvc.perform(get("/signout").header("X-AUTH-TOKEN", a)).andExpect(status().isOk());
        assertNull(repository.findById(a));
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", a)).andExpect(status().isUnauthorized());
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", b)).andExpect(status().isOk());
        verify(client, never()).refreshSessionToken(anyString());
        verify(client, never()).revokeSessionToken(anyString());
        verify(legacy, never()).save(anyString(), anyString(), any());
        verify(legacy, never()).revokeUser(anyString());
    }

    @Test void absoluteExpiryRejectsOldCredentialsWithoutSilentSsoRecovery() throws Exception {
        String a = login();
        var stored = repository.findById(a);
        stored.setAttribute(SessionLifetime.EXPIRES_AT, System.currentTimeMillis() - 1);
        repository.save(stored);
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", a)).andExpect(status().isUnauthorized());
        assertNull(repository.findById(a));
        verify(client, times(1)).validateLoginToken(anyString());
    }

    @Test void accountDisableStillRejectsAnOtherwiseValidLocalSession() throws Exception {
        String a = login(); user.setEnable(false);
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", a)).andExpect(status().isUnauthorized());
        assertNull(repository.findById(a));
    }

    @Test void idleBeyondUpstreamEightHoursDoesNotExpireTheIndependentMspSession() throws Exception {
        String a = login();
        var stored = repository.findById(a);
        Object expiry = stored.getAttribute(SessionLifetime.EXPIRES_AT);
        stored.setLastAccessedTime(java.time.Instant.now().minus(Duration.ofHours(12)));
        repository.save(stored);
        mvc.perform(get("/is-login").header("X-AUTH-TOKEN", a)).andExpect(status().isOk());
        assertEquals(expiry, repository.findById(a).getAttribute(SessionLifetime.EXPIRES_AT));
        verify(client, never()).refreshSessionToken(anyString());
    }

    @Test void legacyCleanupAndAuditFailuresCannotPreventLogoutOrLeakPrivateDiagnostics() throws Exception {
        String a = login();
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MiduoSsoApplicationService.class);
        capture.start(); logger.addAppender(capture);
        try {
            doThrow(new IllegalStateException("synthetic-private-token", new java.sql.SQLException("synthetic-private-password", "08006", 1045)))
                    .when(legacy).delete("member");
            doThrow(new IllegalStateException("https://private.example/?token=synthetic"))
                    .when(users).saveLog(anyString(), anyString(), anyString(), anyString(), anyString());
            mvc.perform(get("/signout").header("X-AUTH-TOKEN", a)).andExpect(status().isOk());
            assertNull(repository.findById(a));
            String diagnostics = capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(diagnostics.contains("phase=LEGACY_CLEANUP"));
            assertTrue(diagnostics.contains("phase=AUDIT"));
            assertTrue(diagnostics.contains("08006"));
            assertTrue(diagnostics.contains("1045"));
            assertFalse(diagnostics.contains("private"));
            assertFalse(diagnostics.contains("password"));
            assertTrue(capture.list.stream().allMatch(event -> event.getThrowableProxy() == null));
        } finally {
            logger.detachAppender(capture); capture.stop();
        }
    }

    @Test void permissionsAreReloadedWithoutExtendingAbsoluteLifetime() throws Exception {
        var role = new io.metersphere.system.domain.UserRole();
        role.setId("custom-role"); role.setType("SYSTEM"); role.setEnabled(true);
        var relation = new io.metersphere.system.domain.UserRoleRelation();
        relation.setId("relation"); relation.setUserId("member");
        relation.setRoleId("custom-role"); relation.setSourceId(io.metersphere.sdk.constants.UserRoleScope.SYSTEM);
        var permission = new io.metersphere.system.domain.UserRolePermission();
        permission.setPermissionId("SYNTHETIC:READ");
        var resource = new io.metersphere.system.dto.user.UserRoleResourceDTO();
        resource.setUserRole(role); resource.setUserRolePermissions(List.of(permission));
        user.setUserRoles(List.of(role)); user.setUserRoleRelations(List.of(relation));
        user.setUserRolePermissions(List.of(resource));
        String a = login();
        Object expiry = repository.findById(a).getAttribute(SessionLifetime.EXPIRES_AT);
        mvc.perform(get("/session-permission-probe").header("X-AUTH-TOKEN", a)).andExpect(status().isOk());
        // Replace the database DTO, do not mutate objects already held by the stored SessionUser.
        var changed = new UserDTO(); changed.setId("member"); changed.setEnable(true);
        user = changed;
        mvc.perform(get("/is-login").accept(MediaType.APPLICATION_JSON).header("X-AUTH-TOKEN", a)).andExpect(status().isOk());
        mvc.perform(get("/session-permission-probe").header("X-AUTH-TOKEN", a)).andExpect(status().isForbidden());
        assertEquals(expiry, repository.findById(a).getAttribute(SessionLifetime.EXPIRES_AT));
    }
}
