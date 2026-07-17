package io.metersphere.system.sso.miduo;

import io.metersphere.system.config.MiduoSsoProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 【AI辅助生成】米多 SSO 客户端单测；联调边界需人工补充。
 */
class MiduoSsoClientTest {

    private MiduoSsoClient client;
    private MiduoSsoProperties properties;

    @BeforeEach
    void setUp() {
        client = new MiduoSsoClient();
        properties = new MiduoSsoProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://miduo.example.com");
        properties.setAppCode("ms-app");
        // 单测假密钥，非真实凭据
        properties.setAppSecret("unit-test-only-not-a-real-secret");
        properties.setRedirectUri("https://ms.example.com/#/sso/miduo/callback");
        ReflectionTestUtils.setField(client, "properties", properties);
    }

    @Test
    void signMatchesHmacSha256Base64WithUnixSeconds() throws Exception {
        // 与米多 OpenSsoRequestVerifier 一致：Unix 秒
        String timestamp = "1710000000";
        String nonce = "abc123";
        String signedValue = "exch_token";
        String expected = hmac(properties.getAppCode(), timestamp, nonce, signedValue, properties.getAppSecret());
        String actual = client.sign(signedValue, timestamp, nonce);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void asEpochMillisKeepsMillisAndScalesSeconds() {
        Assertions.assertEquals(1_710_000_000_000L, MiduoSsoClient.asEpochMillis(1_710_000_000_000L));
        Assertions.assertEquals(1_710_000_000_000L, MiduoSsoClient.asEpochMillis(1_710_000_000L));
        Assertions.assertNull(MiduoSsoClient.asEpochMillis(null));
        Assertions.assertNull(MiduoSsoClient.asEpochMillis("not-a-number"));
    }

    @Test
    void bridgeUrlContainsAppCodeAndRedirectUri() {
        String url = client.buildBridgeUrl("state-xyz");
        Assertions.assertTrue(url.contains("/api/sso/bridge/redirect-url"));
        Assertions.assertTrue(url.contains("appCode=ms-app"));
        Assertions.assertTrue(url.contains("state=state-xyz"));
        Assertions.assertTrue(url.contains("redirectUri="));
    }

    private static String hmac(String appCode, String timestamp, String nonce, String signedValue, String secret)
            throws Exception {
        String canonical = appCode + "\n" + timestamp + "\n" + nonce + "\n" + signedValue;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
