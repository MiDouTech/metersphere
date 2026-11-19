package io.metersphere.agent.security;

import io.metersphere.sdk.exception.MSException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** All configuration values and original documents are encrypted with scope-bound AAD. */
@Component
public class SharedConfigurationCipher {
  private final String encodedKey;

  public SharedConfigurationCipher(
      @Value("${msp.shared-configuration.encryption-key:}") String encodedKey) {
    this.encodedKey = encodedKey;
  }

  private SecretKeySpec key() {
    try {
      byte[] value = Base64.getDecoder().decode(encodedKey);
      if (value.length != 32) throw new IllegalArgumentException();
      return new SecretKeySpec(value, "AES");
    } catch (IllegalArgumentException error) {
      throw new io.metersphere.agent.service.BusinessGovernanceException(
          "CONFIGURATION_KEY_UNAVAILABLE", "公共配置加密密钥未配置或格式无效，请联系管理员", error);
    }
  }

  public String encrypt(byte[] value, String scope) {
    try {
      byte[] iv = new byte[12];
      new SecureRandom().nextBytes(iv);
      var c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      c.updateAAD(scope.getBytes(StandardCharsets.UTF_8));
      return "v1:"
          + Base64.getEncoder().encodeToString(iv)
          + ":"
          + Base64.getEncoder().encodeToString(c.doFinal(value));
    } catch (MSException error) {
      throw error;
    } catch (Exception error) {
      throw new io.metersphere.agent.service.BusinessGovernanceException(
          "CONFIGURATION_ENCRYPTION_FAILED", "公共配置加密失败，请稍后重试", error);
    }
  }

  public byte[] decrypt(String value, String scope) {
    try {
      var p = value.split(":", -1);
      if (p.length != 3 || !p[0].equals("v1")) throw new IllegalArgumentException();
      byte[] iv = Base64.getDecoder().decode(p[1]);
      if (iv.length != 12) throw new IllegalArgumentException();
      var c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
      c.updateAAD(scope.getBytes(StandardCharsets.UTF_8));
      return c.doFinal(Base64.getDecoder().decode(p[2]));
    } catch (MSException error) {
      throw error;
    } catch (Exception error) {
      throw new io.metersphere.agent.service.BusinessGovernanceException(
          "CONFIGURATION_DECRYPTION_FAILED", "公共配置解密失败，请联系管理员核对配置密钥", error);
    }
  }
}
