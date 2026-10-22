package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentCaseImportService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.request.FunctionalCaseImportRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentCaseImportTools {
    @Bean public AgentMcpToolHandler caseImportPrecheck(AgentCaseImportService service) {
        return tool("precheck",Map.of("projectId",id(),"versionId",id(),"moduleId",id(),"cover",Map.of("type","boolean"),"fileName",AgentMcpToolSchemas.string(1,255),"base64",AgentMcpToolSchemas.string(1,69905068),"requestId",AgentMcpToolSchemas.string(1,64)),List.of("projectId","fileName","base64","requestId"),args->{
            String encoded=(String)args.get("base64");
            if(encoded==null||encoded.length()>69905068)throw new MSException("导入文件不能超过50MiB");
            byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException invalid){throw new MSException("导入文件编码无效");}
            var request=JSON.parseObject(JSON.toJSONString(args),FunctionalCaseImportRequest.class);
            var token=AgentTokenContext.get();
            return service.precheck(request,new ImportFile((String)args.get("fileName"),bytes),(String)args.get("requestId"),token.getUserId(),token.getId());
        });
    }
    @Bean public AgentMcpToolHandler caseImportGet(AgentCaseImportService service){return tool("get",Map.of("jobId",id(),"current",Map.of("type","integer","minimum",1),"pageSize",Map.of("type","integer","minimum",1,"maximum",100)),List.of("jobId"),args->{var token=AgentTokenContext.get();return service.get((String)args.get("jobId"),token.getUserId(),token.getId(),number(args,"current",1),number(args,"pageSize",10));});}
    @Bean public AgentMcpToolHandler caseImportCommit(AgentCaseImportService service){return action("commit",service);}
    @Bean public AgentMcpToolHandler caseImportStop(AgentCaseImportService service){return action("stop",service);}
    @Bean public AgentMcpToolHandler caseImportRetry(AgentCaseImportService service){return action("retry",service);}
    private AgentMcpToolHandler action(String action,AgentCaseImportService service){return tool(action,Map.of("jobId",id()),List.of("jobId"),args->{var token=AgentTokenContext.get();return service.action((String)args.get("jobId"),action,token.getUserId(),token.getId());});}
    private int number(Map<String,Object> args,String key,int fallback){Object value=args.get(key);if(value==null)return fallback;if(!(value instanceof Number n)||n.doubleValue()!=n.intValue())throw new MSException("分页参数无效");return n.intValue();}
    private Map<String,Object> id(){return AgentMcpToolSchemas.string(1,128);}
    private AgentMcpToolHandler tool(String action,Map<String,Object> fields,List<String> required,Function<Map<String,Object>,Object> operation){return new AgentMcpToolHandler(){
        public String name(){return "metersphere.functional.import."+action;}
        public String requiredScope(){return AgentTokenScope.CASE_IMPORT_V2;}
        public String description(){return "Durable Excel/XMind import: "+action+". Precheck freezes logical cases, template and overwrite versions (10000 cases/50 MiB, 24h). Commit queues per-case transactions. Get reports every item; retry only pending/failed items, never already committed writes. Explicit CASE_IMPORT_V2 required.";}
        public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(fields,required);}
        public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),"get".equals(action),!"get".equals(action),!"precheck".equals(action));}
        public Object execute(Map<String,Object> args){return operation.apply(args);}
    };}
    private record ImportFile(String name,byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return "application/octet-stream";}
        public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
        public void transferTo(java.io.File file)throws java.io.IOException{java.nio.file.Files.write(file.toPath(),bytes);}
    }
}
