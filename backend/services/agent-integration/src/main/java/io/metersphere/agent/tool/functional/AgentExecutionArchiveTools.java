package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.*;
import io.metersphere.agent.tool.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentExecutionArchiveTools {
    @Bean public AgentMcpToolHandler executionArchivePrepare(AgentExecutionArchiveService service,AgentBusinessAccess access){return tool("prepare",Map.of("projectId",id(),"sourceType",AgentMcpToolSchemas.enumString(List.of("TASK","SUBMISSION")),"sourceId",id()),List.of("projectId","sourceType","sourceId"),args->{
        authorize(access,args);return service.prepare((String)args.get("sourceType"),(String)args.get("sourceId"),AgentTokenContext.get().getUserId(),(String)args.get("projectId"));
    });}
    @Bean public AgentMcpToolHandler executionArchiveGet(AgentExecutionArchiveService service,AgentBusinessAccess access){return action("get",service,access);}
    @Bean public AgentMcpToolHandler executionArchiveRetry(AgentExecutionArchiveService service,AgentBusinessAccess access){return action("retry",service,access);}
    @Bean public AgentMcpToolHandler executionArchiveData(AgentExecutionArchiveService service,AgentBusinessAccess access){return tool("data",Map.of("projectId",id(),"archiveId",id(),"records",Map.of("type","array","minItems",1,"maxItems",500,"items",AgentMcpToolSchemas.object(Map.of("sourceObjectId",AgentMcpToolSchemas.string(1,2000),"reuseConditions",AgentMcpToolSchemas.string(1,2000),"cleanupStatus",AgentMcpToolSchemas.string(1,2000),"values",Map.of("type","object","additionalProperties",true)),List.of("sourceObjectId","reuseConditions","cleanupStatus","values")))),List.of("projectId","archiveId","records"),args->{
        authorize(access,args);String id=(String)args.get("archiveId"),user=AgentTokenContext.get().getUserId();checkProject(args,service.status(id,user));return service.data(id,user,(List<Map<String,Object>>)args.get("records"));
    });}
    private AgentMcpToolHandler action(String action,AgentExecutionArchiveService service,AgentBusinessAccess access){return tool(action,Map.of("projectId",id(),"archiveId",id()),List.of("projectId","archiveId"),args->{
        authorize(access,args);String id=(String)args.get("archiveId"),user=AgentTokenContext.get().getUserId();var result=service.status(id,user);checkProject(args,result);return "retry".equals(action)?service.retry(id,user):result;
    });}
    private void authorize(AgentBusinessAccess access,Map<String,Object> args){access.project((String)args.get("projectId"),"FUNCTIONAL_CASE:READ",false,AgentTokenScope.ARTIFACT_WRITE);}
    private void checkProject(Map<String,Object> args,Map<String,Object> result){if(!Objects.equals(args.get("projectId"),result.get("projectId")))throw AgentBusinessAccess.denied();}
    private Map<String,Object> id(){return AgentMcpToolSchemas.string(1,128);}
    private AgentMcpToolHandler tool(String action,Map<String,Object> fields,List<String> required,Function<Map<String,Object>,Object> operation){return new AgentMcpToolHandler(){
        public String name(){return "metersphere.functional.archive."+action;}
        public String requiredScope(){return AgentTokenScope.ARTIFACT_WRITE;}
        public String description(){return "Archive execution facts independently from results: "+action+". TASK uses an ended managed run; SUBMISSION uses an accepted personal submission. Data requires sourceObjectId/reuseConditions/cleanupStatus and current project file-add permission. Sensitive fields are redacted; reported data is not independently verified. Repeat the same archive; never create fake data when none exists.";}
        public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(fields,required);}
        public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),action.equals("get"),false,true);}
        public Object execute(Map<String,Object> args){return operation.apply(args);}
    };}
}
