package io.metersphere.agent.security;
import io.metersphere.sdk.exception.MSException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Encrypt sensitive fact values; display redaction never changes the value used for verification. */
@Component
public class AgentFactCipher {
    private static final String PREFIX="factenc:v1:";
    @Value("${agent.execution.facts-key:}") private String encodedKey="";
    public String protect(String value){
        if(value==null || !sensitive(value))return value;
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            return PREFIX+Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }catch(MSException error){throw error;}catch(Exception error){throw new MSException("QUALITY_FACTS_ENCRYPTION_FAILED");}
    }
    private boolean sensitive(String value){
        if(!Objects.equals(value,AgentSensitiveDataSanitizer.sanitize(value)))return true;
        try {return sensitiveNode(new com.fasterxml.jackson.databind.ObjectMapper().readTree(value));}catch(Exception ignored){return false;}
    }
    private boolean sensitiveNode(com.fasterxml.jackson.databind.JsonNode node){
        if(node==null)return false;
        if(node.isObject()){
            var fields=node.fields();while(fields.hasNext()){var field=fields.next();if(field.getKey().matches("(?i)password|passwd|token|secret|authorization|cookie|api[-_]?key") || sensitiveNode(field.getValue()))return true;}
        } else if(node.isArray()){for(var item:node)if(sensitiveNode(item))return true;}
        return false;
    }
    public String reveal(String value){
        if(value==null || !value.startsWith(PREFIX))return value;
        try {
            String[] parts=value.substring(PREFIX.length()).split(":",-1);if(parts.length!=2)throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
        }catch(MSException error){throw error;}catch(Exception error){throw new MSException("QUALITY_FACTS_DECRYPTION_FAILED");}
    }
    public static boolean protectedValue(String value){return value!=null && value.startsWith(PREFIX);}
    /** Configuration readiness only; never returns key material or decrypted facts. */
    public boolean configured() {
        try { key(); return true; } catch (MSException unavailable) { return false; }
    }
    private SecretKeySpec key(){
        try {byte[] bytes=Base64.getDecoder().decode(encodedKey);if(bytes.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(bytes,"AES");}
        catch(Exception error){throw new MSException("QUALITY_FACTS_KEY_NOT_CONFIGURED");}
    }
}
