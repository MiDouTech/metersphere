package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.service.AgentLibraryModuleService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentLibraryModuleTools {
    @Bean public AgentMcpToolHandler libraryModuleGet(AgentLibraryModuleService service) {
        return handler("get",true,args->service.get((String)args.get("projectId"),(String)args.get("moduleId")));
    }
    @Bean public AgentMcpToolHandler libraryModuleUpdate(AgentLibraryModuleService service) {
        return handler("update",false,args->service.write(args,false));
    }
    @Bean public AgentMcpToolHandler libraryModuleMove(AgentLibraryModuleService service) {
        return handler("move",false,args->service.write(args,true));
    }
    private AgentMcpToolHandler handler(String action,boolean read,Function<Map<String,Object>,Object> execute) {
        return new AgentMcpToolHandler() {
            public String name(){return "metersphere.functional.module."+action;}
            public String description(){return "get".equals(action)?"Read module metadata and updateTime for concurrency control.":"Modify a case module. Explicit CASE_MODULE_WRITE_V2 grant required. parentId=NONE moves to project root; root itself cannot move.";}
            public String requiredScope(){return read?AgentTokenScope.FUNCTIONAL_READ:AgentTokenScope.CASE_MODULE_WRITE_V2;}
            public Map<String,Object> inputSchema(){
                Map<String,Object> properties=new LinkedHashMap<>();
                properties.put("projectId",AgentMcpToolSchemas.string(1,128));properties.put("moduleId",AgentMcpToolSchemas.string(1,128));
                if(!read){properties.put("expectedUpdateTime",Map.of("type","integer","minimum",0));properties.put("requestId",AgentMcpToolSchemas.string(1,128));properties.put("move".equals(action)?"parentId":"name",AgentMcpToolSchemas.string(1,255));}
                return AgentMcpToolSchemas.object(properties,new ArrayList<>(properties.keySet()));
            }
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),read,false,!read);}
            public Object execute(Map<String,Object> args){return execute.apply(args);}
        };
    }
}
