package io.metersphere.system.wecombot;

import io.metersphere.sdk.exception.MSException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class WecomSecretService {
    private static final String MASTER_KEY_ENV = "MS_WECOM_SECRET_MASTER_KEY";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String encrypt(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
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
            byte[] source = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[source.length - iv.length];
            System.arraycopy(source, 0, iv, 0, iv.length);
            System.arraycopy(source, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
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
