package io.metersphere.system.security;

import org.apache.shiro.session.Session;

/** Absolute authenticated lifetime; reading/refreshing a session never extends it. */
public final class SessionLifetime {
    public static final long MAX_AGE_MS = java.time.Duration.ofDays(14).toMillis();
    public static final String LOGIN_AT = "ms.loginAt";
    public static final String EXPIRES_AT = "ms.expiresAt";
    private SessionLifetime() { }

    public static void initialize(Session session, boolean firstAuthentication, long now) {
        if (session.getAttribute(EXPIRES_AT) != null) return;
        // Older authenticated sessions retain their actual creation time, never a fabricated new login.
        long loginAt = firstAuthentication ? now : session.getStartTimestamp().getTime();
        session.setAttribute(LOGIN_AT, loginAt);
        session.setAttribute(EXPIRES_AT, loginAt + MAX_AGE_MS);
    }

    public static boolean expired(Session session, long now) {
        if (session == null) return true;
        initialize(session, false, now);
        Object expiry = session.getAttribute(EXPIRES_AT);
        return !(expiry instanceof Number) || now >= ((Number) expiry).longValue();
    }
}
