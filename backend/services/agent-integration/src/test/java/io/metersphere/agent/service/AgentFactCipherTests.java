package io.metersphere.agent.service;
import io.metersphere.agent.security.AgentFactCipher;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class AgentFactCipherTests {
    @Test void nestedJsonStringsAndSetCookieUseTheSameProtectionRules() {
        var cipher=new AgentFactCipher();
        String fact="[{\"actual\":\"{\\\"password\\\":\\\"synthetic-nested\\\"}\"}]";
        assertEquals("QUALITY_FACTS_KEY_NOT_CONFIGURED",assertThrows(MSException.class,()->cipher.protect(fact)).getMessage());
        assertThrows(MSException.class,()->cipher.protect("{\"set-cookie\":\"session=synthetic\"}"));
        ReflectionTestUtils.setField(cipher,"encodedKey",Base64.getEncoder().encodeToString(new byte[32]));
        assertEquals(fact,cipher.reveal(cipher.protect(fact)));
        assertEquals("{ \"actual\": \"ordinary result\" }",cipher.protect("{ \"actual\": \"ordinary result\" }"));
    }
    @Test void sensitiveFactsRequireKeyAndDecryptExactlyWithoutReturningPlaintextAtRest(){
        var cipher=new AgentFactCipher();String fact="[{\"actual\":\"token=literal-value\"}]";
        assertEquals("QUALITY_FACTS_KEY_NOT_CONFIGURED",assertThrows(MSException.class,()->cipher.protect(fact)).getMessage());
        ReflectionTestUtils.setField(cipher,"encodedKey",Base64.getEncoder().encodeToString(new byte[32]));
        String encrypted=cipher.protect(fact);assertFalse(encrypted.contains("literal-value"));assertEquals(fact,cipher.reveal(encrypted));
        assertNotEquals(encrypted,cipher.protect(fact));assertEquals("plain",cipher.protect("plain"));
        assertThrows(MSException.class,()->cipher.reveal(encrypted.substring(0,encrypted.length()-4)+"AAAA"));
    }
}
