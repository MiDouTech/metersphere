package io.metersphere.agent.service;
import io.metersphere.agent.security.AgentSensitiveDataSanitizer;import io.metersphere.sdk.exception.MSException;import org.springframework.stereotype.Service;import java.nio.charset.StandardCharsets;import java.util.*;
@Service public class AgentEvidenceRedactionService {
 private static final Set<String> SENSITIVE_HEADERS=Set.of("authorization","cookie","set-cookie","proxy-authorization","x-api-key");
 public Map<String,String> redactHeaders(Map<String,String> headers){Map<String,String> out=new LinkedHashMap<>();if(headers!=null)headers.forEach((k,v)->out.put(k,SENSITIVE_HEADERS.contains(k.toLowerCase(Locale.ROOT))?"***":AgentSensitiveDataSanitizer.sanitize(v)));return out;}
 public String redactBody(String body){return AgentSensitiveDataSanitizer.sanitize(body);}
 public void redactScreenshot(byte[] bytes,boolean runnerMasked){if(bytes==null||bytes.length<8)throw new MSException("ARTIFACT_IMAGE_INVALID");if(!runnerMasked)throw new MSException("ARTIFACT_REDACTION_REQUIRED");}
 public void scanBeforePersist(byte[] bytes,String contentType,boolean runnerMasked){if(contentType!=null&&contentType.startsWith("image/")){redactScreenshot(bytes,runnerMasked);return;}String raw=new String(bytes,StandardCharsets.UTF_8);String redacted=redactBody(raw);if(!raw.equals(redacted))throw new MSException("ARTIFACT_REDACTION_INCOMPLETE");}
}
