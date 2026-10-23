package io.metersphere.agent.tool.functional;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.service.AgentCaseSearchSnapshotService;
import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolSchemas;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.*;
import java.util.function.Function;

@Configuration
public class AgentCaseSearchSnapshotTools {
    @Bean public AgentMcpToolHandler createCaseSearchSnapshot(AgentCaseSearchSnapshotService service) {
        return tool("snapshot.create", "Freeze a case-library query for complete paged extraction (15 minutes, 10000 cases/50 MiB). Ordinary search remains live. query uses the functional search request fields.",
                Map.of("query", AgentMcpToolSchemas.objectAny()), List.of("query"), args -> service.create(JSON.parseObject(JSON.toJSONString(args.get("query")), AgentCaseSearchRequest.class)));
    }
    @Bean public AgentMcpToolHandler readCaseSearchSnapshot(AgentCaseSearchSnapshotService service) {
        return tool("snapshot.page", "Read frozen case contents. Current authorization and existence are checked; invalid scope requires a new snapshot.",
                Map.of("snapshotId", AgentMcpToolSchemas.string(1,50), "current", Map.of("type","integer","minimum",1), "pageSize", Map.of("type","integer","minimum",1,"maximum",100)),
                List.of("snapshotId"), args -> service.page((String) args.get("snapshotId"), number(args,"current",1), number(args,"pageSize",10)));
    }
    @Bean public AgentMcpToolHandler releaseCaseSearchSnapshot(AgentCaseSearchSnapshotService service) {
        return tool("snapshot.release", "Release your temporary extraction snapshot; does not delete cases.",
                Map.of("snapshotId", AgentMcpToolSchemas.string(1,50)), List.of("snapshotId"), args -> { service.release((String) args.get("snapshotId")); return Map.of("released",true); });
    }
    private int number(Map<String,Object> args,String field,int fallback) {
        Object value=args.get(field); if(value==null)return fallback;
        if(!(value instanceof Number n)||n.doubleValue()!=n.intValue())throw new MSException("分页参数无效");
        return n.intValue();
    }
    private AgentMcpToolHandler tool(String suffix,String description,Map<String,Object> properties,List<String> required,Function<Map<String,Object>,Object> action) {
        return new AgentMcpToolHandler() {
            public String name(){return "metersphere.functional."+suffix;}
            public String description(){return description;}
            public String requiredScope(){return AgentTokenScope.FUNCTIONAL_READ;}
            public Map<String,Object> inputSchema(){return AgentMcpToolSchemas.object(properties,required);}
            public Map<String,Object> annotations(){return AgentMcpToolSchemas.annotations(requiredScope(),suffix.equals("snapshot.page"),false,false);}
            public Object execute(Map<String,Object> args){return action.apply(args);}
        };
    }
}
