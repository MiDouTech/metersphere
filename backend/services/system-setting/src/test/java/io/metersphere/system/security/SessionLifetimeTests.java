package io.metersphere.system.security;

import org.apache.shiro.session.mgt.SimpleSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionLifetimeTests {
    @Test
    void fourteenDaysIsAbsoluteAndRefreshCannotExtendIt() {
        var session = new SimpleSession();
        long start = 100000;
        SessionLifetime.initialize(session, true, start);
        assertFalse(SessionLifetime.expired(session, start + SessionLifetime.MAX_AGE_MS - 1));
        SessionLifetime.initialize(session, false, start + 1000);
        assertTrue(SessionLifetime.expired(session, start + SessionLifetime.MAX_AGE_MS));
    }

    @Test
    void legacySessionUsesActualCreationTime() {
        var session = new SimpleSession();
        session.setStartTimestamp(new java.util.Date(100000));
        long now = 100000 + SessionLifetime.MAX_AGE_MS;
        assertTrue(SessionLifetime.expired(session, now));
        assertEquals(100000L, session.getAttribute(SessionLifetime.LOGIN_AT));
    }
}
