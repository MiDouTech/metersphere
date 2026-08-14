package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class WecomCallbackVerifier {
    public void verify(String timestamp, String nonce, String signature, String body) {
        String token = WecomRuntimeSecrets.read("MS_WECOM_BRIDGE_CALLBACK_TOKEN");
        if (StringUtils.isAnyBlank(token, timestamp, nonce, signature)) {
            throw new MSException("Invalid Bridge callback authentication");
        }
        long value;
        try {
            value = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new MSException("Invalid Bridge callback timestamp");
        }
        if (Math.abs(System.currentTimeMillis() - value) > 300_000L) {
            throw new MSException("Expired Bridge callback");
        }
        try {
            String content = timestamp + "\n" + nonce + "\n" + DigestUtils.sha256Hex(body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = java.util.HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                throw new MSException("Invalid Bridge callback signature");
            }
        } catch (MSException e) {
            throw e;
        } catch (Exception e) {
            throw new MSException("Unable to verify Bridge callback");
        }
    }
}
