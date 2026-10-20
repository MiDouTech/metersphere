package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentBusinessAccess;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.sdk.exception.MSException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentLibraryReadTools {
    @Bean public AgentMcpToolHandler libraryCaseOverview(AgentBusinessAccess access, FunctionalCaseService cases) {
        return tool("overview.get",false,args->{
            access.caseProject((String)args.get("caseId"),(String)args.get("projectId"),"FUNCTIONAL_CASE:READ",false,AgentTokenScope.FUNCTIONAL_READ);
            return cases.getOverview((String)args.get("caseId"),AgentTokenContext.get().getUserId());
        });
    }
    @Bean public AgentMcpToolHandler libraryCaseVersions(AgentBusinessAccess access, JdbcTemplate jdbc) {
        return tool("versions.list",true,args->{
            String caseId=(String)args.get("caseId");
            String project=access.caseProject(caseId,(String)args.get("projectId"),"FUNCTIONAL_CASE:READ",false,AgentTokenScope.FUNCTIONAL_READ);
            int current=integer(args,"current",1),size=integer(args,"pageSize",50);
            if(current<1||size<1||size>100)throw new MSException("AGENT_VALIDATION_ERROR");
            String ref=jdbc.queryForObject("SELECT COALESCE(NULLIF(ref_id,''),id) FROM functional_case WHERE id=? AND project_id=? AND deleted=0",String.class,caseId,project);
            String where=" FROM functional_case WHERE project_id=? AND deleted=0 AND COALESCE(NULLIF(ref_id,''),id)=?";
            long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,project,ref);
            var items=jdbc.queryForList("SELECT id AS caseId,project_id AS projectId,version_id AS versionId,ref_id AS refId,name,latest,update_time AS updateTime"+where+" ORDER BY update_time DESC,id ASC LIMIT ? OFFSET ?",project,ref,size,(long)(current-1)*size);
            return Map.of("items",items,"current",current,"pageSize",size,"total",total,"hasMore",(long)current*size<total);
        });
    }
    private int integer(Map<String,Object> args,String field,int fallback) {
        Object value=args.get(field);
        if(value==null)return fallback;
        if(!(value instanceof Number number)||number.doubleValue()!=number.intValue())throw new MSException("AGENT_VALIDATION_ERROR");
        return number.intValue();
    }
    private AgentMcpToolHandler tool(String suffix,boolean paged,Function<Map<String,Object>,Object> action) {
        return new AgentMcpToolHandler() {
            public String name(){return "metersphere.functional."+suffix;}
            public String description(){return "Read authorized case "+suffix+" without changing execution results.";}
            public String requiredScope(){return AgentTokenScope.FUNCTIONAL_READ;}
            public Map<String,Object> inputSchema(){
                var properties=new LinkedHashMap<String,Object>();properties.put("projectId",AgentMcpToolSchemas.string(1,128));properties.put("caseId",AgentMcpToolSchemas.string(1,128));
                if(paged){properties.put("current",Map.of("type","integer","minimum",1));properties.put("pageSize",Map.of("type","integer","minimum",1,"maximum",100));}
                return AgentMcpToolSchemas.object(properties,List.of("projectId","caseId"));
            }
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),true,false,false);}
            public Object execute(Map<String,Object> args){return action.apply(args);}
        };
    }
}
