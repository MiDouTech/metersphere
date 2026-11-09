package io.metersphere.functional.handoff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.metersphere.sdk.util.AesGcmCodec;
import javax.crypto.spec.*;
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
        throw new HandoffSecretUnavailable();
    }
    public boolean configured() {
        try { key(); return true; }
        catch (HandoffSecretUnavailable missing) { return false; }
    }
    public String seal(String project,String field,String value) { return crypt(project,field,value,true); }
    public String open(String project,String field,String value) { return crypt(project,field,value,false); }
    private String crypt(String project,String field,String value,boolean encrypt) {
        try {
            byte[] aad=(project+":"+field).getBytes(StandardCharsets.UTF_8);
            return encrypt?AesGcmCodec.seal(key(),aad,value.getBytes(StandardCharsets.UTF_8))
                    :new String(AesGcmCodec.open(key(),aad,value),StandardCharsets.UTF_8);
        } catch(IllegalStateException missing){throw missing;}
        catch(Exception invalid){throw new HandoffSecretUnavailable();}
    }
}
