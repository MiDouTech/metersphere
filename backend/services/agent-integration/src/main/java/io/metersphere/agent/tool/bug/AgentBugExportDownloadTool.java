package io.metersphere.agent.tool.bug;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentBugExportDownloadService;
import io.metersphere.agent.tool.*;
import io.metersphere.sdk.exception.MSException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class AgentBugExportDownloadTool implements AgentMcpToolHandler {
    @Resource private AgentBugExportDownloadService downloads;
    public String name() { return "metersphere.bug.export.download"; }
    public String requiredScope() { return AgentTokenScope.BUG_READ; }
    public String description() { return "Read an owned unexpired BUG_EXPORT file in bounded chunks; assemble in client file transport and verify SHA-256. Each chunk rechecks export permission."; }
    public Map<String, Object> inputSchema() {
        return AgentMcpToolSchemas.object(Map.of("projectId", AgentMcpToolSchemas.string(1,128),
                "fileId", AgentMcpToolSchemas.string(1,128),
                "offset", Map.of("type","integer","minimum",0,"maximum",5242880,"default",0),
                "length", Map.of("type","integer","minimum",1,"maximum",65536,"default",32768)), List.of("projectId","fileId"));
    }
    public Map<String, Object> outputSchema() {
        var fields = new LinkedHashMap<String,Object>();
        fields.put("fileId", AgentMcpToolSchemas.string(1,128));
        fields.put("contentType", Map.of("type","string"));
        fields.put("offset", Map.of("type","integer","minimum",0));
        fields.put("nextOffset", Map.of("type","integer","minimum",0));
        fields.put("sizeBytes", Map.of("type","integer","minimum",0,"maximum",5242880));
        fields.put("eof", Map.of("type","boolean"));
        fields.put("sha256", Map.of("type","string","pattern","^[a-f0-9]{64}$"));
        fields.put("base64", Map.of("type","string","maxLength",87384));
        return AgentMcpToolSchemas.object(Map.of("result", AgentMcpToolSchemas.object(fields,
                List.copyOf(fields.keySet()))), List.of("result"));
    }
    public Map<String, Object> annotations() { return AgentMcpToolSchemas.annotations(requiredScope(), true, false, true); }
    public Object execute(Map<String, Object> args) {
        if (!Set.of("projectId","fileId","offset","length").containsAll(args.keySet())) throw new MSException("AGENT_VALIDATION_ERROR");
        return downloads.download(text(args,"projectId"), text(args,"fileId"), integer(args,"offset",0,0,5242880),
                (int) integer(args,"length",32768,1,65536));
    }
    private String text(Map<String,Object> args, String key) {
        if (!(args.get(key) instanceof String value) || value.isBlank() || value.length()>128) throw new MSException("AGENT_VALIDATION_ERROR");
        return value;
    }
    private long integer(Map<String,Object> args, String key, long fallback, long min, long max) {
        if (!args.containsKey(key)) return fallback;
        Object value=args.get(key);
        if (!(value instanceof Number number)) throw new MSException("AGENT_VALIDATION_ERROR");
        try {
            long result=new java.math.BigDecimal(number.toString()).longValueExact();
            if (result<min || result>max) throw new ArithmeticException();
            return result;
        } catch (NumberFormatException | ArithmeticException invalid) { throw new MSException("AGENT_VALIDATION_ERROR"); }
    }
}
