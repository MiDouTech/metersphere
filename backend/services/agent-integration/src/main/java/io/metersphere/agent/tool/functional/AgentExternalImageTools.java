package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentExternalImageService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;

@Configuration
public class AgentExternalImageTools {
    @Bean public AgentMcpToolHandler importCaseImage(AgentExternalImageService service){return handler("functional.attachment.import_url","case",service);}
    @Bean public AgentMcpToolHandler importBugImage(AgentExternalImageService service){return handler("bug.attachment.import_url","bug",service);}
    @Bean public AgentMcpToolHandler importExecutionImage(AgentExternalImageService service){return handler("functional.execution.evidence.import_url","execution",service);}

    private AgentMcpToolHandler handler(String name,String kind,AgentExternalImageService service) {
        return new AgentMcpToolHandler() {
            public String name(){return "metersphere."+name;}
            public String description(){return "Import public HTTP/HTTPS PNG/JPEG (max 5 MiB) into persistent internal storage. No authenticated/private source. "
                    +("execution".equals(kind)?"Stage evidence; visible only after the existing submission gate passes.":"Attach directly to the target attachment area.");}
            public String requiredScope(){return "execution".equals(kind)?AgentTokenScope.FUNCTIONAL_SUBMIT:"bug".equals(kind)?AgentTokenScope.BUG_ATTACHMENT:AgentTokenScope.CASE_ATTACHMENT;}
            public Map<String,Object> inputSchema(){
                var fields=new LinkedHashMap<String,Object>();
                fields.put("projectId",AgentMcpToolSchemas.string(1,128));
                fields.put("bug".equals(kind)?"bugId":"caseId",AgentMcpToolSchemas.string(1,128));
                fields.put("url",AgentMcpToolSchemas.string(1,8192));
                fields.put("requestId",AgentMcpToolSchemas.string(1,128));
                if("execution".equals(kind)) {
                    fields.put("contentHash",AgentMcpToolSchemas.string(64,64));
                    fields.put("stepNum",Map.of("type","integer","minimum",1,"maximum",1000));
                    fields.put("purpose",Map.of("type","string","enum",List.of("BEFORE_STEP","AFTER_STEP")));
                }
                var required=new ArrayList<>(fields.keySet());
                fields.put("fileName",AgentMcpToolSchemas.string(1,200));
                return AgentMcpToolSchemas.object(fields,required);
            }
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),false,false,true);}
            public Object execute(Map<String,Object> args){return service.importImage(kind,args);}
        };
    }
}
