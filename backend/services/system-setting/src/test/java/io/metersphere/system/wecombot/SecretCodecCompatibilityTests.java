package io.metersphere.system.wecombot;

import io.metersphere.sdk.util.AesGcmCodec;
import org.junit.jupiter.api.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

/** Independent pre-refactor encryption fixtures, not codec-produced round trips alone. */
class SecretCodecCompatibilityTests {
    static String legacy(byte[] key, byte[] aad, String value) throws Exception {
        byte[] iv=new byte[12]; Arrays.fill(iv,(byte)7);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        if(aad!=null)cipher.updateAAD(aad);
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] result=Arrays.copyOf(iv,12+encrypted.length);System.arraycopy(encrypted,0,result,12,encrypted.length);
        return Base64.getEncoder().encodeToString(result);
    }
    @Test void existingWecomCiphertextAndSecretReferenceRemainCompatible() throws Exception {
        String master=System.getenv("MS_WECOM_SECRET_MASTER_KEY");
        org.junit.jupiter.api.Assumptions.assumeTrue(master!=null,"test runner supplies an isolated nonproduction key");
        byte[] key=MessageDigest.getInstance("SHA-256").digest(master.getBytes(StandardCharsets.UTF_8));
        var service=new WecomSecretService();String old=legacy(key,null,"企微-secret");
        assertEquals("企微-secret",service.resolve(null,old));
        assertEquals(System.getenv("MSP_REUSE_TEST_REFERENCE"),service.resolve("env:MSP_REUSE_TEST_REFERENCE",null));
        assertEquals("new",new String(AesGcmCodec.open(new SecretKeySpec(key,"AES"),null,service.encrypt("new")),StandardCharsets.UTF_8));
    }
    @Test void corruptedAndTruncatedCiphertextIsRejected() throws Exception {
        var key=new SecretKeySpec(new byte[32],"AES");
        String valid=AesGcmCodec.seal(key,null,new byte[]{1,2,3});byte[] bytes=Base64.getDecoder().decode(valid);bytes[bytes.length-1]^=1;
        assertThrows(Exception.class,()->AesGcmCodec.open(key,null,Base64.getEncoder().encodeToString(bytes)));
        assertThrows(Exception.class,()->AesGcmCodec.open(key,null,"AA=="));
    }
}
