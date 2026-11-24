package io.metersphere.system.controller;

import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.dto.user.UserDTO;
import io.metersphere.system.security.SessionLifetime;
import io.metersphere.system.service.UserLoginService;
import io.metersphere.system.sso.miduo.MiduoSsoApplicationService;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MspIndependentSessionTests {
    private LoginController controller;
    private UserLoginService users;
    private MiduoSsoApplicationService upstream;
    private Subject subject;
    private SimpleSession session;

    @BeforeEach
    void setup() {
        controller = new LoginController();
        users = mock(UserLoginService.class);
        upstream = mock(MiduoSsoApplicationService.class);
        ReflectionTestUtils.setField(controller, "userLoginService", users);
        ReflectionTestUtils.setField(controller, "projectMapper", mock(ProjectMapper.class));
        ReflectionTestUtils.setField(controller, "miduoSsoApplicationService", upstream);
        ReflectionTestUtils.setField(controller, "defaultLocale", "zh_CN");
        bind("browser-a");
    }

    private void bind(String id) {
        session = new SimpleSession();
        session.setId(id);
        session.setAttribute("authenticate", "MIDUO");
        session.setAttribute("needMiduoReauth", true); // stale legacy marker
        SessionLifetime.initialize(session, true, System.currentTimeMillis());
        UserDTO user = new UserDTO();
        user.setId("member"); user.setEnable(true);
        session.setAttribute("user", SessionUser.fromUser(user, id));
        subject = mock(Subject.class);
        when(subject.getSession()).thenReturn(session);
        when(subject.getSession(false)).thenReturn(session);
        ThreadContext.bind(subject);
        when(users.getUserDTO("member")).thenReturn(user);
    }

    @AfterEach void cleanup() { ThreadContext.unbindSubject(); }

    @Test void expiredSsoDoesNotInvalidateLocalSessionOrKeepLegacyReauthFlag() throws Exception {
        var response = new MockHttpServletResponse();
        var result = controller.isLogin(response);
        assertEquals(200, response.getStatus());
        assertEquals("browser-a", ((SessionUser) result.getData()).getSessionId());
        assertFalse(Boolean.TRUE.equals(((SessionUser) result.getData()).getNeedMiduoReauth()));
        assertNull(session.getAttribute("needMiduoReauth"));
        verifyNoInteractions(upstream);
    }

    @Test void twoBrowsersKeepTheirOwnSessionAndAbsoluteExpiry() throws Exception {
        var first = session;
        controller.isLogin(new MockHttpServletResponse());
        Object expiry = first.getAttribute(SessionLifetime.EXPIRES_AT);
        bind("browser-b");
        controller.isLogin(new MockHttpServletResponse());
        verify(subject, never()).logout();
        assertEquals("browser-a", ((SessionUser)first.getAttribute("user")).getSessionId());
        assertEquals(expiry, first.getAttribute(SessionLifetime.EXPIRES_AT));
        verifyNoInteractions(upstream);
    }

    @Test void fourteenDayBoundaryIsRejectedWithoutUpstreamRecovery() throws Exception {
        session.setAttribute(SessionLifetime.EXPIRES_AT, System.currentTimeMillis() - 1);
        var response = new MockHttpServletResponse();
        controller.isLogin(response);
        assertEquals(401, response.getStatus());
        verify(subject).logout();
        verifyNoInteractions(upstream);
    }

    @Test void disabledAccountIsRejectedAndSessionDestroyed() throws Exception {
        when(users.getUserDTO("member")).thenThrow(new DisabledAccountException());
        var response = new MockHttpServletResponse();
        controller.isLogin(response);
        assertEquals(401, response.getStatus());
        verify(subject).logout();
    }

    @Test void deletedAccountIsRejectedAndSessionDestroyed() throws Exception {
        when(users.getUserDTO("member")).thenReturn(null);
        var response = new MockHttpServletResponse();
        controller.isLogin(response);
        assertEquals(401, response.getStatus());
        verify(subject).logout();
    }
}
