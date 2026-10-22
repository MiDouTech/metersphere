package io.metersphere.system.sso.miduo;

import io.metersphere.system.config.MiduoSsoProperties;
import io.metersphere.system.dto.sdk.SessionUser;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.HashMap;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MiduoSsoSessionIsolationTests {
    @Test
    @SuppressWarnings("unchecked")
    void sameUserTwoBrowsersDoNotOverwriteOrLogoutEachOther() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String,String> values = mock(ValueOperations.class);
        SetOperations<String,String> sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        var data = new HashMap<String,String>();
        doAnswer(call -> { data.put(call.getArgument(0),call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> data.get(call.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(call -> data.remove(call.getArgument(0)) != null);
        var store = new MiduoSsoSessionStore();
        ReflectionTestUtils.setField(store,"stringRedisTemplate",redis);
        ReflectionTestUtils.setField(store,"properties",new MiduoSsoProperties());
        try {
            bind("browser-a");
            store.save("user", "secret-a", null);
            bind("browser-b");
            assertNull(store.getSessionToken("user"));
            assertTrue(store.isNeedReauth("user"));
            store.save("user", "secret-b", null);
            bind("browser-a");
            assertEquals("secret-a", store.getSessionToken("user"));
            store.delete("user");
            bind("browser-b");
            assertEquals("secret-b", store.getSessionToken("user"));
            ReflectionTestUtils.setField(store,"instance","another-instance");
            assertNull(store.getSessionToken("user"));
        } finally { ThreadContext.unbindSubject(); }
    }

    private void bind(String sessionId) {
        var session = new SimpleSession();
        session.setId(sessionId);
        var user = mock(SessionUser.class);
        when(user.getId()).thenReturn("user");
        session.setAttribute("user",user);
        var subject = mock(Subject.class);
        when(subject.getSession()).thenReturn(session);
        when(subject.getSession(false)).thenReturn(session);
        ThreadContext.bind(subject);
    }
}
