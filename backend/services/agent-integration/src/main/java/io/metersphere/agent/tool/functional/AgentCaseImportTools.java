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
        return tool("precheck",Map.of("projectId",id(),"versionId",id(),"moduleId",id(),"cover",Map.of("type","boolean"),"fileName",AgentMcpToolSchemas.string(1,255),"base64",AgentMcpToolSchemas.string(1,69905068),"requestId",AgentMcpToolSchemas.requestId()),List.of("projectId","fileName","base64","requestId"),args->{
            String encoded=(String)args.get("base64");
            if(encoded==null||encoded.length()>69905068)throw new MSException("导入文件不能超过50MiB");
            byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException invalid){throw new MSException("导入文件编码无效");}
            var request=JSON.parseObject(JSON.toJSONString(args),FunctionalCaseImportRequest.class);
            var token=AgentTokenContext.get();
            return service.precheck(request,new ImportFile((String)args.get("fileName"),bytes),(String)args.get("requestId"),token.getUserId(),token.getId());
        });
    }
    @Bean public AgentMcpToolHandler caseImportGet(AgentCaseImportService service,io.metersphere.agent.service.ImportFollowupCoordinator followups){return tool("get",Map.of("jobId",id(),"current",Map.of("type","integer","minimum",1),"pageSize",Map.of("type","integer","minimum",1,"maximum",100)),List.of("jobId"),args->{
        var token=AgentTokenContext.get();String id=(String)args.get("jobId");
        var result=new LinkedHashMap<String,Object>(service.get(id,token.getUserId(),token.getId(),number(args,"current",1),number(args,"pageSize",10)));
        result.put("followup",followups.status(id,token.getUserId(),token.getId()));return result;
    });}
    @Bean public AgentMcpToolHandler caseImportFollowupOptions(io.metersphere.agent.service.ImportFollowupCoordinator followups){return tool("followup.options",Map.of("jobId",id()),List.of("jobId"),args->{
        io.metersphere.agent.security.AgentScopeAssert.assertScope(io.metersphere.agent.constants.AgentTokenScope.PLAN_WRITE);
        io.metersphere.agent.security.AgentScopeAssert.assertScope(io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE);
        var token=AgentTokenContext.get();return followups.options((String)args.get("jobId"),token.getUserId(),token.getId());
    });}
    @Bean public AgentMcpToolHandler caseImportFollowupRetry(io.metersphere.agent.service.ImportFollowupCoordinator followups){return tool("followup.retry",Map.of("jobId",id()),List.of("jobId"),args->{
        var token=AgentTokenContext.get();String id=(String)args.get("jobId");followups.retry(id,token.getUserId(),token.getId());return followups.status(id,token.getUserId(),token.getId());
    });}
    @Bean public AgentMcpToolHandler caseImportCommit(AgentCaseImportService service,io.metersphere.agent.service.ImportFollowupCoordinator followups){return action("commit",service,followups);}
    @Bean public AgentMcpToolHandler caseImportStop(AgentCaseImportService service,io.metersphere.agent.service.ImportFollowupCoordinator followups){return action("stop",service,followups);}
    @Bean public AgentMcpToolHandler caseImportRetry(AgentCaseImportService service,io.metersphere.agent.service.ImportFollowupCoordinator followups){return action("retry",service,followups);}
    private Map<String,Object> followupSchema(){
        return AgentMcpToolSchemas.object(Map.of(
            "policy",Map.of("type","string","enum",List.of("CREATE_NEW_PAIR","APPEND_EXISTING","IMPORT_ONLY")),
            "targetGroupId",id(),"expectedGroupRevision",Map.of("type","integer","minimum",1),
            "planName",AgentMcpToolSchemas.string(1,255),"reviewName",AgentMcpToolSchemas.string(1,255),
            "reviewers",Map.of("type","array","minItems",1,"maxItems",100,"items",id()),
            "reviewPassRule",Map.of("type","string","enum",List.of("SINGLE","MULTIPLE"))),List.of("policy"));
    }
    private AgentMcpToolHandler action(String action,AgentCaseImportService service,io.metersphere.agent.service.ImportFollowupCoordinator followups){
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("jobId",id());if("commit".equals(action))fields.put("followup",followupSchema());
        return tool(action,fields,List.of("jobId"),args->{
            var token=AgentTokenContext.get();String id=(String)args.get("jobId");
            if("commit".equals(action)&&args.get("followup")!=null){
                io.metersphere.agent.dto.ImportFollowupRequest request;
                try {request=JSON.parseObject(JSON.toJSONString(args.get("followup")),io.metersphere.agent.dto.ImportFollowupRequest.class);}
                catch(Exception invalid){throw new MSException("AGENT_VALIDATION_ERROR");}
                followups.prepare(id,token.getUserId(),request,token.getId());
            }
            var result=service.action(id,action,token.getUserId(),token.getId());
            if("retry".equals(action))followups.retry(id,token.getUserId(),token.getId());
            return result;
        });
    }
    private int number(Map<String,Object> args,String key,int fallback){Object value=args.get(key);if(value==null)return fallback;if(!(value instanceof Number n)||n.doubleValue()!=n.intValue())throw new MSException("分页参数无效");return n.intValue();}
    private Map<String,Object> id(){return AgentMcpToolSchemas.string(1,128);}
    private AgentMcpToolHandler tool(String action,Map<String,Object> fields,List<String> required,Function<Map<String,Object>,Object> operation){return new AgentMcpToolHandler(){
        public String name(){return "metersphere.functional.import."+action;}
        public String requiredScope(){return AgentTokenScope.CASE_IMPORT_V2;}
        public String description(){return "Durable Excel/XMind import: "+action+". Precheck freezes logical cases, template and overwrite versions (10000 cases/50 MiB, 24h). Commit queues per-case transactions. Get reports every item; retry only pending/failed items, never already committed writes. Explicit CASE_IMPORT_V2 required. Optional commit.followup creates or appends a plan/review pair; PLAN_WRITE and REVIEW_WRITE are additionally required. Missing followup preserves legacy import-only behavior; get reports LEGACY.";}
        public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(fields,required);}
        public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),("get".equals(action)||"followup.options".equals(action)),!("get".equals(action)||"followup.options".equals(action)),!"precheck".equals(action));}
        public Object execute(Map<String,Object> args){return operation.apply(args);}
    };}
    private record ImportFile(String name,byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return "application/octet-stream";}
        public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
        public void transferTo(java.io.File file)throws java.io.IOException{java.nio.file.Files.write(file.toPath(),bytes);}
    }
}
