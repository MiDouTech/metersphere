package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentCaseDeletionService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.sdk.exception.MSException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentCaseDeletionTools {
    @Bean public AgentMcpToolHandler caseDeletionPreview(AgentCaseDeletionService service) {
        return tool("case.delete_preview",AgentTokenScope.CASE_DELETE_V2,
                Map.of("projectId",id(),"caseIds",AgentMcpToolSchemas.stringArray(100),"versionScope",AgentMcpToolSchemas.enumString(List.of("CURRENT","ALL"))),List.of("projectId","caseIds"),
                args->service.preview((String)args.get("projectId"),(List<String>)args.get("caseIds"),(String)args.getOrDefault("versionScope","CURRENT")));
    }
    @Bean public AgentMcpToolHandler caseDeletionExecute(AgentCaseDeletionService service){return deletion("case.delete",service);}
    @Bean public AgentMcpToolHandler caseBatchDeletionExecute(AgentCaseDeletionService service){return deletion("case.batch_delete",service);}
    private AgentMcpToolHandler deletion(String name,AgentCaseDeletionService service){return tool(name,AgentTokenScope.CASE_DELETE_V2,Map.of("previewId",id()),List.of("previewId"),args->service.execute((String)args.get("previewId")));}
    @Bean public AgentMcpToolHandler caseTrashList(AgentCaseDeletionService service){return tool("trash.list",AgentTokenScope.FUNCTIONAL_READ,
            Map.of("projectId",id(),"current",Map.of("type","integer","minimum",1),"pageSize",Map.of("type","integer","minimum",1,"maximum",100)),List.of("projectId"),
            args->service.trash((String)args.get("projectId"),integer(args,"current",1),integer(args,"pageSize",10)));}
    @Bean public AgentMcpToolHandler caseRestore(AgentCaseDeletionService service,io.metersphere.agent.service.AgentOperationExecutor operations,io.metersphere.agent.service.AgentBusinessAccess access){return tool("case.restore",AgentTokenScope.CASE_RESTORE_V2,
            Map.of("projectId",id(),"caseId",id(),"expectedUpdateTime",Map.of("type","integer","minimum",0),"moduleId",id(),"versionScope",AgentMcpToolSchemas.enumString(List.of("CURRENT","ALL")),"requestId",id()),List.of("projectId","caseId","expectedUpdateTime","requestId"),
            args->{Object raw=args.get("expectedUpdateTime");if(!(raw instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue())throw new MSException("请提供有效的用例版本");
                return operations.execute("functional.case.restore",(String)args.get("requestId"),args,Map.class,
                        ()->access.project((String)args.get("projectId"),"FUNCTIONAL_CASE:READ+DELETE",true,AgentTokenScope.CASE_RESTORE_V2),
                        ()->service.restore((String)args.get("projectId"),(String)args.get("caseId"),n.longValue(),(String)args.get("moduleId"),(String)args.getOrDefault("versionScope","CURRENT")));});}
    private Map<String,Object> id(){return AgentMcpToolSchemas.string(1,128);}
    private int integer(Map<String,Object> args,String field,int fallback){Object raw=args.get(field);if(raw==null)return fallback;if(!(raw instanceof Number n)||n.doubleValue()!=n.intValue())throw new MSException("分页参数无效");return n.intValue();}
    private AgentMcpToolHandler tool(String name,String scope,Map<String,Object> fields,List<String> required,Function<Map<String,Object>,Object> action){
        return new AgentMcpToolHandler(){
            public String name(){return "metersphere.functional."+name;}
            public String requiredScope(){return scope;}
            public String description(){return "Versioned case lifecycle: "+name+". Deletion requires a short-lived preview and the owner's explicit decision in confirmationPath; never substitute confirm=true. CURRENT is default; ALL is explicit. Restore never restarts tasks. New delete/restore scopes are not implied by AGENT_ALL.";}
            public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(fields,required);}
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(scope,name.equals("trash.list"),!name.endsWith("preview")&&!name.equals("trash.list"),name.equals("case.delete")||name.equals("case.batch_delete"));}
            public Object execute(Map<String,Object> args){return action.apply(args);}
        };
    }
}
