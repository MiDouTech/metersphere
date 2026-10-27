package io.metersphere.agent.service;
import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class AgentFactCipherConfigurationTests {
 @Test void invalidConfigurationRejectsSensitiveFactsWithoutAPlaintextFallback() {
  for(String key:new String[]{"","invalid!",Base64.getEncoder().encodeToString(new byte[16])}) {
   var cipher=new AgentFactCipher();ReflectionTestUtils.setField(cipher,"encodedKey",key);
   assertFalse(cipher.configured());
   assertEquals("QUALITY_FACTS_KEY_NOT_CONFIGURED",assertThrows(MSException.class,()->cipher.protect("{\"token\":\"test-value\"}")).getMessage());
  }
 }
 @Test void sensitiveJsonRoundTripsAndRemainsReadableAfterInstanceRestart() {
  byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
  var encoded=Base64.getEncoder().encodeToString(key);var first=new AgentFactCipher();
  ReflectionTestUtils.setField(first,"encodedKey",encoded);assertTrue(first.configured());
  String value="{\"token\":\"test-value\",\"description\":\"line1\\nline2\"}";
  var protectedValue=first.protect(value);assertTrue(AgentFactCipher.protectedValue(protectedValue));assertFalse(protectedValue.contains("test-value"));
  var restarted=new AgentFactCipher();ReflectionTestUtils.setField(restarted,"encodedKey",encoded);
  assertEquals(value,restarted.reveal(protectedValue));
  assertNotEquals(protectedValue,first.protect(value));
 }
 @Test void readinessDoesNotProveHistoricalDecryptionAndWrongKeyFailsClosed() {
  var first=new AgentFactCipher();ReflectionTestUtils.setField(first,"encodedKey",Base64.getEncoder().encodeToString(new byte[32]));
  var value=first.protect("{\"password\":\"test-value\"}");
  var other=new AgentFactCipher();byte[] key=new byte[32];key[0]=1;ReflectionTestUtils.setField(other,"encodedKey",Base64.getEncoder().encodeToString(key));
  assertTrue(other.configured());assertEquals("QUALITY_FACTS_DECRYPTION_FAILED",assertThrows(MSException.class,()->other.reveal(value)).getMessage());
 }
}
