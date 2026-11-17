package io.metersphere.functional.handoff;

import io.metersphere.sdk.util.AesGcmCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class HandoffSecretCompatibilityTests {
    @ParameterizedTest @ValueSource(ints={0,1,12,27})
    void shortBase64CiphertextIsFormatFailureForBothFields(int size){
        var secrets=new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32]));
        String value=Base64.getEncoder().encodeToString(new byte[size]);
        for(String field:new String[]{"url","token"}){
            var failure=assertThrows(HandoffSecretUnavailable.class,()->secrets.open("project",field,value));
            assertEquals(HandoffSecretUnavailable.Reason.CIPHERTEXT_FORMAT,failure.reason());assertNull(failure.getCause());
        }
    }
    @ParameterizedTest @ValueSource(strings={"url","token"})
    void malformedAuthenticatedAndNormalValuesRemainDistinct(String field){
        var secrets=new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32]));
        assertEquals(HandoffSecretUnavailable.Reason.CIPHERTEXT_FORMAT,assertThrows(HandoffSecretUnavailable.class,()->secrets.open("project",field,"LEAK_SENTINEL_INVALID!")).reason());
        assertEquals(HandoffSecretUnavailable.Reason.AUTHENTICATION_FAILED,assertThrows(HandoffSecretUnavailable.class,()->secrets.open("project",field,Base64.getEncoder().encodeToString(new byte[28]))).reason());
        for(String plaintext:new String[]{"","LEAK_SENTINEL_NORMAL"}){
            String encrypted=secrets.seal("project",field,plaintext);
            assertEquals(plaintext,secrets.open("project",field,encrypted));
            assertEquals(HandoffSecretUnavailable.Reason.AUTHENTICATION_FAILED,assertThrows(HandoffSecretUnavailable.class,()->secrets.open("different-scope",field,encrypted)).reason());
        }
    }
    @Test void missingKeyReportsDeploymentFailureInsteadOfStateConflict() {
        var secrets=new HandoffSecrets("");
        assertFalse(secrets.configured());
        var failure=assertThrows(HandoffSecretUnavailable.class,()->secrets.seal("project","url","private-url"));
        assertEquals(HandoffSecretUnavailable.Reason.KEY_CONFIGURATION,failure.reason());
        var response=new io.metersphere.functional.controller.HandoffExceptionHandler().secretUnavailable(failure);
        assertEquals(503,response.getStatusCode().value());
        assertEquals("HANDOFF_ENCRYPTION_UNAVAILABLE",response.getBody().get("code"));
        assertFalse(response.getBody().toString().contains("private-url"));
        assertFalse(new HandoffSecrets("invalid").configured());
        assertTrue(new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32])).configured());
    }
    static String legacy(byte[] key, byte[] aad, String value) throws Exception {
        byte[] iv=new byte[12]; Arrays.fill(iv,(byte)7);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        if(aad!=null)cipher.updateAAD(aad);
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] result=Arrays.copyOf(iv,12+encrypted.length);System.arraycopy(encrypted,0,result,12,encrypted.length);
        return Base64.getEncoder().encodeToString(result);
    }
    @Test void existingHandoffCiphertextAndAadRemainCompatible() throws Exception {
        byte[] key=new byte[32];var service=new HandoffSecrets(Base64.getEncoder().encodeToString(key));
        String old=legacy(key,"project:url".getBytes(StandardCharsets.UTF_8),"中文秘密🙂");
        assertEquals("中文秘密🙂",service.open("project","url",old));
        assertThrows(IllegalStateException.class,()->service.open("other","url",old));
        assertEquals(HandoffSecretUnavailable.Reason.AUTHENTICATION_FAILED,assertThrows(HandoffSecretUnavailable.class,()->service.open("other","url",old)).reason());
        assertThrows(IllegalStateException.class,()->service.open("project","token",old));
        String current=service.seal("project","url","new");
        assertEquals("new",new String(AesGcmCodec.open(new SecretKeySpec(key,"AES"),"project:url".getBytes(StandardCharsets.UTF_8),current),StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,()->new HandoffSecrets("").seal("project","url","secret"));
    }
}
