package io.metersphere.functional.handoff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deployment-owned 256-bit key, no checked-in/default key. AES-GCM binds secrets to project and field. */
@Component
public class HandoffSecrets {
    private final String key;
    public HandoffSecrets(@Value("${metersphere.hifox-handoff.encryption-key:}") String key) { this.key=key; }
    private SecretKeySpec key() {
        try { byte[] bytes=Base64.getDecoder().decode(key);if(bytes.length==32)return new SecretKeySpec(bytes,"AES"); }
        catch(IllegalArgumentException ignored){ }
        throw new IllegalStateException("尚未配置Webhook秘密加密密钥");
    }
    public String seal(String project,String field,String value) { return crypt(project,field,value,true); }
    public String open(String project,String field,String value) { return crypt(project,field,value,false); }
    private String crypt(String project,String field,String value,boolean encrypt) {
        try {
            byte[] iv=new byte[12]; byte[] input;
            if(encrypt){new SecureRandom().nextBytes(iv);input=value.getBytes(StandardCharsets.UTF_8);}
            else{byte[] all=Base64.getDecoder().decode(value);if(all.length<28)throw new IllegalArgumentException();iv=Arrays.copyOf(all,12);input=Arrays.copyOfRange(all,12,all.length);}
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            cipher.updateAAD((project+":"+field).getBytes(StandardCharsets.UTF_8));byte[] result=cipher.doFinal(input);
            if(!encrypt)return new String(result,StandardCharsets.UTF_8);
            byte[] output=Arrays.copyOf(iv,12+result.length);System.arraycopy(result,0,output,12,result.length);return Base64.getEncoder().encodeToString(output);
        } catch(IllegalStateException missing){throw missing;}
        catch(Exception invalid){throw new IllegalStateException("Webhook秘密暂不可用");}
    }
}
