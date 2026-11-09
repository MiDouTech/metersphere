package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.metersphere.sdk.util.AesGcmCodec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class WecomSecretService {
    private static final String MASTER_KEY_ENV = "MS_WECOM_SECRET_MASTER_KEY";

    public String encrypt(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return AesGcmCodec.seal(key(), null, value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new MSException("Unable to encrypt WeCom Bot secret");
        }
    }

    public String resolve(String secretRef, String ciphertext) {
        if (StringUtils.isNotBlank(secretRef)) {
            String envName = secretRef.startsWith("env:") ? secretRef.substring(4) : secretRef;
            String value = WecomRuntimeSecrets.read(envName);
            if (StringUtils.isBlank(value)) {
                throw new MSException("WeCom Bot secret reference is not configured");
            }
            return value;
        }
        if (StringUtils.isBlank(ciphertext)) {
            throw new MSException("WeCom Bot secret is not configured");
        }
        try {
            return new String(AesGcmCodec.open(key(), null, ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MSException("Unable to decrypt WeCom Bot secret");
        }
    }

    private SecretKeySpec key() throws Exception {
        String masterKey = WecomRuntimeSecrets.read(MASTER_KEY_ENV);
        if (StringUtils.isBlank(masterKey)) {
            throw new MSException(MASTER_KEY_ENV + " is required when storing an encrypted secret");
        }
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(masterKey.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
