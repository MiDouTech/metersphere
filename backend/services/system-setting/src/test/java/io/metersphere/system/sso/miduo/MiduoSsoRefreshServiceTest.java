package io.metersphere.system.sso.miduo;

import io.metersphere.system.config.MiduoSsoProperties;
import io.metersphere.system.dto.sso.miduo.MiduoRefreshResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【AI辅助生成】米多 SSO 续期策略单测；需人工补充联调边界。
 */
@ExtendWith(MockitoExtension.class)
class MiduoSsoRefreshServiceTest {

    @Mock
    private MiduoSsoClient miduoSsoClient;
    @Mock
    private MiduoSsoSessionStore miduoSsoSessionStore;

    private MiduoSsoRefreshService refreshService;
    private MiduoSsoProperties properties;

    @BeforeEach
    void setUp() {
        refreshService = new MiduoSsoRefreshService();
        properties = new MiduoSsoProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://miduo.example.com");
        properties.setAppCode("app");
        properties.setAppSecret("secret");
        properties.setRedirectUri("https://ms.example.com");
        properties.setRefreshAheadSeconds(1800);
        properties.setLoginGraceSeconds(300);
        ReflectionTestUtils.setField(refreshService, "properties", properties);
        ReflectionTestUtils.setField(refreshService, "miduoSsoClient", miduoSsoClient);
        ReflectionTestUtils.setField(refreshService, "miduoSsoSessionStore", miduoSsoSessionStore);
    }

    @Test
    void skipRefreshWithinLoginGrace() {
        when(miduoSsoSessionStore.getSessionToken("u1")).thenReturn("tok");
        when(miduoSsoSessionStore.getExpiresAt("u1")).thenReturn(System.currentTimeMillis() + 60_000L);
        when(miduoSsoSessionStore.getSavedAt("u1")).thenReturn(System.currentTimeMillis() - 5_000L);

        Assertions.assertFalse(refreshService.refreshIfNeeded("u1"));
        verify(miduoSsoClient, never()).refreshSessionToken(anyString());
        verify(miduoSsoSessionStore, never()).markNeedReauth(anyString());
    }

    @Test
    void softFailWhenRefreshFailsButNotHardExpired() {
        long expiresAt = System.currentTimeMillis() + 600_000L;
        when(miduoSsoSessionStore.getSessionToken("u1")).thenReturn("tok");
        when(miduoSsoSessionStore.getExpiresAt("u1")).thenReturn(expiresAt);
        when(miduoSsoSessionStore.getSavedAt("u1")).thenReturn(System.currentTimeMillis() - 400_000L);
        when(miduoSsoClient.refreshSessionToken("tok")).thenThrow(new MiduoSsoException("refresh fail"));

        Assertions.assertFalse(refreshService.refreshIfNeeded("u1"));
        verify(miduoSsoSessionStore, never()).markNeedReauth(anyString());
    }

    @Test
    void needReauthWhenRefreshFailsNearExpiry() {
        long expiresAt = System.currentTimeMillis() + 30_000L;
        when(miduoSsoSessionStore.getSessionToken("u1")).thenReturn("tok");
        when(miduoSsoSessionStore.getExpiresAt("u1")).thenReturn(expiresAt);
        when(miduoSsoSessionStore.getSavedAt("u1")).thenReturn(System.currentTimeMillis() - 400_000L);
        when(miduoSsoClient.refreshSessionToken("tok")).thenThrow(new MiduoSsoException("refresh fail"));

        Assertions.assertTrue(refreshService.refreshIfNeeded("u1"));
        verify(miduoSsoSessionStore).markNeedReauth("u1");
    }

    @Test
    void noRefreshWhenExpiresAtMissing() {
        when(miduoSsoSessionStore.getSessionToken("u1")).thenReturn("tok");
        when(miduoSsoSessionStore.getExpiresAt("u1")).thenReturn(null);
        when(miduoSsoSessionStore.getSavedAt("u1")).thenReturn(System.currentTimeMillis() - 400_000L);
        when(miduoSsoSessionStore.isNeedReauth("u1")).thenReturn(true);

        Assertions.assertFalse(refreshService.refreshIfNeeded("u1"));
        verify(miduoSsoClient, never()).refreshSessionToken(anyString());
        verify(miduoSsoSessionStore).clearNeedReauth("u1");
    }

    @Test
    void refreshSuccessPersistsNewToken() {
        long expiresAt = System.currentTimeMillis() + 600_000L;
        when(miduoSsoSessionStore.getSessionToken("u1")).thenReturn("tok");
        when(miduoSsoSessionStore.getExpiresAt("u1")).thenReturn(expiresAt);
        when(miduoSsoSessionStore.getSavedAt("u1")).thenReturn(System.currentTimeMillis() - 400_000L);
        MiduoRefreshResult result = new MiduoRefreshResult();
        result.setSuccess(true);
        result.setSessionToken("tok2");
        result.setExpiresAt(expiresAt + 3_600_000L);
        when(miduoSsoClient.refreshSessionToken("tok")).thenReturn(result);

        Assertions.assertFalse(refreshService.refreshIfNeeded("u1"));
        verify(miduoSsoSessionStore).save(anyString(), anyString(), anyLong());
    }
}
