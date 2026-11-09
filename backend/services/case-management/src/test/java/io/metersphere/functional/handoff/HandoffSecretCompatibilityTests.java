package io.metersphere.functional.handoff;

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

class HandoffSecretCompatibilityTests {
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
        assertThrows(IllegalStateException.class,()->service.open("project","token",old));
        String current=service.seal("project","url","new");
        assertEquals("new",new String(AesGcmCodec.open(new SecretKeySpec(key,"AES"),"project:url".getBytes(StandardCharsets.UTF_8),current),StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,()->new HandoffSecrets("").seal("project","url","secret"));
    }
}
